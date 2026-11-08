package ink.tuanzi.neomacros.fabric.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ink.tuanzi.neomacros.core.console.ScriptConsole;
import ink.tuanzi.neomacros.core.console.ScriptConsoleEvent;
import ink.tuanzi.neomacros.core.net.NetTrace;
import ink.tuanzi.neomacros.core.net.NetTraceEvent;
import ink.tuanzi.neomacros.fabric.web.auth.DeviceRegistry;
import ink.tuanzi.neomacros.fabric.web.http.ApiError;
import ink.tuanzi.neomacros.fabric.web.json.Jsons;
import ink.tuanzi.neomacros.fabric.web.v1.Meta;
import ink.tuanzi.neomacros.fabric.web.v1.MacroView;
import ink.tuanzi.neomacros.fabric.web.v1.StateSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end over a real HTTP listener: every W1 endpoint, its success and
 * main failure paths, the NM1 chain (including replay and backoff), the
 * pairing long poll with the in-game decision, and the SSE stream. The
 * backend is a stub, so no game context is needed.
 */
class WebApiIntegrationTest {

    static {
        // The rebinding-defense test overrides the Host header, which the
        // JDK client treats as restricted unless allowed here.
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
    }

    @TempDir
    Path temp;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private TestKeys.Device device;
    private WebService service;

    /** A stub backend recording the actions the panel requests. */
    static final class StubBackend implements PanelBackend {

        final List<String> actions = new ArrayList<>();
        final List<String> deletions = new ArrayList<>();
        final List<String> fileDeletions = new ArrayList<>();
        final List<String> moves = new ArrayList<>();
        final List<String> mkdirs = new ArrayList<>();
        final List<String> reveals = new ArrayList<>();
        final List<String> macroMoves = new ArrayList<>();
        final List<String> writes = new ArrayList<>();
        final List<String> reads = new ArrayList<>();
        ApiError failure;
        FileContent fileToServe;
        String servedPath = "event/auto-welcome/index.js";
        FileWriteResult writeOutcome;
        final ink.tuanzi.neomacros.core.task.TaskRegistry tasks =
                new ink.tuanzi.neomacros.core.task.TaskRegistry();

        @Override
        public ink.tuanzi.neomacros.fabric.web.v1.TasksResponse tasks() {
            return new ink.tuanzi.neomacros.fabric.web.v1.TasksResponse(
                    tasks.activeTasks().stream()
                            .map(t -> ink.tuanzi.neomacros.fabric.web.v1.TaskEntry.of(
                                    ink.tuanzi.neomacros.core.task.ScriptTask.Snapshot.of(t)))
                            .toList(),
                    tasks.historyTasks().stream()
                            .map(t -> ink.tuanzi.neomacros.fabric.web.v1.TaskEntry.of(
                                    ink.tuanzi.neomacros.core.task.ScriptTask.Snapshot.of(t)))
                            .toList());
        }

        @Override
        public void stopTask(long taskId) {
            if (failure != null) {
                throw failure;
            }
            var task = tasks.task(taskId)
                    .orElseThrow(() -> ApiError.notFound("unknown task: " + taskId));
            if (task.status() == ink.tuanzi.neomacros.core.task.ScriptTask.Status.STOPPED) {
                throw ApiError.conflict("task " + taskId + " is already stopped");
            }
            tasks.stop(taskId);
        }

        @Override
        public Meta meta() {
            return new Meta(List.of(1), "0.1.0-test", "26.3");
        }

        @Override
        public StateSnapshot snapshot() {
            var macro = new MacroView("event/auto-welcome", ink.tuanzi.neomacros.fabric.web.v1
                    .MacroCategory.event, "auto-welcome", "1.0.0", "you",
                    ink.tuanzi.neomacros.fabric.web.v1.MacroLifecycle.world,
                    true, false, null, List.of(), List.of(), List.of(), null);
            return new StateSnapshot(List.of(macro),
                    new ink.tuanzi.neomacros.fabric.web.v1.ServerInfo(0, 0, "loopback"),
                    "C:\\macros");
        }

        @Override
        public MacroView macroAction(String id, String action) {
            if (failure != null) {
                throw failure;
            }
            if (!id.equals("event/auto-welcome")) {
                throw ApiError.notFound("unknown macro: " + id);
            }
            if (!List.of("enable", "disable", "start", "stop", "reload").contains(action)) {
                throw ApiError.notFound("unknown action: " + action);
            }
            actions.add(id + ":" + action);
            return new MacroView(id, ink.tuanzi.neomacros.fabric.web.v1.MacroCategory.event,
                    id.contains("/") ? id.substring(id.indexOf('/') + 1) : id,
                    "1.0.0", "you", ink.tuanzi.neomacros.fabric.web.v1.MacroLifecycle.world,
                    true, true, null, List.of(), List.of(), List.of(), null);
        }

        @Override
        public FileContent readFile(String path) {
            if (failure != null) {
                throw failure;
            }
            reads.add(path);
            if (fileToServe == null || !path.equals(servedPath)) {
                throw ApiError.notFound("no such file: " + path);
            }
            return fileToServe;
        }

        @Override
        public ink.tuanzi.neomacros.fabric.web.v1.FilesResponse listFiles(String id) {
            if (failure != null) {
                throw failure;
            }
            if (!id.equals("event/auto-welcome")) {
                throw ApiError.notFound("unknown macro: " + id);
            }
            return new ink.tuanzi.neomacros.fabric.web.v1.FilesResponse(
                    List.of("index.js", "lib/util.mjs", "neomacro.yml"),
                    List.of("lib", "lib/deep"));
        }

        @Override
        public void deleteFile(String path) {
            if (failure != null) {
                throw failure;
            }
            fileDeletions.add(path);
        }

        @Override
        public void moveFile(String from, String to) {
            if (failure != null) {
                throw failure;
            }
            moves.add(from + "->" + to);
        }

        @Override
        public void makeDirectory(String path) {
            if (failure != null) {
                throw failure;
            }
            mkdirs.add(path);
        }

        @Override
        public void reveal(String path) {
            if (failure != null) {
                throw failure;
            }
            reveals.add(path);
        }

        @Override
        public FileWriteResult writeFile(String path, byte[] content, String ifMatch) {
            if (failure != null) {
                throw failure;
            }
            writes.add(path);
            return writeOutcome == null ? FileWriteResult.saved(null) : writeOutcome;
        }

        @Override
        public MacroView createMacro(String category, String name, String description) {
            if (failure != null) {
                throw failure;
            }
            return new MacroView(category + "/" + name,
                    ink.tuanzi.neomacros.fabric.web.v1.MacroCategory.event,
                    name, "0.0.1", "your-name",
                    ink.tuanzi.neomacros.fabric.web.v1.MacroLifecycle.world,
                    true, false, null, List.of(), List.of(), List.of(), null);
        }

        @Override
        public MacroView moveMacro(String from, String to) {
            if (failure != null) {
                throw failure;
            }
            macroMoves.add(from + "->" + to);
            return new MacroView(to + "/" + (from.contains("/") ? from.substring(from.indexOf('/') + 1) : from),
                    ink.tuanzi.neomacros.fabric.web.v1.MacroCategory.event,
                    from.contains("/") ? from.substring(from.indexOf('/') + 1) : from,
                    "1.0.0", "you", ink.tuanzi.neomacros.fabric.web.v1.MacroLifecycle.world,
                    true, false, null, List.of(), List.of(), List.of(), null);
        }

        @Override
        public void deleteMacro(String id) {
            if (failure != null) {
                throw failure;
            }
            deletions.add(id);
        }

        final List<String> evals = new ArrayList<>();
        PanelBackend.EvalExecution evalOutcome;

