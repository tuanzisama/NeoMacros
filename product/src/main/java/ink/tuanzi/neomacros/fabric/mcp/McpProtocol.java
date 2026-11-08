package ink.tuanzi.neomacros.fabric.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import ink.tuanzi.neomacros.fabric.web.json.Jsons;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The MCP JSON-RPC layer: routes one decoded message through the protocol
 * methods this server speaks ({@code initialize}, {@code notifications/
 * initialized}, {@code ping}, {@code tools/list}, {@code tools/call}) and
 * renders the HTTP-bound outcome. The initialize handshake mints the
 * session here; everything before and after the JSON body (header checks,
 * status mapping) lives in the HTTP layer.
 */
public final class McpProtocol {

    /** JSON-RPC codes used on this surface. */
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    /** Protocol versions this server negotiates; the first is the fallback. */
    public static final List<String> SUPPORTED_VERSIONS =
            List.of("2025-06-18", "2026-07-28");

    /** One HTTP-bound outcome: status, optional JSON body, optional session header. */
    public record HttpResult(int status, JsonElement body, String sessionId) {

        /** A 200 response with a JSON-RPC message body. */
        public static HttpResult of(JsonElement body) {
            return new HttpResult(200, body, null);
        }

        /** A 202 response for a handled notification (no body). */
        public static HttpResult accepted() {
            return new HttpResult(202, null, null);
        }

        /** A 200 response that also stamps the session header. */
        public static HttpResult of(JsonElement body, String sessionId) {
            return new HttpResult(200, body, sessionId);
        }
    }

    private final String serverVersion;
    private final McpSessions sessions;
    private final Map<String, McpTool> tools;

    /** Creates the protocol over the tool surface and the session table. */
    public McpProtocol(String serverVersion, List<McpTool> tools, McpSessions sessions) {
        this.serverVersion = serverVersion;
        this.sessions = sessions;
        this.tools = tools.stream().collect(Collectors.toMap(McpTool::name, Function.identity(),
                (first, second) -> first, java.util.LinkedHashMap::new));
    }

    /** The registered tools in registration order. */
    public List<McpTool> tools() {
        return List.copyOf(tools.values());
    }

    /** Handles one decoded JSON-RPC message and returns the HTTP outcome. */
    public HttpResult handle(JsonElement message, String sessionId) {
        if (message == null || !message.isJsonObject()) {
            return HttpResult.of(error(JsonNull.INSTANCE, INVALID_REQUEST,
                    "each message must be a JSON-RPC 2.0 object"));
        }
        JsonObject envelope = message.getAsJsonObject();
        JsonElement id = envelope.get("id");
        boolean notification = id == null || id.isJsonNull();
        JsonElement methodElement = envelope.get("method");
        if (methodElement == null || !methodElement.isJsonPrimitive()
                || !methodElement.getAsJsonPrimitive().isString()) {
            return HttpResult.of(error(idOf(id), INVALID_REQUEST,
                    "a string 'method' is required"));
        }
        String method = methodElement.getAsString();
        JsonElement paramsElement = envelope.get("params");
        if (paramsElement != null && !paramsElement.isJsonNull()
                && !paramsElement.isJsonObject()) {
            return HttpResult.of(error(idOf(id), INVALID_REQUEST,
                    "'params' must be an object"));
        }
        JsonObject params = paramsElement == null || paramsElement.isJsonNull()
                ? new JsonObject() : paramsElement.getAsJsonObject();
        return switch (method) {
            case "initialize" -> initialize(id, notification, params);
            case "notifications/initialized" -> notification
                    ? HttpResult.accepted()
                    : HttpResult.of(error(idOf(id), INVALID_REQUEST,
                            "notifications/initialized is a notification"));
            case "ping" -> notification
                    ? HttpResult.accepted()
                    : HttpResult.of(result(idOf(id), new JsonObject()));
            case "tools/list" -> notification
                    ? HttpResult.of(error(idOf(id), INVALID_REQUEST,
                            "tools/list requires an id"))
                    : HttpResult.of(result(idOf(id), toolsList()));
            case "tools/call" -> notification
                    ? HttpResult.of(error(idOf(id), INVALID_REQUEST,
                            "tools/call requires an id"))
                    : callTool(id, params);
            default -> notification
                    ? HttpResult.accepted()
                    : HttpResult.of(error(idOf(id), METHOD_NOT_FOUND,
                            "method not found: " + method));
        };
    }

    /** Renders a JSON-RPC parse-error response (the HTTP layer calls this). */
    public static JsonElement parseError() {
        return error(JsonNull.INSTANCE, PARSE_ERROR, "malformed JSON body");
    }

