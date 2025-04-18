package utils;

import com.google.re2j.Pattern;
import constants.Const;
import constants.Required;
import io.mangoo.core.Config;
import io.mangoo.utils.DateUtils;
import io.mangoo.utils.MangooUtils;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.CookieSameSiteMode;
import models.User;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

public final class Utils {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-6[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MFA_PATTERN = Pattern.compile("\\d{6}");

    private Utils() {
    }

    public static boolean isValidUuid(String uuid) {
        return StringUtils.isNotBlank(uuid) && UUID_PATTERN.matcher(uuid).matches();
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

    public static boolean isValidOtp(String mfa) {
        return StringUtils.isNotBlank(mfa) && MFA_PATTERN.matcher(mfa).matches();
    }

    public static String language(User user) {
        if (user == null || user.getLanguage() == null) return Const.DEFAULT_LANGUAGE;

        return user.getLanguage();
    }

    public static Map<String, String> getLanguages() {
        Map<String, String> languages = new HashMap<>();
        for (String language : MangooUtils.getLanguages()) {
            var locale = Locale.of(language);
            languages.put(language, locale.getDisplayLanguage(locale));
        }

        return languages;
    }

    public static List<Map<String, Object>> convertItems(List<Map<String, Object>> items) {
        Objects.requireNonNull(items, Required.ITEMS);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> map = new HashMap<>(item);

            var instant = Instant.ofEpochSecond(((long) item.get("sort")));
            var localDateTime = LocalDateTime
                    .ofInstant(instant, ZoneOffset.UTC);

            map.put("sort", localDateTime);
            list.add(map);
        }

        return list;
    }

    public static String getVersion() {
        String version = Utils.class.getPackage().getImplementationVersion();
        if (StringUtils.isBlank(version)) {
            version = "Unknown";
        }

        return version;
    }

    public static void checkCondition(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public static Cookie getLanguageCookie(String language, Config config, boolean rememberMe) {
        Objects.requireNonNull(language, Required.LANGUAGE);
        Objects.requireNonNull(config, Required.CONFIG);

        Cookie cookie = new CookieImpl(config.getI18nCookieName());
        cookie.setValue(language);
        cookie.setHttpOnly(true);
        cookie.setSecure(config.isAuthenticationCookieSecure());
        cookie.setSameSite(true);
        cookie.setSameSiteMode(CookieSameSiteMode.STRICT.toString());
        cookie.setPath("/");

        if (rememberMe) {
            cookie.setExpires(DateUtils.localDateTimeToDate(
                    LocalDateTime.now()
                            .plusHours(config.getAuthenticationCookieRememberExpires())));
        } else if (config.isAuthenticationCookieExpires()) {
            cookie.setExpires(DateUtils.localDateTimeToDate(
                    LocalDateTime.now()
                            .plusMinutes(config.getAuthenticationCookieTokenExpires())));
        }

        return cookie;
    }

    public static Optional<String> getImageAsBase64(String url) {
        Objects.requireNonNull(url, Required.URL);

        String data = null;
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String base64 = Base64.getEncoder().encodeToString(response.body());

            String contentType = response.headers().firstValue("Content-Type").orElse("image/jpeg");
            data = "data:" + contentType + ";base64," + base64;
        } catch (Exception e) {
            //Intentionally left blank
        }

        return Optional.ofNullable(data);
    }
}