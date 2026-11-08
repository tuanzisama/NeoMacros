package ink.tuanzi.neomacros.fabric.mcp;

import com.google.gson.JsonElement;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ink.tuanzi.neomacros.fabric.web.http.ApiContext;
import ink.tuanzi.neomacros.fabric.web.http.HostPolicy;
import ink.tuanzi.neomacros.fabric.web.json.Jsons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * The MCP HTTP endpoint: a single {@code /mcp} context on the JDK server
 * with the Streamable HTTP rules — POST carries JSON-RPC, DELETE ends the
 * session, GET is refused. Every request passes the same hardening order as
 * the panel service (Host allowlist, body cap, bearer token, origin check);
 * the session header is required on everything except {@code initialize},
 * and an unknown session answers 404 so the agent re-initializes.
 */
public final class McpServer {

    private static final Logger LOG = LoggerFactory.getLogger("neomacros.mcp");

    private final McpProtocol protocol;
    private final McpSessions sessions;
    private final Supplier<String> token;
    private volatile HostPolicy hosts;

    private HttpServer server;
    private ExecutorService executor;
    private volatile InetSocketAddress boundAddress;

    /** Creates the server over the protocol layer and the token source. */
    public McpServer(McpProtocol protocol, McpSessions sessions, Supplier<String> token) {
        this.protocol = protocol;
        this.sessions = sessions;
        this.token = token;
    }

