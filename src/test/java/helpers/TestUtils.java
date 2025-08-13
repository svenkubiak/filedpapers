package helpers;

import io.mangoo.constants.Default;
import io.mangoo.core.Application;
import io.mangoo.core.Config;
import io.mangoo.exceptions.MangooTokenException;
import io.mangoo.test.http.TestRequest;
import io.mangoo.test.http.TestResponse;
import io.mangoo.utils.paseto.PasetoParser;
import io.mangoo.utils.paseto.Token;

import java.net.HttpCookie;

public final class TestUtils {

    private TestUtils() {}

    public static Csrf getCsrf() throws RuntimeException {
        TestResponse response = TestRequest.get("/auth/login")
                .execute();

        HttpCookie cookie = response
                .getCookie(Application.getInstance(Config.class).getSessionCookieName());

        try {
            Token parse = PasetoParser.create()
                    .withValue(cookie.getValue())
                    .withSecret(Application.getInstance(Config.class).getSessionCookieSecret())
                    .parse();
            return new Csrf(cookie, parse.getClaim(Default.CSRF_TOKEN));
        } catch (MangooTokenException e) {
            throw new RuntimeException(e);
        }
    }
}
