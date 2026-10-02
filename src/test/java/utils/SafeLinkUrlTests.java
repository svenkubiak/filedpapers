package utils;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class SafeLinkUrlTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "JAVASCRIPT:alert(1)",
            // URI reads example.com as the host here, so a host based check would accept it
            "javascript://example.com/%0aalert(document.domain)",
            "vbscript:msgbox(1)",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "file:///etc/passwd",
            "ftp://example.com/x",
            "place:sort=8&maxResults=10",
            "chrome://settings",
            "//example.com/x",
            "/relative/path",
            "not a url",
            ""
    })
    public void testDisallowedSchemesAreRejected(String url) {
        assertThat(url, Utils.isSafeLinkUrl(url), equalTo(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "java\tscript:alert(1)",
            "java\nscript:alert(1)",
            "java\rscript:alert(1)",
            " javascript:alert(1)",
            "\u0000javascript:alert(1)",
            "jav\u0009ascript:alert(1)"
    })
    public void testSchemesHiddenBehindControlCharactersAreRejected(String url) {
        assertThat(url.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r"),
                Utils.isSafeLinkUrl(url), equalTo(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com",
            "https://example.com/path?a=b#frag",
            "HTTPS://EXAMPLE.COM/",
            "https://example.com:8443/on-a-nonstandard-port",
            "http://192.168.1.10/internal-wiki",
            "https://user:pw@example.com/x"
    })
    public void testAllowedSchemesAreAccepted(String url) {
        assertThat(url, Utils.isSafeLinkUrl(url), equalTo(true));
    }

    @Test
    public void testNullIsRejected() {
        assertThat(Utils.isSafeLinkUrl(null), equalTo(false));
    }

    @Test
    public void testGeneratedFallbackCodeIsAcceptedAsSuch() {
        for (var i = 0; i < 50; i++) {
            String code = Utils.randomString();
            assertThat(code, Utils.isValidMfaFallback(code), equalTo(true));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "123456",
            "12345",
            "1234567",
            "short",
            "this-code-is-far-too-long-to-be-a-fallback",
            "has spaces in it 1234567890abcde",
            "contains+invalid/chars===========",
            ""
    })
    public void testNonFallbackShapesAreRejected(String value) {
        assertThat(value, Utils.isValidMfaFallback(value), equalTo(false));
    }

    @Test
    public void testOtpAndFallbackShapesAreDisjoint() {
        assertThat(Utils.isValidOtp("123456"), equalTo(true));
        assertThat(Utils.isValidMfaFallback("123456"), equalTo(false));

        String code = Utils.randomString();
        assertThat(Utils.isValidMfaFallback(code), equalTo(true));
        assertThat(Utils.isValidOtp(code), equalTo(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "&#106;avascript:alert(1)",
            "&#x6a;avascript:alert(1)",
            "&#74;avaScript:alert(1)",
            "java&Tab;script:alert(1)",
            "java&NewLine;script:alert(1)"
    })
    public void testEntityEncodedSchemesAreRejectedAfterParsing(String encodedHref) {
        String html = "<DL><DT><A HREF=\"" + encodedHref + "\">entry</A></DL>";
        String decodedHref = Jsoup.parse(html).select("a").attr("href");

        assertThat(decodedHref, Utils.isSafeLinkUrl(decodedHref), equalTo(false));
    }

    @Test
    public void testOrdinaryBookmarkFileSurvivesParsingAndValidation() {
        String html = """
                <DL><DT><A HREF="https://example.com/a" ADD_DATE="1700000000">A</A>
                <DT><A HREF="http://example.org/b">B</A></DL>""";

        var links = Jsoup.parse(html).select("a");
        assertThat(links.size(), equalTo(2));
        links.forEach(link -> assertThat(link.attr("href"), Utils.isSafeLinkUrl(link.attr("href")), equalTo(true)));
    }
}
