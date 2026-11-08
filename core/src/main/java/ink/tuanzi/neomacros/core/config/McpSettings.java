package ink.tuanzi.neomacros.core.config;

/**
 * The {@code core.mcp} section: the MCP (Model Context Protocol) server that
 * lets external AI agents read logs and game state and drive the client over
 * Streamable HTTP. The service is off by default; connections authenticate
 * with a bearer token and the listener binds the loopback unless the user
 * widens it. Field defaults are the shipped values.
 */
public final class McpSettings {

    /** Whether the MCP service should be running (the last start/stop intent). */
    @Option(description = "MCP server enabled state")
    public boolean enabled = false;

    /** Whether the MCP service starts with the client, regardless of {@link #enabled}. */
    @Option(description = "Start the MCP server with the client")
    public boolean autoStart = false;

    /** TCP port to bind. */
    @Option(description = "MCP server port", min = 0, max = 65535)
    public int port = 30637;

    /** Bind address: {@code 127.0.0.1}, {@code 0.0.0.0}, or {@code ::1}. */
    @Option(description = "Bind address: 127.0.0.1, 0.0.0.0, or ::1")
    public String listen = "127.0.0.1";

    /**
     * The bearer token agents present in the {@code Authorization} header.
     * Blank means one is generated on the next start and persisted here.
     */
    @Option(description = "Bearer token for agent connections")
    public String token = "";

    /**
     * Which log lines the {@code get_logs} tool captures: {@code mod} keeps
     * the neomacros loggers, {@code all} records every logger including the
     * game itself.
     */
    @Option(description = "Log capture scope: mod or all")
    public String logCapture = "mod";

    /** Whether the log/diagnostic tool group (logs, console, tasks, network) is served. */
    @Option(description = "Serve the log and diagnostic tools")
    public boolean groupLogs = true;

    /** Whether the game-state tool group (player, inventory, screen, world) is served. */
    @Option(description = "Serve the game-state read tools")
    public boolean groupState = true;

    /** Whether the control tool group (modules, chat, command, keys) is served. */
    @Option(description = "Serve the script and game control tools")
    public boolean groupControl = true;

    /** Whether the {@code eval_script} JavaScript REPL tool is served. */
    @Option(description = "Serve the eval_script JavaScript REPL tool")
    public boolean groupEval = true;
}
