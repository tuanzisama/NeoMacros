package ink.tuanzi.neomacros.core.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S1v2 module expansions: fs JSON/bytes/stat/move/copy, net fetch with
 * the live Response object, and the typed command argument DSL.
 */
class CoreModuleExpansionsTest {

    @TempDir
    Path dir;

    private HttpServer server;

    private static <T> T join(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }

    private static FsApi fs(Path root) {
        return new FsApi(path -> root.resolve(path).normalize(),
                java.util.concurrent.Executors.newSingleThreadExecutor());
    }

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/json", exchange -> {
            byte[] body = "{\"ok\":true,\"n\":7}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Test", "yes");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/text", exchange -> {
            byte[] body = "plain".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/bad", exchange -> {
            byte[] body = "not-json{".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void fsJsonBytesStatMoveCopyRoundTrip(@TempDir Path root) throws Exception {
        FsApi api = fs(root);
        join(api.writeJSON("cfg/settings.json", Map.of("enabled", true, "level", 3)));
        @SuppressWarnings("unchecked")
        Map<String, Object> readBack = (Map<String, Object>) join(api.readJSON("cfg/settings.json"));
        assertEquals(Boolean.TRUE, readBack.get("enabled"));
        assertEquals(3L, readBack.get("level"));

        join(api.writeBytes("data/blob.bin", List.of(1, 2, 254)));
        byte[] bytes = join(api.readBytes("data/blob.bin"));
        assertEquals(3, bytes.length);
        assertEquals(254, bytes[2] & 0xFF);

        @SuppressWarnings("unchecked")
        Map<String, Object> stat = (Map<String, Object>) join(api.stat("data/blob.bin"));
        assertEquals(3L, stat.get("size"));
        assertEquals(Boolean.TRUE, stat.get("isFile"));
        assertEquals(Boolean.FALSE, stat.get("isDir"));

        join(api.copy("data/blob.bin", "data/blob-copy.bin"));
        assertTrue((Boolean) join(api.exists("data/blob-copy.bin")));
        join(api.move("data/blob-copy.bin", "data/moved.bin"));
        assertFalse((Boolean) join(api.exists("data/blob-copy.bin")));
        assertTrue((Boolean) join(api.exists("data/moved.bin")));
    }

    @Test
    void netFetchCarriesStatusAndBodyReaders() throws Exception {
        var api = new NetApi(java.util.concurrent.Executors.newSingleThreadExecutor());
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        NetApi.Response json = join(api.get(base + "/json"));
        assertEquals(200, json.getStatus());
        assertTrue(json.getOk(), "2xx counts as ok");
        assertEquals("yes", json.getHeaders().get("x-test"));
        Map<String, Object> parsed = (Map<String, Object>) join(json.json());
        assertEquals(Boolean.TRUE, parsed.get("ok"));
        assertEquals(7L, parsed.get("n"));

        NetApi.Response text = join(api.fetch(base + "/text",
                Map.of("method", "GET")));
        assertEquals(201, text.getStatus());
        assertEquals("plain", join(text.text()));
        byte[] bytes = join(text.bytes());
        assertEquals(5, bytes.length);

        NetApi.Response bad = join(api.post(base + "/bad", "x"));
        var failed = new Object() { Throwable error; };
        ((CompletableFuture<?>) bad.json()).whenComplete((value, error) -> failed.error = error);
        assertTrue(failed.error != null, "non-JSON body rejects the json reader");
    }

    @Test
    void commandArgsParseTypedSlots() {
        Map<String, CommandArg> slots = new LinkedHashMap<>();
        slots.put("target", CommandArg.of("player"));
        slots.put("times", CommandArg.of("int").defaultTo(1));
        slots.put("tag", CommandArg.of("word").optional());
        slots.put("rest", CommandArg.of("greedy"));

        Map<String, Object> parsed = CommandArg.parseAll(slots, "tester 3 extra words here");
        assertEquals("tester", parsed.get("target"));
        assertEquals(3, parsed.get("times"));
        assertEquals("extra", parsed.get("tag"));
        assertEquals("words here", parsed.get("rest"));

        Map<String, Object> minimal = CommandArg.parseAll(slots, "tester");
        assertEquals(1, minimal.get("times"), "default applies when the slot is absent");
        assertFalse(minimal.containsKey("tag") && minimal.get("tag") != null,
                "optional slot without default stays absent");

        var failed = new Object() { Throwable error; };
        try {
            CommandArg.parseAll(slots, "3.5");
            // target consumes "3.5"; times has no input -> default; fine
        } catch (Throwable error) {
            failed.error = error;
        }
        assertTrue(failed.error == null, "partial input falls back per slot");
    }

    @Test
    void commandRegisterWrapsHandlerWithTypedContext() throws Exception {
        var captured = new Object() {
            Map<String, Object> spec;
            Object delivered;
        };
        CommandRegistry registry = new CommandRegistry() {
            @Override
            public Object register(Map<String, Object> spec) {
                captured.spec = spec;
                return "handle";
            }

            @Override
            public void unregister(String name) {
            }

            @Override
            public CompletableFuture<Boolean> execute(String commandLine) {
                return CompletableFuture.completedFuture(true);
            }
        };
        var api = new CommandApi(registry);
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("target", CommandArg.of("player"));
        slots.put("times", CommandArg.of("int").defaultTo(2));
        api.register(new LinkedHashMap<>(Map.of(
                "name", "greet",
                "args", slots,
                "handler", (java.util.function.Consumer<Object[]>) args ->
                        captured.delivered = args[0])));

        @SuppressWarnings("unchecked")
        java.util.function.Consumer<Object[]> wrapped =
                (java.util.function.Consumer<Object[]>) captured.spec.get("handler");
        wrapped.accept(new Object[]{"tester 4"});

        CommandContext context = (CommandContext) captured.delivered;
        assertEquals(4, context.getArgs().get("times"));
        assertEquals("tester", context.getArgs().get("target"));
        assertEquals("tester 4", context.getRaw());
    }
}
