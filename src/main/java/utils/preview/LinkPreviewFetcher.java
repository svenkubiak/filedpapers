package utils.preview;

import constants.Const;
import io.mangoo.core.Application;
import io.mangoo.i18n.Messages;
import org.apache.logging.log4j.util.Strings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public final class LinkPreviewFetcher {
    private LinkPreviewFetcher() {}

    public static LinkPreview fetch(String url) throws IOException {
        URL parsedUrl = URI.create(url).toURL();
        Document document = Jsoup.connect(url)
                .userAgent(Const.USER_AGENT)
                .timeout((int) Duration.ofSeconds(10).toMillis())
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Connection", "keep-alive")
                .get();

        System.out.println(document.html());

+        return buildLinkPreview(document, parsedUrl);
    }

    private static LinkPreview buildLinkPreview(Document document, URL url) {
        String title = HtmlParser.extractTitle(document).orElse(Application.getInstance(Messages.class).get("item.missing.title"));
        String description = HtmlParser.extractDescription(document).orElse(Strings.EMPTY);
        String domainName = HtmlParser.extractDomainName(document, url);
        String imageUrl = HtmlParser.extractImageUrl(document, url).orElse(Const.PLACEHOLDER_IMAGE);

        return new LinkPreview(title, description, url, domainName, imageUrl);
    }
} 