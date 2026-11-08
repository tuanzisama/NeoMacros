package ink.tuanzi.neomacros.fabric.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ink.tuanzi.neomacros.fabric.web.json.Jsons;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JSON-RPC layer in isolation: version negotiation, session minting,
 * envelope validation, the tool surface, and the error codes. No HTTP.
 */
class McpProtocolTest {

    private static McpProtocol protocol(McpTool... tools) {
        return new McpProtocol("9.9.9-test", List.of(tools), new McpSessions());
    }

    private static McpProtocol.HttpResult handle(McpProtocol protocol, String json) {
        return protocol.handle(Jsons.parse(json), null);
    }

    private static JsonObject resultBody(McpProtocol.HttpResult result) {
        return JsonParser.parseString(Jsons.stringify(result.body())).getAsJsonObject();
    }

    private static JsonObject errorOf(McpProtocol.HttpResult result) {
        return resultBody(result).getAsJsonObject("error");
    }

    private static JsonObject resultOf(McpProtocol.HttpResult result) {
        return resultBody(result).getAsJsonObject("result");
    }

    @Test
    void initializeEchoesASupportedVersionAndMintsASession() {
        McpProtocol protocol = protocol();
        McpProtocol.HttpResult result = handle(protocol, """
                {"jsonrpc":"2.0","id":1,"method":"initialize",
                 "params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"t"}}}""");
        assertEquals(200, result.status());
        assertEquals("2025-06-18", resultOf(result).get("protocolVersion").getAsString());
        assertNotNull(result.sessionId());
        assertEquals("neomacros", resultOf(result).getAsJsonObject("serverInfo")
                .get("name").getAsString());
        assertTrue(resultOf(result).has("instructions"));
        assertEquals("tools", resultOf(result).getAsJsonObject("capabilities")
                .keySet().iterator().next());
    }

    @Test
    void initializeFallsBackWhenTheVersionIsUnknown() {
        McpProtocol protocol = protocol();
        McpProtocol.HttpResult result = handle(protocol, """
                {"jsonrpc":"2.0","id":1,"method":"initialize",
                 "params":{"protocolVersion":"1999-01-01"}}""");
        assertEquals(McpProtocol.SUPPORTED_VERSIONS.get(0),
                resultOf(result).get("protocolVersion").getAsString());
    }

