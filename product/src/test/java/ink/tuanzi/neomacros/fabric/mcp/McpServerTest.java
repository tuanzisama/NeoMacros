package ink.tuanzi.neomacros.fabric.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ink.tuanzi.neomacros.core.api.CommandRegistry;
import ink.tuanzi.neomacros.core.config.McpSettings;
import ink.tuanzi.neomacros.core.runtime.EvalOutcome;
import ink.tuanzi.neomacros.e2e.harness.StubAdapter;
import ink.tuanzi.neomacros.fabric.web.PanelBackend;
import ink.tuanzi.neomacros.fabric.web.http.ApiError;
import ink.tuanzi.neomacros.fabric.web.v1.FilesResponse;
import ink.tuanzi.neomacros.fabric.web.v1.Meta;
import ink.tuanzi.neomacros.fabric.web.v1.MacroView;
import ink.tuanzi.neomacros.fabric.web.v1.ServerInfo;
import ink.tuanzi.neomacros.fabric.web.v1.StateSnapshot;
import ink.tuanzi.neomacros.fabric.web.v1.TasksResponse;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end over a real HTTP listener: the full MCP conversation
 * (initialize → initialized → tools/list → tools/call → DELETE), the
 * transport rules (401/403/404/405/400), the group switches, and the
 * game-backed tools over the e2e stub adapter. No game context needed.
 */
class McpServerTest {

    static {
        // The rebinding-defense test overrides the Host header, which the
        // JDK client treats as restricted unless allowed here.
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
    }

    private static final String TOKEN = "test-token-0123456789abcdef";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final StubBackend backend = new StubBackend();
    private final StubAdapter adapter = new StubAdapter();
    private McpService service;

    @AfterEach
    void stopService() {
        if (service != null) {
            service.stop();
            service = null;
        }
    }

