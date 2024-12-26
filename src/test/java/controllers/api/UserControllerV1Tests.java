package controllers.api;

import constants.Const;
import controllers.TestExtension;
import io.mangoo.core.Application;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.test.http.TestRequest;
import io.mangoo.test.http.TestResponse;
import io.mangoo.utils.CodecUtils;
import io.mangoo.utils.JsonUtils;
import io.mangoo.utils.MangooUtils;
import models.Category;
import models.Item;
import models.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith({TestExtension.class})
public class UserControllerV1Tests {

    @BeforeAll
    public static void init() {
        Datastore datastore = Application.getInstance(Datastore.class);
        datastore.dropCollection(Category.class);
        datastore.dropCollection(Item.class);
        datastore.dropCollection(User.class);

        User user = new User("foo");
        user.setPassword(CodecUtils.hashArgon2("bar", user.getSalt()));
        datastore.save(user);
        datastore.save(new Category(Const.INBOX, user.getUid()));
        datastore.save(new Category(Const.TRASH, user.getUid()));
    }

    @Test
    void testLogin() {
        //given
        String username = "foo";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        //when
        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isNotNull();
        assertThat(response.getContent()).contains("accessToken", "refreshToken");
        assertThatJson(response.getContent()).node("accessToken").isString().isNotNull();
        assertThatJson(response.getContent()).node("refreshToken").isString().isNotNull();
        assertThatJson(response.getContent()).node("accessToken").isString().contains("v4.local");
        assertThatJson(response.getContent()).node("refreshToken").isString().contains("v4.local");
    }

    @Test
    void testInvalidLogin() {
        //given
        String username = MangooUtils.randomString(16);
        String password = MangooUtils.randomString(16);
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        //when
        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getContent()).doesNotContain("accessToken", "refreshToken");
    }

    @Test
    void testRefresh() {
        //given
        String username = "foo";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        Map<String, String> credentials = JsonUtils.toFlatMap(response.getContent());

        //when
        response = TestRequest.post("/api/v1/users/refresh")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("refreshToken", credentials.get("refreshToken"))))
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isNotNull();
        assertThat(response.getContent()).contains("accessToken", "refreshToken");
        assertThatJson(response.getContent()).node("accessToken").isString().isNotNull();
        assertThatJson(response.getContent()).node("refreshToken").isString().isNotNull();
        assertThatJson(response.getContent()).node("accessToken").isString().contains("v4.local");
        assertThatJson(response.getContent()).node("refreshToken").isString().contains("v4.local");
    }

    @Test
    void testRefreshShouldNotWorkWithAccessToken() {
        //given
        String username = "foo";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        Map<String, String> credentials = JsonUtils.toFlatMap(response.getContent());

        //when
        response = TestRequest.post("/api/v1/users/refresh")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("refreshToken", credentials.get("accessToken"))))
                .execute();

        //then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getContent()).doesNotContain("accessToken", "refreshToken");
    }
}