    /** The initialize handshake: negotiate the version, mint the session. */
    private HttpResult initialize(JsonElement id, boolean notification, JsonObject params) {
        if (notification) {
            return HttpResult.of(error(JsonNull.INSTANCE, INVALID_REQUEST,
                    "initialize requires an id"));
        }
        JsonElement versionElement = params.get("protocolVersion");
        if (versionElement == null || !versionElement.isJsonPrimitive()) {
            return HttpResult.of(error(idOf(id), INVALID_PARAMS,
                    "initialize requires params.protocolVersion"));
        }
        String requested = versionElement.getAsString();
        String negotiated = SUPPORTED_VERSIONS.contains(requested)
                ? requested : SUPPORTED_VERSIONS.get(0);
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", negotiated);
        JsonObject toolsCapability = new JsonObject();
        toolsCapability.addProperty("listChanged", false);
        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", toolsCapability);
        result.add("capabilities", capabilities);
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "neomacros");
        serverInfo.addProperty("title", "NeoMacros");
        serverInfo.addProperty("version", serverVersion);
        result.add("serverInfo", serverInfo);
        result.addProperty("instructions", INSTRUCTIONS);
        McpSessions.Session session = sessions.create(negotiated, System.currentTimeMillis());
        return HttpResult.of(result(idOf(id), result), session.id());
    }

    private JsonObject toolsList() {
        JsonArray array = new JsonArray();
        for (McpTool tool : tools.values()) {
            JsonObject definition = new JsonObject();
            definition.addProperty("name", tool.name());
            definition.addProperty("description", tool.description());
            definition.add("inputSchema", tool.inputSchema());
            array.add(definition);
        }
        JsonObject result = new JsonObject();
        result.add("tools", array);
        return result;
    }

    private HttpResult callTool(JsonElement id, JsonObject params) {
        JsonElement nameElement = params.get("name");
        if (nameElement == null || !nameElement.isJsonPrimitive()) {
            return HttpResult.of(error(idOf(id), INVALID_PARAMS,
                    "tools/call requires params.name"));
        }
        String name = nameElement.getAsString();
        McpTool tool = tools.get(name);
        if (tool == null) {
            return HttpResult.of(error(idOf(id), INVALID_PARAMS, "unknown tool: " + name));
        }
        JsonElement argumentsElement = params.get("arguments");
        if (argumentsElement != null && !argumentsElement.isJsonNull()
                && !argumentsElement.isJsonObject()) {
            return HttpResult.of(error(idOf(id), INVALID_REQUEST,
                    "params.arguments must be an object"));
        }
        Map<String, Object> arguments = argumentsElement == null || argumentsElement.isJsonNull()
                ? Map.of() : Jsons.objectOf(argumentsElement.getAsJsonObject());
        Object payload;
        try {
            payload = tool.handler().apply(arguments);
        } catch (McpToolError e) {
            return HttpResult.of(result(idOf(id), textResult(e.getMessage(), true)));
        } catch (RuntimeException e) {
            return HttpResult.of(result(idOf(id),
                    textResult("tool failed: " + e, true)));
        }
        return HttpResult.of(result(idOf(id),
                textResult(Jsons.stringify(payload == null ? Map.of() : payload), false)));
    }

    /** The one-text-content tool result shape. */
    public static JsonObject textResult(String text, boolean isError) {
        JsonObject content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", text == null ? "" : text);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject result = new JsonObject();
        result.add("content", contents);
        if (isError) {
            result.addProperty("isError", true);
        }
        return result;
    }

    private static JsonElement result(JsonElement id, JsonObject result) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        envelope.add("id", id);
        envelope.add("result", result);
        return envelope;
    }

    private static JsonElement error(JsonElement id, int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        envelope.add("id", id);
        envelope.add("error", error);
        return envelope;
    }

    private static JsonElement idOf(JsonElement id) {
        return id == null ? JsonNull.INSTANCE : id;
    }

    /** The server introduction agents see at initialize. */
    private static final String INSTRUCTIONS = """
            This server bridges the MCP client into the NeoMacros Minecraft client \
            mod running inside a live game. Read tools report script and game \
            logs, running script tasks, and game state (player, inventory, \
            open screen, nearby world). Control tools act as the player: load \
            or unload scripts, send chat, run client commands, inject keys, \
            and evaluate JavaScript in the shared console session. Game-state \
            and control tools return an isError result when the game client is \
            not inside a loaded world. Prefer get_logs and get_console first \
            to see what the scripts are doing.""";
}
