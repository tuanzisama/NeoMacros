package ink.tuanzi.neomacros.fabric.web.http;

import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CORS responses echo exactly one of the configured origins — the panel
 * site plus any extras configured under {@code core.web.allowedOrigins}
 * (mirrors, local dev servers) — and never the wildcard. Preflights carry
 * the extra {@code Access-Control-Allow-Private-Network} header that
 * current Chromium requires when a public site reaches a local port, and
 * responses expose {@code ETag} so panel scripts can read it for
 * optimistic concurrency.
 */
public final class CorsPolicy {

    private static final Logger LOG = LoggerFactory.getLogger("neomacros.web");

    private static final String ALLOW_METHODS = "GET, POST, PUT, DELETE, OPTIONS";
    private static final List<String> ALLOW_HEADERS =
            List.of("Authorization", "Content-Type", "If-Match");
    private static final String EXPOSE_HEADERS = "ETag";

    private final Set<String> allowedOrigins;
    private final Set<String> warnedOrigins = ConcurrentHashMap.newKeySet();

    /** Creates the policy over raw site URLs; unusable entries are skipped. */
    public CorsPolicy(List<String> siteUrls) {
        Set<String> origins = new LinkedHashSet<>();
        for (String url : siteUrls) {
            String origin = originOf(url);
            if (origin != null) {
                origins.add(origin);
            }
        }
        this.allowedOrigins = origins;
    }

    /** The allowed origins (diagnostics and tests). */
    public Set<String> allowedOrigins() {
        return allowedOrigins;
    }

    /** The origin of a site URL (default ports normalized), or null when unusable. */
    private static String originOf(String siteUrl) {
        if (siteUrl == null || siteUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(siteUrl.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host = uri.getHost();
            if (!scheme.equals("http") && !scheme.equals("https") || host == null) {
                return null;
            }
            int port = uri.getPort();
            boolean defaultPort = port == -1
                    || (scheme.equals("https") && port == 443)
                    || (scheme.equals("http") && port == 80);
            return scheme + "://" + host + (defaultPort ? "" : ":" + port);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Adds the CORS response headers for a request, when applicable. */
    public void apply(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || !allowedOrigins.contains(origin)) {
            if (origin != null && warnedOrigins.add(origin)) {
                LOG.warn("request Origin '{}' is not in the CORS allowlist"
                        + " (core.web.panelUrl / core.web.allowedOrigins);"
                        + " the response carries no Access-Control-Allow-Origin header",
                        origin);
            }
            return;
        }
        var headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", origin);
        headers.set("Vary", "Origin");
        headers.set("Access-Control-Allow-Methods", ALLOW_METHODS);
        headers.set("Access-Control-Allow-Headers", String.join(", ", ALLOW_HEADERS));
        headers.set("Access-Control-Expose-Headers", EXPOSE_HEADERS);
        headers.set("Access-Control-Max-Age", "600");
        String requested = exchange.getRequestHeaders().getFirst(
                "Access-Control-Request-Private-Network");
        if (requested != null) {
            headers.set("Access-Control-Allow-Private-Network", "true");
        }
    }
}
