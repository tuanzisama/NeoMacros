package ink.tuanzi.neomacros.fabric.web.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ink.tuanzi.neomacros.fabric.web.json.Jsons;
import ink.tuanzi.neomacros.fabric.web.sse.SseHub;
import ink.tuanzi.neomacros.fabric.web.v1.ApiErrorBody;
import ink.tuanzi.neomacros.fabric.web.v1.ApiV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The embedded HTTP service: binds the loopback (or the experimental LAN
 * scope), runs every request through the hardening pipeline — Host
 * allowlist, CORS, body cap, NM1 authentication with per-source backoff —
 * and dispatches the survivors to the API router. The executor is a daemon
 * cached thread pool: one thread per request, including the long-polling
 * pair request and the SSE streams, which is why shutdown closes the event
 * streams explicitly before stopping the listener.
 */
public final class WebServer {

    private static final Logger LOG = LoggerFactory.getLogger("neomacros.web");

    private final ApiV1 api;
    private final SseHub sse;
    private final ApiRouter router;
    private final AuthFilter auth;
    private final CorsPolicy cors;
    private volatile HostPolicy hosts;

    private HttpServer server;
    private ExecutorService executor;
    private ScheduledExecutorService heartbeat;
    private volatile long startedAt;
    private volatile InetSocketAddress boundAddress;

    /** Creates the server over the API controllers; the host policy is rebuilt per start. */
    public WebServer(ApiV1 api, Object docsController, SseHub sse, AuthFilter auth,
                     CorsPolicy cors) {
        this.api = api;
        this.sse = sse;
        this.router = new ApiRouter(api, docsController);
        this.auth = auth;
        this.cors = cors;
    }

    /**
     * Binds and starts serving. The listen scope is {@code loopback} or the
     * experimental {@code lan}; the configured port may be 0 (ephemeral).
     * Returns the actual port.
     *
     * @throws java.net.BindException when the configured port is taken
     */
    public int start(String listen, int configuredPort) throws IOException {
        boolean lan = "lan".equals(listen);
        InetAddress address = lan
                ? InetAddress.getByName("0.0.0.0")
                : InetAddress.getLoopbackAddress();
        HttpServer created = HttpServer.create(new InetSocketAddress(address, configuredPort), 8);
        int boundPort = created.getAddress().getPort();
        hosts = lan ? HostPolicy.lan(boundPort) : HostPolicy.loopback(boundPort);
        created.createContext("/", this::handle);
        server = created;
        executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "neomacros-web");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        boundAddress = server.getAddress();
        startedAt = System.currentTimeMillis();
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "neomacros-web-hb");
            thread.setDaemon(true);
            return thread;
        });
        heartbeat.scheduleWithFixedDelay(this::heartbeat, 15, 15, TimeUnit.SECONDS);
        return boundAddress.getPort();
    }

    /** Stops the service: closes the event streams, then stops the listener. */
    public void stop() {
        if (heartbeat != null) {
            heartbeat.shutdownNow();
            heartbeat = null;
        }
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

    /** Whether the service is currently running. */
    public boolean running() {
        return server != null;
    }

    /** The bound port, or 0 when not running. */
    public int port() {
        InetSocketAddress address = boundAddress;
        return address == null ? 0 : address.getPort();
    }

    /** Milliseconds since start, or 0 when not running. */
    public long uptimeMs() {
        return running() ? System.currentTimeMillis() - startedAt : 0;
    }

    private void heartbeat() {
        try {
            sse.heartbeat();
        } catch (RuntimeException ignored) {
            // heartbeat failures are never fatal
        }
    }

    /** The full request pipeline. */
    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        String rawPath = exchange.getRequestURI().getRawPath();
        String rawQuery = exchange.getRequestURI().getRawQuery();
        boolean preflight = "OPTIONS".equals(method);
        try {
            // 1. Host allowlist: the rebinding defense runs before everything.
            if (!hosts.allows(exchange.getRequestHeaders().getFirst("Host"))) {
                throw ApiError.forbidden("host not allowed");
            }
            // 2. CORS headers echo on every response; preflight ends here.
            cors.apply(exchange);
            if (preflight) {
                ApiContext.respond(exchange, 204, "text/plain", new byte[0]);
                return;
            }
            // 3. Body cap (read before auth: the signature covers the bytes).
            byte[] body;
            try {
                body = ApiContext.readBody(exchange);
            } catch (ApiContext.BodyTooLarge e) {
                respondError(exchange, 413, ApiError.INVALID_REQUEST,
                        "request body exceeds 1 MiB");
                return;
            }
            ApiContext context = new ApiContext(exchange, method, rawPath, rawQuery, body);
            // 4. Backoff gate, then NM1 (except the exempt endpoints; the SSE
            //    stream authenticates by ticket inside its handler).
            String source = context.source();
            boolean ticketAuth = rawPath.equals("/api/v1/events");
            if (!AuthFilter.exempt(method, rawPath) && !ticketAuth) {
                if (auth.blocked(source)) {
                    throw ApiError.tooManyRequests(
                            "too many failed attempts; wait out the backoff window");
                }
                auth.authenticate(source, method, rawPath,
                        rawQuery == null ? "" : rawQuery, body,
                        exchange.getRequestHeaders().getFirst("Authorization"));
            }
            // 5. Route.
            router.dispatch(context);
        } catch (ApiError error) {
            respondError(exchange, error.status(), error.code(), error.getMessage());
        } catch (Exception e) {
            respondError(exchange, 500, ApiError.INTERNAL, "internal error");
            LOG.warn("web request failed: {}", e.toString());
        }
    }

    /** Writes the unified error body (best effort — the client may be gone). */
    private static void respondError(HttpExchange exchange, int status, String code,
                                     String message) {
        try {
            byte[] payload = Jsons
                    .stringify(ApiErrorBody.of(new ApiError(status, code, message)))
                    .getBytes(StandardCharsets.UTF_8);
            ApiContext.respond(exchange, status, "application/json; charset=utf-8", payload);
        } catch (IOException ignored) {
            exchange.close();
        }
    }
}