    /**
     * Binds and starts serving; the listen value is a bind address literal
     * ({@code 127.0.0.1}, {@code 0.0.0.0}, or {@code ::1}) and the port may
     * be 0 (ephemeral). Returns the actual port.
     *
     * @throws java.net.BindException when the configured port is taken
     */
    public int start(String listen, int configuredPort) throws IOException {
        InetAddress address = resolveBindAddress(listen);
        HttpServer created = HttpServer.create(new InetSocketAddress(address, configuredPort), 8);
        int boundPort = created.getAddress().getPort();
        hosts = address.isLoopbackAddress()
                ? HostPolicy.loopback(boundPort) : HostPolicy.lan(boundPort);
        created.createContext("/mcp", this::handle);
        server = created;
        executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "neomacros-mcp");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        boundAddress = server.getAddress();
        return boundAddress.getPort();
    }

    /** Stops the listener and its request threads. */
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        boundAddress = null;
    }

    /** Whether the listener is up. */
    public boolean running() {
        return server != null;
    }

    /** The bound port, or 0 when not running. */
    public int port() {
        InetSocketAddress address = boundAddress;
        return address == null ? 0 : address.getPort();
    }

    /** The full request pipeline. */
    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        try {
            if (!"POST".equals(method) && !"DELETE".equals(method)) {
                plain(exchange, 405, "method not allowed");
                return;
            }
            // 1. Host allowlist (the rebinding defense) runs first.
            if (!hosts.allows(exchange.getRequestHeaders().getFirst("Host"))) {
                plain(exchange, 403, "host not allowed");
                return;
            }
            // 2. Body cap.
            byte[] body;
            try {
                body = readBody(exchange);
            } catch (BodyTooLarge e) {
                plain(exchange, 413, "request body exceeds 1 MiB");
                return;
            }
            // 3. Bearer token.
            String expected = token.get();
            if (expected == null || expected.isBlank()
                    || !McpAuth.tokenMatches(
                            exchange.getRequestHeaders().getFirst("Authorization"), expected)) {
                exchange.getResponseHeaders().set("WWW-Authenticate",
                        "Bearer realm=\"neomacros-mcp\"");
                plain(exchange, 401, "unauthorized");
                return;
            }
            // 4. Origin check (browsers announce themselves; agents do not).
            if (!McpAuth.originAllowed(exchange.getRequestHeaders().getFirst("Origin"))) {
                plain(exchange, 403, "origin not allowed");
                return;
            }
            // 5. Route by verb.
            if ("DELETE".equals(method)) {
                terminate(exchange);
                return;
            }
            serve(exchange, body);
        } catch (Exception e) {
            try {
                plain(exchange, 500, "internal error");
            } catch (IOException ignored) {
                exchange.close();
            }
            LOG.warn("mcp request failed: {}", e.toString());
        }
    }

    /** The POST path: parse, enforce the session, dispatch, write. */
    private void serve(HttpExchange exchange, byte[] body) throws IOException {
        JsonElement message;
        try {
            message = Jsons.parse(new String(body, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            respond(exchange, 200, McpProtocol.parseError(), null);
            return;
        }
        String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        String requestedMethod = methodOf(message);
        boolean initialize = "initialize".equals(requestedMethod);
        if (!initialize) {
            String declared = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
            if (declared != null
                    && !McpProtocol.SUPPORTED_VERSIONS.contains(declared)) {
                respond(exchange, 400, McpProtocol.parseError(), null);
                return;
            }
            if (sessionId == null || sessionId.isBlank()) {
                plain(exchange, 400, "the Mcp-Session-Id header is required");
                return;
            }
            if (sessions.get(sessionId) == null) {
                plain(exchange, 404, "unknown session; initialize again");
                return;
            }
        }
        McpProtocol.HttpResult result = protocol.handle(message, sessionId);
        respond(exchange, result.status(), result.body(), result.sessionId());
    }

    /** The DELETE path: end the session (404 when unknown). */
    private void terminate(HttpExchange exchange) throws IOException {
        String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        if (sessionId == null || sessionId.isBlank()) {
            plain(exchange, 400, "the Mcp-Session-Id header is required");
            return;
        }
        if (!sessions.remove(sessionId)) {
            plain(exchange, 404, "unknown session");
            return;
        }
        ApiContext.respond(exchange, 204, "text/plain", new byte[0]);
    }

    /** The request's {@code method} field for the initialize exemption. */
    private static String methodOf(JsonElement message) {
        if (message == null || !message.isJsonObject()) {
            return null;
        }
        JsonElement method = message.getAsJsonObject().get("method");
        return method != null && method.isJsonPrimitive() ? method.getAsString() : null;
    }

    /** Writes a plain-text status line (transport-level failures). */
    private static void plain(HttpExchange exchange, int status, String text)
            throws IOException {
        ApiContext.respond(exchange, status, "text/plain; charset=utf-8",
                text.getBytes(StandardCharsets.UTF_8));
    }

    /** Writes the JSON-RPC outcome, stamping the session header when set. */
    private static void respond(HttpExchange exchange, int status, JsonElement body,
                                String sessionId) throws IOException {
        if (sessionId != null) {
            exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
        }
        byte[] payload = body == null
                ? new byte[0] : Jsons.stringify(body).getBytes(StandardCharsets.UTF_8);
        ApiContext.respond(exchange, status, "application/json; charset=utf-8", payload);
    }

    /** Reads the request body up to the cap; oversized or truncated → the marker. */
    private static byte[] readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        byte[] out = in.readNBytes(ApiContext.MAX_BODY_BYTES + 1);
        if (out.length > ApiContext.MAX_BODY_BYTES) {
            throw new BodyTooLarge();
        }
        return out;
    }

    /** Marker for the over-cap body case. */
    private static final class BodyTooLarge extends RuntimeException {
    }

    /** Resolves the configured listen value to a bind address. */
    private static InetAddress resolveBindAddress(String listen) {
        String value = listen == null ? "" : listen.trim();
        if (value.equalsIgnoreCase("loopback")) {
            return InetAddress.getLoopbackAddress();
        }
        if (value.equalsIgnoreCase("lan")) {
            return wildcardAddress();
        }
        try {
            return InetAddress.getByName(value);
        } catch (java.net.UnknownHostException e) {
            LOG.warn("core.mcp.listen '{}' is not a valid bind address (127.0.0.1,"
                    + " 0.0.0.0, ::1); falling back to 127.0.0.1", listen);
            return InetAddress.getLoopbackAddress();
        }
    }

    private static InetAddress wildcardAddress() {
        try {
            return InetAddress.getByName("0.0.0.0");
        } catch (java.net.UnknownHostException e) {
            return InetAddress.getLoopbackAddress();
        }
    }
}
