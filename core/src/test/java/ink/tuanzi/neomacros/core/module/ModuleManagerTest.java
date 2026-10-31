package ink.tuanzi.neomacros.core.module;

import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.runtime.ScriptEngine;
import ink.tuanzi.neomacros.core.runtime.ScriptError;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module lifecycle over a stub engine: manifest resolution, load-phase
 * filtering, unload teardown (dispose hook, tracked subscriptions, context
 * close), load-tracker attribution, reload, and failure isolation.
 */
class ModuleManagerTest {

    /** Stub engine serving per-module export data and function-call recording. */
    static final class ExportStubEngine implements ScriptEngine {
        final Map<String, Map<String, Object>> metaByModule;
        final List<String> imported = new ArrayList<>();
        final List<String> trackerOwnerAtImport = new ArrayList<>();
        final AtomicInteger disposeCalls = new AtomicInteger();
        final AtomicBoolean failNext = new AtomicBoolean();
        private ink.tuanzi.neomacros.core.runtime.ScriptContext sessionContext;

        ExportStubEngine(Map<String, Map<String, Object>> metaByModule) {
            this.metaByModule = metaByModule;
        }

        @Override
        public EngineSession openSession(ink.tuanzi.neomacros.core.runtime.ScriptContext context) {
            sessionContext = context;
            return new EngineSession() {
                @Override
                public void installBindings(Map<String, Object> bindings) {
                }

                @Override
                public void importModule(String moduleName, Path sourceFile) throws ScriptError {
                    if (failNext.getAndSet(false)) {
                        throw new ScriptError("stub failure", null, moduleName, 1, 1);
                    }
                    imported.add(moduleName);
                    // The attribution marker lives on the context loop thread.
                    var ownerName = new java.util.concurrent.CompletableFuture<String>();
                    sessionContext.loop().execute(() -> {
                        LoadedModule owner = LoadTracker.current();
                        ownerName.complete(owner != null ? owner.meta().name() : "(none)");
                    });
                    try {
                        trackerOwnerAtImport.add(ownerName.get(5, java.util.concurrent.TimeUnit.SECONDS));
                    } catch (Exception e) {
                        trackerOwnerAtImport.add("(unreadable)");
                    }
                }

                @Override
                public Map<String, Object> readExportedData(String exportName) {
                    if (!exportName.equals("meta")) {
                        return Map.of();
                    }
                    String current = imported.isEmpty() ? "" : imported.get(imported.size() - 1);
                    return metaByModule.getOrDefault(current, Map.of());
                }

                @Override
                public Optional<Runnable> readExportedFunction(String exportName) {
                    if (!exportName.equals("dispose")) {
                        return Optional.empty();
                    }
                    return Optional.of(disposeCalls::incrementAndGet);
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void clearModuleCache() {
        }

        @Override
        public void close() {
        }
    }

    private static ModuleManager manager(ScriptRuntime runtime, Path dir, List<String> warnings) {
        return new ModuleManager(runtime, new BindingRegistry("26.3"), PermissionView.ALLOW_ALL,
                dir, warnings::add);
    }

    private static ModuleManager silentManager(ScriptRuntime runtime, Path dir) {
        return new ModuleManager(runtime, new BindingRegistry("26.3"), PermissionView.ALLOW_ALL,
                dir, m -> { });
    }

    @Test
    void loadsModuleAndResolvesManifest(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("alpha.js");
        Files.writeString(file, "// stub");
        var engine = new ExportStubEngine(Map.of("alpha",
                Map.of("name", "alpha", "version", "1.2.0", "lifecycle", "always",
                        "permissions", List.of("fs"))));

        try (ScriptThreadPool pool = new ScriptThreadPool(1);
             ScriptRuntime runtime = new ScriptRuntime(engine, null, new EventCatalog(),
                     new EventBus(new EventCatalog(), Runnable::run), pool, 500);
             ModuleManager manager = silentManager(runtime, dir)) {
            LoadedModule module = manager.load(file);

            assertEquals("alpha", module.meta().name());
            assertEquals("1.2.0", module.meta().version());
            assertEquals(ScriptMeta.Lifecycle.ALWAYS, module.meta().lifecycle());
            assertEquals(List.of("fs"), module.meta().permissions());
            assertTrue(manager.loaded("alpha").isPresent());
            assertFalse(module.context().isClosed());
        }
    }

    @Test
    void unloadRunsDisposeUnsubscribesAndCloses(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("beta.js");
        Files.writeString(file, "// stub");
        var engine = new ExportStubEngine(Map.of("beta", Map.of("name", "beta")));

        try (ScriptThreadPool pool = new ScriptThreadPool(1);
             ScriptRuntime runtime = new ScriptRuntime(engine, null, new EventCatalog(),
                     new EventBus(new EventCatalog(), Runnable::run), pool, 500);
             ModuleManager manager = silentManager(runtime, dir)) {
            LoadedModule module = manager.load(file);
            AtomicInteger disposeCalls = engine.disposeCalls;
            EventBus bus = new EventBus(new EventCatalog(), Runnable::run);
            AtomicReference<Object> received = new AtomicReference<>();
            module.track(bus.on("tick", received::set));

            manager.unload("beta");

            assertTrue(module.context().isClosed());
            assertEquals(1, disposeCalls.get());
            assertTrue(manager.loaded("beta").isEmpty());
            // Unsubscribed handles no longer receive publishes.
            bus.publish("tick", 1L);
            assertTrue(received.get() == null);
        }
    }

    @Test
    void loadPhaseUnloadsNonMatchingLifecycle(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("gamma.js");
        Files.writeString(file, "// stub");
        var engine = new ExportStubEngine(Map.of("gamma", Map.of("name", "gamma", "lifecycle", "manual")));

        try (ScriptThreadPool pool = new ScriptThreadPool(1);
             ScriptRuntime runtime = new ScriptRuntime(engine, null, new EventCatalog(),
                     new EventBus(new EventCatalog(), Runnable::run), pool, 500);
             ModuleManager manager = silentManager(runtime, dir)) {
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertTrue(manager.loaded("gamma").isEmpty());
        }
    }

    @Test
    void reloadReimportsEverything(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("delta.js");
        Files.writeString(file, "// stub");
        var engine = new ExportStubEngine(Map.of("delta", Map.of("name", "delta", "lifecycle", "always")));

        try (ScriptThreadPool pool = new ScriptThreadPool(1);
             ScriptRuntime runtime = new ScriptRuntime(engine, null, new EventCatalog(),
                     new EventBus(new EventCatalog(), Runnable::run), pool, 500);
             ModuleManager manager = silentManager(runtime, dir)) {
            manager.loadPhase(ScriptMeta.Lifecycle.ALWAYS);
            assertEquals(1, engine.imported.size());

            manager.reloadAll();
            assertEquals(2, engine.imported.size());
            assertEquals(1, manager.loaded().size());
        }
    }

    @Test
    void trackerIsAttributedDuringImport(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("epsilon.js");
        Files.writeString(file, "// stub");
        var engine = new ExportStubEngine(Map.of("epsilon", Map.of("name", "epsilon")));

        try (ScriptThreadPool pool = new ScriptThreadPool(1);
             ScriptRuntime runtime = new ScriptRuntime(engine, null, new EventCatalog(),
                     new EventBus(new EventCatalog(), Runnable::run), pool, 500);
             ModuleManager manager = silentManager(runtime, dir)) {
            manager.load(file);
            assertEquals(List.of("epsilon"), engine.trackerOwnerAtImport);
        }
    }

    @Test
    void loadFailureIsolatesAndCleansUp(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("zeta.js");
        Files.writeString(file, "// stub");
        var engine = new ExportStubEngine(Map.of("zeta", Map.of("name", "zeta")));

        try (ScriptThreadPool pool = new ScriptThreadPool(1);
             ScriptRuntime runtime = new ScriptRuntime(engine, null, new EventCatalog(),
                     new EventBus(new EventCatalog(), Runnable::run), pool, 500);
             ModuleManager manager = silentManager(runtime, dir)) {
            engine.failNext.set(true);
            assertThrows(ScriptError.class, () -> manager.load(file));
            assertTrue(manager.loaded().isEmpty());
        }
    }
}
