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
import io.undertow.util.StatusCodes;
import models.Category;
import models.Item;
import models.User;
import utils.Utils;
import models.enums.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.HttpCookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Renders every dashboard page of a signed in user. A template that references
 * a variable the controller does not render fails at render time, not at
 * compile time, which is what these tests are here to catch.
 */
@ExtendWith({TestRunner.class})
public class DashboardRenderTests {
    private static final String USERNAME = "render@bar.com";
    private static final String PASSWORD = "bar";
    private static HttpCookie authentication;
    private static HttpCookie session;
    private static String trashUid;
    private static String itemUid;

    @BeforeAll
    public static void init() {
        Datastore datastore = Application.getInstance(Datastore.class);
        datastore.dropCollection(User.class);
        datastore.dropCollection(Category.class);
        datastore.dropCollection(Item.class);

        User user = new User(USERNAME);
        user.setPassword(CommonUtils.hashArgon2(PASSWORD, user.getSalt()));
        datastore.save(user);

        Category inbox = new Category(Const.INBOX, user.getUid(), Role.INBOX);
        Category reading = new Category("Reading list", user.getUid(), Role.CUSTOM);
        Category trash = new Category(Const.TRASH, user.getUid(), Role.TRASH);
        datastore.save(inbox);
        datastore.save(reading);
        datastore.save(trash);
        trashUid = trash.getUid();

        Item deleted = Item.create()
                .withUserUid(user.getUid())
                .withCategoryUid(trash.getUid())
                .withUrl("https://example.com/deleted")
                .withImage("https://example.com/preview.png")
                .withTitle("A deleted bookmark")
                .withDomain("example.com")
                .withDescription("description");
        //the retention counts from here, which is what deleteItem() sets
        deleted.setTrashed(LocalDateTime.now());
        datastore.save(deleted);

        //an item that reached the trash before the timestamp existed
        datastore.save(Item.create()
                .withUserUid(user.getUid())
                .withCategoryUid(trash.getUid())
                .withUrl("https://example.com/old")
                .withImage("https://example.com/preview.png")
                .withTitle("A bookmark without a trashed date")
                .withDomain("example.com")
                .withDescription("description"));

        Item inboxItem = Item.create()
                .withUserUid(user.getUid())
                .withCategoryUid(inbox.getUid())
                .withUrl("https://example.com/a-rather-long-article-title")
                .withImage("https://example.com/preview.png")
                .withTitle("A bookmark with a title long enough to wrap onto a second line")
                .withDomain("example.com")
                .withDescription("description");
        datastore.save(inboxItem);
        itemUid = inboxItem.getUid();

        signIn();
    }

