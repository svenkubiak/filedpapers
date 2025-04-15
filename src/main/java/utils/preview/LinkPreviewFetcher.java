package utils.preview;

import constants.Const;
import de.svenkubiak.http.Http;
import de.svenkubiak.http.Result;
import io.mangoo.core.Application;
import io.mangoo.i18n.Messages;
import org.apache.logging.log4j.util.Strings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class LinkPreviewFetcher {
    private LinkPreviewFetcher() {}

    public static LinkPreview fetch(String url) throws IOException, URISyntaxException {
        URL parsedUrl = URI.create(url).toURL();
        Result result = Http.get(url)
                .withHeader("User-Agent", Const.USER_AGENT)
                .withHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
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