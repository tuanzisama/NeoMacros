package ink.tuanzi.neomacros.fabric.mcp;

import java.net.URI;

/**
 * The MCP connection gate: an origin check that keeps browsers (the
 * DNS-rebinding surface) out while leaving header-only agent clients
 * untouched.
 */
public final class McpAuth {

    private McpAuth() {
    }

    /**
     * Whether the request may proceed based on its {@code Origin} header.
     * Agents and plain HTTP clients send none (allowed); browsers always do,
     * and only loopback origins are accepted.
     */
    public static boolean originAllowed(String originHeader) {
        if (originHeader == null || originHeader.isBlank()) {
            return true;
        }
        try {
            String host = new URI(originHeader).getHost();
            if (host == null) {
                return false;
            }
            return host.equals("127.0.0.1") || host.equals("localhost")
                    || host.replace("[", "").replace("]", "").equals("::1");
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }
}
