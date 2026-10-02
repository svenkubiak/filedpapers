package controllers;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import constants.Const;
import helpers.Csrf;
import helpers.TestUtils;
import io.mangoo.core.Application;
import io.mangoo.core.Config;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.test.TestRunner;
import io.mangoo.test.http.TestRequest;
import io.mangoo.test.http.TestResponse;
import io.mangoo.utils.CommonUtils;
import io.mangoo.utils.JsonUtils;
import models.Category;
import models.Item;
import models.User;
import models.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import services.DataService;

import java.net.HttpCookie;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/** Authenticates like the browser (session cookie + page csrf token), which ApiAccessFilter handles differently from access tokens. */
@ExtendWith({TestRunner.class})
public class DashboardMoveTests {
    private static final Pattern CSRF = Pattern.compile("id=\"x-csrf-token\"[^>]*data-csrf-token='([^']*)'");
    private static final String USERNAME = "move@bar.com";

    private HttpCookie authentication;
    private HttpCookie session;
    private String itemUid;
    private String inboxUid;
    private String targetUid;

    @BeforeEach
    public void init() {
        Datastore datastore = Application.getInstance(Datastore.class);
        datastore.dropCollection(User.class);
        datastore.dropCollection(Category.class);
        datastore.dropCollection(Item.class);

        User user = new User(USERNAME);
        user.setPassword(CommonUtils.hashArgon2("bar", user.getSalt()));
        datastore.save(user);

        Category inbox = new Category(Const.INBOX, user.getUid(), Role.INBOX);
        Category target = new Category("Engineering", user.getUid(), Role.CUSTOM);
        Category trash = new Category(Const.TRASH, user.getUid(), Role.TRASH);
        datastore.save(inbox);
        datastore.save(target);
        datastore.save(trash);

        Item item = Item.create()
                .withUserUid(user.getUid())
                .withCategoryUid(inbox.getUid())
                .withUrl("https://example.com")
                .withImage("foo")
                .withTitle("bar")
                .withDomain("example.com")
                .withDescription("barfoo");
        datastore.save(item);

        itemUid = item.getUid();
        inboxUid = inbox.getUid();
        targetUid = target.getUid();

        Csrf csrf = TestUtils.getCsrf();
        Multimap<String, String> form = ArrayListMultimap.create();
        form.put("username", USERNAME);
        form.put("password", "bar");
        form.put(io.mangoo.constants.Const.CSRF_TOKEN, csrf.token());

        TestResponse login = TestRequest.post("/auth/login")
                .withCookie(csrf.cookie())
                .withForm(form)
                .execute();

        Config config = Application.getInstance(Config.class);
        authentication = login.getCookie(config.getAuthenticationCookieName());
        session = login.getCookie(config.getSessionCookieName());
    }

    private String tokenFromDashboard() {
        TestResponse dashboard = get("/dashboard");
        Config config = Application.getInstance(Config.class);

        HttpCookie refreshed = dashboard.getCookie(config.getSessionCookieName());
        if (refreshed != null) {
            session = refreshed;
        }

        Matcher matcher = CSRF.matcher(dashboard.getContent());
        assertThat("the dashboard has to carry a csrf token for the api", matcher.find(), equalTo(true));
        return matcher.group(1);
    }

    private TestResponse get(String path) {
        TestResponse request = TestRequest.get(path).withCookie(authentication);
        if (session != null) {
            request = request.withCookie(session);
        }
        return request.execute();
    }

    @Test
    public void testMoveFromTheDashboard() {
        String token = tokenFromDashboard();

        TestResponse response = TestRequest.put("/api/v1/items")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("uid", itemUid, "category", targetUid)))
                .execute();

        assertThat(response.getStatusCode(), equalTo(200));

        DataService dataService = Application.getInstance(DataService.class);
        String userUid = dataService.findUser(USERNAME).getUid();
        assertThat(dataService.findItem(itemUid, userUid).getCategoryUid(), equalTo(targetUid));
    }

    @Test
    public void testBulkMoveFromTheDashboard() {
        String token = tokenFromDashboard();

        TestResponse response = TestRequest.put("/api/v1/items/bulk/move")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("uids", List.of(itemUid), "category", targetUid)))
                .execute();

        assertThat(response.getStatusCode(), equalTo(200));
    }

    @Test
    public void testSearchFromTheDashboard() {
        String token = tokenFromDashboard();

        TestResponse response = TestRequest.get("/api/v1/search?q=bar")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .execute();

        assertThat(response.getStatusCode(), equalTo(200));
    }

    @Test
    public void testTheTokenSurvivesAnEarlierApiCall() {
        String token = tokenFromDashboard();

        TestResponse first = TestRequest.get("/api/v1/search?q=bar")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .execute();
        assertThat(first.getStatusCode(), equalTo(200));

        TestResponse response = TestRequest.put("/api/v1/items")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("uid", itemUid, "category", targetUid)))
                .execute();

        assertThat(response.getStatusCode(), equalTo(200));
    }

    @Test
    public void testMovingIntoTheSameCategory() {
        String token = tokenFromDashboard();

        TestResponse response = TestRequest.put("/api/v1/items")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("uid", itemUid, "category", inboxUid)))
                .execute();

        assertThat(response.getStatusCode(), equalTo(200));
    }

    @Test
    public void testTheApiRejectsCookiesAfterLogout() {
        String token = tokenFromDashboard();

        TestRequest.get("/auth/logout")
                .withCookie(authentication)
                .withCookie(session)
                .execute();

        TestResponse response = TestRequest.get("/api/v1/search?q=bar")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .execute();

        assertThat(response.getStatusCode(), equalTo(401));
    }

    @Test
    public void testAMoveWithoutTheCsrfTokenIsRejected() {
        TestResponse response = TestRequest.put("/api/v1/items")
                .withCookie(authentication)
                .withCookie(session)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("uid", itemUid, "category", targetUid)))
                .execute();

        assertThat(response.getStatusCode(), equalTo(401));
    }
}
