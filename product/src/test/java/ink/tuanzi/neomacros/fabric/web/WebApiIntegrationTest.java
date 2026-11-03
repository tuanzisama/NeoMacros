package ink.tuanzi.neomacros.fabric.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

        @Override
        public Meta meta() {
            return new Meta(List.of(1), "0.1.0-test", "26.3");
        }

        @Override
        public StateSnapshot snapshot() {
            var macro = new MacroView("event/auto-welcome", ink.tuanzi.neomacros.fabric.web.v1
                    .MacroCategory.event, "auto-welcome", "1.0.0", "you",
                    ink.tuanzi.neomacros.fabric.web.v1.MacroLifecycle.world,
                    true, false, null, List.of(), null);
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
                    true, true, null, List.of(), null);
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
                    true, false, null, List.of(), null);
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
                    true, false, null, List.of(), null);
        }

        @Override
        public void deleteMacro(String id) {
            if (failure != null) {
                throw failure;
            }
            deletions.add(id);
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
        device = TestKeys.ed25519("dev_test");
        DeviceRegistry registry = new DeviceRegistry(temp.resolve("web-devices.json"),
                message -> { });
        device = device.withId(registry
                .register("integration browser", device.algo, device.pubJwk).id());
        service = new WebService(backend, runner, registry,
                () -> "loopback", () -> 0, () -> "http://localhost:5173",
                () -> extraOrigins, new ink.tuanzi.neomacros.fabric.web.sse.PanelEvents());
        service.start();
        return backend;
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
        service.logs().append("INFO", "neomacros.test", "one");
        service.logs().append("WARN", "neomacros.test", "two");
        service.logs().append("ERROR", "neomacros.test", "three");

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
                "/api/v1/logs", "/api/v1/events/ticket",
                "/api/v1/events", "/api-docs/openapi.json"}) {
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
        service = new WebService(backend, DIRECT, registry,
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
