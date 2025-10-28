package controllers;

import constants.Const;
import io.mangoo.core.Application;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.test.TestRunner;
import io.mangoo.test.http.TestRequest;
import io.mangoo.test.http.TestResponse;
import io.mangoo.utils.CommonUtils;
import io.undertow.util.StatusCodes;
import models.Category;
import models.User;
import models.enums.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith({TestRunner.class})
public class DashboardControllerTests {
    private static String categoryId;

    @BeforeAll
    public static void init() {
        Datastore datastore = Application.getInstance(Datastore.class);
        datastore.dropCollection(User.class);

        User user = new User("bla@bar.com");
        user.setPassword(CommonUtils.hashArgon2("bar", user.getSalt()));
        datastore.save(user);

        Category category = new Category(Const.INBOX, user.getUid(), Role.INBOX);
        datastore.save(category);

        categoryId = category.getUid();
    }

    @Test
    public void testDashboardUnauthorized() {
        //when
        TestResponse response = TestRequest.get("/dashboard/" + categoryId).execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testDashboardWithIdUnauthorized() {
        //when
        TestResponse response = TestRequest.get("/dashboard/" + categoryId).execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testProfileUnauthorized() {
        //when
        TestResponse response = TestRequest.get("/dashboard/profile").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testAboutUnauthorized() {
        //when
        TestResponse response = TestRequest.get("/dashboard/about").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testResyncUnauthorized() {
        //when
        TestResponse response = TestRequest.get("/dashboard/resync").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testChangeUsernameUnauthorized() {
        //when
        TestResponse response = TestRequest.post("/dashboard/profile/change-username").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testChangePasswordUnauthorized() {
        //when
        TestResponse response = TestRequest.post("/dashboard/profile/change-password").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testChangeDeleteAccountUnauthorized() {
        //when
        TestResponse response = TestRequest.post("/dashboard/profile/delete-account").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testEnableMfaUnauthorized() {
        //when
        TestResponse response = TestRequest.post("/dashboard/profile/enable-mfa").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testLogoutDevicesUnauthorized() {
        //when
        TestResponse response = TestRequest.post("/dashboard/profile/logout-devices").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testSetLanguageUnauthorized() {
        //when
        TestResponse response = TestRequest.post("/dashboard/profile/language").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testConfirmEmailUnauthorized() {
        //when
        TestResponse response = TestRequest.get("/dashboard/profile/confirm-email").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testDashboardIoUnauthorized() {
        //when
        TestResponse response = TestRequest.get("/dashboard/io").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testExportUnauthorized() {
        //when
        TestResponse response = TestRequest.post("/dashboard/io/exporter").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testImporterUnauthorized() {
        //when
        TestResponse response = TestRequest.post("/dashboard/io/importer").execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }
}