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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import services.DataService;
import utils.Utils;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith({TestExtension.class})
public class CategoriesControllerV1Tests {
    private static String ACCESS_TOKEN;
    private static String USER_UID;

    @BeforeAll
    public static void init() {
        Datastore datastore = Application.getInstance(Datastore.class);
        datastore.dropCollection(Category.class);
        datastore.dropCollection(Item.class);
        datastore.dropCollection(User.class);

        User user = new User("foo@bar.com");
        user.setPassword(CodecUtils.hashArgon2("bar", user.getSalt()));
        datastore.save(user);
        datastore.save(new Category(Const.INBOX, user.getUid()));
        datastore.save(new Category(Const.TRASH, user.getUid()));

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
    }

    @Test
    void testList() {
        //when
        TestResponse response = TestRequest.get("/api/v1/categories")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getContent()).contains("Inbox", "Trash");
    }

    @Test
    void testListUnauthorized() {
        //when
        TestResponse response = TestRequest.get("/api/v1/categories")
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testDeleteUnauthorized() {
        //when
        TestResponse response = TestRequest.delete("/api/v1/categories/" + CodecUtils.uuid())
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
        String name = Utils.randomString();
        Category category = new Category(name, USER_UID);
        Application.getInstance(DataService.class).save(category);
        TestResponse response = TestRequest.delete("/api/v1/categories/" + category.getUid())
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();
        assertThat(Application.getInstance(DataService.class).findCategoryByName(name, USER_UID)).isNull();
    }

    @Test
    void testAddUnauthorized() {
        //when
        TestResponse response = TestRequest.post("/api/v1/categories")
                .withContentType("application/json")
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testAdd() {
        //when
        String name = Utils.randomString();
        Map<String, String> body = Map.of("name", name);
        TestResponse response = TestRequest.post("/api/v1/categories")
                .withHeader("Authorization", ACCESS_TOKEN)
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(body))
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(Application.getInstance(DataService.class).findCategoryByName(name, USER_UID)).isNotNull();
    }
}
