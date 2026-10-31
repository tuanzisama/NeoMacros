package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.runtime.ScriptError;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests of the GraalJS engine against real script files and
 * virtual modules: the feasibility checks behind risk item R1 (ESM specifier
 * interception, top-level await, host denial, source-position errors, frozen
 * global, promise settlement on the context loop).
 */
class GraalJsEngineTest {

    @TempDir
    Path scriptsDir;

    private ScriptThreadPool pool;
    private ScriptRuntime runtime;
    private GraalJsEngine engine;
    private GraalJsEngine.GraalJsSession session;
    private ScriptContext context;

    @BeforeEach
    void setUp() throws Exception {
        pool = new ScriptThreadPool(2);
        engine = new GraalJsEngine(testResolver());
        runtime = new ScriptRuntime(engine, null,
                new EventCatalog(), new EventBus(new EventCatalog(), Runnable::run), pool, 500);
        context = runtime.createContext("graal");
        session = (GraalJsEngine.GraalJsSession) engine.openSession(context);
    }

    @AfterEach
    void tearDown() {
        runtime.close();
        pool.close();
    }

    /** Serves the core virtual module; re-exports members of the frozen neo tree. */
    private ModuleResolver testResolver() {
        return new ModuleResolver() {
            @Override
            public Optional<String> virtualSource(String specifier) {
                if ("@neomacros/core".equals(specifier)) {
                    return Optional.of("const m = globalThis.neo.core;\n"
                            + "export const marker = m.marker;\n"
                            + "export const sleep = m.sleep;\n"
                            + "export const thread = m.thread;\n"
                            + "export const explode = m.explode;\n");
                }
                return Optional.empty();
            }

            @Override
            public Optional<Path> fileFor(String specifier) {
                return Optional.empty();
            }
        };
    }

    private Path writeModule(String fileName, String source) throws Exception {
        Path file = scriptsDir.resolve(fileName);
        Files.writeString(file, source);
        return file;
    }

    private Value sessionGlobal(String name) throws ScriptError {
        return session.global(name);
    }

    @Test
    void esmImportsAndExportsWork() throws Exception {
        writeModule("helper.mjs", "export function helper(x) { return x * 2; }\n");
        Path entry = writeModule("entry.mjs",
                "import { helper } from './helper.mjs';\n"
                + "globalThis.__result = helper(21);\n");

        session.importModule("entry", entry);
        assertEquals(42, sessionGlobal("__result").asInt());
    }

    @Test
    void virtualSpecifierResolvesThroughSandbox() throws Exception {
        session.installBindings(Map.of("core", Map.of("marker", "core-marker")));
        Path entry = writeModule("virtual-entry.mjs",
                "import { marker } from '@neomacros/core';\n"
                + "globalThis.__result = marker;\n");

        session.importModule("virtual-entry", entry);
        assertEquals("core-marker", sessionGlobal("__result").asString());
    }

    @Test
    void topLevelAwaitWorks() throws Exception {
        Path entry = writeModule("tla.mjs",
                "const v = await Promise.resolve(7);\n"
                + "globalThis.__result = v + 1;\n");

        session.importModule("tla", entry);
        assertEquals(8, sessionGlobal("__result").asInt());
    }

    @Test
    void hostAccessIsDenied() throws Exception {
        Path entry = writeModule("host.mjs",
                "const system = Java.type('java.lang.System');\n");

        ScriptError error = assertThrows(ScriptError.class, () -> session.importModule("host", entry));
        assertTrue(error.getMessage().contains("Java"),
                () -> "host denial surfaces as a script error: " + error.getMessage());
    }

    @Test
    void scriptErrorsCarryModuleNameAndMessage() throws Exception {
        Path entry = writeModule("broken.mjs",
                "throw new Error('boom');\n");

        ScriptError error = assertThrows(ScriptError.class, () -> session.importModule("broken", entry));
        assertTrue(error.getMessage().contains("broken"), () -> error.getMessage());
        assertTrue(error.getMessage().contains("boom"), () -> error.getMessage());
    }

