package services;

import constants.Const;
import constants.Required;
import io.mangoo.constants.Key;
import io.mangoo.exceptions.MangooTokenException;
import io.mangoo.utils.MangooUtils;
import io.mangoo.utils.paseto.PasetoBuilder;
import io.mangoo.utils.paseto.PasetoParser;
import io.mangoo.utils.paseto.Token;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import models.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import utils.Utils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class AuthenticationService {
    private final DataService dataService;
    private final String challengeTokenSecret;
    private final String accessTokenSecret;
    private final String refreshTokenSecret;
    private final String cookieSecret;
    private final int accessTokenExpires;
    private final int refreshTokenExpires;

    @Inject
    public AuthenticationService(DataService dataService,
                                 @Named("api.challengeToken.secret") String challengeTokenSecret,
                                 @Named("api.accessToken.secret") String accessTokenSecret,
                                 @Named("api.refreshToken.secret") String refreshTokenSecret,
                                 @Named(Key.AUTHENTICATION_COOKIE_SECRET) String cookieSecret,
                                 @Named("api.accessToken.expires") int accessTokenExpires,
                                 @Named("api.refreshToken.expires") int refreshTokenExpires) {
        this.dataService = Objects.requireNonNull(dataService, Required.DATA_SERVICE);
        this.challengeTokenSecret = Objects.requireNonNull(challengeTokenSecret, Required.CHALLENGE_TOKEN_SECRET);
        this.accessTokenSecret = Objects.requireNonNull(accessTokenSecret, Required.ACCESS_TOKEN_SECRET);
        this.refreshTokenSecret = Objects.requireNonNull(refreshTokenSecret, Required.REFRESH_TOKEN_SECRET);
        this.cookieSecret = Objects.requireNonNull(cookieSecret, Required.COOKIE_SECRET);
        this.accessTokenExpires = accessTokenExpires;
        this.refreshTokenExpires = refreshTokenExpires;
    }

    public Map<String, String> getChallengeToken(String userUid) throws MangooTokenException {
        Objects.requireNonNull(userUid, Required.USER_UID);

        var now = LocalDateTime.now();

        String challengeToken = PasetoBuilder.create()
                .withSecret(challengeTokenSecret)
                .withExpires(now.plusMinutes(5))
                .withClaim(Const.NONCE, MangooUtils.randomString(32))
                .withSubject(userUid)
                .build();

        return Map.of(Const.CHALLENGE_TOKEN, challengeToken);
    }

    public Map<String, String> getAccessTokens(String userUid) throws MangooTokenException {
        Objects.requireNonNull(userUid, Required.USER_UID);

        var now = LocalDateTime.now();
        String accessToken = PasetoBuilder.create()
                .withSecret(accessTokenSecret)
                .withExpires(now.plusMinutes(accessTokenExpires))
                .withClaim(Const.NONCE, MangooUtils.randomString(32))
                .withClaim(Const.PEPPER, getPepper(userUid))
                .withSubject(userUid)
                .build();

        String refreshToken = PasetoBuilder.create()
                .withSecret(refreshTokenSecret)
                .withExpires(now.plusMinutes(refreshTokenExpires))
                .withClaim(Const.NONCE, MangooUtils.randomString(32))
                .withClaim(Const.PEPPER, getPepper(userUid))
                .withSubject(userUid)
                .build();

        return Map.of(Const.ACCESS_TOKEN, accessToken, Const.REFRESH_TOKEN, refreshToken);
    }

    private String getPepper(String userUid) {
        Objects.requireNonNull(userUid, Required.USER_UID);

        String pepper = Strings.EMPTY;
        var user = dataService.findUserByUid(userUid);
        if (user != null) {
            pepper = user.getPepper();
            if (StringUtils.isBlank(pepper)) {
                pepper = MangooUtils.randomString(64);
                user.setPepper(pepper);
                dataService.save(user);
            }
        }

        return pepper;
    }

    private Token parsePaseto(String value, String secret) throws MangooTokenException {
        Objects.requireNonNull(value, Required.VALUE);
        Objects.requireNonNull(secret, Required.SECRET);

        return PasetoParser.create()
                .withValue(value)
                .withSecret(secret)
                .parse();
    }

    public Token parseChallengeToken(String value) throws MangooTokenException {
        Objects.requireNonNull(value, Required.VALUE);

        return parsePaseto(value, challengeTokenSecret);
    }

    public Token parseRefreshToken(String value) throws MangooTokenException {
        Objects.requireNonNull(value, Required.VALUE);

        return parsePaseto(value, refreshTokenSecret);
    }

    public Token parseAccessToken(String value) throws MangooTokenException {
        Objects.requireNonNull(value, Required.VALUE);

        return parsePaseto(value, accessTokenSecret);
    }

    public Token parseAuthenticationCookie(String value) throws MangooTokenException {
        Objects.requireNonNull(value, Required.VALUE);

        return parsePaseto(value, cookieSecret);
    }

    public Optional<String> validateToken(Token token) {
        Objects.requireNonNull(token, Required.TOKEN);

        LocalDateTime expires = token.getExpires();
        String userUid = token.getSubject();
        if (expires != null && expires.isAfter(LocalDateTime.now()) && Utils.isValidUserUid(userUid)) {
            return Optional.of(userUid);
        }

        return Optional.empty();
    }
}