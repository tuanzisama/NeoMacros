package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.fabric.web.auth.DeviceRegistry;
import ink.tuanzi.neomacros.fabric.web.auth.NonceStore;
import ink.tuanzi.neomacros.fabric.web.http.ApiRoute;
import ink.tuanzi.neomacros.fabric.web.http.CorsPolicy;
import ink.tuanzi.neomacros.fabric.web.http.HostPolicy;
import ink.tuanzi.neomacros.fabric.web.log.LogRing;
import ink.tuanzi.neomacros.fabric.web.pair.PairingRequests;
import ink.tuanzi.neomacros.fabric.web.pair.PairingService;
import ink.tuanzi.neomacros.fabric.web.sse.SseHub;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit coverage for the small support pieces behind the web panel. */
class SupportTest {

    @TempDir
    Path temp;

    // ---- NonceStore -------------------------------------------------------

    @Test
    void nonceIsSingleUsePerDevice() {
        NonceStore store = new NonceStore(60_000);
        assertTrue(store.accept("dev_a", "n1"));
        assertFalse(store.accept("dev_a", "n1"));
        assertTrue(store.accept("dev_b", "n1"));
    }

    @Test
    void nonceExpiresAfterTtl() {
        AtomicLong now = new AtomicLong(0);
        NonceStore store = new NonceStore(1_000, now::get);
        assertTrue(store.accept("dev_a", "n1"));
        now.set(900);
        assertFalse(store.accept("dev_a", "n1"));
        now.set(1_500);
        assertTrue(store.accept("dev_a", "n1"));
    }

    // ---- BackoffTracker ---------------------------------------------------

    // ---- PairingService ---------------------------------------------------

    @Test
    void tokenIsSingleUseOneToOneAndExpiring() {
        AtomicLong now = new AtomicLong(0);
        PairingService pairing = new PairingService(temp.resolve("web-pairing.json"),
                now::get, () -> 180_000, m -> { });
        String token = pairing.newToken();
        assertTrue(pairing.validate(token));
        assertTrue(pairing.validate(token));
        pairing.consume(token);
        assertFalse(pairing.validate(token), "single use: consumed on redemption");

        String second = pairing.newToken();
        assertFalse(pairing.validate(token), "one-to-one: a new token kills the old");
        assertTrue(pairing.validate(second));
    }

    @Test
    void tokenExpiresAfterThreeMinutes() {
        AtomicLong now = new AtomicLong(0);
        PairingService pairing = new PairingService(temp.resolve("web-pairing.json"),
                now::get, () -> 180_000, m -> { });
        String token = pairing.newToken();
        now.set(179_999);
        assertTrue(pairing.validate(token));
        now.set(180_000);
        assertFalse(pairing.validate(token));
    }

    @Test
    void newTokenReplacesTheLiveOne() {
        PairingService pairing = new PairingService(temp.resolve("web-pairing.json"),
                System::currentTimeMillis, () -> 180_000, m -> { });
        String first = pairing.newToken();
        String second = pairing.newToken();
        assertFalse(pairing.validate(first));
        assertTrue(pairing.validate(second));
    }

    @Test
    void pairingStateSurvivesARestart() {
        AtomicLong now = new AtomicLong(1_000_000);
        PairingService pairing = new PairingService(temp.resolve("web-pairing.json"),
                now::get, () -> 60_000, m -> { });
        String token = pairing.newToken();
        pairing.setTrustLoopback(true);

        PairingService reloaded = new PairingService(temp.resolve("web-pairing.json"),
                now::get, () -> 60_000, m -> { });
        assertTrue(reloaded.validate(token), "token persists across restarts");
        assertTrue(reloaded.trustLoopback(), "loopback trust persists");
        assertTrue(Files.exists(temp.resolve("web-pairing.json.bak")));
    }