    /** Starts the service on an ephemeral port and returns the bound port. */
    private int startService(java.util.function.Consumer<McpSettings> tweak) {
        McpSettings settings = new McpSettings();
        settings.token = TOKEN;
        settings.port = 0;
        tweak.accept(settings);
        adapter.lifecyclePort().setInWorld(true);
        adapter.playerPort().setSelf(new PlayerSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "tester",
                new Pos3d(1, 64, 2), new Rot2d(0, 0), 20, 20, 300));
        adapter.worldPort().time = 6000;
        adapter.worldPort().dimension = "minecraft:overworld";
        service = new McpService(backend, java.util.function.Supplier::get, adapter, new StubCommands(),
                () -> settings, () -> "9.9.9-test", token -> { });
        try {
            return service.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One POST to the MCP endpoint with the given session and origin. */
    private HttpResponse<String> post(int port, String body, String sessionId,
                                      String origin) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (sessionId != null) {
            request.header("Mcp-Session-Id", sessionId);
        }
        if (origin != null) {
            request.header("Origin", origin);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Sends one JSON-RPC request and returns the parsed body. */
    private JsonObject rpc(int port, String sessionId, String method, String paramsJson)
            throws IOException, InterruptedException {
        HttpResponse<String> response = post(port,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\""
                        + (paramsJson == null ? "}" : ",\"params\":" + paramsJson + "}"),
                sessionId, null);
        assertEquals(200, response.statusCode(), response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    /** Runs the initialize handshake and returns the assigned session id. */
    private String initialize(int port) throws IOException, InterruptedException {
        HttpResponse<String> response = post(port,
                "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":"
                        + "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                        + "\"clientInfo\":{\"name\":\"mcp-server-test\",\"version\":\"0\"}}}",
                null, null);
        assertEquals(200, response.statusCode(), response.body());
        String sessionId = response.headers().firstValue("Mcp-Session-Id").orElseThrow();
        assertEquals(202, post(port,
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
                sessionId, null).statusCode());
        return sessionId;
    }

    private static String errorOf(JsonObject envelope) {
        assertTrue(envelope.has("error"), envelope.toString());
        return envelope.getAsJsonObject("error").get("message").getAsString();
    }

    private static JsonObject resultOf(JsonObject envelope) {
        return envelope.getAsJsonObject("result");
    }

    /** The text of the first content entry in a tool result. */
    private static String textOf(JsonObject envelope) {
        return resultOf(envelope).getAsJsonArray("content").get(0).getAsJsonObject()
                .get("text").getAsString();
    }

    private static boolean isError(JsonObject envelope) {
        return resultOf(envelope).has("isError")
                && resultOf(envelope).get("isError").getAsBoolean();
    }

    @Test
    void theFullConversationServesToolsAndReplaysLogs() throws Exception {
        int port = startService(settings -> { });
        String session = initialize(port);
        org.slf4j.LoggerFactory.getLogger("neomacros.mcp").info("hello-mcp-marker");

        JsonObject listing = rpc(port, session, "tools/list", null);
        String tools = listing.toString();
        assertTrue(tools.contains("get_logs"));
        assertTrue(tools.contains("get_player"));
        assertTrue(tools.contains("send_chat"));
        assertTrue(tools.contains("eval_script"));

        JsonObject logs = rpc(port, session, "tools/call",
                "{\"name\":\"get_logs\",\"arguments\":{\"limit\":50}}");
        assertFalse(isError(logs));
        assertTrue(textOf(logs).contains("hello-mcp-marker"));

        JsonObject ping = rpc(port, session, "ping", null);
        assertEquals(0, resultOf(ping).entrySet().size());
    }

    @Test
    void stateToolsReadTheStubbedGame() throws Exception {
        int port = startService(settings -> { });
        String session = initialize(port);

        JsonObject player = rpc(port, session, "tools/call",
                "{\"name\":\"get_player\"}");
        assertFalse(isError(player));
        assertTrue(textOf(player).contains("\"tester\""));
        assertTrue(textOf(player).contains("\"health\""));

        JsonObject world = rpc(port, session, "tools/call",
                "{\"name\":\"get_world\"}");
        assertFalse(isError(world));
        assertTrue(textOf(world).contains("minecraft:overworld"));
    }

    @Test
    void controlToolsGuardTheWorldAndDispatchChat() throws Exception {
        int port = startService(settings -> { });
        String session = initialize(port);

        adapter.lifecyclePort().setInWorld(false);
        JsonObject blocked = rpc(port, session, "tools/call",
                "{\"name\":\"send_chat\",\"arguments\":{\"message\":\"hi\"}}");
        assertTrue(isError(blocked));
        assertTrue(textOf(blocked).contains("not inside a loaded world"));

        adapter.lifecyclePort().setInWorld(true);
        JsonObject sent = rpc(port, session, "tools/call",
                "{\"name\":\"send_chat\",\"arguments\":{\"message\":\"hi\"}}");
        assertFalse(isError(sent));
        assertTrue(adapter.chatPort().output().contains("say:hi"));

        JsonObject eval = rpc(port, session, "tools/call",
                "{\"name\":\"eval_script\",\"arguments\":{\"expression\":\"1+1\"}}");
        assertFalse(isError(eval));
        assertTrue(textOf(eval).contains("\"ok\":true"));

        backend.failWith = new EvalOutcome(null, "SyntaxError", "bad code", "", 1);
        JsonObject failedEval = rpc(port, session, "tools/call",
                "{\"name\":\"eval_script\",\"arguments\":{\"expression\":\"oops\"}}");
        assertFalse(isError(failedEval));
        assertTrue(textOf(failedEval).contains("\"ok\":false"));
        assertTrue(textOf(failedEval).contains("SyntaxError"));
    }

    @Test
    void unknownToolsAnswerInvalidParams() throws Exception {
        int port = startService(settings -> { });
        String session = initialize(port);
        assertEquals(-32602, rpc(port, session, "tools/call",
                "{\"name\":\"does_not_exist\"}").getAsJsonObject("error")
                .get("code").getAsInt());
    }

    @Test
    void groupSwitchesDropToolsFromTheListing() throws Exception {
        int port = startService(settings -> {
            settings.groupControl = false;
            settings.groupEval = false;
        });
        String session = initialize(port);
        String tools = rpc(port, session, "tools/list", null).toString();
        assertTrue(tools.contains("get_logs"));
        assertFalse(tools.contains("send_chat"));
        assertFalse(tools.contains("eval_script"));
    }

    @Test
    void theTransportEnforcesAuthSessionAndVerbs() throws Exception {
        int port = startService(settings -> { });
        String session = initialize(port);

        HttpResponse<String> noToken = client.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/mcp"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noToken.statusCode());
        assertEquals("Bearer realm=\"neomacros-mcp\"",
                noToken.headers().firstValue("WWW-Authenticate").orElse(""));

        HttpResponse<String> badToken = client.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Authorization", "Bearer wrong")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, badToken.statusCode());

        assertEquals(400, post(port,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",
                null, null).statusCode());
        assertEquals(404, post(port,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",
                "not-a-session", null).statusCode());
        assertEquals(403, post(port,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",
                session, "http://evil.example.com").statusCode());
        assertTrue(post(port,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",
                session, "http://localhost:5173").statusCode() == 200);

        HttpResponse<String> get = client.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Authorization", "Bearer " + TOKEN)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, get.statusCode());

        HttpResponse<String> badHost = client.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Host", "attacker.example.com:" + port)
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, badHost.statusCode());

        HttpResponse<String> terminate = client.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Mcp-Session-Id", session)
                .DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, terminate.statusCode());
        assertEquals(404, post(port,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",
                session, null).statusCode());
    }

    @Test
    void malformedJsonAnswersTheParseError() throws Exception {
        int port = startService(settings -> { });
        HttpResponse<String> response = post(port, "{not json", null, null);
        assertEquals(200, response.statusCode());
        assertEquals(-32700, JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    void aBlankTokenIsGeneratedAndPersistedOnFirstStart() throws Exception {
        McpSettings settings = new McpSettings();
        settings.token = "";
        settings.port = 0;
        AtomicReference<String> persisted = new AtomicReference<>();
        adapter.lifecyclePort().setInWorld(true);
        service = new McpService(backend, java.util.function.Supplier::get, adapter,
                new StubCommands(), () -> settings, () -> "9.9.9-test",
                token -> {
                    persisted.set(token);
                    settings.token = token;
                });
        service.start();
        assertNotNull(persisted.get());
        assertEquals(32, persisted.get().length());
        assertEquals(persisted.get(), settings.token);
    }

    /** A stub backend: the MCP path only touches tasks, eval, and snapshots. */
    static final class StubBackend implements PanelBackend {

        EvalOutcome failWith;

        @Override
        public Meta meta() {
            return new Meta(List.of(1), "9.9.9-test", "26.3-stub");
        }

        @Override
        public StateSnapshot snapshot() {
            return new StateSnapshot(List.of(), new ServerInfo(0, 0, "loopback"), "/macros");
        }

        @Override
        public MacroView macroAction(String id, String action) {
            throw ApiError.notFound("unknown macro: " + id);
        }

        @Override
        public TasksResponse tasks() {
            return new TasksResponse(List.of(), List.of());
        }

        @Override
        public void stopTask(long taskId) {
            throw ApiError.notFound("unknown task: " + taskId);
        }

        @Override
        public ink.tuanzi.neomacros.fabric.web.FileContent readFile(String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FilesResponse listFiles(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ink.tuanzi.neomacros.fabric.web.FileWriteResult writeFile(String path,
                byte[] content, String ifMatch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteFile(String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void moveFile(String from, String to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void makeDirectory(String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reveal(String path) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MacroView createMacro(String category, String name, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MacroView moveMacro(String from, String to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteMacro(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EvalExecution evaluateConsole(String target, String expression) {
            EvalOutcome outcome = failWith != null
                    ? failWith
                    : EvalOutcome.ofValue(Map.of("echo", expression), 1);
            return new EvalExecution(target, outcome);
        }

        @Override
        public ink.tuanzi.neomacros.fabric.web.v1.EventsCatalogResponse eventCatalog() {
            throw new UnsupportedOperationException();
        }
    }

    /** A recording command registry stub. */
    record StubCommands() implements CommandRegistry {

        @Override
        public Object register(Map<String, Object> spec) {
            return spec;
        }

        @Override
        public void unregister(String name) {
        }

        @Override
        public CompletableFuture<Boolean> execute(String commandLine) {
            return CompletableFuture.completedFuture(true);
        }
    }
}
