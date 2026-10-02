package controllers.api;

import constants.Const;
import controllers.TestExtension;
import io.mangoo.core.Application;
import io.mangoo.persistence.interfaces.Datastore;
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
import utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith({TestExtension.class})
public class ItemsControllerV1Tests {
    private static final long ASYNC_ADD_TIMEOUT_SECONDS = 40;
    private static Datastore datastore;
    private static String ACCESS_TOKEN;
    private static String USER_UID;
    private static String INBOX_UID;
    private static String TRASH_UID;
    private static String ITEM_UID;
    private static String TEST_UID;

    @BeforeEach
    public void init() {
        datastore = Application.getInstance(Datastore.class);
        datastore.dropCollection(Category.class);
        datastore.dropCollection(Item.class);
        datastore.dropCollection(User.class);

        User user = new User("foo@bar.com");
        user.setPassword(CommonUtils.hashArgon2("bar", user.getSalt()));
        datastore.save(user);

        Category inbox = new Category(Const.INBOX, user.getUid(), Role.INBOX);
        Category test = new Category("test", user.getUid(), Role.CUSTOM);
        Category trash = new Category(Const.TRASH, user.getUid(), Role.TRASH);

        datastore.save(inbox);
        datastore.save(test);
        datastore.save(trash);

        String username = "foo@bar.com";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        Map<String, String> tokens = JsonUtils.toFlatMap(response.getContent());
        ACCESS_TOKEN = tokens.get("accessToken");
        USER_UID = user.getUid();
        INBOX_UID = inbox.getUid();
        TRASH_UID = trash.getUid();
        TEST_UID = test.getUid();

        Item item = Item.create()
                .withUserUid(USER_UID)
                .withCategoryUid(INBOX_UID)
                .withUrl("https://svenkubiak.de")
                .withImage("foo")
                .withTitle("bar")
                .withDomain("foobar")
                .withDescription("barfoo");

        datastore.save(item);

        ITEM_UID = item.getUid();
    }