    @Test
    void consumedTokenStaysBurnedAfterRestart() {
        AtomicLong now = new AtomicLong(0);
        PairingService pairing = new PairingService(temp.resolve("web-pairing.json"),
                now::get, () -> 60_000, m -> { });
        String token = pairing.newToken();
        pairing.consume(token);

        PairingService reloaded = new PairingService(temp.resolve("web-pairing.json"),
                now::get, () -> 60_000, m -> { });
        assertFalse(reloaded.validate(token), "consumption persists");
    }

    @Test
    void ttlSupplierAppliesAtMintTime() {
        AtomicLong now = new AtomicLong(0);
        AtomicLong ttl = new AtomicLong(180_000);
        PairingService pairing = new PairingService(temp.resolve("web-pairing.json"),
                now::get, ttl::get, m -> { });
        String token = pairing.newToken();
        now.set(179_999);
        assertTrue(pairing.validate(token));

        ttl.set(60_000);
        String second = pairing.newToken();
        now.set(179_999 + 1);
        assertFalse(pairing.validate(token), "old token superseded anyway");
        now.set(179_999 + 59_999);
        assertTrue(pairing.validate(second), "second token minted with the new ttl");
        now.set(179_999 + 60_000);
        assertFalse(pairing.validate(second));
    }

    @Test
    void corruptPairingFileIsQuarantined() {
        Path file = temp.resolve("web-pairing.json");
        PairingService pairing = new PairingService(file,
                System::currentTimeMillis, () -> 180_000, m -> { });
        pairing.newToken();
        try {
            Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        PairingService recovered = new PairingService(file,
                System::currentTimeMillis, () -> 180_000, m -> { });
        assertFalse(recovered.validate("anything"), "quarantined state starts empty");
        assertFalse(recovered.trustLoopback());
        assertTrue(Files.exists(file.resolveSibling("web-pairing.json.corrupted")));
    }

    // ---- PairingRequests ---------------------------------------------------

    @Test
    void pairRequestLifecycleGoesThroughPendingAndDecisions() {
        AtomicLong now = new AtomicLong(0);
        PairingRequests requests = new PairingRequests(now::get);
        var submitted = requests.submit("Chrome@Windows", "ed25519",
                Map.of("kty", "OKP"), "http://localhost:5173", "/127.0.0.1");
        assertEquals(1, requests.pending().size());
        assertEquals(PairingRequests.Outcome.PENDING, requests.outcomeOf(submitted.id()));

        // Deny wins first; a later approve cannot resurrect the request.
        assertTrue(requests.deny(submitted.id()));
        assertEquals(PairingRequests.Outcome.DENIED, requests.outcomeOf(submitted.id()));
        assertFalse(requests.approve(submitted.id(), "dev_x"));

        // A fresh request approves with the frozen client id.
        var second = requests.submit("Firefox", "ecdsa-p256",
                Map.of("kty", "EC"), "http://localhost:5173", "/127.0.0.1");
        assertTrue(requests.approve(second.id(), "dev_y"));
        assertEquals("dev_y", requests.clientIdOf(second.id()));
        assertEquals(PairingRequests.Outcome.APPROVED, requests.outcomeOf(second.id()));
    }

    @Test
    void pairRequestsExpireLazilyAndKeepTheirOutcomeForLatePolls() {
        AtomicLong now = new AtomicLong(0);
        PairingRequests requests = new PairingRequests(now::get);
        var expired = requests.submit("Chrome", "ed25519", Map.of("kty", "OKP"),
                "http://localhost:5173", "/127.0.0.1");
        var approved = requests.submit("Firefox", "ed25519", Map.of("kty", "OKP"),
                "http://localhost:5173", "/127.0.0.1");
        requests.approve(approved.id(), "dev_z");

        now.set(PairingRequests.REQUEST_TTL_MILLIS);
        assertEquals(PairingRequests.Outcome.EXPIRED, requests.outcomeOf(expired.id()));
        assertEquals(0, requests.pending().size(), "expired pending drops out");
        // The decided entry outlives its request window for late polls.
        assertEquals(PairingRequests.Outcome.APPROVED, requests.outcomeOf(approved.id()));
        assertEquals("dev_z", requests.clientIdOf(approved.id()));
        // An unknown id reads as null (the endpoint maps it to expired).
        assertNull(requests.outcomeOf("nope"));
    }

    // ---- DeviceRegistry ---------------------------------------------------

    @Test
    void devicesRoundTripThroughTheFile() {
        Path file = temp.resolve("web-devices.json");
        DeviceRegistry registry = new DeviceRegistry(file, m -> { });
        var created = registry.register("Chrome on Windows", "ed25519",
                Map.of("kty", "OKP", "crv", "Ed25519", "x", "aa"));
        registry.revoke(created.id());
        var second = registry.register("Firefox", "ecdsa-p256",
                Map.of("kty", "EC", "crv", "P-256", "x", "aa", "y", "bb"));
        registry.touch(second.id());

        DeviceRegistry reloaded = new DeviceRegistry(file, m -> { });
        assertEquals(1, reloaded.all().size());
        assertEquals(second.id(), reloaded.all().get(0).id());
        assertEquals("Firefox", reloaded.all().get(0).label());
        assertTrue(Files.exists(file.resolveSibling("web-devices.json.bak")));
    }

    @Test
    void corruptDeviceFileIsQuarantined() {
        Path file = temp.resolve("web-devices.json");
        DeviceRegistry registry = new DeviceRegistry(file, m -> { });
        registry.register("Chrome", "ed25519", Map.of("kty", "OKP"));
        String corrupt = "{ this is not json";
        try {
            Files.writeString(file, corrupt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        DeviceRegistry recovered = new DeviceRegistry(file, m -> { });
        assertEquals(0, recovered.all().size());
        assertTrue(Files.exists(file.resolveSibling("web-devices.json.corrupted")));
    }

    // ---- LogRing ----------------------------------------------------------

    @Test
    void logRingReplaysIncrementallyAndWraps() {
        LogRing ring = new LogRing(1000);
        for (int i = 1; i <= 1100; i++) {
            ring.append(i * 1000L, "INFO", "neomacros.test", "line " + i);
        }
        assertEquals(1000, ring.after(0).size());
        assertEquals(1100, ring.lastSeq());
        assertEquals("line 101", ring.after(0).get(0).line());
        List<LogRing.Entry> tail = ring.after(1098);
        assertEquals(2, tail.size());
        assertEquals(1099, tail.get(0).seq());
        assertEquals("line 1099", tail.get(0).line());
        assertEquals("line 1100", tail.get(1).line());
    }

    // ---- SseHub -----------------------------------------------------------

    @Test
    void sseTicketIsSingleUseAndExpiring() throws Exception {
        AtomicLong now = new AtomicLong(0);
        SseHub hub = new SseHub(now::get);
        String ticket = hub.issueTicket();
        assertTrue(hub.consumeTicket(ticket));
        assertFalse(hub.consumeTicket(ticket));

        String ephemeral = hub.issueTicket();
        now.set(SseHub.TICKET_TTL_MILLIS + 1);
        assertFalse(hub.consumeTicket(ephemeral));
    }

    @Test
    void sseBroadcastReachesTheConnectionAndDropsDeadOnes() throws Exception {
        SseHub hub = new SseHub(System::currentTimeMillis);
        var out = new ByteArrayOutputStream();
        var connection = hub.open(out, "127.0.0.1");
        hub.broadcast("log", "{\"seq\":1}");
        hub.broadcast("log", "{\"seq\":2}");
        String frames = out.toString(StandardCharsets.UTF_8);
        assertTrue(frames.startsWith("retry: 3000"));
        assertTrue(frames.contains("event: log\ndata: {\"seq\":1}"));
        assertTrue(frames.contains("event: log\ndata: {\"seq\":2}"));
        assertEquals(1, hub.connectionCount());
        connection.close();
        // The first broadcast after close observes the failed write and drops
        // the connection.
        hub.broadcast("log", "{}");
        assertEquals(0, hub.connectionCount());
    }

    // ---- HostPolicy -------------------------------------------------------

    @Test
    void hostPolicyAcceptsOnlyLoopbackForms() {
        HostPolicy policy = HostPolicy.loopback(52841);
        assertTrue(policy.allows("127.0.0.1:52841"));
        assertTrue(policy.allows("localhost:52841"));
        assertTrue(policy.allows("[::1]:52841"));
        assertFalse(policy.allows("evil.example:52841"));
        assertFalse(policy.allows("127.0.0.1:9999"));
        assertFalse(policy.allows(null));
        assertFalse(policy.allows(""));
    }

    // ---- CorsPolicy -------------------------------------------------------

    @Test
    void corsEchoesOnlyTheConfiguredOrigins() {
        assertEquals(java.util.Set.of("https://panel.neomacros.app"),
                new CorsPolicy(java.util.List.of("https://panel.neomacros.app")).allowedOrigins());
        assertEquals(java.util.Set.of("http://localhost:5173"),
                new CorsPolicy(java.util.List.of("http://localhost:5173/")).allowedOrigins());
        assertEquals(java.util.Set.of("https://panel.neomacros.app"),
                new CorsPolicy(java.util.List.of("https://panel.neomacros.app:443")).allowedOrigins());
        assertEquals(java.util.Set.of("https://panel.example", "http://localhost:5173"),
                new CorsPolicy(java.util.List.of("https://panel.example",
                        "http://localhost:5173", "not a url", "")).allowedOrigins());
        assertEquals(java.util.Set.of(), new CorsPolicy(java.util.List.of()).allowedOrigins());
        // The * entry switches the policy to wildcard mode (any origin echoes *).
        assertTrue(new CorsPolicy(java.util.List.of("*")).wildcard());
        assertTrue(new CorsPolicy(java.util.List.of("*", "https://panel.example")).wildcard());
        assertFalse(new CorsPolicy(java.util.List.of("https://panel.example")).wildcard());
    }

    // ---- ApiRoute matching ------------------------------------------------

    static final class Probe {
        @ink.tuanzi.neomacros.fabric.web.api.NmGet("/api/v1/macros/{id+}/{action}")
        @ink.tuanzi.neomacros.fabric.web.api.NmApi(tag = "macros", summary = "probe")
        public String action(String id, String action) {
            return id + "|" + action;
        }

        @ink.tuanzi.neomacros.fabric.web.api.NmGet("/api/v1/logs")
        @ink.tuanzi.neomacros.fabric.web.api.NmApi(tag = "logs", summary = "probe")
        public String logs() {
            return "logs";
        }
    }

    @Test
    void multiSegmentParameterGreedyMatch() throws Exception {
        List<ApiRoute> routes = ApiRoute.scan(new Probe());
        ApiRoute action = routes.get(0).template().contains("macros")
                ? routes.get(0) : routes.get(1);
        var bindings = action.match("GET",
                List.of("api", "v1", "macros", "event", "auto-welcome", "reload"));
        assertEquals("event/auto-welcome", bindings.pathParams().get("id"));
        assertEquals("reload", bindings.pathParams().get("action"));
        // The plain route must not swallow the parameterized one's path.
        ApiRoute logs = routes.get(0).template().contains("logs")
                ? routes.get(0) : routes.get(1);
        assertNull(logs.match("GET",
                List.of("api", "v1", "macros", "event", "x", "reload")));
        assertEquals(0, logs.match("GET", List.of("api", "v1", "logs"))
                .pathParams().size());
        // Method mismatch never matches.
        assertNull(action.match("DELETE",
                List.of("api", "v1", "macros", "event", "x", "reload")));
        // Too few segments for the multi parameter leaves nothing to bind.
        assertNull(action.match("GET", List.of("api", "v1", "macros", "reload")));
    }
}
