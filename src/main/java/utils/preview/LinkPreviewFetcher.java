package utils.preview;

import constants.Const;
import de.svenkubiak.http.Http;
import de.svenkubiak.http.Result;
import io.mangoo.core.Application;
import io.mangoo.i18n.Messages;
import io.mangoo.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public final class LinkPreviewFetcher {
    private LinkPreviewFetcher() {}

    public static LinkPreview fetch(String url, String language) throws IOException {
        if (StringUtils.isBlank(language)) { language = "en"; };

        String metascraper = System.getProperty("application.metascraper.url");
        if (StringUtils.isBlank(metascraper)) {
            metascraper = "http://filedpapers-metascraper:3000";
        }

        Result result = Http.get(metascraper + "/preview?lang=" + language + "&url=" + URLEncoder.encode(url, StandardCharsets.UTF_8))
                .withTimeout(Duration.ofSeconds(10))
                .send();

        return buildLinkPreview(result.body(), url);
    }

    private static LinkPreview buildLinkPreview(String json, String url) throws MalformedURLException {
        Map<String, String> flatMap = JsonUtils.toFlatMap(json);

        String title = Optional.ofNullable(flatMap.get("title")).orElse(Application.getInstance(Messages.class).get("item.missing.title"));
        String description = Optional.ofNullable(flatMap.get("description")).orElse(Strings.EMPTY);
        String image = Optional.ofNullable(flatMap.get("image")).orElse(Const.PLACEHOLDER_IMAGE);
        String domain = Optional.ofNullable(flatMap.get("domain")).orElse(Strings.EMPTY);

        return new LinkPreview(title, description, url, domain, image);
    }
} 