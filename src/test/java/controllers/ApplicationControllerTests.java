package controllers;

import constants.Const;
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
        //when
        TestResponse response = TestRequest.get("/health").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("OK"));
    }

    @Test
    public void testSuccess() {
        //when
        TestResponse response = TestRequest.get("/success").execute();

        //then
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
        //when
        TestResponse response = TestRequest.get("/error").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Error"));
    }

}