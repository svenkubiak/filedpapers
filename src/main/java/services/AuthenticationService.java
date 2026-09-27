package services;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jwt.JWTClaimsSet;
import constants.Const;
import constants.Invalid;
import constants.Required;
import io.mangoo.cache.Cache;
import io.mangoo.cache.CacheImpl;
import io.mangoo.core.Config;
import io.mangoo.exceptions.MangooJwtException;
import io.mangoo.utils.Argument;
import io.mangoo.utils.CommonUtils;
import io.mangoo.utils.JwtUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import models.Token;
import models.User;
import org.apache.logging.log4j.util.Strings;
import utils.Utils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;

@Singleton
public class AuthenticationService {
    private static final int THOUSAND = 1000;
    private static final String INVALID = "invalid_";
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
    private final Cache cache;

    @Inject
    public AuthenticationService(DataService dataService, Config config) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.config = Objects.requireNonNull(config, Required.CONFIG);
        this.cache = new CacheImpl( Caffeine.newBuilder()
                .maximumSize(THOUSAND)
                .expireAfterWrite(Duration.of(10, ChronoUnit.MINUTES))
                .build());
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

    /**
     * Checks whether a token or authentication cookie was issued after the last
     * revocation for the given user. This is the single point that makes
     * "logout all devices", a password change and a password reset effective
     * for access tokens, refresh tokens and dashboard sessions alike.
     *
     * @param user The user the token was issued for, may be null
     * @param jwtClaimsSet The claims of the parsed token or cookie
     * @return true if the token is still considered valid
     */
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

    public void blacklistToken(String id) {
        Argument.requireNonBlank(id, Required.ID);
        cache.put(INVALID + id, Strings.EMPTY);
    }

    public boolean isTokenBlacklisted(String id) {
        Argument.requireNonBlank(id, Required.ID);

        return cache.get(INVALID + id) != null;
    }

    public boolean isRefreshBlacklisted(String id) {
        Argument.requireNonBlank(id, Required.ID);
        return dataService.tokenExists(id);
    }

    public void blacklistRefreshToken(String id) {
        Argument.requireNonBlank(id, Required.ID);

        if (!isRefreshBlacklisted(id)) {
            dataService.save(new Token(id, LocalDateTime.now()));
        }
    }
}