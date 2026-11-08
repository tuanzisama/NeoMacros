package ink.tuanzi.neomacros.fabric.mcp.tools;

import com.google.gson.JsonObject;
import ink.tuanzi.neomacros.core.api.CommandRegistry;
import ink.tuanzi.neomacros.fabric.mcp.McpTool;
import ink.tuanzi.neomacros.fabric.mcp.McpToolError;
import ink.tuanzi.neomacros.fabric.web.GameRunner;
import ink.tuanzi.neomacros.fabric.web.PanelBackend;
import ink.tuanzi.neomacros.fabric.web.http.ApiError;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.argBool;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.argString;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.booleanProperty;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.objectSchema;
import static ink.tuanzi.neomacros.fabric.mcp.tools.McpSchemas.stringProperty;

/**
 * The control tools: script module lifecycle over the same backend path the
 * web panel uses, chat and client command dispatch as the player, and key
 * injection. Module lifecycle and chat dispatch run on the game main
 * thread; the other actions self-dispatch internally.
 */
public final class ControlTools {

    private final PanelBackend backend;
    private final GameRunner game;
    private final NeomacrosAdapter adapter;
    private final CommandRegistry commands;

    /** Creates the tools over the backend, ports, and command registry. */
    public ControlTools(PanelBackend backend, GameRunner game, NeomacrosAdapter adapter,
                        CommandRegistry commands) {
        this.backend = backend;
        this.game = game;
        this.adapter = adapter;
        this.commands = commands;
    }

    /** The six tools of this group, in registry order. */
    public List<McpTool> tools() {
        return List.of(listModules(), startModule(), stopModule(),
                sendChat(), runCommand(), injectKey());
    }

    /** Runs a main-thread action after confirming a loaded world. */
    private <T> T actInWorld(Supplier<T> action) {
        return game.call(() -> {
            if (!adapter.lifecycle().inWorld().get()) {
                throw new McpToolError(
                        "the game client is not inside a loaded world; join a world first");
            }
            return action.get();
        });
    }

    private McpTool listModules() {
        JsonObject schema = objectSchema(new JsonObject());
        return new McpTool("list_modules",
                "Every discovered script: identity, category, lifecycle, enabled state,"
                        + " running state, key binding, triggers, declared permissions,"
                        + " and the current load error.",
                "control", schema, args -> game.call(() -> {
                    var snapshot = backend.snapshot();
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("macrosRoot", snapshot.macrosRoot());
                    out.put("macros", snapshot.macros());
                    return out;
                }));
    }

    private McpTool startModule() {
        JsonObject properties = new JsonObject();
        properties.add("name", stringProperty(
                "The script id as reported by list_modules (category/name)"));
        JsonObject schema = objectSchema(properties, "name");
        return new McpTool("start_module",
                "Starts a script (loads it now; a disabled script becomes active for"
                        + " this session without changing its saved autostart intent).",
                "control", schema, args -> moduleAction("start"));
    }

    private McpTool stopModule() {
        JsonObject properties = new JsonObject();
        properties.add("name", stringProperty(
                "The script id as reported by list_modules (category/name)"));
        JsonObject schema = objectSchema(properties, "name");
        return new McpTool("stop_module",
                "Stops a running script by unloading it (the saved autostart intent"
                        + " stays untouched).",
                "control", schema, args -> moduleAction("stop"));
    }

    private java.util.function.Function<Map<String, Object>, Object> moduleAction(
            String action) {
        return args -> game.call(() -> {
            try {
                return backend.macroAction(argString(args, "name"), action);
            } catch (ApiError e) {
                throw new McpToolError(e.getMessage());
            }
        });
    }

    private McpTool sendChat() {
        JsonObject properties = new JsonObject();
        properties.add("message", stringProperty(
                "The chat message to send as the player"));
        JsonObject schema = objectSchema(properties, "message");
        return new McpTool("send_chat",
                "Sends one chat message to the server as if typed by the player.",
                "control", schema, args -> actInWorld(() -> {
                    adapter.chat().say(argString(args, "message"));
                    return Map.of("sent", true);
                }));
    }

    private McpTool runCommand() {
        JsonObject properties = new JsonObject();
        properties.add("command", stringProperty(
                "The command line without the leading slash, e.g. 'time set day'"));
        JsonObject schema = objectSchema(properties, "command");
        return new McpTool("run_command",
                "Runs a Minecraft command as the player (the same path as typing"
                        + " '/...' into chat).",
                "control", schema, args -> actInWorld(() -> {
                    commands.execute(argString(args, "command"));
                    return Map.of("queued", true);
                }));
    }

    private McpTool injectKey() {
        JsonObject properties = new JsonObject();
        properties.add("key", stringProperty(
                "The vanilla key name (GLFW style, e.g. 'F3', 'key.keyboard.e')"));
        properties.add("down", booleanProperty(
                "True to press, false to release; a full press needs down=true then"
                        + " down=false"));
        JsonObject schema = objectSchema(properties, "key");
        return new McpTool("inject_key",
                "Injects a raw key press or release into the client input pipeline.",
                "control", schema, args -> {
                    adapter.input().injectKey(argString(args, "key"),
                            argBool(args, "down", true));
                    return Map.of("injected", true);
                });
    }
}