    @Test
    void initializeRequiresAnIdAndAVersion() {
        McpProtocol protocol = protocol();
        McpProtocol.HttpResult noId = handle(protocol, """
                {"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2025-06-18"}}""");
        assertEquals(McpProtocol.INVALID_REQUEST, errorOf(noId).get("code").getAsInt());

        McpProtocol.HttpResult noVersion = handle(protocol,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        assertEquals(McpProtocol.INVALID_PARAMS, errorOf(noVersion).get("code").getAsInt());
    }

    @Test
    void theInitializedNotificationIsAcceptedSilently() {
        McpProtocol.HttpResult result = handle(protocol(),
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        assertEquals(202, result.status());
        assertNull(result.body());
    }

    @Test
    void pingAnswersAnEmptyResult() {
        McpProtocol.HttpResult result = handle(protocol(),
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}");
        assertEquals(0, resultOf(result).entrySet().size());
    }

    @Test
    void unknownMethodsAnswerMethodNotFoundButUnknownNotificationsStaySilent() {
        McpProtocol.HttpResult request = handle(protocol(),
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"resources/list\"}");
        assertEquals(McpProtocol.METHOD_NOT_FOUND, errorOf(request).get("code").getAsInt());

        McpProtocol.HttpResult notification = handle(protocol(),
                "{\"jsonrpc\":\"2.0\",\"method\":\"resources/list\"}");
        assertEquals(202, notification.status());
    }

    @Test
    void malformedEnvelopesAnswerInvalidRequest() {
        McpProtocol protocol = protocol();
        assertEquals(McpProtocol.INVALID_REQUEST,
                errorOf(handle(protocol, "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}]"))
                        .get("code").getAsInt());
        assertEquals(McpProtocol.INVALID_REQUEST,
                errorOf(handle(protocol, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":5}"))
                        .get("code").getAsInt());
        assertEquals(McpProtocol.INVALID_REQUEST,
                errorOf(handle(protocol, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\",\"params\":[]}")
                ).get("code").getAsInt());
    }

    @Test
    void theParseErrorBodyCarriesTheJsonRpcCode() {
        JsonObject error = JsonParser.parseString(Jsons.stringify(McpProtocol.parseError()))
                .getAsJsonObject().getAsJsonObject("error");
        assertEquals(McpProtocol.PARSE_ERROR, error.get("code").getAsInt());
    }

    @Test
    void toolsListServesNameDescriptionAndSchema() {
        McpTool tool = new McpTool("get_logs", "Recent log lines", "logs",
                schema(), args -> Map.of());
        McpProtocol.HttpResult result = handle(protocol(tool),
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}");
        JsonObject entry = resultOf(result).getAsJsonArray("tools").get(0).getAsJsonObject();
        assertEquals("get_logs", entry.get("name").getAsString());
        assertEquals("Recent log lines", entry.get("description").getAsString());
        assertEquals("object", entry.getAsJsonObject("inputSchema").get("type").getAsString());
    }

    @Test
    void toolCallReturnsThePayloadAsTextContent() {
        McpTool tool = new McpTool("get_logs", "Recent log lines", "logs",
                schema(), args -> Map.of("lines", List.of("a", "b")));
        McpProtocol.HttpResult result = handle(protocol(tool),
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"get_logs\"}}");
        JsonObject output = resultOf(result);
        assertFalse(output.has("isError"));
        String text = output.getAsJsonArray("content").get(0).getAsJsonObject()
                .get("text").getAsString();
        assertTrue(text.contains("\"lines\""));
    }

    @Test
    void toolCallBusinessFailuresComeBackAsIsErrorResults() {
        McpTool tool = new McpTool("get_player", "Player state", "state",
                schema(), args -> {
                    throw new McpToolError("not inside a world");
                });
        McpProtocol.HttpResult result = handle(protocol(tool),
                "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"get_player\"}}");
        JsonObject output = resultOf(result);
        assertTrue(output.get("isError").getAsBoolean());
        assertTrue(output.getAsJsonArray("content").get(0).getAsJsonObject()
                .get("text").getAsString().contains("not inside a world"));
    }

    @Test
    void toolCallCrashesComeBackAsIsErrorResultsToo() {
        McpTool tool = new McpTool("boom", "Explodes", "eval",
                schema(), args -> {
                    throw new IllegalStateException("engine gone");
                });
        McpProtocol.HttpResult result = handle(protocol(tool),
                "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"boom\",\"arguments\":{\"x\":1}}}");
        assertTrue(resultOf(result).get("isError").getAsBoolean());
    }

    @Test
    void toolCallValidatesTheEnvelopeArguments() {
        McpProtocol protocol = protocol(new McpTool("get_logs", "Recent log lines", "logs",
                schema(), args -> Map.of()));
        assertEquals(McpProtocol.INVALID_PARAMS, errorOf(handle(protocol,
                        "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{}}"))
                .get("code").getAsInt());
        assertEquals(McpProtocol.INVALID_PARAMS, errorOf(handle(protocol,
                        "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"missing\"}}"))
                .get("code").getAsInt());
        assertEquals(McpProtocol.INVALID_REQUEST, errorOf(handle(protocol,
                        "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"get_logs\",\"arguments\":5}}"))
                .get("code").getAsInt());
    }

    @Test
    void toolHandlersReceiveTheDecodedArgumentsObject() {
        List<Map<String, Object>> seen = new ArrayList<>();
        McpTool tool = new McpTool("get_logs", "Recent log lines", "logs",
                schema(), args -> {
                    seen.add(args);
                    return Map.of();
                });
        handle(protocol(tool), "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"get_logs\",\"arguments\":{\"after\":12,\"tag\":null}}}");
        assertEquals(12L, ((Number) seen.get(0).get("after")).longValue());
        assertNull(seen.get(0).get("tag"));
    }

    private static JsonObject schema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        return schema;
    }
}
