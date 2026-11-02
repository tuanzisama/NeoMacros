package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.fabric.web.auth.BackoffTracker;
import ink.tuanzi.neomacros.fabric.web.auth.DeviceRecord;
import ink.tuanzi.neomacros.fabric.web.auth.DeviceRegistry;
import ink.tuanzi.neomacros.fabric.web.auth.Nm1Verifier;
import ink.tuanzi.neomacros.fabric.web.auth.NonceStore;
import ink.tuanzi.neomacros.fabric.web.http.ApiRoute;
import ink.tuanzi.neomacros.fabric.web.http.AuthFilter;
import ink.tuanzi.neomacros.fabric.web.http.CorsPolicy;
import ink.tuanzi.neomacros.fabric.web.http.WebServer;
import ink.tuanzi.neomacros.fabric.web.log.LogRing;
import ink.tuanzi.neomacros.fabric.web.log.LogTap;
import ink.tuanzi.neomacros.fabric.web.openapi.DocsRoutes;
import ink.tuanzi.neomacros.fabric.web.openapi.OpenApiGenerator;
import ink.tuanzi.neomacros.fabric.web.pair.PairingService;
import ink.tuanzi.neomacros.fabric.web.sse.SseHub;
import ink.tuanzi.neomacros.fabric.web.v1.ApiV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The web panel service facade: owns the HTTP server, the authentication
 * chain, the pairing flow, the device registry handle, and the log ring,
 * and maps the {@code core.web} settings onto them per start. Starting is
 * explicit (command, editor deep link, or autoStart); stopping broadcasts
 * the SSE goodbye before the listener closes. The fabric layer feeds it the
 * game-state backend and surfaces pairing prompts in chat.
 */
public final class WebService {

    /** The status line the commands render. */
    public record Status(boolean running, int port, String listen, long uptimeMs,
                         int deviceCount) {
    }

    private static final Logger LOG = LoggerFactory.getLogger("neomacros.web");

    private final PanelBackend backend;
    private final GameRunner game;
    private final DeviceRegistry devices;
    private final Supplier<String> listenScope;
    private final IntSupplier configuredPort;
    private final Supplier<String> panelUrl;
    private final Supplier<java.util.List<String>> extraOrigins;
    private LogRing logs;
    private LogTap logTap;
    private PairingService pairing;
    private SseHub sse;
    private WebServer server;

    /**
     * Creates the service. The setting suppliers are read per start so
     * config changes apply without recreating the service.
     */
    public WebService(PanelBackend backend, GameRunner game, DeviceRegistry devices,
                      Supplier<String> listenScope, IntSupplier configuredPort,
                      Supplier<String> panelUrl, Supplier<java.util.List<String>> extraOrigins) {
        this.backend = backend;
        this.game = game;
        this.devices = devices;
        this.listenScope = listenScope;
        this.configuredPort = configuredPort;
        this.panelUrl = panelUrl;
        this.extraOrigins = extraOrigins;
    }

    /** Whether the HTTP listener is up. */
    public boolean running() {
        return server != null && server.running();
    }

    /** The bound port, or 0 when stopped. */
    public int port() {
        return server == null ? 0 : server.port();
    }

    /**
     * Starts the service with the current settings. Returns the bound port.
     *
     * @throws BindException when the configured port is taken
     * @throws IllegalStateException when already running
     */
    public int start() throws IOException {
        if (running()) {
            throw new IllegalStateException("the web service is already running");
        }
        String scope = listenScope.get();
        LogRing ring = new LogRing(1000);
        PairingService pairingService = new PairingService(System::currentTimeMillis);
        SseHub hub = new SseHub(System::currentTimeMillis);
        Nm1Verifier verifier = new Nm1Verifier(devices,
                new NonceStore(2 * Nm1Verifier.WINDOW_MILLIS), System::currentTimeMillis);
        AuthFilter auth = new AuthFilter(verifier, new BackoffTracker());
        ApiV1 controller = new ApiV1(backend, game, pairingService, devices, hub, ring);
        // The contract document is built over the live route set (the docs
        // endpoint included), then rebuilt with the bound port once known.
        var document = new java.util.concurrent.atomic.AtomicReference<
                com.google.gson.JsonElement>();
        DocsRoutes docs = new DocsRoutes(document::get);
        List<ApiRoute> routes = new ArrayList<>(ApiRoute.scan(controller));
        routes.addAll(ApiRoute.scan(docs));
        var meta = backend.meta();
        OpenApiGenerator generator = new OpenApiGenerator(routes,
                "NeoMacros Web Panel API", meta.modVersion(), meta.gameVersion(), 0);
        document.set(generator.generate());
        var origins = new ArrayList<String>();
        origins.add(panelUrl.get());
        origins.addAll(extraOrigins.get());
        WebServer http = new WebServer(controller, docs, hub, auth, new CorsPolicy(origins));
        int bound = http.start(scope, configuredPort.getAsInt());
        document.set(new OpenApiGenerator(routes, "NeoMacros Web Panel API",
                meta.modVersion(), meta.gameVersion(), bound).generate());
        this.logs = ring;
        this.pairing = pairingService;
        this.sse = hub;
        this.server = http;
        this.logTap = LogTap.install("neomacros", ring);
        ring.append("INFO", "neomacros.web", "web panel listening on "
                + scope + " port " + bound);
        LOG.info("web panel listening on {} port {} (apifox import: "
                        + "http://127.0.0.1:{}/api-docs/openapi.json)",
                scope, bound, bound);
        return bound;
    }

    /** Stops the service: goodbye event, brief drain, then close. */
    public void stop() {
        if (server == null) {
            return;
        }
        if (sse != null) {
            sse.broadcast("server:shutdown", "");
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sse.closeAll();
        }
        server.stop();
        if (logTap != null) {
            logTap.remove();
            logTap = null;
        }
        server = null;
        sse = null;
        pairing = null;
        logs = null;
        LOG.info("web panel stopped");
    }

    /**
     * Mints a fresh pairing token for a deep link; the service must be
     * running (the token belongs to a listener the browser can reach).
     */
    public String newPairingToken() {
        requirePairing();
        return pairing.newToken();
    }

    /** The live editor sessions (empty while the service is stopped). */
    public List<SseHub.SessionInfo> sessions() {
        return sse == null ? List.of() : sse.sessions();
    }

    /** Closes one live editor session; false for an unknown id. */
    public boolean disconnectSession(String id) {
        return sse != null && sse.disconnect(id);
    }

    /** All paired devices (the command listing). */
    public List<DeviceRecord> devices() {
        return devices.all();
    }

    /** Revokes one device; returns false for an unknown id. */
    public boolean revokeDevice(String id) {
        return devices.revoke(id);
    }

    /** Saves the device table (game quit flush for the last-seen updates). */
    public void saveDevices() {
        devices.save();
    }

    /** The current status for the command surface. */
    public Status status() {
        return new Status(running(), port(), listenScope.get(),
                server == null ? 0 : server.uptimeMs(), devices.all().size());
    }

    /** The log ring (the SSE log feed for tests and diagnostics). */
    LogRing logs() {
        return logs;
    }

    /** The SSE hub (tests broadcast panels events through it). */
    SseHub sse() {
        return sse;
    }

    private void requirePairing() {
        if (pairing == null) {
            throw new IllegalStateException("the web service is not running");
        }
    }

    private void logLine(String line) {
        if (logs != null) {
            logs.append("INFO", "neomacros.web", line);
        }
        LOG.info(line);
    }
}
