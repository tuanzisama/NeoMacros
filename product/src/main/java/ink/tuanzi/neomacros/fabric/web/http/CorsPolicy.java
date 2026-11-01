package ink.tuanzi.neomacros.fabric.web.http;

import com.sun.net.httpserver.HttpExchange;

import java.net.URI;
import java.util.List;

/**
 * CORS responses echo exactly one origin — the configured panel site — and
 * never the wildcard. Preflights carry the extra
 * {@code Access-Control-Allow-Private-Network} header that current Chromium
 * requires when a public site reaches a local port.
 */
public final class CorsPolicy {

    private final String allowedOrigin;
    private static final String ALLOW_METHODS = "GET, POST, PUT, DELETE, OPTIONS";
    private static final List<String> ALLOW_HEADERS =
            List.of("Authorization", "Content-Type", "If-Match");

    /** Creates the policy from the panel site URL (origin is derived). */
    public CorsPolicy(String panelUrl) {
        this.allowedOrigin = originOf(panelUrl);
    }

    /** The allowed origin, or null when the panel URL is unusable. */
    private static String originOf(String panelUrl) {
        if (panelUrl == null || panelUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(panelUrl.strip());
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
        if (allowedOrigin == null) {
            return;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || !origin.equals(allowedOrigin)) {
            return;
        }
        var headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", allowedOrigin);
        headers.set("Vary", "Origin");
        headers.set("Access-Control-Allow-Methods", ALLOW_METHODS);
        headers.set("Access-Control-Allow-Headers", String.join(", ", ALLOW_HEADERS));
        headers.set("Access-Control-Max-Age", "600");
        String requested = exchange.getRequestHeaders().getFirst(
                "Access-Control-Request-Private-Network");
        if (requested != null) {
            headers.set("Access-Control-Allow-Private-Network", "true");
        }
    }

    /** The origin this policy echoes (diagnostics and tests). */
    public String allowedOrigin() {
        return allowedOrigin;
    }
}