    private static void signIn() {
        Csrf csrf = TestUtils.getCsrf();
        Multimap<String, String> form = ArrayListMultimap.create();
        form.put("username", USERNAME);
        form.put("password", PASSWORD);
        form.put(io.mangoo.constants.Const.CSRF_TOKEN, csrf.token());

        TestResponse response = TestRequest.post("/auth/login")
                .withCookie(csrf.cookie())
                .withForm(form)
                .execute();

        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));

        Config config = Application.getInstance(Config.class);
        authentication = response.getCookie(config.getAuthenticationCookieName());
        session = response.getCookie(config.getSessionCookieName());
    }

    private TestResponse get(String path) {
        TestResponse request = TestRequest.get(path).withCookie(authentication);
        if (session != null) {
            request = request.withCookie(session);
        }
        return request.execute();
    }

    @Test
    public void testDashboardRenders() {
        //when
        TestResponse response = get("/dashboard");

        //then
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("class=\"sidebar\""));
        assertThat(response.getContent(), containsString("class=\"shelf\""));
        assertThat(response.getContent(), containsString("A bookmark with a title long enough"));
        //the tile itself is the link, and it opens in a new tab
        assertThat(response.getContent(), containsString("class=\"item__link\" href=\"https://example.com/a-rather-long-article-title\" target=\"_blank\""));
        assertThat(response.getContent(), not(containsString("undefined")));
        assertThat(response.getContent(), not(containsString("FreeMarker")));
    }

    @Test
    public void testDashboardHasNoUnresolvedMessages() {
        //when
        TestResponse response = get("/dashboard");

        //then a missing translation renders as the key itself
        assertThat(response.getContent(), not(containsString("layout.")));
        assertThat(response.getContent(), not(containsString("dashboard.")));
    }

    @Test
    public void testProfileRenders() {
        //when
        TestResponse response = get("/dashboard/profile");

        //then
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString(USERNAME));
        assertThat(response.getContent(), containsString("class=\"sheet\""));
        assertThat(response.getContent(), not(containsString("undefined")));
    }

    @Test
    public void testIoRenders() {
        //when
        TestResponse response = get("/dashboard/io");

        //then
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("class=\"drop\""));
        assertThat(response.getContent(), not(containsString("undefined")));
    }

    @Test
    public void testAboutRenders() {
        //when
        TestResponse response = get("/dashboard/about");

        //then
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Filed Papers"));
        assertThat(response.getContent(), not(containsString("undefined")));
    }

    @Test
    public void testEveryPageShipsTheIconSprite() {
        //the sprite replaced the icon webfont, so a page without it has no icons
        for (String path : List.of("/dashboard", "/dashboard/profile", "/dashboard/io", "/dashboard/about")) {
            TestResponse response = get(path);
            assertThat(path, response.getContent(), containsString("<symbol id=\"i-inbox\""));
            assertThat(path, response.getContent(), not(containsString("font-awesome")));
        }
    }

    @Test
    public void testAssetsCarryACacheBuster() {
        //a browser that kept the stylesheet or the script of an earlier build
        //makes every fix look like it did not work
        TestResponse response = get("/dashboard");

        assertThat(response.getContent(), containsString("/assets/css/app.css?v="));
        assertThat(response.getContent(), containsString("/assets/js/app.js?v="));
        assertThat(response.getContent(), containsString("/assets/js/api.js?v="));
    }

    @Test
    public void testTheTileItselfIsTheDragHandle() {
        //A tile is a link. If the drag starts on the link instead of on the
        //article, the browser sends the url as the payload and the move fails
        //with "itemUid is null or invalid".
        TestResponse response = get("/dashboard");

        assertThat(response.getContent(), containsString("<article class=\"item\" draggable=\"true\""));
    }

    @Test
    public void testTrashShowsTheConfiguredRetention() {
        //when
        TestResponse response = get("/dashboard/" + trashUid);

        //then the retention comes from application.trash.retention, it used to
        //be a hard coded "30 days" that was wrong for every configuration
        Map<String, String> retention = utils.Utils.getTrashRetentionLabel();
        //in english the unit key and its translation are the same word
        String expected = retention.get("value") + " " + retention.get("unit");

        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("deleted automatically after " + expected));
        assertThat(response.getContent(), containsString("Everything in here is deleted after " + expected));
        assertThat(response.getContent(), not(containsString("30 days")));
    }

    @Test
    public void testATrashedBookmarkShowsWhenItGoes() {
        //when
        TestResponse response = get("/dashboard/" + trashUid);

        //then the tile says how long this one bookmark still has, instead of
        //when it was added - the retention starts at the moment it was trashed
        assertThat(response.getContent(), containsString("item__expiry"));
        //prettytime words the distance itself ("3 days from now", "in 3 Tagen"),
        //so the assertion stays on the part the template contributes
        assertThat(response.getContent(), containsString("item__expiry\">deleted "));
    }

    @Test
    public void testAnInboxBookmarkHasNoDeletionDate() {
        //when
        TestResponse response = get("/dashboard");

        //then
        assertThat(response.getContent(), not(containsString("item__expiry")));
    }

    @Test
    public void testATrashedBookmarkWithoutATimestampFallsBackToItsAddedDate() {
        //given a bookmark that was trashed before the timestamp was recorded
        TestResponse response = get("/dashboard/" + trashUid);

        //then it still renders, it just cannot say when it goes
        assertThat(response.getContent(), containsString("A bookmark without a trashed date"));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
    }

    @Test
    public void testTheItemFragmentIsJustTheTile() {
        //when the dashboard asks for a single bookmark it heard about
        TestResponse response = get("/dashboard/item/" + itemUid);

        //then it gets the tile and nothing around it - no layout, no sidebar
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("<article class=\"item\" draggable=\"true\""));
        assertThat(response.getContent(), containsString("A bookmark with a title long enough"));
        assertThat(response.getContent(), not(containsString("<html")));
        assertThat(response.getContent(), not(containsString("class=\"sidebar\"")));
    }

    @Test
    public void testTheItemFragmentLooksLikeTheOneInTheList() {
        //the list and the fragment come from the same macro, so a tile has to
        //carry the same hooks either way - the javascript relies on them
        String fromList = get("/dashboard").getContent();
        String fragment = get("/dashboard/item/" + itemUid).getContent();

        for (String hook : List.of("item__link", "item-move", "item-archive", "item-trash", "item__pick")) {
            assertThat(hook, fromList, containsString(hook));
            assertThat(hook, fragment, containsString(hook));
        }
    }

    @Test
    public void testAnItemOfSomebodyElseIsNotRendered() {
        //given an item that belongs to another user
        Datastore datastore = Application.getInstance(Datastore.class);
        User other = new User("stranger@bar.com");
        datastore.save(other);

        Item foreign = Item.create()
                .withUserUid(other.getUid())
                .withCategoryUid(Utils.randomString())
                .withUrl("https://example.com/secret")
                .withImage("https://example.com/preview.png")
                .withTitle("Not yours")
                .withDomain("example.com")
                .withDescription("description");
        datastore.save(foreign);

        //when
        TestResponse response = get("/dashboard/item/" + foreign.getUid());

        //then
        assertThat(response.getStatusCode(), equalTo(StatusCodes.NOT_FOUND));
        assertThat(response.getContent(), not(containsString("Not yours")));
    }

    @Test
    public void testAnUnknownItemIsNotFound() {
        assertThat(get("/dashboard/item/" + Utils.randomString()).getStatusCode(), equalTo(StatusCodes.NOT_FOUND));
    }
}