        @Override
        public PanelBackend.EvalExecution evaluateConsole(String target, String expression) {
            if (failure != null) {
                throw failure;
            }
            evals.add(target + ":" + expression);
            if (evalOutcome != null) {
                return evalOutcome;
            }
            if (!"devtools".equals(target)) {
                throw ApiError.notFound("the macro '" + target + "' is not loaded");
            }
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> node = (java.util.Map<String, Object>) strNode("ok");
            return new PanelBackend.EvalExecution(target,
                    ink.tuanzi.neomacros.core.runtime.EvalOutcome.ofValue(node, 3));
        }

        List<ink.tuanzi.neomacros.fabric.web.v1.EventsCatalogResponse.Entry> catalog =
                List.of(
                        new ink.tuanzi.neomacros.fabric.web.v1.EventsCatalogResponse.Entry(
                                "chat:receive", false),
                        new ink.tuanzi.neomacros.fabric.web.v1.EventsCatalogResponse.Entry(
                                "key:press", true),
                        new ink.tuanzi.neomacros.fabric.web.v1.EventsCatalogResponse.Entry(
                                "player:join", false),
                        new ink.tuanzi.neomacros.fabric.web.v1.EventsCatalogResponse.Entry(
                                "tick", false));

        @Override
        public ink.tuanzi.neomacros.fabric.web.v1.EventsCatalogResponse eventCatalog() {
            if (failure != null) {
                throw failure;
            }
            return new ink.tuanzi.neomacros.fabric.web.v1.EventsCatalogResponse(catalog);
        }
    }

    /** Starts the service on an ephemeral port with a stub backend. */
    private StubBackend start(GameRunner runner) throws IOException {
        return startWithOrigins(runner, java.util.List.of());
    }

    /** Starts the service on an ephemeral port with extra CORS origins. */
    private StubBackend startWithOrigins(GameRunner runner,
            java.util.List<String> extraOrigins) throws IOException {
        StubBackend backend = new StubBackend();
        var events = new ink.tuanzi.neomacros.fabric.web.sse.PanelEvents();
        // The stub mirrors the real WebRuntime wiring: task registry changes
        // broadcast to the panel as task:state events.
        backend.tasks.onTaskEvent((event, snapshot) -> events.broadcast("task:state",
                Jsons.stringify(ink.tuanzi.neomacros.fabric.web.v1.TaskEntry.of(snapshot))));
        device = TestKeys.ed25519("dev_test");
        DeviceRegistry registry = new DeviceRegistry(temp.resolve("web-devices.json"),
                message -> { });
        device = device.withId(registry
                .register("integration browser", device.algo, device.pubJwk).id());
        service = new WebService(backend, runner, registry, newPairingStore(),
                new ink.tuanzi.neomacros.fabric.web.pair.PairingRequests(System::currentTimeMillis),
                () -> "loopback", () -> 0, () -> "http://localhost:5173",
                () -> extraOrigins, events);
        service.start();
        return backend;
    }

    /** A pairing store over the temp dir with the integration-test lifetime. */
    private ink.tuanzi.neomacros.fabric.web.pair.PairingService newPairingStore() {
        return new ink.tuanzi.neomacros.fabric.web.pair.PairingService(
                temp.resolve("web-pairing.json"), System::currentTimeMillis,
                () -> 180_000, message -> { });
    }

