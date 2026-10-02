package controllers.api;

import constants.Const;
import controllers.TestExtension;
import io.mangoo.core.Application;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.test.http.TestRequest;
import io.mangoo.test.http.TestResponse;
import io.mangoo.utils.CommonUtils;
import io.mangoo.utils.JsonUtils;
import io.mangoo.utils.TotpUtils;
import models.Category;
import models.Item;
import models.User;
import models.enums.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import utils.Utils;

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
        user.setPassword(CommonUtils.hashArgon2("bar", user.getSalt()));
        datastore.save(user);

        User user2 = new User("bar");
        user2.setPassword(CommonUtils.hashArgon2("bar", user2.getSalt()));
        user2.setMfa(true);
        user2.setMfaSecret("foobar");
        user2.setMfaFallback(Utils.randomString());
        datastore.save(user2);

        datastore.save(new Category(Const.INBOX, user.getUid(), Role.INBOX));
        datastore.save(new Category(Const.TRASH, user.getUid(), Role.TRASH));
    }

    @Test
    void testLogin() {
        String username = "foo";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isNotNull();
        assertThat(response.getContent()).contains("accessToken", "refreshToken");
        assertThatJson(response.getContent()).node("accessToken").isString().isNotNull();
        assertThatJson(response.getContent()).node("refreshToken").isString().isNotNull();
        assertThatJson(response.getContent()).node("accessToken").isString().startsWith("ey");
        assertThatJson(response.getContent()).node("refreshToken").isString().startsWith("ey");
        assertThatJson(response.getContent()).isEqualTo("""
            {
              "accessToken": "${json-unit.any-string}",
              "refreshToken": "${json-unit.any-string}"
            }
        """);
    }

    @Test
    void testAuthMfa() {
        String username = "bar";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(202);
        assertThat(response.getContent()).isNotNull();
        assertThat(response.getContent()).contains("challengeToken");
        assertThatJson(response.getContent()).node("challengeToken").isString().startsWith("ey");
        assertThatJson(response.getContent()).isEqualTo("""
            {
              "challengeToken": "${json-unit.any-string}"
            }
        """);
    }

    @Test
    void testLoginMfa() {
        String username = "bar";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(202);
        assertThat(response.getContent()).isNotNull();
        assertThat(response.getContent()).contains("challengeToken");
        assertThatJson(response.getContent()).node("challengeToken").isString().startsWith("ey");

        Map<String, String> credentials = JsonUtils.toFlatMap(response.getContent());
        body = JsonUtils.toJson(Map.of("challengeToken", credentials.get("challengeToken"), "otp", TotpUtils.getTotp("foobar")));

        response = TestRequest.post("/api/v1/users/mfa")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isNotNull();
        assertThat(response.getContent()).contains("accessToken", "refreshToken");
        assertThatJson(response.getContent()).node("accessToken").isString().isNotNull();
        assertThatJson(response.getContent()).node("refreshToken").isString().isNotNull();
        assertThatJson(response.getContent()).node("accessToken").isString().startsWith("ey");
        assertThatJson(response.getContent()).node("refreshToken").isString().startsWith("ey");

        response = TestRequest.post("/api/v1/users/mfa")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(403);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getContent()).doesNotContain("accessToken", "refreshToken");
    }

    @Test
    void testIncorrectOtpLoginMfa() {
        String username = "bar";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(202);
        assertThat(response.getContent()).isNotNull();
        assertThat(response.getContent()).contains("challengeToken");
        assertThatJson(response.getContent()).node("challengeToken").isString().startsWith("ey");

        Map<String, String> credentials = JsonUtils.toFlatMap(response.getContent());
        body = JsonUtils.toJson(Map.of("challengeToken", credentials.get("challengeToken"), "otp", "111111"));

        response = TestRequest.post("/api/v1/users/mfa")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(403);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getContent()).doesNotContain("accessToken", "refreshToken");
    }

    @Test
    void testAMistypedOtpKeepsTheChallengeToken() {
        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("username", "bar", "password", "bar")))
                .execute();

        String challengeToken = JsonUtils.toFlatMap(response.getContent()).get("challengeToken");

        response = TestRequest.post("/api/v1/users/mfa")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("challengeToken", challengeToken, "otp", "111111")))
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(403);

        String body = JsonUtils.toJson(Map.of("challengeToken", challengeToken, "otp", TotpUtils.getTotp("foobar")));
        response = TestRequest.post("/api/v1/users/mfa")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(200);

        response = TestRequest.post("/api/v1/users/mfa")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(403);
    }

    @Test
    void testAuthShouldNotWorkWithChallengeToken() {
        String username = "bar";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        Map<String, String> credentials = JsonUtils.toFlatMap(response.getContent());

        response = TestRequest.post("/api/v1/categories")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("accessToken", credentials.get("challengeToken"))))
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testAuthShouldNotWorkWithRefreshToken() {
        String username = "foo";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        Map<String, String> credentials = JsonUtils.toFlatMap(response.getContent());

        response = TestRequest.post("/api/v1/categories")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("accessToken", credentials.get("refreshToken"))))
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void testRefreshShouldNotWorkWithChallengeToken() {
        String username = "bar";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        Map<String, String> credentials = JsonUtils.toFlatMap(response.getContent());

        response = TestRequest.post("/api/v1/users/refresh")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("refreshToken", credentials.get("challengeToken"))))
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getContent()).doesNotContain("accessToken", "refreshToken");
    }

    @Test
    void testInvalidLogin() {
        String username = Utils.randomString();
        String password = Utils.randomString();
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getContent()).doesNotContain("accessToken", "refreshToken");
    }

    @Test
    void testRefresh() {
        String username = "foo";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        Map<String, String> credentials = JsonUtils.toFlatMap(response.getContent());

        response = TestRequest.post("/api/v1/users/refresh")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("refreshToken", credentials.get("refreshToken"))))
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isNotNull();
        assertThat(response.getContent()).contains("accessToken", "refreshToken");
        assertThatJson(response.getContent()).node("accessToken").isString().isNotNull();
        assertThatJson(response.getContent()).node("refreshToken").isString().isNotNull();
        assertThatJson(response.getContent()).node("accessToken").isString().startsWith("ey");
        assertThatJson(response.getContent()).node("refreshToken").isString().startsWith("ey");

        response = TestRequest.post("/api/v1/users/refresh")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("refreshToken", credentials.get("refreshToken"))))
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getContent()).doesNotContain("accessToken", "refreshToken");
    }

    @Test
    void testRefreshShouldNotWorkWithAccessToken() {
        String username = "foo";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        Map<String, String> credentials = JsonUtils.toFlatMap(response.getContent());

        response = TestRequest.post("/api/v1/users/refresh")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("refreshToken", credentials.get("accessToken"))))
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getContent()).doesNotContain("accessToken", "refreshToken");
    }

    @Test
    void testRefreshRevokesTheOldAccessToken() {
        String body = JsonUtils.toJson(Map.of("username", "foo", "password", "bar"));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        Map<String, String> credentials = JsonUtils.toFlatMap(response.getContent());

        response = TestRequest.post("/api/v1/users/refresh")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("refreshToken", credentials.get("refreshToken"))))
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<String, String> refreshed = JsonUtils.toFlatMap(response.getContent());

        response = TestRequest.get("/api/v1/categories")
                .withContentType("application/json")
                .withHeader("Authorization", credentials.get("accessToken"))
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(401);

        response = TestRequest.get("/api/v1/categories")
                .withContentType("application/json")
                .withHeader("Authorization", refreshed.get("accessToken"))
                .execute();

        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    @Test
    void testLogout() {
        String username = "foo";
        String password = "bar";
        String body = JsonUtils.toJson(Map.of("username", username, "password", password));

        TestResponse response = TestRequest.post("/api/v1/users/login")
                .withContentType("application/json")
                .withStringBody(body)
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isNotNull();
        assertThat(response.getContent()).contains("accessToken", "refreshToken");
        assertThatJson(response.getContent()).node("accessToken").isString().isNotNull();
        assertThatJson(response.getContent()).node("refreshToken").isString().isNotNull();
        assertThatJson(response.getContent()).node("accessToken").isString().startsWith("ey");
        assertThatJson(response.getContent()).node("refreshToken").isString().startsWith("ey");
        assertThatJson(response.getContent()).isEqualTo("""
            {
              "accessToken": "${json-unit.any-string}",
              "refreshToken": "${json-unit.any-string}"
            }
        """);

        Map<String, String> credentials = JsonUtils.toFlatMap(response.getContent());
        String accessToken = credentials.get("accessToken");

        response = TestRequest.post("/api/v1/users/logout")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("refreshToken", credentials.get("refreshToken"), "accessToken", accessToken)))
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getContent()).isEmpty();

        response = TestRequest.post("/api/v1/users/refresh")
                .withContentType("application/json")
                .withStringBody(JsonUtils.toJson(Map.of("refreshToken", credentials.get("refreshToken"))))
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getContent()).doesNotContain("accessToken", "refreshToken");

        response = TestRequest.get("/api/v1/categories")
                .withContentType("application/json")
                .withHeader("Authorization", credentials.get("accessToken"))
                .execute();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(401);
        assertThat(response.getContent()).isEmpty();
    }
}
