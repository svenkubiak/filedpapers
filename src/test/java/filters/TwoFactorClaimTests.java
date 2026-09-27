package filters;

import com.nimbusds.jwt.JWTClaimsSet;
import io.mangoo.constants.ClaimKey;
import io.mangoo.utils.JwtUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Pins the assumption ApiAccessFilter relies on: the two factor claim survives
 * the authentication cookie round trip and is readable the same way the
 * framework writes it.
 */
public class TwoFactorClaimTests {
    private static final byte[] KEY = "0123456789012345678901234567890123456789012345678901234567890123".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECRET = "9876543210987654321098765432109876543210987654321098765432109876".getBytes(StandardCharsets.UTF_8);
    private static final String ISSUER = "filedpapers";
    private static final String AUDIENCE = "__Host-filedpapers-authentication";

    private JWTClaimsSet roundTrip(String twoFactor) throws Exception {
        String jwt = JwtUtils.createJwt(JwtUtils.jwtData()
                .withKey(KEY)
                .withSecret(SECRET)
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withSubject("1f0088c32e226350913fb0b6f26ac0ac")
                .withTtlSeconds(600)
                .withClaims(Map.of(
                        ClaimKey.TWO_FACTOR, twoFactor,
                        ClaimKey.REMEMBER_ME, "false")));

        return JwtUtils.parseJwt(jwt, JwtUtils.jwtData()
                .withKey(KEY)
                .withSecret(SECRET)
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withTtlSeconds(600));
    }

    @Test
    public void testPendingSecondFactorIsReadableFromCookie() throws Exception {
        var claims = roundTrip("true");

        assertThat(Boolean.parseBoolean(claims.getClaimAsString(ClaimKey.TWO_FACTOR)), equalTo(true));
    }

    @Test
    public void testCompletedSecondFactorIsReadableFromCookie() throws Exception {
        var claims = roundTrip("false");

        assertThat(Boolean.parseBoolean(claims.getClaimAsString(ClaimKey.TWO_FACTOR)), equalTo(false));
    }
}
