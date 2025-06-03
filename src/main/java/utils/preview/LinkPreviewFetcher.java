package utils.preview;

import constants.Const;
import de.svenkubiak.http.Http;
import de.svenkubiak.http.Result;
import io.mangoo.core.Application;
import io.mangoo.i18n.Messages;
import io.mangoo.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public final class LinkPreviewFetcher {
    private static final Logger LOG = LogManager.getLogger(LinkPreviewFetcher.class);
    private LinkPreviewFetcher() {}

    public static LinkPreview fetch(String url, String language) {
        if (StringUtils.isBlank(language)) { language = "en"; }

        var result = Http.get(getUrl() + "/preview?lang=" + language + "&url=" + URLEncoder.encode(url, StandardCharsets.UTF_8))
                .withTimeout(Duration.ofSeconds(10))
                .send();

        LOG.info("Link preview fetch result: {}", result.body());

        return buildLinkPreview(result.body(), url);
    }

    private static LinkPreview buildLinkPreview(String json, String url) {
        Map<String, String> flatMap = JsonUtils.toFlatMap(json);

        if (("null").equals(flatMap.get("title"))) { flatMap.put("title", null); }
        if (("null").equals(flatMap.get("image"))) { flatMap.put("image", null); }
        if (("null").equals(flatMap.get("description"))) { flatMap.put("description", null); }
        if (("null").equals(flatMap.get("domain"))) { flatMap.put("domain", null); }

        String title = Optional.ofNullable(flatMap.get("title")).orElse(Application.getInstance(Messages.class).get("item.missing.title"));
        String description = Optional.ofNullable(flatMap.get("description")).orElse(Strings.EMPTY);
        String image = Optional.ofNullable(flatMap.get("image")).orElse(Const.PLACEHOLDER_IMAGE);
        String domain = Optional.ofNullable(flatMap.get("domain")).orElse(Strings.EMPTY);

        LOG.info("LinkPreview title: {}", title);
        LOG.info("LinkPreview description: {}", description);
        LOG.info("LinkPreview image: {}", image);
        LOG.info("LinkPreview domain: {}", domain);

        return new LinkPreview(title, description, url, domain, image);
    }

    private static String getUrl() {
        String url = System.getProperty("application.metascraper.url");

        if (StringUtils.isNotBlank(url)) { return url; }

        if (Application.inProdMode()) {
            return "http://filedpapers-metascraper:3000";
        }

        return "http://localhost:3000";
    }

} 