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
        TestResponse response = TestRequest.get("/dashboard/" + categoryId).execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testDashboardWithIdUnauthorized() {
        TestResponse response = TestRequest.get("/dashboard/" + categoryId).execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testProfileUnauthorized() {
        TestResponse response = TestRequest.get("/dashboard/profile").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testAboutUnauthorized() {
        TestResponse response = TestRequest.get("/dashboard/about").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testResyncUnauthorized() {
        TestResponse response = TestRequest.get("/dashboard/resync").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testChangeUsernameUnauthorized() {
        TestResponse response = TestRequest.post("/dashboard/profile/change-username").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testChangePasswordUnauthorized() {
        TestResponse response = TestRequest.post("/dashboard/profile/change-password").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testChangeDeleteAccountUnauthorized() {
        TestResponse response = TestRequest.post("/dashboard/profile/delete-account").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testEnableMfaUnauthorized() {
        TestResponse response = TestRequest.post("/dashboard/profile/enable-mfa").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testLogoutDevicesUnauthorized() {
        TestResponse response = TestRequest.post("/dashboard/profile/logout-devices").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testSetLanguageUnauthorized() {
        TestResponse response = TestRequest.post("/dashboard/profile/language").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testConfirmEmailUnauthorized() {
        TestResponse response = TestRequest.get("/dashboard/profile/confirm-email").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testDashboardIoUnauthorized() {
        TestResponse response = TestRequest.get("/dashboard/io").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testExportUnauthorized() {
        TestResponse response = TestRequest.post("/dashboard/io/exporter").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }

    @Test
    public void testImporterUnauthorized() {
        TestResponse response = TestRequest.post("/dashboard/io/importer").execute();

        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Please login to proceed."));
    }
}