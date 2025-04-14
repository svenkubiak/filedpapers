package controllers.api;

import constants.Const;
import controllers.TestExtension;
import io.mangoo.core.Application;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.test.http.TestRequest;
import io.mangoo.test.http.TestResponse;
import io.mangoo.utils.CodecUtils;
import io.mangoo.utils.JsonUtils;
import models.Category;
import models.Item;
import models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import services.DataService;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith({TestExtension.class})
public class ItemsControllerV1Tests {
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
        user.setPassword(CodecUtils.hashArgon2("bar", user.getSalt()));
        datastore.save(user);

        Category inbox = new Category(Const.INBOX, user.getUid());
        Category test = new Category("test", user.getUid());
        Category trash = new Category(Const.TRASH, user.getUid());

        datastore.save(inbox);
        datastore.save(test);
        datastore.save(trash);

        String username = "foo@bar.com";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        //when
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

        Item item = new Item(USER_UID, INBOX_UID, "https://svenkubiak.de", "foo", "bar");
        datastore.save(item);

        ITEM_UID = item.getUid();
    }

    @Test
    void testListUnauthorized() {
        //when
        TestResponse response = TestRequest.get("/api/v1/items/" + CodecUtils.uuid())
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testList() {
        //when
        TestResponse response = TestRequest.get("/api/v1/items/" + INBOX_UID)
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isNotEmpty();
    }

    @Test
    void testETag() {
        //when
        TestResponse response = TestRequest.get("/api/v1/items/" + INBOX_UID)
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getHeader("ETag")).isNotEmpty();

        String etag = response.getHeader("ETag");

        //when
        response = TestRequest.get("/api/v1/items/" + INBOX_UID)
                .withHeader("Authorization", ACCESS_TOKEN)
                .withHeader("If-None-Match", etag)
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(304);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testTrashUnauthorized() {
        //when
        TestResponse response = TestRequest.delete("/api/v1/items/trash")
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testTrash() {
        //when
        TestResponse response = TestRequest.delete("/api/v1/items/trash")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        assertThat(Application.getInstance(DataService.class).findItems(USER_UID, TRASH_UID).orElseThrow().isEmpty()).isTrue();
    }

    @Test
    void testDeleteUnauthorized() {
        //when
        TestResponse response = TestRequest.put("/api/v1/items/" + CodecUtils.uuid())
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testDelete() {
        //when
        Item item = Application.getInstance(DataService.class).findItem(ITEM_UID, USER_UID);
        assertThat(item).isNotNull();
        assertThat(item.getCategoryUid()).isNotEqualTo(TRASH_UID);

        TestResponse response = TestRequest.put("/api/v1/items/" + ITEM_UID)
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();

        item = Application.getInstance(DataService.class).findItem(ITEM_UID, USER_UID);
        assertThat(item).isNotNull();
        assertThat(item.getCategoryUid()).isEqualTo(TRASH_UID);
    }


    @Test
    void testAddUnauthorized() {
        //when
        TestResponse response = TestRequest.post("/api/v1/items")
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testAddWithCategory() {
        //given
        String url = "https://svenkubiak.de?uid=" + CodecUtils.uuid();

        //when
        Map<String, String> data = Map.of("url", url, "category", TEST_UID);
        TestResponse response = TestRequest.post("/api/v1/items")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(JsonUtils.toJson(data))
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        assertThat(datastore.find(Item.class, eq("url", url))).isNotNull();
        assertThat(datastore.find(Item.class, eq("url", url)).getCategoryUid()).isEqualTo(TEST_UID);
    }

    @Test
    void testAddAsyncWithCategory() {
        //given
        String url = "https://svenkubiak.de?uid=" + CodecUtils.uuid();

        //when
        Map<String, String> data = Map.of("url", url, "category", TEST_UID);
        TestResponse response = TestRequest.post("/api/v1/items?async=true")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(JsonUtils.toJson(data))
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(datastore.find(Item.class, eq("url", url))).isNotNull());
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(datastore.find(Item.class, eq("url", url)).getCategoryUid()).isEqualTo(TEST_UID));
    }

    @Test
    void testAddWithoutCategory() {
        //given
        String url = "https://svenkubiak.de?uid=" + CodecUtils.uuid();

        //when
        Map<String, String> data = Map.of("url", url);
        TestResponse response = TestRequest.post("/api/v1/items")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(JsonUtils.toJson(data))
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        assertThat(datastore.find(Item.class, eq("url", url))).isNotNull();
        assertThat(datastore.find(Item.class, eq("url", url)).getCategoryUid()).isEqualTo(INBOX_UID);
    }

    @Test
    void testAddAsyncWithoutCategory() {
        //given
        String url = "https://svenkubiak.de?uid=" + CodecUtils.uuid();

        //when
        Map<String, String> data = Map.of("url", url);
        TestResponse response = TestRequest.post("/api/v1/items?async=true")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(JsonUtils.toJson(data))
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(datastore.find(Item.class, eq("url", url))).isNotNull());
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(datastore.find(Item.class, eq("url", url)).getCategoryUid()).isEqualTo(INBOX_UID));
    }

    @Test
    void testMoveUnauthorized() {
        //when
        TestResponse response = TestRequest.put("/api/v1/items")
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testMove() {
        //given
        Map<String, String> data = Map.of("uid", ITEM_UID, "category", TRASH_UID);

        //when
        TestResponse response = TestRequest.put("/api/v1/items")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withStringBody(JsonUtils.toJson(data))
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        assertThat(Application.getInstance(DataService.class).findItem(ITEM_UID, USER_UID).getCategoryUid()).isEqualTo(TRASH_UID);
    }
}
