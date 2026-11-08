package ink.tuanzi.neomacros.fabric.mcp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The MCP connection gate: a bearer token every request must present, and
 * an origin check that keeps browsers (the DNS-rebinding surface) out while
 * leaving header-only agent clients untouched. The token travels only in
 * the {@code Authorization} header, never in a URL.
 */
public final class McpAuth {

    private McpAuth() {
    }

    /**
     * Whether the {@code Authorization} header carries {@code Bearer} plus
     * exactly the expected token. The comparison is constant-time.
     */
    public static boolean tokenMatches(String authorizationHeader, String expectedToken) {
        if (authorizationHeader == null || expectedToken == null || expectedToken.isBlank()) {
            return false;
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            return false;
        }
        byte[] presented = authorizationHeader.substring("Bearer ".length())
                .trim().getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedToken.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presented, expected);
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
