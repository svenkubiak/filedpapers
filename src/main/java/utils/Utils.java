package utils;

import constants.Const;
import constants.Required;
import io.mangoo.core.Application;
import io.mangoo.core.Config;
import io.mangoo.utils.CommonUtils;
import io.mangoo.utils.DateUtils;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.CookieSameSiteMode;
import models.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

public final class Utils {
    private static final int HOURS_PER_DAY = 24;
    private static final String ASSET_VERSION = assetVersion();
    private static final Pattern RANDOM_PATTERN = Pattern.compile(
            "^[a-z0-9-_]+$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "[-_a-z0-9äöüß]{1,32}",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern MFA_PATTERN = Pattern.compile("\\d{6}");
    // Must match the output of randomString().
    private static final Pattern MFA_FALLBACK_PATTERN = Pattern.compile("[A-Za-z0-9_-]{32}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x20]");
    private static final Set<String> ALLOWED_LINK_SCHEMES = Set.of("http", "https");

    private Utils() {
    }

    // Scheme check for href rendering only, no DNS; use SsrfGuard where the server fetches the url.
    public static boolean isSafeLinkUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }

        try {
            // Browsers ignore control characters in a scheme, so "java&Tab;script:" is "javascript:".
            var uri = new URI(CONTROL_CHARS.matcher(url).replaceAll(Strings.EMPTY));
            String scheme = uri.getScheme();

            return scheme != null && ALLOWED_LINK_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidRandom(String value) {
        return StringUtils.isNotBlank(value) && RANDOM_PATTERN.matcher(value).matches();
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

    public static boolean isValidOtp(String mfa) {
        return StringUtils.isNotBlank(mfa) && MFA_PATTERN.matcher(mfa).matches();
    }

    public static boolean isValidMfaFallback(String value) {
        return StringUtils.isNotBlank(value) && MFA_FALLBACK_PATTERN.matcher(value).matches();
    }

    public static boolean isValidName(String name) {
        return StringUtils.isNotBlank(name) && NAME_PATTERN.matcher(name).matches();
    }

    public static String language(User user) {
        if (user == null || user.getLanguage() == null) return Const.DEFAULT_LANGUAGE;

        return user.getLanguage();
    }

    public static Map<String, String> getLanguages() {
        Map<String, String> languages = null;
        try {
            int size = io.mangoo.utils.internal.MangooUtils.getLanguages().size();
            int initialCapacity = (int) (size / 0.75f) + 1;
            languages = HashMap.newHashMap(initialCapacity);

            for (String language : io.mangoo.utils.internal.MangooUtils.getLanguages()) {
                var locale = Locale.of(language);
                languages.put(language, locale.getDisplayLanguage(locale));
            }
        } catch (Exception e) {
            //intentionally left blank
        }


        return languages;
    }

    public static List<Map<String, Object>> convertItems(List<Map<String, Object>> items) {
        Objects.requireNonNull(items, Required.ITEMS);

        List<Map<String, Object>> list = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            Map<String, Object> map = HashMap.newHashMap((int)((item.size() + 1) / 0.75f) + 1);
            map.putAll(item);

            map.put("sort", toLocalDateTime(item.get("sort")));

            if (item.get("deleteAt") != null) {
                map.put("deleteAt", toLocalDateTime(item.get("deleteAt")));
            }

            list.add(map);
        }

        return list;
    }

    private static LocalDateTime toLocalDateTime(Object epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond((long) epochSecond), ZoneOffset.UTC);
    }

    public static String getVersion() {
        String version = Utils.class.getPackage().getImplementationVersion();
        if (StringUtils.isBlank(version)) {
            version = "Unknown";
        }

        return version;
    }

    // Snapshot builds use the start time, since their version doesn't change between runs.
    public static String getAssetVersion() {
        return ASSET_VERSION;
    }

    private static String assetVersion() {
        String version = getVersion();
        return ("Unknown").equals(version) || version.contains("SNAPSHOT")
                ? String.valueOf(System.currentTimeMillis())
                : version;
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
        cookie.setSameSiteMode(CookieSameSiteMode.STRICT.toString());
        cookie.setPath("/");

        if (rememberMe) {
            cookie.setExpires(DateUtils.localDateTimeToDate(
                    LocalDateTime.now()
                            .plusHours(config.getAuthenticationCookieRememberExpires())));
        } else {
            cookie.setExpires(DateUtils.localDateTimeToDate(
                    LocalDateTime.now()
                            .plusMinutes(config.getAuthenticationCookieTokenExpires())));
        }

        return cookie;
    }

    public static String randomString() {
        return CommonUtils.randomString(32);
    }

    public static int getTrashRetention() {
        return Application
                .getInstance(Config.class)
                .getInt("application.trash.retention", 72);
    }

    public static Map<String, String> getTrashRetentionLabel() {
        int hours = getTrashRetention();

        if (hours >= HOURS_PER_DAY && hours % HOURS_PER_DAY == 0) {
            int days = hours / HOURS_PER_DAY;
            return Map.of("value", String.valueOf(days), "unit", days == 1 ? "day" : "days");
        }

        return Map.of("value", String.valueOf(hours), "unit", hours == 1 ? "hour" : "hours");
    }
}