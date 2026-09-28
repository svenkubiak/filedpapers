package controllers;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mongodb.client.model.Filters;
import constants.Const;
import helpers.Csrf;
import helpers.TestUtils;
import io.mangoo.core.Application;
import io.mangoo.persistence.interfaces.Datastore;
import io.mangoo.test.TestRunner;
import io.mangoo.test.http.TestRequest;
import io.mangoo.test.http.TestResponse;
import io.mangoo.core.Config;
import io.mangoo.utils.CommonUtils;
import io.mangoo.utils.TotpUtils;
import io.undertow.util.StatusCodes;
import models.Category;
import models.User;
import models.enums.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.net.HttpCookie;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith({TestRunner.class})
public class AuthenticationControllerTests {
    private static final String VALID_FALLBACK = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String GARBAGE_FALLBACK = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @BeforeAll
    public static void init() {
        Datastore datastore = Application.getInstance(Datastore.class);
        datastore.dropCollection(User.class);

        User user = new User("foo@bar.com");
        user.setPassword(CommonUtils.hashArgon2("bar", user.getSalt()));
        datastore.save(user);
        datastore.save(new Category(Const.INBOX, user.getUid(), Role.INBOX));
        datastore.save(new Category(Const.TRASH, user.getUid(), Role.TRASH));

        createMfaUser(datastore, "mfa-garbage@bar.com", GARBAGE_FALLBACK);
        createMfaUser(datastore, "mfa-fallback@bar.com", VALID_FALLBACK);
    }

    private static void createMfaUser(Datastore datastore, String username, String fallback) {
        User user = new User(username);
        user.setPassword(CommonUtils.hashArgon2("bar", user.getSalt()));
        user.setMfa(true);
        user.setMfaSecret(TotpUtils.createSecret());
        user.setMfaFallback(CommonUtils.hashArgon2(fallback, user.getSalt()));
        datastore.save(user);
        datastore.save(new Category(Const.INBOX, user.getUid(), Role.INBOX));
        datastore.save(new Category(Const.TRASH, user.getUid(), Role.TRASH));
    }

    /**
     * Logs in and returns the response of the login, which carries the
     * authentication cookie and a session cookie with a fresh csrf token.
     */
    private static TestResponse login(String username) {
        Csrf csrf = TestUtils.getCsrf();
        Multimap<String, String> form = ArrayListMultimap.create();
        form.put("username", username);
        form.put("password", "bar");
        form.put(io.mangoo.constants.Const.CSRF_TOKEN, csrf.token());

        return TestRequest.post("/auth/login")
                .withCookie(csrf.cookie())
                .withForm(form)
                .execute();
    }

    private static TestResponse submitMfa(TestResponse login, String mfa) {
        Config config = Application.getInstance(Config.class);
        HttpCookie authCookie = login.getCookie(config.getAuthenticationCookieName());
        Csrf csrf = TestUtils.csrfFrom(login);

        Multimap<String, String> form = ArrayListMultimap.create();
        form.put("mfa", mfa);
        form.put(io.mangoo.constants.Const.CSRF_TOKEN, csrf.token());

        return TestRequest.post("/auth/mfa")
                .withCookie(authCookie)
                .withCookie(csrf.cookie())
                .withForm(form)
                .execute();
    }

    @Test
    public void testLogin() {
        //given
        Csrf csrf = TestUtils.getCsrf();
        Multimap<String, String> form = ArrayListMultimap.create();
        form.put("username", "foo@bar.com");
        form.put("password", "bar");
        form.put(io.mangoo.constants.Const.CSRF_TOKEN, csrf.token());

        //when
        TestResponse response = TestRequest.post("/auth/login")
                .withCookie(csrf.cookie())
                .withForm(form)
                .execute();

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Logout"));
        assertThat(response.getContent(), not(containsString("undefined")));
    }

    @Test
    public void testMfaWithMalformedInputIsRejected() {
        //given
        TestResponse login = login("mfa-garbage@bar.com");

        //when a value that is neither an otp nor a fallback code is submitted
        TestResponse response = submitMfa(login, "not-a-code");

        //then the attempt fails like any other, it must not blow up the request
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Two-Step Verification"));
    }

    @Test
    public void testMfaWithWrongFallbackIsRejected() {
        //given
        TestResponse login = login("mfa-garbage@bar.com");

        //when a well formed but wrong fallback code is submitted
        TestResponse response = submitMfa(login, "cccccccccccccccccccccccccccccccc");

        //then
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Two-Step Verification"));
    }

    @Test
    public void testMfaFallbackIsRedeemedAndRotated() {
        //given
        Datastore datastore = Application.getInstance(Datastore.class);
        User before = datastore.find(User.class, Filters.eq("username", "mfa-fallback@bar.com"));
        TestResponse login = login("mfa-fallback@bar.com");

        //when
        TestResponse response = submitMfa(login, VALID_FALLBACK);

        //then the fallback completes the login
        assertThat(response, not(nullValue()));
        assertThat(response.getStatusCode(), equalTo(StatusCodes.OK));
        assertThat(response.getContent(), containsString("Logout"));

        //and redeeming it is single use: mfa is off, secret and code are rotated
        User after = datastore.find(User.class, Filters.eq("username", "mfa-fallback@bar.com"));
        assertThat(after.isMfa(), equalTo(false));
        assertThat(after.getMfaFallback(), not(equalTo(before.getMfaFallback())));
        assertThat(after.getMfaSecret(), not(equalTo(before.getMfaSecret())));
    }

    @Test
    public void testMfaFallbackCanNotBeReplayed() {
        //given a user of its own, so the test does not depend on the order in
        //which the other fallback test runs
        Datastore datastore = Application.getInstance(Datastore.class);
        createMfaUser(datastore, "mfa-replay@bar.com", VALID_FALLBACK);
        submitMfa(login("mfa-replay@bar.com"), VALID_FALLBACK);

        //when the same code is submitted a second time
        TestResponse response = submitMfa(login("mfa-replay@bar.com"), VALID_FALLBACK);

        //then it no longer completes a login
        assertThat(response, not(nullValue()));
        assertThat(response.getContent(), not(containsString("Logout")));
    }
}
