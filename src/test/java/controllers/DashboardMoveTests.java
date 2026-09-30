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

/**
 * Drives the api the way the dashboard does: with the session cookie plus the
 * csrf token out of the rendered page, not with an access token. That
 * combination is what {@link filters.ApiAccessFilter} treats differently, so it
 * needs a test of its own.
 */
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

    /** The token the browser would send, read out of the page it was rendered into. */
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
        //given the token of the currently rendered page
        String token = tokenFromDashboard();

        //when the bookmark is dropped onto another category
        TestResponse response = TestRequest.put("/api/v1/items")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("uid", itemUid, "category", targetUid)))
                .execute();

        //then
        assertThat(response.getStatusCode(), equalTo(200));

        DataService dataService = Application.getInstance(DataService.class);
        String userUid = dataService.findUser(USERNAME).getUid();
        assertThat(dataService.findItem(itemUid, userUid).getCategoryUid(), equalTo(targetUid));
    }

    @Test
    public void testBulkMoveFromTheDashboard() {
        //given
        String token = tokenFromDashboard();

        //when several bookmarks are dropped at once
        TestResponse response = TestRequest.put("/api/v1/items/bulk/move")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("uids", List.of(itemUid), "category", targetUid)))
                .execute();

        //then
        assertThat(response.getStatusCode(), equalTo(200));
    }

    @Test
    public void testSearchFromTheDashboard() {
        //given
        String token = tokenFromDashboard();

        //when the command palette searches
        TestResponse response = TestRequest.get("/api/v1/search?q=bar")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response.getStatusCode(), equalTo(200));
    }

    @Test
    public void testTheTokenSurvivesAnEarlierApiCall() {
        //given the page was rendered once, and something else already used its token
        String token = tokenFromDashboard();

        TestResponse first = TestRequest.get("/api/v1/search?q=bar")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .execute();
        assertThat(first.getStatusCode(), equalTo(200));

        //when a move follows with the same token, as it does in the browser
        TestResponse response = TestRequest.put("/api/v1/items")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("uid", itemUid, "category", targetUid)))
                .execute();

        //then
        assertThat(response.getStatusCode(), equalTo(200));
    }

    @Test
    public void testMovingIntoTheSameCategory() {
        //given
        String token = tokenFromDashboard();

        //when the bookmark is dropped onto the category it already sits in
        TestResponse response = TestRequest.put("/api/v1/items")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("uid", itemUid, "category", inboxUid)))
                .execute();

        //then
        assertThat(response.getStatusCode(), equalTo(200));
    }

    @Test
    public void testTheTokenSurvivesThePollTheDashboardRuns() {
        //given the page was rendered, and the poll loop has fired once - it runs
        //every three seconds for as long as the dashboard is open
        String token = tokenFromDashboard();

        TestResponse poll = TestRequest.post("/api/v1/categories/poll")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("count", "1", "category", inboxUid)))
                .execute();
        assertThat(poll.getStatusCode(), anyOf(equalTo(200), equalTo(304)));

        //when the user drops a bookmark on another category afterwards
        TestResponse response = TestRequest.put("/api/v1/items")
                .withCookie(authentication)
                .withCookie(session)
                .withHeader("x-csrf-token", token)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("uid", itemUid, "category", targetUid)))
                .execute();

        //then
        assertThat(response.getStatusCode(), equalTo(200));
    }

    @Test
    public void testAMoveWithoutTheCsrfTokenIsRejected() {
        //when the header is missing, the cookie alone must not be enough
        TestResponse response = TestRequest.put("/api/v1/items")
                .withCookie(authentication)
                .withCookie(session)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("uid", itemUid, "category", targetUid)))
                .execute();

        //then
        assertThat(response.getStatusCode(), equalTo(401));
    }
}