    @Test
    void neoGlobalIsFrozenAndWriteProof() throws Exception {
        session.installBindings(Map.of("core", Map.of("marker", "core-marker")));

        Path probe = writeModule("neo-probe.mjs",
                "globalThis.__frozen = Object.isFrozen(neo);\n"
                + "globalThis.__marker = neo.core.marker;\n"
                + "try {\n"
                + "    neo = 5;\n"
                + "    globalThis.__reassign = 'silently-allowed';\n"
                + "} catch (e) {\n"
                + "    globalThis.__reassign = 'rejected';\n"
                + "}\n"
                + "try {\n"
                + "    neo.core.extra = 1;\n"
                + "    globalThis.__mutate = 'silently-allowed';\n"
                + "} catch (e) {\n"
                + "    globalThis.__mutate = 'rejected';\n"
                + "}\n");

        session.importModule("neo-probe", probe);
        assertTrue(sessionGlobal("__frozen").asBoolean());
        assertEquals("core-marker", sessionGlobal("__marker").asString());
        assertEquals("rejected", sessionGlobal("__reassign").asString(),
                "global reassignment throws in strict modules");
        assertEquals("rejected", sessionGlobal("__mutate").asString(),
                "deep-frozen tree rejects property mutation in strict modules");
    }

    @Test
    void asyncBindingsSettlePromisesOnLoopThread() throws Exception {
        java.util.function.Supplier<String> threadProbe = () -> Thread.currentThread().getName();
        session.installBindings(Map.of("core", Map.of(
                "thread", threadProbe,
                "sleep", (AsyncBinding) (args, resolve, reject) -> {
                    long millis = args.length > 0 ? args[0].asLong() : 0;
                    context.loop().schedule(() -> resolve.execute("woke"), millis);
                })));

        Path entry = writeModule("async-probe.mjs",
                "import { sleep, thread } from '@neomacros/core';\n"
                + "const message = await sleep(40);\n"
                + "globalThis.__message = message;\n"
                + "globalThis.__continuationThread = thread();\n");

        session.importModule("async-probe", entry);

        assertEquals("woke", sessionGlobal("__message").asString());
        assertEquals(context.loop().threadName(), sessionGlobal("__continuationThread").asString(),
                "promise continuation runs on the context loop thread");
    }

    @Test
    void promiseBridgeRejectsPathReportsErrors() throws Exception {
        session.installBindings(Map.of("core", Map.of(
                "explode", (AsyncBinding) (args, resolve, reject) -> {
                    // Guest Error created during promise construction (the
                    // binding runs inside the context on the loop thread);
                    // rejection lands on the loop afterwards.
                    Value error = session.newGuestErrorHere("binding failure");
                    context.loop().execute(() -> reject.execute(error));
                })));
        Path entry = writeModule("reject-probe.mjs",
                "try {\n"
                + "    await neo.core.explode();\n"
                + "    globalThis.__caught = 'not-reached';\n"
                + "} catch (e) {\n"
                + "    globalThis.__caught = String(e.message || e);\n"
                + "}\n");

        session.importModule("reject-probe", entry);
        assertEquals("binding failure", sessionGlobal("__caught").asString());
    }

    @Test
    void synchronousModuleErrorsCarryLineFromStackFrames() throws Exception {
        ScriptError error = assertThrows(ScriptError.class,
                () -> session.importSource("linecheck.mjs",
                        "// line 1\n"
                        + "// line 2\n"
                        + "throw new Error('boom on line 3');\n"));
        assertEquals(3, error.getLine(), "line extracted from the first guest stack frame");
    }

    @Test
    void crossModuleImportBetweenRealFilesSharesExports() throws Exception {
        writeModule("values.mjs", "export const shared = { hits: 0 };\n");
        Path first = writeModule("first.mjs",
                "import { shared } from './values.mjs';\n"
                + "shared.hits += 1;\n");
        Path second = writeModule("second.mjs",
                "import { shared } from './values.mjs';\n"
                + "globalThis.__hits = shared.hits;\n");

        session.importModule("first", first);
        session.importModule("second", second);
        assertEquals(1, sessionGlobal("__hits").asInt(),
                "the module instance is cached per session: second import sees one mutation");
    }

    @Test
    void clearModuleCacheIsCallableAndSessionsOutliveIt() throws Exception {
        Path entry = writeModule("plain.mjs", "globalThis.__loaded = true;\n");
        session.importModule("plain", entry);
        engine.clearModuleCache();
        assertTrue(sessionGlobal("__loaded").asBoolean(),
                "existing session state is untouched by the cache signal");
    }
}