    @AfterEach
    void stopService() {
        if (service != null && service.running()) {
            service.stop();
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + service.port();
    }

    private HttpResponse<String> send(String method, String pathAndQuery, byte[] body,
                                      String authorization) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl() + pathAndQuery));
        builder.header("Content-Type", "application/json");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        }
        return client.send(builder.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String nonce() {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        return B64.encodeToString(raw);
    }

    private HttpResponse<String> signed(String method, String pathAndQuery,
                                        byte[] body) throws Exception {
        int queryAt = pathAndQuery.indexOf('?');
        String path = queryAt < 0 ? pathAndQuery : pathAndQuery.substring(0, queryAt);
        String query = queryAt < 0 ? "" : pathAndQuery.substring(queryAt + 1);
        String authorization = device.authorize(method, path, query, body,
                System.currentTimeMillis() / 1000, nonce());
        return send(method, pathAndQuery, body, authorization);
    }

    private JsonObject errorOf(HttpResponse<String> response) {
        assertEquals("application/json; charset=utf-8",
                response.headers().firstValue("Content-Type").orElse(""));
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertTrue(body.has("error"), "unified error body: " + response.body());
        assertTrue(body.getAsJsonObject("error").has("code"));
        assertTrue(body.getAsJsonObject("error").has("message"));
        return body;
    }

    // ---- meta + hardening --------------------------------------------------

    @Test
    void metaIsUnsignedAndShaped() throws Exception {
        start(DIRECT);
        HttpResponse<String> response = send("GET", "/api/v1/meta", new byte[0], null);
        assertEquals(200, response.statusCode());
        JsonObject meta = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, meta.getAsJsonArray("api").get(0).getAsInt());
        assertEquals("0.1.0-test", meta.get("modVersion").getAsString());
        assertEquals("26.3", meta.get("gameVersion").getAsString());
    }

    @Test
    void aForeignHostIsRejectedBeforeAnythingElse() throws Exception {
        start(DIRECT);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/meta"))
                .header("Host", "evil.example")
                .GET().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
        assertEquals("forbidden", errorOf(response).getAsJsonObject("error")
                .get("code").getAsString());
    }

    @Test
    void preflightEchoesOnlyThePanelOrigin() throws Exception {
        start(DIRECT);
        HttpRequest good = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/state"))
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Private-Network", "true")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> preflight = client.send(good,
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, preflight.statusCode());
        assertEquals("http://localhost:5173", preflight.headers()
                .firstValue("Access-Control-Allow-Origin").orElse(""));
        assertEquals("true", preflight.headers()
                .firstValue("Access-Control-Allow-Private-Network").orElse(""));

        // A signed request that also carries an echoed origin exposes ETag
        // to panel scripts (optimistic concurrency needs it in W3).
        String authorization = device.authorize("GET", "/api/v1/logs", "after=0",
                new byte[0], System.currentTimeMillis() / 1000, nonce());
        HttpRequest actual = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/logs?after=0"))
                .header("Authorization", authorization)
                .header("Origin", "http://localhost:5173")
                .GET().build();
        HttpResponse<String> actualResponse = client.send(actual,
                HttpResponse.BodyHandlers.ofString());
        assertEquals("ETag", actualResponse.headers()
                .firstValue("Access-Control-Expose-Headers").orElse(""));

        HttpRequest stranger = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/state"))
                .header("Origin", "https://evil.example")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> denied = client.send(stranger,
                HttpResponse.BodyHandlers.ofString());
        assertTrue(denied.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    // ---- state -------------------------------------------------------------

    @Test
    void stateRequiresASignatureAndServesTheSnapshot() throws Exception {
        StubBackend backend = start(DIRECT);
        HttpResponse<String> ok = signed("GET", "/api/v1/state", new byte[0]);
        assertEquals(200, ok.statusCode());
        JsonObject state = JsonParser.parseString(ok.body()).getAsJsonObject();
        assertEquals("event/auto-welcome",
                state.getAsJsonArray("macros").get(0).getAsJsonObject().get("id").getAsString());
        assertTrue(state.getAsJsonObject("server").has("port"));

        // An unsigned request is an authentication failure (backoff follows;
        // this test ends here so the window never affects another test).
        HttpResponse<String> denied = send("GET", "/api/v1/state", new byte[0], null);
        assertEquals(401, denied.statusCode());
        assertEquals("unauthorized", errorOf(denied).getAsJsonObject("error")
                .get("code").getAsString());
    }

    @Test
    void aReplayedRequestIsRejected() throws Exception {
        start(DIRECT);
        String authorization = device.authorize("GET", "/api/v1/state", "",
                new byte[0], System.currentTimeMillis() / 1000, nonce());
        assertEquals(200, send("GET", "/api/v1/state", new byte[0], authorization)
                .statusCode());
        HttpResponse<String> replay = send("GET", "/api/v1/state", new byte[0], authorization);
        assertEquals(401, replay.statusCode());
    }

    @Test
    void aStaleTimestampIsRejected() throws Exception {
        start(DIRECT);
        String authorization = device.authorize("GET", "/api/v1/state", "",
                new byte[0], System.currentTimeMillis() / 1000 - 120, nonce());
        HttpResponse<String> response = send("GET", "/api/v1/state", new byte[0], authorization);
        assertEquals(401, response.statusCode());
    }

    @Test
    void clientMistakesGetPlain401s() throws Exception {
        start(DIRECT);
        // Missing header, unknown device, stale timestamp, reused nonce —
        // every failure is a plain 401 and the service keeps serving.
        assertEquals(401, send("GET", "/api/v1/state", new byte[0], null).statusCode());
        // A well-signed request right after the mistake still works.
        assertEquals(200, signed("GET", "/api/v1/state", new byte[0]).statusCode());
        String stale = device.authorize("GET", "/api/v1/state", "", new byte[0],
                System.currentTimeMillis() / 1000 - 120, nonce());
        assertEquals(401, send("GET", "/api/v1/state", new byte[0], stale).statusCode());
        String reused = device.authorize("GET", "/api/v1/state", "", new byte[0],
                System.currentTimeMillis() / 1000, nonce());
        assertEquals(200, send("GET", "/api/v1/state", new byte[0], reused).statusCode());
        assertEquals(401, send("GET", "/api/v1/state", new byte[0], reused).statusCode());
        // Still serving: the next well-signed request goes straight through.
        assertEquals(200, signed("GET", "/api/v1/state", new byte[0]).statusCode());
    }

    @Test
    void aStuckGameThreadTimesOutAs504() throws Exception {
        start(new GameRunner() {
            @Override
            public <T> T call(java.util.function.Supplier<T> task) {
                throw ApiError.gameThreadTimeout();
            }
        });
        HttpResponse<String> response = signed("GET", "/api/v1/state", new byte[0]);
        assertEquals(504, response.statusCode());
        assertEquals("game_thread_timeout", errorOf(response).getAsJsonObject("error")
                .get("code").getAsString());
    }

    // ---- macro actions -----------------------------------------------------

    @Test
    void macroActionsReachTheBackend() throws Exception {
        StubBackend backend = start(DIRECT);
        assertEquals(200, signed("POST", "/api/v1/macros/event%2Fauto-welcome/enable",
                new byte[0]).statusCode());
        assertEquals(200, signed("POST", "/api/v1/macros/event%2Fauto-welcome/disable",
                new byte[0]).statusCode());
        assertEquals(200, signed("POST", "/api/v1/macros/event%2Fauto-welcome/reload",
                new byte[0]).statusCode());
        assertEquals(List.of("event/auto-welcome:enable",
                "event/auto-welcome:disable",
                "event/auto-welcome:reload"), backend.actions);
    }

    @Test
    void unknownMacroAndActionAre404() throws Exception {
        start(DIRECT);
        assertEquals(404, signed("POST", "/api/v1/macros/nope%2Fmissing/start",
                new byte[0]).statusCode());
        assertEquals(404, signed("POST", "/api/v1/macros/event%2Fauto-welcome/explode",
                new byte[0]).statusCode());
    }

    @Test
    void aStateConflictSurfacesAs409() throws Exception {
        StubBackend backend = start(DIRECT);
        backend.failure = ApiError.conflict("it is not running");
        HttpResponse<String> response = signed("POST",
                "/api/v1/macros/event%2Fauto-welcome/stop", new byte[0]);
        assertEquals(409, response.statusCode());
        assertEquals("conflict", errorOf(response).getAsJsonObject("error")
                .get("code").getAsString());
    }

    // ---- logs --------------------------------------------------------------

    @Test
    void logsReplayIncrementally() throws Exception {
        start(DIRECT);
        // The service logs its own boot lines; measure the baseline first.
        long baseline = service.logs().lastSeq();
        service.logs().append(System.currentTimeMillis(), "INFO", "neomacros.test", "one");
        service.logs().append(System.currentTimeMillis(), "WARN", "neomacros.test", "two");
        service.logs().append(System.currentTimeMillis(), "ERROR", "neomacros.test", "three");

        HttpResponse<String> full = signed("GET", "/api/v1/logs?after=" + baseline,
                new byte[0]);
        assertEquals(200, full.statusCode());
        JsonObject body = JsonParser.parseString(full.body()).getAsJsonObject();
        assertEquals(3, body.getAsJsonArray("entries").size());
        assertEquals(baseline + 3, body.get("lastSeq").getAsLong());

        HttpResponse<String> tail = signed("GET", "/api/v1/logs?after=" + (baseline + 2),
                new byte[0]);
        JsonObject tailBody = JsonParser.parseString(tail.body()).getAsJsonObject();
        assertEquals(1, tailBody.getAsJsonArray("entries").size());
        assertEquals("three", tailBody.getAsJsonArray("entries").get(0)
                .getAsJsonObject().get("line").getAsString());
    }

    // ---- script console ----------------------------------------------------

    @Test
    void consoleReplayIncrementally() throws Exception {
        start(DIRECT);
        long baseline = service.console().lastSeq();
        service.console().append(consoleEvent("info", "index.js", 12,
                List.of(strNode("hello"), numNode(42))));
        service.console().append(consoleEvent("error", "lib/util.js", 3,
                List.of(strNode("boom"))));

        HttpResponse<String> full = signed("GET", "/api/v1/console?after=" + baseline,
                new byte[0]);
        assertEquals(200, full.statusCode());
        JsonObject body = JsonParser.parseString(full.body()).getAsJsonObject();
        assertEquals(2, body.getAsJsonArray("entries").size());
        assertEquals(baseline + 2, body.get("lastSeq").getAsLong());
        JsonObject first = body.getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("event/auto-welcome", first.get("macro").getAsString());
        assertEquals("info", first.get("level").getAsString());
        assertEquals("index.js", first.get("file").getAsString());
        assertEquals(12, first.get("line").getAsInt());
        assertEquals("hello", first.getAsJsonArray("args").get(0)
                .getAsJsonObject().get("v").getAsString());
        assertTrue(first.get("stack").isJsonNull(), "stack stays null for plain logs");

        HttpResponse<String> tail = signed("GET", "/api/v1/console?after=" + (baseline + 1),
                new byte[0]);
        JsonObject tailBody = JsonParser.parseString(tail.body()).getAsJsonObject();
        assertEquals(1, tailBody.getAsJsonArray("entries").size());
        assertEquals("error", tailBody.getAsJsonArray("entries").get(0)
                .getAsJsonObject().get("level").getAsString());
    }

    @Test
    void theConsoleSseFrameCarriesScriptEmissions() throws Exception {
        start(DIRECT);
        HttpResponse<String> ticketResponse = signed("POST", "/api/v1/events/ticket",
                new byte[0]);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl()
                + "/api/v1/events?ticket="
                + JsonParser.parseString(ticketResponse.body()).getAsJsonObject()
                .get("ticket").getAsString()))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        HttpResponse<InputStream> stream = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, stream.statusCode());
        InputStream input = stream.body();
        readUntil(input, "\n\n");

        CompletableFuture<Void> append = CompletableFuture.runAsync(() ->
                service.console().append(consoleEvent("warn", "index.js", 7,
                        List.of(strNode("careful")))));
        append.get(5, TimeUnit.SECONDS);
        String frame = readUntil(input, "\n\n");
        assertTrue(frame.contains("event: console"), frame);
        assertTrue(frame.contains("\"macro\":\"event/auto-welcome\""), frame);
        assertTrue(frame.contains("\"kind\":\"warn\""), frame);
        input.close();
    }

    @Test
    void theConsoleDispatcherWiresCoreEventsIntoTheRing() throws Exception {
        start(DIRECT);
        long baseline = service.console().lastSeq();
        ScriptConsole.dispatch(consoleEvent("log", "index.js", 5,
                List.of(strNode("live"))));
        // The core pipeline thread hands the event to the ring asynchronously.
        long deadline = System.currentTimeMillis() + 5000;
        while (service.console().lastSeq() == baseline
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(baseline + 1, service.console().lastSeq());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> firstArg = (java.util.Map<String, Object>)
                service.console().after(baseline).get(0).args().get(0);
        assertEquals("live", firstArg.get("v"));
    }

    /** One ScriptConsoleEvent addressed to the stub backend's macro. */
    private static ScriptConsoleEvent consoleEvent(String level, String file, int line,
                                                   List<Object> args) {
        return new ScriptConsoleEvent("event/auto-welcome", level, level,
                file, line, 1, 0, args, null, null, null, null,
                System.currentTimeMillis());
    }

    /** A neutral string argument node. */
    private static Object strNode(String value) {
        return Map.of("t", "str", "v", value);
    }

    // ---- DevTools console eval ---------------------------------------------

    @Test
    void evalRecordsInputAndResultIntoTheRing() throws Exception {
        StubBackend backend = start(DIRECT);
        long baseline = service.console().lastSeq();

        HttpResponse<String> response = signed("POST", "/api/v1/console/eval",
                Jsons.stringify(Map.of("target", "devtools", "expression", "1 + 1"))
                        .getBytes(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(2, body.getAsJsonArray("entries").size());
        assertEquals(baseline + 2, body.get("lastSeq").getAsLong());
        assertEquals(List.of("devtools:1 + 1"), backend.evals);

        // Both entries landed in the ring (SSE and replay share this source).
        assertEquals(2, service.console().after(baseline).size());
        JsonObject input = body.getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("input", input.get("kind").getAsString());
        assertEquals("devtools", input.get("macro").getAsString());
        assertEquals("1 + 1", input.getAsJsonArray("args").get(0)
                .getAsJsonObject().get("v").getAsString());
        JsonObject result = body.getAsJsonArray("entries").get(1).getAsJsonObject();
        assertEquals("result", result.get("kind").getAsString());
        assertEquals("info", result.get("level").getAsString());
        assertEquals("ok", result.getAsJsonArray("args").get(0)
                .getAsJsonObject().get("v").getAsString());
    }

    @Test
    void evalReportsGuestFailuresAsErrorResults() throws Exception {
        StubBackend backend = start(DIRECT);
        backend.evalOutcome = new PanelBackend.EvalExecution("devtools",
                ink.tuanzi.neomacros.core.runtime.EvalOutcome.ofError(
                        "ReferenceError", "nope is not defined", "    at devtools-repl:1:1", 1));

        HttpResponse<String> response = signed("POST", "/api/v1/console/eval",
                Jsons.stringify(Map.of("expression", "nope")).getBytes(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonArray("entries").get(1).getAsJsonObject();
        assertEquals("error", result.get("level").getAsString());
        JsonObject node = result.getAsJsonArray("args").get(0).getAsJsonObject();
        assertEquals("err", node.get("t").getAsString());
        assertEquals("ReferenceError", node.get("name").getAsString());
        assertEquals("nope is not defined", node.get("msg").getAsString());
    }

    @Test
    void evalMapsBackendFailuresToTheirStatusCodes() throws Exception {
        start(DIRECT);
        assertEquals(404, signed("POST", "/api/v1/console/eval",
                Jsons.stringify(Map.of("target", "event/missing", "expression", "1"))
                        .getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals(400, signed("POST", "/api/v1/console/eval",
                Jsons.stringify(Map.of("target", "devtools", "expression", "  "))
                        .getBytes(StandardCharsets.UTF_8)).statusCode());
    }

    // ---- network replay -------------------------------------------------------

    @Test
    void networkReplaysSettledRequestsIncrementally() throws Exception {
        start(DIRECT);
        long baseline = service.net().lastSeq();

        NetTrace.dispatch(new NetTraceEvent(1234L, "GET", "https://example.com/api",
                null, null, Map.of("authorization", "Bearer t"), null, null, false,
                200, Map.of("content-type", "application/json"), "{\"ok\":true}",
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), false, false,
                11L, 42L, 30L, 12L, null));
        NetTrace.dispatch(new NetTraceEvent(1235L, "GET", "https://example.com/dead",
                "event/auto-welcome", "index.js", Map.of(), null, null, false,
                null, null, null, null, false, false, null, 7L, null, null,
                "java.net.ConnectException: connection refused"));

        // Dispatch runs on the core pipeline thread; wait for both to land.
        long deadline = System.currentTimeMillis() + 5000;
        while (service.net().lastSeq() < baseline + 2
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        HttpResponse<String> full = signed("GET", "/api/v1/network?after=" + baseline,
                new byte[0]);
        assertEquals(200, full.statusCode());
        JsonObject body = JsonParser.parseString(full.body()).getAsJsonObject();
        var entries = body.getAsJsonArray("entries");
        assertEquals(2, entries.size());
        assertEquals(baseline + 2, body.get("lastSeq").getAsLong());

        JsonObject ok = entries.get(0).getAsJsonObject();
        assertEquals("GET", ok.get("method").getAsString());
        assertEquals(200, ok.get("status").getAsInt());
        assertEquals(11L, ok.get("size").getAsLong());
        assertEquals(42L, ok.get("durationMs").getAsLong());
        assertEquals(30L, ok.get("waitMs").getAsLong());
        assertEquals(12L, ok.get("downloadMs").getAsLong());
        assertEquals("{\"ok\":true}", ok.get("responseBodyPreview").getAsString());
        assertEquals(true, ok.get("hasResponseBody").getAsBoolean());
        assertTrue(ok.get("error").isJsonNull());

        JsonObject failed = entries.get(1).getAsJsonObject();
        assertTrue(failed.get("status").isJsonNull());
        assertEquals("event/auto-welcome", failed.get("initiator").getAsString());
        assertEquals("index.js", failed.get("initiatorFile").getAsString());
        assertEquals("java.net.ConnectException: connection refused",
                failed.get("error").getAsString());

        // Incremental replay: the cursor only sees newer entries.
        HttpResponse<String> tail = signed("GET", "/api/v1/network?after=" + (baseline + 1),
                new byte[0]);
        assertEquals(1, JsonParser.parseString(tail.body()).getAsJsonObject()
                .getAsJsonArray("entries").size());
    }

    @Test
    void networkBodyEndpointServesRetainedContent() throws Exception {
        start(DIRECT);
        long baseline = service.net().lastSeq();

        NetTrace.dispatch(new NetTraceEvent(1236L, "POST", "https://example.com/echo",
                null, null, Map.of("content-type", "text/plain"), "ping", "ping-body", false,
                200, Map.of("content-type", "text/plain"), "pong",
                "pong-body".getBytes(StandardCharsets.UTF_8), false, false,
                9L, 15L, 10L, 5L, null));
        long deadline = System.currentTimeMillis() + 5000;
        while (service.net().lastSeq() < baseline + 1
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        long seq = baseline + 1;

        // response side (default): raw bytes with the recorded disposition
        HttpResponse<String> response = signed("GET", "/api/v1/network/" + seq + "/body",
                new byte[0]);
        assertEquals(200, response.statusCode());
        assertEquals("pong-body", response.body());
        assertEquals("attachment; filename=\"echo\"",
                response.headers().firstValue("Content-Disposition").orElse(""));

        // request side via the side parameter
        HttpResponse<String> requestSide = signed("GET",
                "/api/v1/network/" + seq + "/body?side=request", new byte[0]);
        assertEquals(200, requestSide.statusCode());
        assertEquals("ping-body", requestSide.body());

        // unknown seq is a plain 404, not an eviction
        assertEquals(404, signed("GET", "/api/v1/network/9999/body",
                new byte[0]).statusCode());
    }

    // ---- tasks ---------------------------------------------------------------

    @Test
    void theTaskSnapshotServesActiveAndHistoryAndStopWorks() throws Exception {
        StubBackend backend = start(DIRECT);
        var tasks = backend.tasks;
        var finished = tasks.start("auto-welcome",
                ink.tuanzi.neomacros.core.module.manifest.ScriptCategory.EVENT, "manual");
        tasks.stop(finished.taskId());
        var running = tasks.start("uptime",
                ink.tuanzi.neomacros.core.module.manifest.ScriptCategory.SERVICES, "lifecycle");
        tasks.countTrigger(running.taskId());

        HttpResponse<String> response = signed("GET", "/api/v1/tasks", new byte[0]);
        assertEquals(200, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(1, body.getAsJsonArray("active").size());
        assertEquals(1, body.getAsJsonArray("history").size());
        JsonObject runningEntry = body.getAsJsonArray("active").get(0).getAsJsonObject();
        assertEquals("uptime", runningEntry.get("macro").getAsString());
        assertEquals("running", runningEntry.get("status").getAsString());
        assertEquals(1, runningEntry.get("triggers").getAsInt());

        // Stop by id moves the task into history.
        HttpResponse<String> stop = signed("POST", "/api/v1/tasks/" + running.taskId() + "/stop",
                new byte[0]);
        assertEquals(200, stop.statusCode());
        JsonObject stoppedBody = JsonParser.parseString(stop.body()).getAsJsonObject();
        assertEquals(0, stoppedBody.getAsJsonArray("active").size());
        assertEquals(2, stoppedBody.getAsJsonArray("history").size());
        assertEquals("stopped", stoppedBody.getAsJsonArray("history").get(1)
                .getAsJsonObject().get("status").getAsString());

        // Error shapes: unknown id, double stop.
        assertEquals(404, signed("POST", "/api/v1/tasks/9999/stop", new byte[0]).statusCode());
        assertEquals(409, signed("POST", "/api/v1/tasks/" + running.taskId() + "/stop",
                new byte[0]).statusCode());
    }

    @Test
    void taskStateChangesBroadcastOverSse() throws Exception {
        StubBackend backend = start(DIRECT);
        HttpResponse<String> ticketResponse = signed("POST", "/api/v1/events/ticket",
                new byte[0]);
        JsonObject ticket = JsonParser.parseString(ticketResponse.body()).getAsJsonObject();
        HttpResponse<InputStream> stream = client.send(HttpRequest.newBuilder(
                        URI.create(baseUrl() + "/api/v1/events?ticket="
                                + ticket.get("ticket").getAsString()))
                .timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, stream.statusCode());
        InputStream input = stream.body();
        readUntil(input, "\n\n");

        backend.tasks.start("auto-welcome",
                ink.tuanzi.neomacros.core.module.manifest.ScriptCategory.EVENT, "manual");
        // Heartbeats interleave with the frames; read until the task frame shows up.
        String frame = "";
        long deadline = System.currentTimeMillis() + 5000;
        while (!frame.contains("event: task:state") && System.currentTimeMillis() < deadline) {
            frame = readUntil(input, "\n\n");
        }
        assertTrue(frame.contains("event: task:state"), frame);
        assertTrue(frame.contains("\"macro\":\"auto-welcome\""), frame);
        assertTrue(frame.contains("\"status\":\"running\""), frame);
        input.close();
    }

    /** A neutral number argument node. */
    private static Object numNode(long value) {
        return Map.of("t", "num", "v", value);
    }

    // ---- event catalog -------------------------------------------------------

    @Test
    void theEventCatalogListsEveryEventWithCancellation() throws Exception {
        start(DIRECT);
        HttpResponse<String> response = signed("GET", "/api/v1/events/catalog",
                new byte[0]);
        assertEquals(200, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        var events = body.getAsJsonArray("events");
        assertEquals(4, events.size());
        JsonObject first = events.get(0).getAsJsonObject();
        assertEquals("chat:receive", first.get("name").getAsString());
        assertFalse(first.get("cancellable").getAsBoolean());
        JsonObject keyPress = events.get(1).getAsJsonObject();
        assertEquals("key:press", keyPress.get("name").getAsString());
        assertTrue(keyPress.get("cancellable").getAsBoolean());

        // The catalog route is NM1-guarded like every non-stream endpoint.
        HttpResponse<String> anonymous = send("GET", "/api/v1/events/catalog",
                new byte[0], null);
        assertEquals(401, anonymous.statusCode());
        assertEquals("unauthorized", errorOf(anonymous).getAsJsonObject("error")
                .get("code").getAsString());
    }

    // ---- SSE ---------------------------------------------------------------

    @Test
    void theSseStreamNeedsAFreshTicketAndBroadcasts() throws Exception {
        start(DIRECT);
        HttpResponse<String> ticketResponse = signed("POST", "/api/v1/events/ticket",
                new byte[0]);
        assertEquals(200, ticketResponse.statusCode());
        JsonObject ticket = JsonParser.parseString(ticketResponse.body()).getAsJsonObject();
        assertEquals(30, ticket.get("expiresIn").getAsInt());

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl()
                + "/api/v1/events?ticket=" + ticket.get("ticket").getAsString()))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        HttpResponse<InputStream> stream = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, stream.statusCode());
        InputStream input = stream.body();
        readUntil(input, "\n\n");

        CompletableFuture<Void> broadcast = CompletableFuture.runAsync(() ->
                service.sse().broadcast("log", "{\"seq\":7,\"line\":\"hello\"}"));
        broadcast.get(5, TimeUnit.SECONDS);
        String frame = readUntil(input, "\n\n");
        assertTrue(frame.contains("event: log"), frame);
        assertTrue(frame.contains("\"seq\":7"), frame);
        input.close();

        // The consumed ticket is dead: a second connect is an unsigned 401.
        HttpRequest replay = HttpRequest.newBuilder(URI.create(baseUrl()
                + "/api/v1/events?ticket=" + ticket.get("ticket").getAsString()))
                .GET().build();
        HttpResponse<String> denied = client.send(replay,
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, denied.statusCode());
        assertEquals("unauthorized", errorOf(denied).getAsJsonObject("error")
                .get("code").getAsString());

        // Without a ticket too.
        HttpResponse<String> noTicket = client.send(HttpRequest.newBuilder(
                        URI.create(baseUrl() + "/api/v1/events")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noTicket.statusCode());
    }

    /** Reads from the stream until the delimiter passes; returns the chunk. */
    private static String readUntil(InputStream input, String delimiter) throws IOException {
        var buffer = new StringBuilder();
        int previous = -1;
        int current;
        while ((current = input.read()) != -1) {
            buffer.append((char) current);
            if (previous == '\n' && current == '\n') {
                break;
            }
            previous = current;
        }
        return buffer.toString();
    }

    // ---- pairing -----------------------------------------------------------

    @Test
    void pairingRegistersTheDeviceImmediately() throws Exception {
        start(DIRECT);
        TestKeys.Device browser = TestKeys.ed25519("dev_browser");
        String token = service.newPairingToken();
        byte[] body = Jsons.stringify(Map.of(
                "token", token,
                "label", "Chrome on Windows",
                "algo", "ed25519",
                "pubJwk", browser.pubJwk)).getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = send("POST", "/api/v1/pair", body, null);
        assertEquals(200, response.statusCode());
        String clientId = JsonParser.parseString(response.body()).getAsJsonObject()
                .get("clientId").getAsString();
        assertFalse(clientId.isBlank());
        browser = browser.withId(clientId);

        // The token is burned: replaying the same pairing is a 400.
        HttpResponse<String> replay = send("POST", "/api/v1/pair", body, null);
        assertEquals(400, replay.statusCode());

        // The paired device signs successfully right away.
        String authorization = browser.authorize("GET", "/api/v1/state", "",
                new byte[0], System.currentTimeMillis() / 1000, nonce());
        assertEquals(200, send("GET", "/api/v1/state", new byte[0], authorization)
                .statusCode());
    }

    @Test
    void pairingTokenSurvivesAServiceRestart() throws Exception {
        StubBackend backend = new StubBackend();
        device = TestKeys.ed25519("dev_test");
        DeviceRegistry registry = new DeviceRegistry(temp.resolve("web-devices.json"),
                message -> { });
        device = device.withId(registry
                .register("integration browser", device.algo, device.pubJwk).id());
        var file = temp.resolve("web-pairing-restart.json");
        var store = new ink.tuanzi.neomacros.fabric.web.pair.PairingService(
                file, System::currentTimeMillis, () -> 3_600_000, message -> { });
        service = new WebService(backend, DIRECT, registry, store,
                new ink.tuanzi.neomacros.fabric.web.pair.PairingRequests(System::currentTimeMillis),
                () -> "loopback", () -> 0, () -> "http://localhost:5173",
                () -> java.util.List.of(), new ink.tuanzi.neomacros.fabric.web.sse.PanelEvents());
        service.start();
        String token = service.newPairingToken();
        service.stop();

        // A fresh store over the same file simulates a full process restart.
        var reloaded = new ink.tuanzi.neomacros.fabric.web.pair.PairingService(
                file, System::currentTimeMillis, () -> 3_600_000, message -> { });
        service = new WebService(backend, DIRECT, registry, reloaded,
                new ink.tuanzi.neomacros.fabric.web.pair.PairingRequests(System::currentTimeMillis),
                () -> "loopback", () -> 0, () -> "http://localhost:5173",
                () -> java.util.List.of(), new ink.tuanzi.neomacros.fabric.web.sse.PanelEvents());
        service.start();
        TestKeys.Device browser = TestKeys.ed25519("dev_browser_restart");
        byte[] body = Jsons.stringify(Map.of(
                "token", token,
                "label", "Chrome on Windows",
                "algo", "ed25519",
                "pubJwk", browser.pubJwk)).getBytes(StandardCharsets.UTF_8);
        assertEquals(200, send("POST", "/api/v1/pair", body, null).statusCode(),
                "the pre-restart token still pairs");
    }

    @Test
    void aNewPairingTokenKillsThePreviousOne() throws Exception {
        start(DIRECT);
        TestKeys.Device browser = TestKeys.ed25519("dev_browser2");
        String first = service.newPairingToken();
        service.newPairingToken();
        byte[] body = Jsons.stringify(Map.of(
                "token", first,
                "label", "Firefox",
                "algo", "ecdsa-p256",
                "pubJwk", browser.pubJwk)).getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = send("POST", "/api/v1/pair", body, null);
        assertEquals(400, response.statusCode());
        assertEquals("invalid_request", errorOf(response).getAsJsonObject("error")
                .get("code").getAsString());
    }

    @Test
    void pairingRejectsBadTokensAndBodies() throws Exception {
        start(DIRECT);
        var validDevice = Map.of("kty", "OKP", "crv", "Ed25519", "x", "aa");
        // A token that was never minted.
        byte[] bogus = Jsons.stringify(Map.of(
                "token", "bogus", "label", "x", "algo", "ed25519",
                "pubJwk", validDevice)).getBytes(StandardCharsets.UTF_8);
        assertEquals(400, send("POST", "/api/v1/pair", bogus, null).statusCode());
        // Missing fields.
        byte[] incomplete = "{\"token\":\"x\"}".getBytes(StandardCharsets.UTF_8);
        assertEquals(400, send("POST", "/api/v1/pair", incomplete, null).statusCode());
        // Malformed JSON.
        byte[] malformed = "{not json".getBytes(StandardCharsets.UTF_8);
        assertEquals(400, send("POST", "/api/v1/pair", malformed, null).statusCode());
        // A token-shaped request with unusable key material.
        String token = service.newPairingToken();
        byte[] badKey = Jsons.stringify(Map.of(
                "token", token, "label", "x", "algo", "ed25519",
                "pubJwk", Map.of("kty", "oct"))).getBytes(StandardCharsets.UTF_8);
        assertEquals(400, send("POST", "/api/v1/pair", badKey, null).statusCode());
    }

    // ---- tokenless pairing ---------------------------------------------------

    /** Posts to the tokenless pair-request endpoint with an Origin header. */
    private HttpResponse<String> sendPairRequest(byte[] body, String origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/pair-request"))
                .header("Content-Type", "application/json");
        if (origin != null) {
            builder.header("Origin", origin);
        }
        builder.POST(HttpRequest.BodyPublishers.ofByteArray(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void tokenlessPairingWaitsForTheInGameDecision() throws Exception {
        start(DIRECT);
        TestKeys.Device browser = TestKeys.ed25519("dev_browser_tokenless");
        byte[] body = Jsons.stringify(Map.of(
                "label", "Chrome@Windows",
                "algo", "ed25519",
                "pubJwk", browser.pubJwk)).getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = sendPairRequest(body, "http://localhost:5173");
        assertEquals(200, response.statusCode());
        JsonObject answer = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("pending", answer.get("status").getAsString());
        String requestId = answer.get("requestId").getAsString();
        assertFalse(requestId.isBlank());
        assertTrue(answer.get("expiresIn").getAsInt() > 0);
        assertEquals(1, service.pendingPairRequests().size());

        // Polling before the decision reads pending.
        HttpResponse<String> poll = send("GET", "/api/v1/pair-request/" + requestId,
                new byte[0], null);
        assertEquals("pending", JsonParser.parseString(poll.body()).getAsJsonObject()
                .get("status").getAsString());

        // The in-game approve registers the device and freezes the client id.
        String deviceId = service.approvePairRequest(requestId);
        assertFalse(deviceId == null || deviceId.isBlank());
        HttpResponse<String> after = send("GET", "/api/v1/pair-request/" + requestId,
                new byte[0], null);
        JsonObject decided = JsonParser.parseString(after.body()).getAsJsonObject();
        assertEquals("approved", decided.get("status").getAsString());
        assertEquals(deviceId, decided.get("clientId").getAsString());

        // Deciding twice is refused.
        assertTrue(service.approvePairRequest(requestId) == null);
        assertTrue(service.denyPairRequest(requestId) == false);
    }

    @Test
    void tokenlessPairingRejectsForeignOrigins() throws Exception {
        start(DIRECT);
        TestKeys.Device browser = TestKeys.ed25519("dev_browser_evil");
        byte[] body = Jsons.stringify(Map.of(
                "label", "Chrome@Windows",
                "algo", "ed25519",
                "pubJwk", browser.pubJwk)).getBytes(StandardCharsets.UTF_8);
        // An origin outside the allowlist.
        assertEquals(403, sendPairRequest(body, "https://evil.example").statusCode());
        // No Origin header at all (non-browser client).
        assertEquals(403, sendPairRequest(body, null).statusCode());
        // Nothing pended, and the unknown id polls as expired.
        assertEquals(0, service.pendingPairRequests().size());
    }

    @Test
    void tokenlessPairingRejectsBadBodies() throws Exception {
        start(DIRECT);
        var validKey = Map.of("kty", "OKP", "crv", "Ed25519", "x", "aa");
        byte[] incomplete = "{\"label\":\"x\"}".getBytes(StandardCharsets.UTF_8);
        assertEquals(400, sendPairRequest(incomplete, "http://localhost:5173").statusCode());
        byte[] badKey = Jsons.stringify(Map.of(
                "label", "x", "algo", "ed25519", "pubJwk", Map.of("kty", "oct")))
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(400, sendPairRequest(badKey, "http://localhost:5173").statusCode());
    }

    @Test
    void loopbackTrustAnswersTokenlessPairingImmediately() throws Exception {
        start(DIRECT);
        service.setTrustLoopback(true);
        TestKeys.Device browser = TestKeys.ed25519("dev_browser_trusted");
        byte[] body = Jsons.stringify(Map.of(
                "label", "Chrome@Windows",
                "algo", "ed25519",
                "pubJwk", browser.pubJwk)).getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = sendPairRequest(body, "http://localhost:5173");
        assertEquals(200, response.statusCode());
        JsonObject answer = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("paired", answer.get("status").getAsString());
        String clientId = answer.get("clientId").getAsString();
        assertFalse(clientId.isBlank());

        // The trusted pairing never asked the game: no pending requests.
        assertEquals(0, service.pendingPairRequests().size());

        // The device signs immediately, like a token pairing would.
        String authorization = browser.withId(clientId).authorize("GET", "/api/v1/state",
                "", new byte[0], System.currentTimeMillis() / 1000, nonce());
        assertEquals(200, send("GET", "/api/v1/state", new byte[0], authorization)
                .statusCode());
    }

    // ---- files --------------------------------------------------------------

    @Test
    void filesGetServesRawTextWithAnEtag() throws Exception {
        StubBackend backend = start(DIRECT);
        byte[] content = "chat.log('hello');".getBytes(StandardCharsets.UTF_8);
        backend.fileToServe = new FileContent(content,
                ink.tuanzi.neomacros.fabric.web.auth.Nm1Verifier.bodyHash(content));
        HttpResponse<String> response = signed("GET",
                "/api/v1/files/event%2Fauto-welcome%2Findex.js", new byte[0]);
        assertEquals(200, response.statusCode());
        assertEquals("chat.log('hello');", response.body());
        assertEquals("text/plain; charset=utf-8",
                response.headers().firstValue("Content-Type").orElse(""));
        assertEquals(backend.fileToServe.etag(),
                response.headers().firstValue("ETag").orElse(""));

        assertEquals(404, signed("GET", "/api/v1/files/event%2Fmissing.js",
                new byte[0]).statusCode());
        assertEquals(401, send("GET", "/api/v1/files/event%2Fauto-welcome%2Findex.js",
                new byte[0], null).statusCode());
        // Separators smuggled through percent-encoding are decoded before
        // the handler sees them, so the backend-side sandbox faces plain
        // dot-dot segments (rejected by the WebRuntimeFilesTest matrix).
        assertEquals(404, signed("GET", "/api/v1/files/..%2F..%2Fescape.js",
                new byte[0]).statusCode());
        assertTrue(backend.reads.contains("../../escape.js"),
                "decoded traversal reached the backend: " + backend.reads);
    }

    @Test
    void filesPutSavesAndReportsTheReloadError() throws Exception {
        StubBackend backend = start(DIRECT);
        HttpResponse<String> clean = signed("PUT",
                "/api/v1/files/event%2Fauto-welcome%2Findex.js",
                "chat.log('v2');".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, clean.statusCode());
        assertEquals(JsonNull.INSTANCE, JsonParser.parseString(clean.body())
                .getAsJsonObject().get("error"));
        assertEquals(List.of("event/auto-welcome/index.js"), backend.writes);

        backend.writeOutcome = FileWriteResult.saved(
                new ink.tuanzi.neomacros.fabric.web.v1.LocatedError(
                        "event/auto-welcome/index.js", 3, 7, "unexpected token"));
        HttpResponse<String> broken = signed("PUT",
                "/api/v1/files/event%2Fauto-welcome%2Findex.js",
                "!!!".getBytes(StandardCharsets.UTF_8));
        assertEquals(200, broken.statusCode());
        JsonObject error = JsonParser.parseString(broken.body())
                .getAsJsonObject().getAsJsonObject("error");
        assertEquals(3, error.get("line").getAsInt());
        assertEquals("unexpected token", error.get("message").getAsString());

        assertEquals(401, send("PUT", "/api/v1/files/event%2Fauto-welcome%2Findex.js",
                "x".getBytes(StandardCharsets.UTF_8), null).statusCode());
    }

    @Test
    void filesPutConflictCarriesTheCurrentText() throws Exception {
        StubBackend backend = start(DIRECT);
        backend.writeOutcome = FileWriteResult.conflict("chat.log('disk');", "hash-of-disk");
        HttpResponse<String> response = signed("PUT",
                "/api/v1/files/event%2Fauto-welcome%2Findex.js",
                "chat.log('stale');".getBytes(StandardCharsets.UTF_8));
        assertEquals(409, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("conflict", body.getAsJsonObject("error").get("code").getAsString());
        assertEquals("chat.log('disk');", body.get("content").getAsString());
        assertEquals("hash-of-disk", body.get("etag").getAsString());
    }

    // ---- macro create / delete ---------------------------------------------

    @Test
    void macroCreateServesTheScaffoldView() throws Exception {
        start(DIRECT);
        byte[] body = Jsons.stringify(Map.of("category", "event", "name", "shop-reporter"))
                .getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = signed("POST", "/api/v1/macros", body);
        assertEquals(200, response.statusCode());
        JsonObject view = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("event/shop-reporter", view.get("id").getAsString());
        assertEquals("0.0.1", view.get("version").getAsString());
    }

    @Test
    void macroDeleteNeedsExplicitConfirmation() throws Exception {
        StubBackend backend = start(DIRECT);
        String path = "/api/v1/macros/event%2Fauto-welcome";
        assertEquals(422, signed("DELETE", path, new byte[0]).statusCode());
        assertEquals(422, signed("DELETE", path,
                "{\"confirm\":false}".getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals(422, signed("DELETE", path,
                "{not json".getBytes(StandardCharsets.UTF_8)).statusCode());
        HttpResponse<String> deleted = signed("DELETE", path,
                "{\"confirm\":true}".getBytes(StandardCharsets.UTF_8));
        assertEquals(204, deleted.statusCode());
        assertEquals(List.of("event/auto-welcome"), backend.deletions);
    }

    // ---- macro file listing --------------------------------------------------

    @Test
    void macroFilesListingServesRelativePaths() throws Exception {
        start(DIRECT);
        HttpResponse<String> response = signed("GET",
                "/api/v1/macros/event%2Fauto-welcome/files", new byte[0]);
        assertEquals(200, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        var files = body.getAsJsonArray("files");
        assertEquals(3, files.size());
        assertEquals("neomacro.yml", files.get(2).getAsString());
        assertEquals("lib/util.mjs", files.get(1).getAsString());
        var dirs = body.getAsJsonArray("dirs");
        assertEquals(2, dirs.size());
        assertEquals("lib", dirs.get(0).getAsString());
        assertEquals("lib/deep", dirs.get(1).getAsString());

        assertEquals(404, signed("GET", "/api/v1/macros/nope%2Fmissing/files",
                new byte[0]).statusCode());
        assertEquals(401, send("GET", "/api/v1/macros/event%2Fauto-welcome/files",
                new byte[0], null).statusCode());
    }

    @Test
    void fileTreeMutationsReachTheBackend() throws Exception {
        StubBackend backend = start(DIRECT);

        // Delete needs the explicit confirm body, then reaches the backend.
        String doomed = "/api/v1/files/event%2Fauto-welcome%2Flib";
        assertEquals(422, signed("DELETE", doomed, new byte[0]).statusCode());
        assertEquals(422, signed("DELETE", doomed,
                "{\"confirm\":false}".getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals(204, signed("DELETE", doomed,
                "{\"confirm\":true}".getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals(List.of("event/auto-welcome/lib"), backend.fileDeletions);

        // Move and mkdir carry their operands as JSON.
        byte[] move = Jsons.stringify(Map.of("from", "event/auto-welcome/index.js",
                "to", "event/auto-welcome/main.js")).getBytes(StandardCharsets.UTF_8);
        assertEquals(204, signed("POST", "/api/v1/files/move", move).statusCode());
        byte[] mkdir = Jsons.stringify(Map.of("path", "event/auto-welcome/lib/nested"))
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(204, signed("POST", "/api/v1/files/mkdir", mkdir).statusCode());
        assertEquals(List.of("event/auto-welcome/index.js->event/auto-welcome/main.js"),
                backend.moves);
        assertEquals(List.of("event/auto-welcome/lib/nested"), backend.mkdirs);

        // Missing operands are unprocessable; unsigned requests never pass.
        assertEquals(422, signed("POST", "/api/v1/files/move",
                "{\"from\":\"a\"}".getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals(422, signed("POST", "/api/v1/files/mkdir",
                "{}".getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals(401, send("DELETE", doomed,
                "{\"confirm\":true}".getBytes(StandardCharsets.UTF_8), null).statusCode());
        assertEquals(401, send("POST", "/api/v1/files/move", move, null).statusCode());
    }

    @Test
    void revealReachesTheBackendAndValidates() throws Exception {
        StubBackend backend = start(DIRECT);
        byte[] body = Jsons.stringify(Map.of("path", "event/auto-welcome"))
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(204, signed("POST", "/api/v1/files/reveal", body).statusCode());
        assertEquals(List.of("event/auto-welcome"), backend.reveals);
        assertEquals(422, signed("POST", "/api/v1/files/reveal",
                "{}".getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals(401, send("POST", "/api/v1/files/reveal", body, null).statusCode());
    }

    @Test
    void macroMoveServesTheNewView() throws Exception {
        StubBackend backend = start(DIRECT);
        byte[] body = Jsons.stringify(Map.of("from", "keypress/shop-reporter",
                "to", "services")).getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = signed("POST", "/api/v1/macros/move", body);
        assertEquals(200, response.statusCode());
        JsonObject view = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("services/shop-reporter", view.get("id").getAsString());
        assertEquals(List.of("keypress/shop-reporter->services"), backend.macroMoves);
        assertEquals(422, signed("POST", "/api/v1/macros/move",
                "{\"to\":\"services\"}".getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals(401, send("POST", "/api/v1/macros/move", body, null).statusCode());
    }

    @Test
    void stateCarriesTheMacrosRoot() throws Exception {
        start(DIRECT);
        HttpResponse<String> response = signed("GET", "/api/v1/state", new byte[0]);
        assertEquals(200, response.statusCode());
        JsonObject state = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("C:\\macros", state.get("macrosRoot").getAsString());
    }

    // ---- devices ------------------------------------------------------------

    @Test
    void devicesAreListedAndRevoked() throws Exception {
        start(DIRECT);
        HttpResponse<String> list = signed("GET", "/api/v1/devices", new byte[0]);
        assertEquals(200, list.statusCode());
        JsonObject body = JsonParser.parseString(list.body()).getAsJsonObject();
        var devices = body.getAsJsonArray("devices");
        assertEquals(1, devices.size());
        JsonObject view = devices.get(0).getAsJsonObject();
        assertEquals(device.id, view.get("id").getAsString());
        assertEquals("ed25519", view.get("algo").getAsString());
        assertFalse(view.has("pubJwk"), "no key material in the panel view");

        assertEquals(204, signed("DELETE", "/api/v1/devices/" + device.id,
                new byte[0]).statusCode());
        // The revoked device cannot even ask: NM1 rejects before the route.
        assertEquals(401, signed("DELETE", "/api/v1/devices/" + device.id,
                new byte[0]).statusCode());
        assertEquals(401, signed("GET", "/api/v1/state", new byte[0]).statusCode());
    }

    // ---- api-docs ----------------------------------------------------------

    @Test
    void theContractServesUnsignedAndCoversW1() throws Exception {
        start(DIRECT);
        HttpResponse<String> response = send("GET", "/api-docs/openapi.json",
                new byte[0], null);
        assertEquals(200, response.statusCode());
        JsonObject doc = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("3.0.3", doc.get("openapi").getAsString());
        JsonObject paths = doc.getAsJsonObject("paths");
        for (String path : new String[] {"/api/v1/meta", "/api/v1/pair", "/api/v1/state",
                "/api/v1/macros", "/api/v1/macros/{id}/{action}", "/api/v1/macros/{id}",
                "/api/v1/macros/move", "/api/v1/macros/{id}/files",
                "/api/v1/files/{path}", "/api/v1/files/move", "/api/v1/files/mkdir",
                "/api/v1/files/reveal",
                "/api/v1/devices", "/api/v1/devices/{id}",
                "/api/v1/logs", "/api/v1/events/ticket", "/api/v1/events/catalog",
                "/api/v1/events", "/api/v1/pair-request", "/api/v1/pair-request/{id}",
                "/api-docs/openapi.json"}) {
            assertTrue(paths.has(path), "missing path " + path);
        }
        JsonObject filesGet = paths.getAsJsonObject("/api/v1/files/{path}")
                .getAsJsonObject("get");
        assertTrue(filesGet.getAsJsonObject("responses").getAsJsonObject("200")
                .getAsJsonObject("content").has("text/plain; charset=utf-8"));
        assertTrue(paths.getAsJsonObject("/api/v1/files/{path}")
                .getAsJsonObject("put").has("requestBody"));
        assertTrue(paths.getAsJsonObject("/api/v1/files/{path}")
                .getAsJsonObject("delete").getAsJsonObject("responses").has("204"));
        assertTrue(paths.getAsJsonObject("/api/v1/files/move")
                .getAsJsonObject("post").has("requestBody"));
        assertTrue(paths.getAsJsonObject("/api/v1/files/mkdir")
                .getAsJsonObject("post").has("requestBody"));
        assertTrue(doc.getAsJsonObject("components").getAsJsonObject("schemas")
                .has("StateSnapshot"));
        assertTrue(doc.getAsJsonArray("servers").get(0).getAsJsonObject().get("url")
                .getAsString().contains(String.valueOf(service.port())));
    }

    @Test
    void configuredExtraOriginsAreEchoedToo() throws Exception {
        startWithOrigins(DIRECT, java.util.List.of("https://mirror.example"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/state"))
                .header("Origin", "https://mirror.example")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> preflight = client.send(request,
                HttpResponse.BodyHandlers.ofString());
        assertEquals("https://mirror.example", preflight.headers()
                .firstValue("Access-Control-Allow-Origin").orElse(""));

        HttpRequest stranger = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/state"))
                .header("Origin", "https://still-evil.example")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> denied = client.send(stranger,
                HttpResponse.BodyHandlers.ofString());
        assertTrue(denied.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void wildcardOriginsEchoAnyOriginAsStar() throws Exception {
        startWithOrigins(DIRECT, java.util.List.of("*"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/state"))
                .header("Origin", "https://anywhere.example")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> preflight = client.send(request,
                HttpResponse.BodyHandlers.ofString());
        assertEquals("*", preflight.headers()
                .firstValue("Access-Control-Allow-Origin").orElse(""));
    }

    @Test
    void theListenLiteralIsHonored() throws Exception {
        StubBackend backend = new StubBackend();
        device = TestKeys.ed25519("dev_test");
        DeviceRegistry registry = new DeviceRegistry(temp.resolve("web-devices.json"),
                message -> { });
        device = device.withId(registry
                .register("integration browser", device.algo, device.pubJwk).id());
        service = new WebService(backend, DIRECT, registry, newPairingStore(),
                new ink.tuanzi.neomacros.fabric.web.pair.PairingRequests(System::currentTimeMillis),
                () -> "127.0.0.1", () -> 0, () -> "http://localhost:5173",
                () -> java.util.List.of("*"), new ink.tuanzi.neomacros.fabric.web.sse.PanelEvents());
        service.start();
        HttpResponse<String> response = send("GET", "/api/v1/meta", new byte[0], null);
        assertEquals(200, response.statusCode());
    }

    // ---- body cap ----------------------------------------------------------

    @Test
    void oversizedBodiesAreRejected() throws Exception {
        start(DIRECT);
        byte[] huge = new byte[1024 * 1024 + 1];
        HttpResponse<String> response = send("POST", "/api/v1/pair", huge, null);
        assertEquals(413, response.statusCode());
    }

    /** A direct GameRunner: the tests run their backend calls inline. */
    private static final GameRunner DIRECT = new GameRunner() {
        @Override
        public <T> T call(java.util.function.Supplier<T> task) {
            return task.get();
        }
    };
}
