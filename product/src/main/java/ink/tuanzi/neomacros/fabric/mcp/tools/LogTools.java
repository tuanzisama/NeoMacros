package ink.tuanzi.neomacros.fabric.mcp.tools;

import com.google.gson.JsonObject;
import ink.tuanzi.neomacros.fabric.mcp.McpTool;
import ink.tuanzi.neomacros.fabric.web.PanelBackend;
import ink.tuanzi.neomacros.fabric.web.console.ConsoleRing;
import ink.tuanzi.neomacros.fabric.web.log.LogRing;
import ink.tuanzi.neomacros.fabric.web.net.NetRing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.argInt;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.argString;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.integerProperty;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.objectSchema;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.page;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.stringProperty;

/**
 * The log and diagnostic tools: incremental replay of the mod log ring
 * (mod loggers or the full game depending on capture scope), the script
 * console ring, the script network ring, and the task snapshot. Everything
 * reads on the calling thread — the rings are synchronized and the task
 * snapshot is plain registry state.
 */
public final class LogTools {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;

    private final LogRing logs;
    private final ConsoleRing console;
    private final NetRing net;
    private final PanelBackend backend;

    /** Creates the tools over the live rings and the panel backend. */
    public LogTools(LogRing logs, ConsoleRing console, NetRing net, PanelBackend backend) {
        this.logs = logs;
        this.console = console;
        this.net = net;
        this.backend = backend;
    }

    /** The four tools of this group, in registry order. */
    public List<McpTool> tools() {
        return List.of(getLogs(), getConsole(), getNetwork(), getTasks());
    }

    private McpTool getLogs() {
        JsonObject properties = new JsonObject();
        properties.add("after", integerProperty(
                "Return entries with a sequence greater than this; omit for everything retained",
                0));
        properties.add("level", stringProperty(
                "Exact level filter: DEBUG, INFO, WARN, or ERROR"));
        properties.add("logger", stringProperty(
                "Substring match on the logger name"));
        properties.add("limit", integerProperty("Maximum entries returned", 1));
        JsonObject schema = objectSchema(properties);
        return new McpTool("get_logs",
                "Recent log lines captured by the mod (mod loggers by default; the full game"
                        + " log when the capture scope is all), replayed incrementally by"
                        + " sequence number.",
                "logs", schema, args -> {
                    List<LogRing.Entry> entries = logs.after(argInt(args, "after", 0));
                    String level = argString(args, "level", null);
                    if (level != null) {
                        String wanted = level.toUpperCase(Locale.ROOT);
                        entries = entries.stream()
                                .filter(entry -> entry.level().equals(wanted))
                                .toList();
                    }
                    String logger = argString(args, "logger", null);
                    if (logger != null) {
                        entries = entries.stream()
                                .filter(entry -> entry.logger().contains(logger))
                                .toList();
                    }
                    entries = page(entries, Math.min(argInt(args, "limit", DEFAULT_LIMIT),
                            MAX_LIMIT));
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("lastSeq", logs.lastSeq());
                    out.put("returned", entries.size());
                    out.put("entries", entries);
                    return out;
                });
    }

    private McpTool getConsole() {
        JsonObject properties = new JsonObject();
        properties.add("after", integerProperty(
                "Return entries with a sequence greater than this", 0));
        properties.add("limit", integerProperty("Maximum entries returned", 1));
        JsonObject schema = objectSchema(properties);
        return new McpTool("get_console",
                "Recent script console output (console.log and friends from every loaded"
                        + " script plus the shared DevTools session), replayed incrementally.",
                "logs", schema, args -> {
                    List<ink.tuanzi.neomacros.fabric.web.v1.ConsoleEntry> entries =
                            console.after(argInt(args, "after", 0));
                    entries = page(entries, Math.min(argInt(args, "limit", DEFAULT_LIMIT),
                            MAX_LIMIT));
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("lastSeq", console.lastSeq());
                    out.put("returned", entries.size());
                    out.put("entries", entries);
                    return out;
                });
    }

    private McpTool getNetwork() {
        JsonObject properties = new JsonObject();
        properties.add("after", integerProperty(
                "Return entries with a sequence greater than this", 0));
        properties.add("limit", integerProperty("Maximum entries returned", 1));
        JsonObject schema = objectSchema(properties);
        return new McpTool("get_network",
                "Recent script network requests recorded from neo.net (fetch and"
                        + " WebSocket) with status, timing, and body previews.",
                "logs", schema, args -> {
                    List<ink.tuanzi.neomacros.fabric.web.v1.NetworkEntry> entries =
                            net.after(argInt(args, "after", 0));
                    entries = page(entries, Math.min(argInt(args, "limit", DEFAULT_LIMIT),
                            MAX_LIMIT));
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("lastSeq", net.lastSeq());
                    out.put("returned", entries.size());
                    out.put("entries", entries);
                    return out;
                });
    }

    private McpTool getTasks() {
        JsonObject schema = objectSchema(new JsonObject());
        return new McpTool("get_tasks",
                "The script task manager snapshot: currently active script activations"
                        + " plus the bounded finished history.",
                "logs", schema, args -> backend.tasks());
    }
}
