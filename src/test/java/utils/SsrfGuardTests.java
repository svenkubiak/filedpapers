package utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class SsrfGuardTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/",
            "http://127.0.0.1:80/admin",
            "https://127.0.0.2/",
            "http://localhost/",
            "http://[::1]/",
            "http://169.254.169.254/latest/meta-data/",
            "http://[fd00::1]/",
            "http://[fe80::1]/",
            "http://10.0.0.1/",
            "http://172.16.0.1/",
            "http://192.168.1.1/",
            "http://100.64.0.1/",
            "http://0.0.0.0/",
            "http://255.255.255.255/",
            // Alternative notations are covered because the guard inspects the
            // resolved address, not the literal the user typed.
            "http://2130706433/",
            "http://127.1/",
            "http://192.0.2.1/",
            "http://198.18.0.1/",
            "http://203.0.113.1/",
            "http://240.0.0.1/"
    })
    public void testBlocksNonPublicTargets(String url) {
        assertThat(url, SsrfGuard.isPubliclyRoutable(url), equalTo(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            // the scheme allowlist is also what keeps a javascript: url out of the
            // api entry point, see SafeLinkUrlTests for the import side
            "javascript://example.com/%0aalert(1)",
            "javascript:alert(1)",
            "vbscript:msgbox(1)",
            "data:text/html,<script>alert(1)</script>",
            "gopher://example.com:70/",
            "ftp://example.com/",
            "//example.com/",
            "not a url",
            "",
            "http://example.com:8080/",
            "https://example.com:22/",
            "http://0x7f000001/"
    })
    public void testBlocksDisallowedSchemesPortsAndNotations(String url) {
        assertThat(url, SsrfGuard.isPubliclyRoutable(url), equalTo(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://8.8.8.8/",
            "https://1.1.1.1/",
            "https://8.8.4.4:443/some/path?a=b",
            "http://[2606:4700:4700::1111]/"
    })
    public void testAllowsPublicTargets(String url) {
        assertThat(url, SsrfGuard.isPubliclyRoutable(url), equalTo(true));
    }

    @Test
    public void testBlocksNullUrl() {
        assertThat(SsrfGuard.isPubliclyRoutable(null), equalTo(false));
    }
}
