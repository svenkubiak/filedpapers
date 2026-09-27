package utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Guards outbound HTTP requests that are triggered by untrusted input against
 * Server-Side Request Forgery.
 *
 * A URL is only considered safe if its scheme and port are allow-listed and
 * <b>every</b> address the hostname resolves to is publicly routable.
 */
public final class SsrfGuard {
    private static final Logger LOG = LogManager.getLogger(SsrfGuard.class);
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<Integer> ALLOWED_PORTS = Set.of(80, 443);

    private SsrfGuard() {
    }

    /**
     * Checks whether the given URL may be requested by the server.
     *
     * @param url the URL to check
     * @return true if scheme, port and all resolved addresses are acceptable
     */
    public static boolean isPubliclyRoutable(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            LOG.warn("Blocked malformed URL");
            return false;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            LOG.warn("Blocked URL without scheme or host");
            return false;
        }

        if (!ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            LOG.warn("Blocked URL with disallowed scheme: {}", scheme);
            return false;
        }

        int port = uri.getPort();
        if (port != -1 && !ALLOWED_PORTS.contains(port)) {
            LOG.warn("Blocked URL with disallowed port: {}", port);
            return false;
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            LOG.warn("Blocked URL with unresolvable host: {}", host);
            return false;
        }

        if (addresses.length == 0) {
            LOG.warn("Blocked URL with unresolvable host: {}", host);
            return false;
        }

        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                LOG.warn("Blocked URL resolving to non-public address: {} -> {}", host, address.getHostAddress());
                return false;
            }
        }

        return true;
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress()         // 0.0.0.0, ::
                || address.isLoopbackAddress()  // 127.0.0.0/8, ::1
                || address.isLinkLocalAddress() // 169.254.0.0/16, fe80::/10
                || address.isSiteLocalAddress() // 10/8, 172.16/12, 192.168/16
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;

            if (first == 100 && second >= 64 && second <= 127) return false;        // 100.64.0.0/10 CGNAT
            if (first == 192 && second == 0) return false;                          // 192.0.0.0/24, 192.0.2.0/24
            if (first == 198 && (second == 18 || second == 19)) return false;       // 198.18.0.0/15
            if (first == 198 && second == 51) return false;                         // 198.51.100.0/24
            if (first == 203 && second == 0) return false;                          // 203.0.113.0/24
            if (first >= 240) return false;                                         // 240.0.0.0/4, 255.255.255.255
        } else {
            if ((bytes[0] & 0xFE) == 0xFC) return false;                            // fc00::/7 unique local
        }

        return true;
    }
}
