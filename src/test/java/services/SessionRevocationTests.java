package services;

import com.nimbusds.jwt.JWTClaimsSet;
import io.mangoo.utils.JwtUtils;
import models.User;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

/**
 * Covers the revocation rule itself: a token is only accepted when it was
 * issued at or after the last revocation for that user.
 */
public class SessionRevocationTests {
    private static final byte[] KEY = "0123456789012345678901234567890123456789012345678901234567890123".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECRET = "9876543210987654321098765432109876543210987654321098765432109876".getBytes(StandardCharsets.UTF_8);
    private static final String ISSUER = "filedpapers";
    private static final String AUDIENCE = "filedpapers";

    private boolean isSessionValid(User user, JWTClaimsSet jwtClaimsSet) {
        return AuthenticationService.isSessionValid(user, jwtClaimsSet);
    }

    private User userWithRevocationAt(long epochSeconds) {
        var user = new User("test@example.com");
        user.setSessionsValidFrom(epochSeconds);

        return user;
    }

    private JWTClaimsSet issuedToken() throws Exception {
        String jwt = JwtUtils.createJwt(JwtUtils.jwtData()
                .withKey(KEY)
                .withSecret(SECRET)
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withSubject("1f0088c32e226350913fb0b6f26ac0ac")
                .withTtlSeconds(600)
                .withClaims(Map.of("nonce", "abc")));

        return JwtUtils.parseJwt(jwt, JwtUtils.jwtData()
                .withKey(KEY)
                .withSecret(SECRET)
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withTtlSeconds(600));
    }

    @Test
    public void testFrameworkIssuesAnIatClaim() throws Exception {
        assertThat(issuedToken().getIssueTime(), org.hamcrest.Matchers.notNullValue());
    }

    @Test
    public void testTokenIssuedBeforeRevocationIsRejected() throws Exception {
        var token = issuedToken();
        var user = userWithRevocationAt(Instant.now().getEpochSecond() + 60);

        assertThat(isSessionValid(user, token), equalTo(false));
    }

    @Test
    public void testTokenIssuedAfterRevocationIsAccepted() throws Exception {
        var token = issuedToken();
        var user = userWithRevocationAt(Instant.now().getEpochSecond() - 60);

        assertThat(isSessionValid(user, token), equalTo(true));
    }

    @Test
    public void testTokenIssuedInTheSameSecondAsRevocationSurvives() throws Exception {
        var token = issuedToken();
        var user = userWithRevocationAt(token.getIssueTime().toInstant().getEpochSecond());

        assertThat(isSessionValid(user, token), equalTo(true));
    }

    @Test
    public void testUntouchedUserAcceptsTokens() throws Exception {
        var user = new User("test@example.com");

        assertThat(isSessionValid(user, issuedToken()), equalTo(true));
    }

    @Test
    public void testLegacyUserWithoutRevocationTimestampAcceptsTokens() throws Exception {
        // Documents are migrated with sessionsValidFrom = 0, which must not lock
        // existing users out.
        assertThat(isSessionValid(userWithRevocationAt(0L), issuedToken()), equalTo(true));
    }

    @Test
    public void testMissingIatIsRejected() {
        var claims = new JWTClaimsSet.Builder()
                .subject("1f0088c32e226350913fb0b6f26ac0ac")
                .build();

        assertThat(isSessionValid(userWithRevocationAt(0L), claims), equalTo(false));
    }

    @Test
    public void testUnknownUserIsRejected() throws Exception {
        assertThat(isSessionValid(null, issuedToken()), equalTo(false));
    }

    @Test
    public void testNewUserStartsWithCurrentRevocationTimestamp() {
        var user = new User("test@example.com");

        assertThat(user.getSessionsValidFrom(), greaterThan(0L));
    }

    @Test
    public void testRevocationRejectsAPreviouslyValidToken() throws Exception {
        var token = issuedToken();
        var user = userWithRevocationAt(0L);
        assertThat(isSessionValid(user, token), equalTo(true));

        // "logout all devices" one second after the token was issued
        user.setSessionsValidFrom(token.getIssueTime().toInstant().getEpochSecond() + 1);

        assertThat(isSessionValid(user, token), equalTo(false));
    }

    @Test
    public void testIssueTimeIsSecondGranular() throws Exception {
        var issuedAt = issuedToken().getIssueTime();

        assertThat(issuedAt, equalTo(Date.from(Instant.ofEpochSecond(issuedAt.toInstant().getEpochSecond()))));
    }
}
