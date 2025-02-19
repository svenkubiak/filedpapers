package utils;

import constants.Const;
import constants.Required;
import io.mangoo.exceptions.MangooTokenException;
import io.mangoo.utils.MangooUtils;
import io.mangoo.utils.paseto.PasetoBuilder;
import io.mangoo.utils.paseto.PasetoParser;
import io.mangoo.utils.paseto.Token;
import org.apache.commons.lang3.StringUtils;
import models.User;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

public final class Utils {

    private Utils() {
    }

    public static boolean isValidUserUid(String userUid) {
        return StringUtils.isNotBlank(userUid) && UUID.fromString(userUid).version() == 6;
    }

    public static Map<String, String> getChallengeToken(String userUid, String challengeTokenSecret) throws MangooTokenException {
        Objects.requireNonNull(userUid, Required.USER_UID);
        Objects.requireNonNull(challengeTokenSecret, Required.ACCESS_TOKEN_SECRET);

        var now = LocalDateTime.now();

        String challengeToken = PasetoBuilder.create()
                .withSecret(challengeTokenSecret)
                .withExpires(now.plusMinutes(5))
                .withClaim(Const.NONCE, MangooUtils.randomString(32))
                .withSubject(userUid)
                .build();

        return Map.of(Const.CHALLENGE_TOKEN, challengeToken);
    }

    public static Map<String, String> getAccessTokens(String userUid, String accessTokenSecret, String refreshTokenSecret, int accessTokenExpires, int refreshTokenExpires) throws MangooTokenException {
        Objects.requireNonNull(userUid, Required.USER_UID);
        Objects.requireNonNull(accessTokenSecret, Required.ACCESS_TOKEN_SECRET);
        Objects.requireNonNull(refreshTokenSecret, Required.REFRESH_TOKEN_SECRET);

        var now = LocalDateTime.now();

        String accessToken = PasetoBuilder.create()
                .withSecret(accessTokenSecret)
                .withExpires(now.plusMinutes(accessTokenExpires))
                .withClaim(Const.NONCE, MangooUtils.randomString(32))
                .withSubject(userUid)
                .build();

        String refreshToken = PasetoBuilder.create()
                .withSecret(refreshTokenSecret)
                .withExpires(now.plusMinutes(refreshTokenExpires))
                .withClaim(Const.NONCE, MangooUtils.randomString(32))
                .withSubject(userUid)
                .build();

        return Map.of(Const.ACCESS_TOKEN, accessToken, Const.REFRESH_TOKEN, refreshToken);
    }

    public static Token parsePaseto(String value, String secret) throws MangooTokenException {
        Objects.requireNonNull(value, Required.VALUE);
        Objects.requireNonNull(secret, Required.SECRET);

        return PasetoParser.create()
                .withValue(value)
                .withSecret(secret)
                .parse();
    }

    public static Optional<String> validateToken(Token token) {
        Objects.requireNonNull(token, Required.TOKEN);

        LocalDateTime expires = token.getExpires();
        String userUid = token.getSubject();
        if (expires != null && expires.isAfter(LocalDateTime.now()) && isValidUserUid(userUid)) {
            return Optional.of(userUid);
        }

        return Optional.empty();
    }

    public static void sortCategories(List<Map<String, Object>> categories) {
        Objects.requireNonNull(categories, Required.CATEGORIES);
        
        categories.sort((map1, map2) -> {
            String name1 = (String) map1.get("name");
            String name2 = (String) map2.get("name");

            if (Const.INBOX.equals(name1)) return -1;
            if (Const.INBOX.equals(name2)) return 1;

            if (Const.TRASH.equals(name1)) return 1;
            if (Const.TRASH.equals(name2)) return -1;

            return name1.compareToIgnoreCase(name2);
        });
    }

    public static boolean isValidURL(String url) {
        try {
            var uri = new URI(url);
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static String language(User user) {
        if (user == null || user.getLanguage() == null) return Const.DEFAULT_LANGUAGE;

        return user.getLanguage();
    }

    public static Map<String, String> getLanguages() {
        Map<String, String> languages = new HashMap<>();
        for (String language : MangooUtils.getLanguages()) {
            Locale locale = Locale.of(language);
            languages.put(language, locale.getDisplayLanguage(locale));
        }

        return languages;
    }
}
