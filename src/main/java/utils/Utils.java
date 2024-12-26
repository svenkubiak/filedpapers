package utils;

import constants.Const;
import io.mangoo.exceptions.MangooTokenException;
import io.mangoo.utils.MangooUtils;
import io.mangoo.utils.paseto.PasetoBuilder;
import io.mangoo.utils.paseto.PasetoParser;
import io.mangoo.utils.paseto.Token;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

public final class Utils {

    public static boolean isValidUserUid(String userUid) {
        return StringUtils.isNotBlank(userUid) && UUID.fromString(userUid).version() == 6;
    }

    public static Map<String, String> getTokens(String userUid, String accessTokenSecret, String refreshTokenSecret, int accessTokenExpires, int refreshTokenExpires) throws MangooTokenException {
        Objects.requireNonNull(userUid, "userUid can not be null");
        Objects.requireNonNull(accessTokenSecret, "accessTokenSecret can not be null");
        Objects.requireNonNull(refreshTokenSecret, "refreshTokenSecret can not be null");

        LocalDateTime now = LocalDateTime.now();

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
        Objects.requireNonNull(value, "value can not be null");
        Objects.requireNonNull(secret, "secret can not be null");

        return PasetoParser.create()
                .withValue(value)
                .withSecret(secret)
                .parse();
    }

    public static Optional<String> validateToken(Token token) {
        Objects.requireNonNull(token, "token can not be null");

        LocalDateTime expires = token.getExpires();
        String userUid = token.getSubject();
        if (expires != null && expires.isAfter(LocalDateTime.now()) && isValidUserUid(userUid)) {
            return Optional.of(userUid);
        }

        return Optional.empty();
    }


    public static void sortCategories(Optional<List<Map<String, Object>>> categories) {
        // Sort the list
        categories.get().sort((map1, map2) -> {
            String name1 = (String) map1.get("name");
            String name2 = (String) map2.get("name");

            if ("Inbox".equals(name1)) return -1; // Inbox goes first
            if ("Inbox".equals(name2)) return 1;  // Inbox goes first

            if ("Trash".equals(name1)) return 1;  // Trash goes last
            if ("Trash".equals(name2)) return -1; // Trash goes last

            // Otherwise, sort alphabetically
            return name1.compareToIgnoreCase(name2);
        });
    }
}
