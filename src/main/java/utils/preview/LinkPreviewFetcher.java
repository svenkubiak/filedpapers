package utils.preview;

import constants.Const;
import de.svenkubiak.http.Http;
import de.svenkubiak.http.Result;
import io.mangoo.core.Application;
import io.mangoo.i18n.Messages;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.util.Optional;

public final class LinkPreviewFetcher {
    private LinkPreviewFetcher() {}

    public static LinkPreview fetch(String url, String language) throws IOException {
        if (StringUtils.isBlank(language)) { language = "en"; };

        URL parsedUrl = URI.create(url).toURL();
        Result result = Http.get(url)
                .withHeader("User-Agent", "Googlebot")
                .withHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .withHeader("Accept-Language", language + ", en;q=0.8, *;q=0.5")
                .withVersion(HttpClient.Version.HTTP_1_1)
                .followRedirects()
                .send();

        Document document = Jsoup.parse(result.body());
        return buildLinkPreview(document, parsedUrl);
    }

    private static LinkPreview buildLinkPreview(Document document, URL url) {
        String title = HtmlParser.extractTitle(document).orElse(Application.getInstance(Messages.class).get("item.missing.title"));
        String description = HtmlParser.extractDescription(document).orElse(Strings.EMPTY);
        String domainName = HtmlParser.extractDomainName(document, url);
        String imageUrl = HtmlParser.extractImageUrl(document, url).orElse(Const.PLACEHOLDER_IMAGE);

        return new LinkPreview(title, description, url, domainName, imageUrl);
    }
} 