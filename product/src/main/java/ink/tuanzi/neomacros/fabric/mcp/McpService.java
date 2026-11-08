package ink.tuanzi.neomacros.fabric.mcp;

import ink.tuanzi.neomacros.core.api.CommandRegistry;
import ink.tuanzi.neomacros.core.config.McpSettings;
import ink.tuanzi.neomacros.core.console.ScriptConsole;
import ink.tuanzi.neomacros.core.console.ScriptConsoleEvent;
import ink.tuanzi.neomacros.core.net.NetTrace;
import ink.tuanzi.neomacros.core.net.NetTraceEvent;
import ink.tuanzi.neomacros.fabric.web.GameRunner;
import ink.tuanzi.neomacros.fabric.web.PanelBackend;
import ink.tuanzi.neomacros.fabric.web.console.ConsoleRing;
import ink.tuanzi.neomacros.fabric.web.log.LogRing;
import ink.tuanzi.neomacros.fabric.web.log.LogTap;
import ink.tuanzi.neomacros.fabric.web.net.NetRing;
import ink.tuanzi.neomacros.fabric.mcp.tools.ControlTools;
import ink.tuanzi.neomacros.fabric.mcp.tools.EvalTools;
import ink.tuanzi.neomacros.fabric.mcp.tools.LogTools;
import ink.tuanzi.neomacros.fabric.mcp.tools.StateTools;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.BindException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The MCP service: owns the log/console/network rings the agent tools read,
 * the session table, and the HTTP server, and maps the {@code core.mcp}
 * settings onto them per start. A blank configured token is generated and
 * persisted on the first start; rotating the token clears every live
 * session. The tool surface assembles from the group switches, so a group
 * turned off in config disappears from {@code tools/list}.
 */
public final class McpService {

    /** The status line the commands render. */
    public record Status(boolean running, int port, String listen, long uptimeMs,
                         int sessions) {
    }

    private static final Logger LOG = LoggerFactory.getLogger("neomacros.mcp");

    private final PanelBackend backend;
    private final GameRunner game;
    private final NeomacrosAdapter adapter;
    private final CommandRegistry commands;
    private final Supplier<McpSettings> settings;
    private final Supplier<String> modVersion;
    private final Consumer<String> tokenPersister;

    private LogRing logs;
    private LogTap logTap;
    private ConsoleRing console;
    private Consumer<ScriptConsoleEvent> consoleDispatcher;
    private NetRing net;
    private Consumer<NetTraceEvent> netDispatcher;
    private McpSessions sessions;
    private McpServer server;
    private volatile long startedAt;

    /**
     * Creates the service over the collaborators the fabric layer owns; the
     * settings supplier is read per start so config changes apply without
     * recreating the service, and the persister stores generated tokens.
     */
    public McpService(PanelBackend backend, GameRunner game, NeomacrosAdapter adapter,
                      CommandRegistry commands, Supplier<McpSettings> settings,
                      Supplier<String> modVersion, Consumer<String> tokenPersister) {
        this.backend = backend;
        this.game = game;
        this.adapter = adapter;
        this.commands = commands;
        this.settings = settings;
        this.modVersion = modVersion;
        this.tokenPersister = tokenPersister;
    }

    /** Whether the HTTP listener is up. */
    public boolean running() {
        return server != null && server.running();
    }

    /** The bound port, or 0 when stopped. */
    public int port() {
        return server == null ? 0 : server.port();
    }

    /** The live session count, 0 while stopped. */
    public int sessionCount() {
        return sessions == null ? 0 : sessions.count();
    }

    /**
     * Starts the service with the current settings and returns the bound
     * port.
     *
     * @throws BindException when the configured port is taken
     * @throws IllegalStateException when already running
     */
    public int start() throws IOException {
        if (running()) {
            throw new IllegalStateException("the mcp service is already running");
        }
        McpSettings s = settings.get();
        if (s.token == null || s.token.isBlank()) {
            tokenPersister.accept(newToken());
            s = settings.get();
        }
        logs = new LogRing(1000);
        console = new ConsoleRing(1000);
        net = new NetRing(500);
        boolean captureAll = "all".equalsIgnoreCase(s.logCapture);
        logTap = LogTap.install("neomacros-mcp-log-ring", captureAll ? "" : "neomacros", logs);
        consoleDispatcher = console::append;
        ScriptConsole.installDispatcher(consoleDispatcher);
        netDispatcher = net::append;
        NetTrace.installDispatcher(netDispatcher);
        sessions = new McpSessions();
        McpProtocol protocol = new McpProtocol(modVersion.get(), assembleTools(s), sessions);
        server = new McpServer(protocol, sessions, () -> settings.get().token);
        int bound = server.start(s.listen, s.port);
        startedAt = System.currentTimeMillis();
        logs.append(System.currentTimeMillis(), "INFO", "neomacros.mcp",
                "mcp server listening on " + s.listen + " port " + bound);
        LOG.info("mcp server listening on {} port {} (endpoint /mcp, {} log capture)",
                bound, s.listen, captureAll ? "full-game" : "mod");
        return bound;
    }

    /** Stops the service and detaches the capture dispatchers. */
    public void stop() {
        if (server == null) {
            return;
        }
        server.stop();
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
        server = null;
        sessions = null;
        logs = null;
        console = null;
        net = null;
        LOG.info("mcp server stopped");
    }

    /** Generates a fresh token, persists it, and drops every live session. */
    public void rotateToken() {
        tokenPersister.accept(newToken());
        if (sessions != null) {
            sessions.clear();
        }
    }

    /** The current status for the command surface. */
    public Status status() {
        McpSettings s = settings.get();
        return new Status(running(), port(), s.listen,
                running() ? System.currentTimeMillis() - startedAt : 0, sessionCount());
    }

    /** The connection URL agents register, e.g. {@code http://127.0.0.1:30637/mcp}. */
    public String endpointUrl() {
        return "http://127.0.0.1:" + port() + "/mcp";
    }

    /** The active token (never blank once started at least once). */
    public String token() {
        return settings.get().token;
    }

    /** The tools this start serves, filtered by the group switches. */
    private List<McpTool> assembleTools(McpSettings s) {
        List<McpTool> tools = new ArrayList<>();
        if (s.groupLogs) {
            tools.addAll(new LogTools(logs, console, net, backend).tools());
        }
        if (s.groupState) {
            tools.addAll(new StateTools(adapter, game).tools());
        }
        if (s.groupControl) {
            tools.addAll(new ControlTools(backend, game, adapter, commands).tools());
        }
        if (s.groupEval) {
            tools.addAll(new EvalTools(backend).tools());
        }
        return List.copyOf(tools);
    }

    private static String newToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
