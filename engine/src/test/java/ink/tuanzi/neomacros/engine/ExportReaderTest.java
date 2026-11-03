package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.runtime.ScriptError;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the {@code meta} object export and the {@code dispose} function
 * export from a real ES module (the drop-in manifest contract), and verifies
 * the dispose handle executes guest code on the context loop.
 */
class ExportReaderTest {

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
        engine = new GraalJsEngine(new ModuleResolver() {
            @Override
            public java.util.Optional<String> virtualSource(String specifier) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<Path> fileFor(String specifier) {
                return java.util.Optional.empty();
            }
        });
        runtime = new ScriptRuntime(engine, null,
                new EventCatalog(), new EventBus(new EventCatalog(), Runnable::run), pool, 500);
        context = runtime.createContext("export-reader");
        session = (GraalJsEngine.GraalJsSession) engine.openSession(context, scriptsDir);
    }

    @AfterEach
    void tearDown() {
        runtime.close();
        pool.close();
    }

    @Test
    void readsMetaObjectExportAsNeutralMap() throws Exception {
        Path module = scriptsDir.resolve("meta-module.mjs");
        Files.writeString(module, "export const meta = {\n"
                + "  name: 'widget',\n"
                + "  version: '2.1.0',\n"
                + "  lifecycle: 'always',\n"
                + "  permissions: ['fs', 'net'],\n"
                + "};\n");

        session.importModule("meta-module", module);
        Map<String, Object> meta = session.readExportedData("meta");

        assertEquals("widget", meta.get("name"));
        assertEquals("2.1.0", meta.get("version"));
        assertEquals("always", meta.get("lifecycle"));
        assertEquals(List.of("fs", "net"), meta.get("permissions"));
    }

    @Test
    void absentDataExportYieldsEmptyMap() throws Exception {
        Path module = scriptsDir.resolve("bare.mjs");
        Files.writeString(module, "export const unrelated = 1;\n");

        session.importModule("bare", module);
        assertTrue(session.readExportedData("meta").isEmpty());
    }

    @Test
    void readsDisposeFunctionExportAndRunsGuestCode() throws Exception {
        Path module = scriptsDir.resolve("disposable.mjs");
        Files.writeString(module, "globalThis.__teardownRuns = 0;\n"
                + "export function dispose() { globalThis.__teardownRuns += 1; }\n");

        session.importModule("disposable", module);
        Optional<Runnable> dispose = session.readExportedFunction("dispose");
        assertTrue(dispose.isPresent());

        dispose.get().run();
        assertEquals(1, session.global("__teardownRuns").asInt());

        dispose.get().run();
        assertEquals(2, session.global("__teardownRuns").asInt());
    }

    @Test
    void absentFunctionExportYieldsEmpty(@TempDir Path dir) throws Exception {
        Path module = scriptsDir.resolve("no-dispose.mjs");
        Files.writeString(module, "export const meta = { name: 'x' };\n");

        session.importModule("no-dispose", module);
        assertTrue(session.readExportedFunction("dispose").isEmpty());
    }
}
