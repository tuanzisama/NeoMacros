package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.core.console.ScriptConsole;
import ink.tuanzi.neomacros.core.console.ScriptConsoleEvent;
import ink.tuanzi.neomacros.core.net.NetTrace;
import ink.tuanzi.neomacros.core.net.NetTraceEvent;
import ink.tuanzi.neomacros.fabric.web.auth.DeviceRecord;
import ink.tuanzi.neomacros.fabric.web.auth.DeviceRegistry;
import ink.tuanzi.neomacros.fabric.web.auth.Nm1Verifier;
import ink.tuanzi.neomacros.fabric.web.auth.NonceStore;
import ink.tuanzi.neomacros.fabric.web.console.ConsoleRing;
import ink.tuanzi.neomacros.fabric.web.http.ApiRoute;
import ink.tuanzi.neomacros.fabric.web.http.AuthFilter;
import ink.tuanzi.neomacros.fabric.web.http.CorsPolicy;
import ink.tuanzi.neomacros.fabric.web.http.WebServer;
import ink.tuanzi.neomacros.fabric.web.json.Jsons;
import ink.tuanzi.neomacros.fabric.web.log.LogRing;
import ink.tuanzi.neomacros.fabric.web.log.LogTap;
import ink.tuanzi.neomacros.fabric.web.net.NetRing;
import ink.tuanzi.neomacros.fabric.web.openapi.DocsRoutes;
import ink.tuanzi.neomacros.fabric.web.openapi.OpenApiGenerator;
import ink.tuanzi.neomacros.fabric.web.pair.PairingRequests;
import ink.tuanzi.neomacros.fabric.web.pair.PairingService;
import ink.tuanzi.neomacros.fabric.web.sse.PanelEvents;
import ink.tuanzi.neomacros.fabric.web.sse.SseHub;
import ink.tuanzi.neomacros.fabric.web.v1.ApiV1;
import ink.tuanzi.neomacros.fabric.web.v1.LogEntry;
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
 * The web panel service: owns the HTTP server, the authentication
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
    private final PairingService pairing;
    private final PairingRequests pairingRequests;
    private final Supplier<String> listenScope;
    private final IntSupplier configuredPort;
    private final Supplier<String> panelUrl;
    private final Supplier<java.util.List<String>> extraOrigins;
    private final PanelEvents events;
    private final ink.tuanzi.neomacros.fabric.web.memory.MemoryCenter memoryCenter;
    private volatile Consumer<PairingRequests.Request> pairRequestListener;
    private LogRing logs;
    private LogTap logTap;
    private ConsoleRing console;
    private Consumer<ScriptConsoleEvent> consoleDispatcher;
    private NetRing net;
    private Consumer<NetTraceEvent> netDispatcher;
    private ink.tuanzi.neomacros.fabric.web.memory.MemoryRing memory;
    private SseHub sse;
    private WebServer server;

    /**
     * Creates the service. The pairing store, request table, and device
     * registry are process-lifetime collaborators handed in by the fabric
     * layer (the stores' files survive service stop/start); the setting
     * suppliers are read per start so config changes apply without
     * recreating the service; the events module carries broadcasts from
     * game-side surfaces to the per-start hub.
     */
    public WebService(PanelBackend backend, GameRunner game, DeviceRegistry devices,
                      PairingService pairing, PairingRequests pairingRequests,
                      Supplier<String> listenScope, IntSupplier configuredPort,
                      Supplier<String> panelUrl,
                      Supplier<java.util.List<String>> extraOrigins, PanelEvents events,
                      ink.tuanzi.neomacros.fabric.web.memory.MemoryCenter memoryCenter) {
        this.backend = backend;
        this.game = game;
        this.devices = devices;
        this.pairing = pairing;
        this.pairingRequests = pairingRequests;
        this.listenScope = listenScope;
        this.configuredPort = configuredPort;
        this.memoryCenter = memoryCenter;
        this.panelUrl = panelUrl;
        this.extraOrigins = extraOrigins;
        this.events = events;
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
        ConsoleRing consoleRing = new ConsoleRing(1000);
        NetRing netRing = new NetRing(500);
        var memoryRing = new ink.tuanzi.neomacros.fabric.web.memory.MemoryRing(1800);
        SseHub hub = new SseHub(System::currentTimeMillis);
        Nm1Verifier verifier = new Nm1Verifier(devices,
                new NonceStore(2 * Nm1Verifier.WINDOW_MILLIS), System::currentTimeMillis);
        AuthFilter auth = new AuthFilter(verifier);
        var origins = new ArrayList<String>();
        origins.add(panelUrl.get());
        origins.addAll(extraOrigins.get());
        CorsPolicy cors = new CorsPolicy(origins);
        ApiV1 controller = new ApiV1(backend, game, pairing, pairingRequests,
                cors::allowsOrigin, pairRequestListener, devices, hub, ring,
                consoleRing, netRing, memoryRing);
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
        WebServer http = new WebServer(controller, docs, hub, auth, cors);
        int bound = http.start(scope, configuredPort.getAsInt());
        document.set(new OpenApiGenerator(routes, "NeoMacros Web Panel API",
                meta.modVersion(), meta.gameVersion(), bound).generate());
        this.logs = ring;
        this.console = consoleRing;
        this.net = netRing;
        this.memory = memoryRing;
        this.sse = hub;
        this.server = http;
        events.attach(hub);
        // Memory feed: sampler ticks land in the ring (replay) and the SSE
        // memory:sample event (live tail). Sampling gates itself on hub
        // sessions (auto mode), so an idle panel costs nothing.
        if (memoryCenter != null) {
            memoryCenter.start(memoryRing::append, () -> !hub.sessions().isEmpty());
            memoryRing.addListener(entry -> events.broadcast("memory:sample",
                    Jsons.stringify(entry)));
        }
        ring.addListener(entry -> events.broadcast("log",
                Jsons.stringify(new LogEntry(
                        entry.seq(), entry.ts(), entry.level(), entry.logger(),
                        entry.line()))));
        this.logTap = LogTap.install("neomacros", ring);
        // Script console feed: core events land in the ring (replay) and the
        // SSE console event (live tail). The dispatcher is removed on stop;
        // the always-on log4j mirror is separate and stays installed.
        this.consoleDispatcher = consoleRing::append;
        ScriptConsole.installDispatcher(this.consoleDispatcher);
        consoleRing.addListener(entry -> events.broadcast("console", Jsons.stringify(entry)));
        // Network feed: settled neo.net requests land in the ring (replay) and
        // the SSE network event (live tail). The dispatcher is removed on stop.
        this.netDispatcher = netRing::append;
        NetTrace.installDispatcher(this.netDispatcher);
        netRing.addListener(entry -> events.broadcast("network", Jsons.stringify(entry)));
        ring.append(System.currentTimeMillis(), "INFO", "neomacros.web", "web panel listening on "
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
        events.detach();
        if (logTap != null) {
            logTap.remove();
            logTap = null;
        }
        if (consoleDispatcher != null) {
            ScriptConsole.removeDispatcher(consoleDispatcher);
            consoleDispatcher = null;
        }
        if (netDispatcher != null) {
            NetTrace.removeDispatcher(netDispatcher);
            netDispatcher = null;
        }
        if (memoryCenter != null) {
            memoryCenter.close();
        }
        server = null;
        sse = null;
        logs = null;
        console = null;
        net = null;
        memory = null;
        LOG.info("web panel stopped");
    }

    /**
     * Mints a fresh pairing token for a deep link. Callers ensure the
     * service is running first (the token belongs to a listener the
     * browser can reach); the store itself lives across stop/start.
     */
    public String newPairingToken() {
        return pairing.newToken();
    }

    /** Registers the game-side listener rendered for each new request. */
    public void onPairRequest(Consumer<PairingRequests.Request> listener) {
        this.pairRequestListener = listener;
    }

    /**
     * Approves one pending pairing request: registers the device and
     * freezes its client id. Returns the new device id, or {@code null}
     * when the request is unknown, expired, or already decided.
     */
    public String approvePairRequest(String id) {
        PairingRequests.Request request = pairingRequests.get(id);
        if (request == null
                || pairingRequests.outcomeOf(id) != PairingRequests.Outcome.PENDING) {
            return null;
        }
        DeviceRecord device = devices.register(
                request.label(), request.algo(), request.pubJwk());
        pairingRequests.approve(id, device.id());
        return device.id();
    }

    /**
     * Approves one pending request and remembers the machine: later
     * loopback browsers pair without asking again. Returns the new device
     * id, or {@code null} when the request is not decidable.
     */
    public String trustPairRequest(String id) {
        String deviceId = approvePairRequest(id);
        if (deviceId != null) {
            pairing.setTrustLoopback(true);
        }
        return deviceId;
    }

    /** Denies one pending pairing request; false when not decidable. */
    public boolean denyPairRequest(String id) {
        return pairingRequests.deny(id);
    }

    /** The live pending pairing requests (tab completion and prompts). */
    public List<PairingRequests.Request> pendingPairRequests() {
        return pairingRequests.pending();
    }

    /** Whether tokenless pairing from loopback browsers is auto-approved. */
    public boolean trustLoopback() {
        return pairing.trustLoopback();
    }

    /** Toggles tokenless loopback trust (persisted immediately). */
    public void setTrustLoopback(boolean value) {
        pairing.setTrustLoopback(value);
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

    /** The script console ring (the SSE console feed for tests). */
    ConsoleRing console() {
        return console;
    }

    /** The network ring (the SSE network feed for tests). */
    NetRing net() {
        return net;
    }

    /** The SSE hub (tests broadcast panels events through it). */
    SseHub sse() {
        return sse;
    }

    private void logLine(String line) {
        if (logs != null) {
            logs.append(System.currentTimeMillis(), "INFO", "neomacros.web", line);
        }
        LOG.info(line);
    }
}
