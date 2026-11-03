package ink.tuanzi.neomacros.core.runtime;

import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime assembly with the stub engine: context lifecycle, SPI wiring, and
 * ordered teardown.
 */
class ScriptRuntimeTest {

    @Test
    void assemblyWiresEverythingExplicitly() {
        StubEngine engine = new StubEngine();
        EventCatalog catalog = new EventCatalog();
        try (ScriptThreadPool pool = new ScriptThreadPool(2);
             ScriptRuntime runtime = new ScriptRuntime(engine, null, catalog,
                     new EventBus(catalog, Runnable::run), pool, 500)) {
            assertNotNull(runtime.eventCatalog());
            assertNotNull(runtime.eventBus());
            assertNotNull(runtime.threadPool());
            assertNotNull(runtime.tickClock());
            assertNotNull(runtime.mainThread());
            assertNotNull(runtime.watchdog());
            assertNull(runtime.adapter(), "engine-only assembly has no adapter");
        }
    }

    @Test
    void contextCreationAndLookup() throws Exception {
        StubEngine engine = new StubEngine();
        EventCatalog catalog = new EventCatalog();
        try (ScriptThreadPool pool = new ScriptThreadPool(2);
             ScriptRuntime runtime = new ScriptRuntime(engine, null, catalog,
                     new EventBus(catalog, Runnable::run), pool, 500)) {
            ScriptContext context = runtime.createContext("hello");
            assertEquals("hello", context.name());
            assertEquals(context.id(), runtime.context(context.id()).id());
            assertFalse(context.isClosed());

            context.close();
            assertNull(runtime.context(context.id()), "closed contexts drop out of lookup");
        }
    }

    @Test
    void engineSpiWiringThroughContext() throws Exception {
        StubEngine engine = new StubEngine();
        EventCatalog catalog = new EventCatalog();
        try (ScriptThreadPool pool = new ScriptThreadPool(2);
             ScriptRuntime runtime = new ScriptRuntime(engine, null, catalog,
                     new EventBus(catalog, Runnable::run), pool, 500)) {
            ScriptContext context = runtime.createContext("wired");
            ScriptEngine.EngineSession session = runtime.engine().openSession(context, Path.of("scripts"));
            session.installBindings(Map.of("neo", Map.of()));
            session.importModule("wired", Path.of("scripts/wired.js"));
            session.close();

            assertEquals(List.of("wired"), engine.openedContexts);
            assertEquals(List.of("wired"), engine.importedModules);
            assertEquals(1, engine.installedBindings.size());
        }
    }

    @Test
    void scriptErrorCarriesSourceLocation() throws Exception {
        StubEngine engine = new StubEngine();
        EventCatalog catalog = new EventCatalog();
        try (ScriptThreadPool pool = new ScriptThreadPool(2);
             ScriptRuntime runtime = new ScriptRuntime(engine, null, catalog,
                     new EventBus(catalog, Runnable::run), pool, 500)) {
            ScriptContext context = runtime.createContext("failing");
            ScriptEngine.EngineSession session = runtime.engine().openSession(context, Path.of("scripts"));
            engine.failImports.set(true);

            ScriptError error = assertThrows(ScriptError.class,
                    () -> session.importModule("failing", Path.of("scripts/failing.js")));
            assertEquals("failing", error.getSourceName());
            assertEquals(3, error.getLine());
            assertEquals(7, error.getColumn());
            session.close();
        }
    }

    @Test
    void closeTearsDownEngineAndContexts() throws Exception {
        StubEngine engine = new StubEngine();
        EventCatalog catalog = new EventCatalog();
        ScriptThreadPool pool = new ScriptThreadPool(2);
        ScriptRuntime runtime = new ScriptRuntime(engine, null, catalog,
                new EventBus(catalog, Runnable::run), pool, 500);
        ScriptContext context = runtime.createContext("teardown");

        runtime.close();
        assertTrue(engine.closed.get());
        assertTrue(context.isClosed());
        assertNull(runtime.context(context.id()));
        assertTrue(pool.workers().isShutdown());
        runtime.close(); // idempotent
    }

    @Test
    void contextCreationAfterCloseRejected() {
        StubEngine engine = new StubEngine();
        EventCatalog catalog = new EventCatalog();
        ScriptThreadPool pool = new ScriptThreadPool(2);
        ScriptRuntime runtime = new ScriptRuntime(engine, null, catalog,
                new EventBus(catalog, Runnable::run), pool, 500);
        runtime.close();
        assertThrows(IllegalStateException.class, () -> runtime.createContext("late"));
        pool.close();
    }
}
