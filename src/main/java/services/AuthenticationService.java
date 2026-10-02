package services;

import com.nimbusds.jwt.JWTClaimsSet;
import constants.Const;
import constants.Invalid;
import constants.Required;
import io.mangoo.core.Config;
import io.mangoo.exceptions.MangooJwtException;
import io.mangoo.interfaces.TokenBlacklist;
import io.mangoo.utils.Argument;
import io.mangoo.utils.CommonUtils;
import io.mangoo.utils.JwtUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import models.User;
import utils.Utils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class AuthenticationService {
    private static final String API_CHALLENGE_TOKEN_SECRET = "api.challengeToken.secret";
    private static final String API_CHALLENGE_TOKEN_KEY = "api.challengeToken.key";
    private static final String API_ACCESS_TOKEN_SECRET = "api.accessToken.secret";
    private static final String API_ACCESS_TOKEN_KEY = "api.accessToken.key";
    private static final String API_ACCESS_TOKEN_EXPIRES = "api.accessToken.expires";
    private static final String API_REFRESH_TOKEN_KEY = "api.refreshToken.key";
    private static final String API_REFRESH_TOKEN_SECRET = "api.refreshToken.secret";
    private static final String API_REFRESH_TOKEN_EXPIRES = "api.refreshToken.expires";
    private final DataService dataService;
    private final Config config;
    private final TokenBlacklist tokenBlacklist;
    private final Set<String> challengesInUse = ConcurrentHashMap.newKeySet();

    @Inject
    public AuthenticationService(DataService dataService, Config config, TokenBlacklist tokenBlacklist) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.config = Objects.requireNonNull(config, Required.CONFIG);
        this.tokenBlacklist = Objects.requireNonNull(tokenBlacklist, Required.TOKEN_BLACKLIST);
    }

    public Map<String, String> getChallengeToken(String userUid) throws MangooJwtException {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);

        var jwtData = JwtUtils.jwtData()
                .withJwtID(CommonUtils.randomString(32))
                .withSecret(config.getString(API_CHALLENGE_TOKEN_SECRET).getBytes(StandardCharsets.UTF_8))
                .withKey(config.getString(API_CHALLENGE_TOKEN_KEY).getBytes(StandardCharsets.UTF_8))
                .withClaims(Map.of(Const.NONCE, Utils.randomString()))
                .withSubject(userUid)
                .withTtlSeconds(300)
                .withIssuer(config.getApplicationName())
                .withAudience(config.getApplicationName());

        var jwt = JwtUtils.createJwt(jwtData);

        return Map.of(Const.CHALLENGE_TOKEN, jwt);
    }

    public Map<String, String> getRefreshAndAccessToken(String userUid) throws MangooJwtException {
        Utils.checkCondition(Utils.isValidRandom(userUid), Invalid.USER_UID);
        var user = dataService.findUserByUid(userUid);
        if (user == null) {
            throw new MangooJwtException("User not found");
        }

        var atid = CommonUtils.randomString(32);
        var jwtData = JwtUtils.jwtData()
                .withJwtID(atid)
                .withSecret(config.getString(API_ACCESS_TOKEN_SECRET).getBytes(StandardCharsets.UTF_8))
                .withKey(config.getString(API_ACCESS_TOKEN_KEY).getBytes(StandardCharsets.UTF_8))
                .withClaims(Map.of(Const.NONCE, Utils.randomString()))
                .withSubject(userUid)
                .withTtlSeconds(config.getInt(API_ACCESS_TOKEN_EXPIRES) * 60L)
                .withIssuer(config.getApplicationName())
                .withAudience(config.getApplicationName());

        var accessToken = JwtUtils.createJwt(jwtData);

        jwtData = JwtUtils.jwtData()
                .withJwtID(CommonUtils.randomString(32))
                .withSecret(config.getString(API_REFRESH_TOKEN_SECRET).getBytes(StandardCharsets.UTF_8))
                .withKey(config.getString(API_REFRESH_TOKEN_KEY).getBytes(StandardCharsets.UTF_8))
                .withClaims(Map.of(Const.NONCE, Utils.randomString(), Const.ATID, atid))
                .withSubject(userUid)
                .withTtlSeconds(config.getInt(API_REFRESH_TOKEN_EXPIRES) * 60L)
                .withIssuer(config.getApplicationName())
                .withAudience(config.getApplicationName());

        var refreshToken = JwtUtils.createJwt(jwtData);

        return Map.of(Const.ACCESS_TOKEN, accessToken, Const.REFRESH_TOKEN, refreshToken);
    }

    // Single revocation check for all tokens and cookies: anything issued before
    // sessionsValidFrom (epoch seconds) is invalid.
    public static boolean isSessionValid(User user, JWTClaimsSet jwtClaimsSet) {
        if (user == null || jwtClaimsSet == null) {
            return false;
        }

        var issuedAt = jwtClaimsSet.getIssueTime();
        if (issuedAt == null) {
            return false;
        }

        return issuedAt.toInstant().getEpochSecond() >= user.getSessionsValidFrom();
    }

    private JWTClaimsSet parseJwt(String value, byte[] key, byte[] secret, String audience, int expires) throws MangooJwtException {
        Objects.requireNonNull(value, Required.VALUE);
        Objects.requireNonNull(secret, Required.SECRET);

        var jwtData = JwtUtils.jwtData()
                .withKey(key)
                .withSecret(secret)
                .withTtlSeconds(expires)
                .withIssuer(config.getApplicationName())
                .withAudience(audience);

        return JwtUtils.parseJwt(value, jwtData);
    }

    public JWTClaimsSet parseAccessToken(String value) throws MangooJwtException {
        Objects.requireNonNull(value, Required.VALUE);

        return parseJwt(value,
                config.getString(API_ACCESS_TOKEN_KEY).getBytes(StandardCharsets.UTF_8),
                config.getString(API_ACCESS_TOKEN_SECRET).getBytes(StandardCharsets.UTF_8),
                config.getApplicationName(),
                config.getInt(API_ACCESS_TOKEN_EXPIRES) * 60);
    }

    public JWTClaimsSet parseChallengeToken(String value) throws MangooJwtException {
        Objects.requireNonNull(value, Required.VALUE);

        return parseJwt(value,
                config.getString(API_CHALLENGE_TOKEN_KEY).getBytes(StandardCharsets.UTF_8),
                config.getString(API_CHALLENGE_TOKEN_SECRET).getBytes(StandardCharsets.UTF_8),
                config.getApplicationName(),
                300);
    }

    public JWTClaimsSet parseRefreshToken(String value) throws MangooJwtException {
        Objects.requireNonNull(value, Required.VALUE);

        return parseJwt(value,
                config.getString(API_REFRESH_TOKEN_KEY).getBytes(StandardCharsets.UTF_8),
                config.getString(API_REFRESH_TOKEN_SECRET).getBytes(StandardCharsets.UTF_8),
                config.getApplicationName(),
                config.getInt(API_REFRESH_TOKEN_EXPIRES) * 60);
    }

    public JWTClaimsSet parseAuthenticationCookie(String value) throws MangooJwtException {
        Objects.requireNonNull(value, Required.VALUE);

        return parseJwt(value,
                config.getAuthenticationCookieKey(),
                config.getAuthenticationCookieSecret(),
                config.getAuthenticationCookieName(),
                (int) config.getAuthenticationCookieRememberExpires());
    }

    public void revoke(JWTClaimsSet jwtClaimsSet) {
        Objects.requireNonNull(jwtClaimsSet, Required.JWT_CLAIMS_SET);

        tokenBlacklist.revoke(jwtClaimsSet.getJWTID(), jwtClaimsSet.getExpirationTime().toInstant());
    }

    // The refresh token only carries the id of its access token, so the configured lifetime is the expiry bound.
    public void revokeAccessToken(String jwtId) {
        Argument.requireNonBlank(jwtId, Required.ID);

        tokenBlacklist.revoke(jwtId, Instant.now().plusSeconds(config.getInt(API_ACCESS_TOKEN_EXPIRES) * 60L));
    }

    // Covers every token type and the authentication cookie, which mangoo revokes on logout.
    public boolean isRevoked(JWTClaimsSet jwtClaimsSet) {
        Objects.requireNonNull(jwtClaimsSet, Required.JWT_CLAIMS_SET);

        var issuedAt = jwtClaimsSet.getIssueTime();
        return tokenBlacklist.isRevoked(jwtClaimsSet.getJWTID(),
                jwtClaimsSet.getSubject(),
                issuedAt == null ? null : issuedAt.toInstant());
    }

    /**
     * Reserves a challenge token for one attempt; a parallel attempt with the same token is refused.
     * Must be paired with {@link #endChallenge(JWTClaimsSet, boolean)}.
     */
    public boolean beginChallenge(JWTClaimsSet jwtClaimsSet) {
        Objects.requireNonNull(jwtClaimsSet, Required.JWT_CLAIMS_SET);

        String jwtId = jwtClaimsSet.getJWTID();
        if (!challengesInUse.add(jwtId)) {
            return false;
        }

        // Checked only after reserving: a redeeming attempt revokes before it releases, so this sees it.
        if (isRevoked(jwtClaimsSet)) {
            challengesInUse.remove(jwtId);
            return false;
        }

        return true;
    }

    // A failed attempt releases the token so a mistyped code can be retried.
    public void endChallenge(JWTClaimsSet jwtClaimsSet, boolean redeemed) {
        Objects.requireNonNull(jwtClaimsSet, Required.JWT_CLAIMS_SET);

        try {
            if (redeemed) {
                revoke(jwtClaimsSet);
            }
        } finally {
            challengesInUse.remove(jwtClaimsSet.getJWTID());
        }
    }
}