    @Test
    void testListUnauthorized() {
        TestResponse response = TestRequest.get("/api/v1/items/" + Utils.randomString())
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testList() {
        TestResponse response = TestRequest.get("/api/v1/items/" + INBOX_UID)
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isNotEmpty();
        assertThatJson(response.getContent()).inPath("$.items[0]").isEqualTo("""
                {
                  "url": "${json-unit.any-string}",
                  "uid": "${json-unit.any-string}",
                  "added": "${json-unit.any-string}",
                  "domain": "${json-unit.any-string}",
                  "title": "${json-unit.any-string}",
                  "image": "${json-unit.any-string}",
                  "description": "${json-unit.any-string}",
                  "sort": "${json-unit.any-number}",
                  "archived": "${json-unit.any-boolean}"
                }
        """);
    }

    @Test
    void testETag() {
        TestResponse response = TestRequest.get("/api/v1/items/" + INBOX_UID)
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getHeader("ETag")).isNotEmpty();

        String etag = response.getHeader("ETag");

        response = TestRequest.get("/api/v1/items/" + INBOX_UID)
                .withHeader("Authorization", ACCESS_TOKEN)
                .withHeader("If-None-Match", etag)
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(304);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testTrashUnauthorized() {
        TestResponse response = TestRequest.delete("/api/v1/items/trash")
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testTrash() {
        TestResponse response = TestRequest.delete("/api/v1/items/trash")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        assertThat(Application.getInstance(DataService.class).findItems(USER_UID, TRASH_UID).orElseThrow().isEmpty()).isTrue();
    }

    @Test
    void testDeleteUnauthorized() {
        TestResponse response = TestRequest.put("/api/v1/items/" + Utils.randomString())
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testDelete() {
        Item item = Application.getInstance(DataService.class).findItem(ITEM_UID, USER_UID);
        assertThat(item).isNotNull();
        assertThat(item.getCategoryUid()).isNotEqualTo(TRASH_UID);

        TestResponse response = TestRequest.put("/api/v1/items/" + ITEM_UID)
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();

        item = Application.getInstance(DataService.class).findItem(ITEM_UID, USER_UID);
        assertThat(item).isNotNull();
        assertThat(item.getCategoryUid()).isEqualTo(TRASH_UID);
    }

    @Test
    void testAddUnauthorized() {
        TestResponse response = TestRequest.post("/api/v1/items")
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testAddWithCategory() {
        String url = "https://svenkubiak.de?uid=" + Utils.randomString();

        Map<String, String> data = Map.of("url", url, "category", TEST_UID);
        TestResponse response = TestRequest.post("/api/v1/items")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(JsonUtils.toJson(data))
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        assertThat(datastore.find(Item.class, eq("url", url))).isNotNull();
        assertThat(datastore.find(Item.class, eq("url", url)).getCategoryUid()).isEqualTo(TEST_UID);
    }

    @Test
    void testAddAsyncWithCategory() {
        String url = "https://svenkubiak.de?uid=" + Utils.randomString();

        Map<String, String> data = Map.of("url", url, "category", TEST_UID);
        TestResponse response = TestRequest.post("/api/v1/items?async=true")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(JsonUtils.toJson(data))
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        await().atMost(ASYNC_ADD_TIMEOUT_SECONDS, TimeUnit.SECONDS).untilAsserted(() -> assertThat(datastore.find(Item.class, eq("url", url))).isNotNull());
        await().atMost(ASYNC_ADD_TIMEOUT_SECONDS, TimeUnit.SECONDS).untilAsserted(() -> assertThat(datastore.find(Item.class, eq("url", url)).getCategoryUid()).isEqualTo(TEST_UID));
    }

    @Test
    void testAddWithoutCategory() {
        String url = "https://svenkubiak.de?uid=" + Utils.randomString();

        Map<String, String> data = Map.of("url", url);
        TestResponse response = TestRequest.post("/api/v1/items")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(JsonUtils.toJson(data))
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        assertThat(datastore.find(Item.class, eq("url", url))).isNotNull();
        assertThat(datastore.find(Item.class, eq("url", url)).getCategoryUid()).isEqualTo(INBOX_UID);
    }

    @Test
    void testAddAsyncWithoutCategory() {
        String url = "https://svenkubiak.de?uid=" + Utils.randomString();

        Map<String, String> data = Map.of("url", url);
        TestResponse response = TestRequest.post("/api/v1/items?async=true")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(JsonUtils.toJson(data))
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        await().atMost(ASYNC_ADD_TIMEOUT_SECONDS, TimeUnit.SECONDS).untilAsserted(() -> assertThat(datastore.find(Item.class, eq("url", url))).isNotNull());
        await().atMost(ASYNC_ADD_TIMEOUT_SECONDS, TimeUnit.SECONDS).untilAsserted(() -> assertThat(datastore.find(Item.class, eq("url", url)).getCategoryUid()).isEqualTo(INBOX_UID));
    }

    @Test
    void testMoveUnauthorized() {
        TestResponse response = TestRequest.put("/api/v1/items")
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testMove() {
        Map<String, String> data = Map.of("uid", ITEM_UID, "category", TRASH_UID);

        TestResponse response = TestRequest.put("/api/v1/items")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(JsonUtils.toJson(data))
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        assertThat(Application.getInstance(DataService.class).findItem(ITEM_UID, USER_UID).getCategoryUid()).isEqualTo(TRASH_UID);
    }

    @Test
    void testSearchUnauthorized() {
        TestResponse response = TestRequest.get("/api/v1/search?q=bar")
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testSearch() {
        TestResponse response = TestRequest.get("/api/v1/search?q=bar")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThatJson(response.getContent()).inPath("$.items[0]").isEqualTo("""
                {
                  "uid": "${json-unit.any-string}",
                  "url": "${json-unit.any-string}",
                  "title": "${json-unit.any-string}",
                  "domain": "${json-unit.any-string}",
                  "categoryUid": "${json-unit.any-string}",
                  "category": "${json-unit.any-string}",
                  "added": "${json-unit.any-string}"
                }
        """);
    }

    @Test
    void testSearchIsCaseInsensitiveAndMatchesTheDomain() {
        TestResponse response = TestRequest.get("/api/v1/search?q=FOOBAR")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThatJson(response.getContent()).inPath("$.items").isArray().hasSize(1);
    }

    @Test
    void testSearchWithoutAMatch() {
        TestResponse response = TestRequest.get("/api/v1/search?q=nothinghere")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThatJson(response.getContent()).inPath("$.items").isArray().isEmpty();
    }

    @Test
    void testSearchIgnoresRegularExpressions() {
        TestResponse response = TestRequest.get("/api/v1/search?q=.*")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThatJson(response.getContent()).inPath("$.items").isArray().isEmpty();
    }

    @Test
    void testBulkMoveUnauthorized() {
        TestResponse response = TestRequest.put("/api/v1/items/bulk/move")
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
    }

    @Test
    void testBulkMove() {
        String body = JsonUtils.toJson(Map.of("uids", List.of(ITEM_UID), "category", TEST_UID));

        TestResponse response = TestRequest.put("/api/v1/items/bulk/move")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(body)
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(Application.getInstance(DataService.class).findItem(ITEM_UID, USER_UID).getCategoryUid()).isEqualTo(TEST_UID);
    }

    @Test
    void testBulkMoveWithAMalformedUid() {
        String body = JsonUtils.toJson(Map.of("uids", List.of("not a uid!"), "category", TEST_UID));

        TestResponse response = TestRequest.put("/api/v1/items/bulk/move")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(body)
                .withContentType("application/json")
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(Application.getInstance(DataService.class).findItem(ITEM_UID, USER_UID).getCategoryUid()).isEqualTo(INBOX_UID);
    }

    @Test
    void testBulkMoveWithAnUnknownUid() {
        String body = JsonUtils.toJson(Map.of("uids", List.of(Utils.randomString()), "category", TEST_UID));

        TestResponse response = TestRequest.put("/api/v1/items/bulk/move")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(body)
                .withContentType("application/json")
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(Application.getInstance(DataService.class).findItem(ITEM_UID, USER_UID).getCategoryUid()).isEqualTo(INBOX_UID);
    }

    @Test
    void testBulkMoveOfAnotherUsersItem() {
        Datastore store = Application.getInstance(Datastore.class);
        User other = new User("other@bar.com");
        store.save(other);
        Item foreign = Item.create()
                .withUserUid(other.getUid())
                .withCategoryUid(INBOX_UID)
                .withUrl("https://example.com")
                .withImage("foo")
                .withTitle("foreign")
                .withDomain("example.com")
                .withDescription("foreign");
        store.save(foreign);

        String body = JsonUtils.toJson(Map.of("uids", List.of(foreign.getUid()), "category", TEST_UID));

        TestResponse response = TestRequest.put("/api/v1/items/bulk/move")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(body)
                .withContentType("application/json")
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(Application.getInstance(DataService.class).findItem(foreign.getUid(), other.getUid()).getCategoryUid()).isEqualTo(INBOX_UID);
    }

    @Test
    void testBulkMoveWithoutABody() {
        TestResponse response = TestRequest.put("/api/v1/items/bulk/move")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    @Test
    void testBulkDelete() {
        String body = JsonUtils.toJson(Map.of("uids", List.of(ITEM_UID)));

        TestResponse response = TestRequest.put("/api/v1/items/bulk/delete")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(body)
                .withContentType("application/json")
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(Application.getInstance(DataService.class).findItem(ITEM_UID, USER_UID).getCategoryUid()).isEqualTo(TRASH_UID);
    }

    @Test
    void testMoveIntoTheSameCategoryIsANoOp() {
        Map<String, String> data = Map.of("uid", ITEM_UID, "category", INBOX_UID);

        TestResponse response = TestRequest.put("/api/v1/items")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(JsonUtils.toJson(data))
                .withContentType("application/json")
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(Application.getInstance(DataService.class).findItem(ITEM_UID, USER_UID).getCategoryUid()).isEqualTo(INBOX_UID);
    }

    @Test
    void testTrashedItemsCarryTheirDeletionDate() {
        // PUT on a single item moves it to the trash
        TestRequest.put("/api/v1/items/" + ITEM_UID)
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        TestResponse response = TestRequest.get("/api/v1/items/" + TRASH_UID)
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThatJson(response.getContent()).inPath("$.items[0].deleteAt").isNumber();
    }

    @Test
    void testItemsOutsideTheTrashHaveNoDeletionDate() {
        TestResponse response = TestRequest.get("/api/v1/items/" + INBOX_UID)
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).doesNotContain("deleteAt");
    }
}
