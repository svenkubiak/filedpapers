package controllers;

import constants.Const;
import io.mangoo.core.Application;
import io.mangoo.interfaces.TokenBlacklist;
import io.mangoo.test.TestRunner;
import io.mangoo.test.http.TestRequest;
import io.mangoo.test.http.TestResponse;
import io.undertow.util.StatusCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith({TestRunner.class})
public class ApplicationControllerTests {

    @Test
    public void testHealth() {
        TestResponse response = TestRequest.get("/health").execute();
        Application.getInstance(TokenBlacklist.class).isRevoked("foo", null, null);

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("OK"));
    }

    @Test
    public void testSuccess() {
        TestResponse response = TestRequest.get("/success").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Success"));
    }

    @Test
    public void testAssets() {
        assertThat(TestRequest.get(Const.PLACEHOLDER_IMAGE).execute().getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(TestRequest.get("/robots.txt").execute().getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(TestRequest.get("/favicon.ico").execute().getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(TestRequest.get("/favicon-16x16.png").execute().getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(TestRequest.get("/favicon-32x32.png").execute().getStatusCode(), equalTo(StatusCodes.OK));
    }

    @Test
    public void testError() {
        TestResponse response = TestRequest.get("/error").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Error"));
    }

}