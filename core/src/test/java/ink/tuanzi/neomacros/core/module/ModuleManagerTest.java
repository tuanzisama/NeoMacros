package ink.tuanzi.neomacros.core.module;

import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.module.manifest.ScriptCategory;
import ink.tuanzi.neomacros.core.runtime.ScriptEngine;
import ink.tuanzi.neomacros.core.runtime.ScriptError;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module lifecycle over a stub engine: folder discovery with manifests,
 * entry resolution, category lifecycles, invalid-folder isolation, load-phase
 * filtering, manual loads, key dispatch, the autoload filter, watch scans,
 * and unload teardown.
 */
class ModuleManagerTest {

    /** Stub engine recording imports, the dispose export, and key invocations. */
    static final class StubEngine implements ScriptEngine {
        final List<String> imported = new ArrayList<>();
        final List<String> trackerOwnerAtImport = new ArrayList<>();
        final List<Object> invokedPayloads = new ArrayList<>();
        final AtomicInteger disposeCalls = new AtomicInteger();
        final AtomicBoolean failNext = new AtomicBoolean();
        final AtomicBoolean exposeOnKey = new AtomicBoolean();
        private ink.tuanzi.neomacros.core.runtime.ScriptContext sessionContext;

        @Override
        public EngineSession openSession(ink.tuanzi.neomacros.core.runtime.ScriptContext context,
                                         java.nio.file.Path moduleRoot) {
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
                public Optional<Runnable> readExportedFunction(String exportName) {
                    if (!exportName.equals("dispose")) {
                        return Optional.empty();
                    }
                    return Optional.of(disposeCalls::incrementAndGet);
                }

                @Override
                public CompletableFuture<Boolean> invokeExport(String exportName, Object payload) {
                    if (exposeOnKey.get()) {
                        invokedPayloads.add(payload);
                        return CompletableFuture.completedFuture(true);
                    }
                    return CompletableFuture.completedFuture(false);
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

    /** Writes one script folder with a YAML manifest and an index.js entry. */
    private static void writeScript(Path root, String category, String folder,
                                    String manifestYaml, String js) throws IOException {
        Path dir = root.resolve(category).resolve(folder);
        Files.createDirectories(dir);
        if (manifestYaml != null) {
            Files.writeString(dir.resolve("neomacro.yml"), manifestYaml);
        }
        if (js != null) {
            Files.writeString(dir.resolve("index.js"), js);
        }
    }

    private static ScriptRuntime runtime(ScriptEngine engine) {
        ScriptThreadPool pool = new ScriptThreadPool(1);
        return new ScriptRuntime(engine, null, new EventCatalog(),
                new EventBus(new EventCatalog(), Runnable::run), pool, 500);
    }

    private static ModuleManager manager(ScriptRuntime runtime, Path root,
                                         java.util.function.Consumer<String> warnings) {
        return new ModuleManager(runtime, new BindingRegistry("26.3"), PermissionView.ALLOW_ALL,
                root, warnings);
    }

    private static final String MANIFEST = """
            name: %s
            version: 1.0.0
            author: tester
            """;

    @Test
    void loadsFolderAndBuildsMetaFromManifest(@TempDir Path root) throws Exception {
        writeScript(root, "event", "hello",
                MANIFEST.formatted("hello") + "permissions:\n  - fs\n", "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            LoadedModule module = manager.loadPhase(ScriptMeta.Lifecycle.WORLD).get(0);

            assertEquals("hello", module.meta().name());
            assertEquals("1.0.0", module.meta().version());
            assertEquals(ScriptMeta.Lifecycle.WORLD, module.meta().lifecycle());
            assertEquals(List.of("fs"), module.meta().permissions());
            assertTrue(manager.loaded("hello").isPresent());
            assertEquals(ScriptCategory.EVENT, module.entry().category());
            assertFalse(module.context().isClosed());
        }
    }

    @Test
    void categorySuppliesDefaultLifecycle(@TempDir Path root) throws Exception {
        writeScript(root, "services", "svc", MANIFEST.formatted("svc"), "// stub");
        writeScript(root, "keypress", "key", MANIFEST.formatted("key"), "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.ALWAYS);
            assertTrue(manager.loaded("svc").isPresent());
            assertTrue(manager.loaded("key").isEmpty());

            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertTrue(manager.loaded("key").isPresent());
            assertEquals(ScriptMeta.Lifecycle.ALWAYS, manager.loaded("svc").get().meta().lifecycle());
        }
    }

    @Test
    void manifestLifecycleOverridesCategory(@TempDir Path root) throws Exception {
        writeScript(root, "event", "early",
                MANIFEST.formatted("early") + "lifecycle: always\n", "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.ALWAYS);
            assertTrue(manager.loaded("early").isPresent());
            assertEquals(ScriptMeta.Lifecycle.ALWAYS, manager.loaded("early").get().meta().lifecycle());
        }
    }

    @Test
    void unloadRunsDisposeUnsubscribesAndCloses(@TempDir Path root) throws Exception {
        writeScript(root, "event", "beta", MANIFEST.formatted("beta"), "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            LoadedModule module = manager.loadPhase(ScriptMeta.Lifecycle.WORLD).get(0);
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
    void invalidFoldersAreCollectedWithoutBlockingOthers(@TempDir Path root) throws Exception {
        writeScript(root, "event", "no-manifest", null, "// stub");
        writeScript(root, "event", "no-author", "name: x\nversion: 1.0.0\n", "// stub");
        writeScript(root, "event", "no-entry", MANIFEST.formatted("no-entry"), null);
        writeScript(root, "services", "dup", MANIFEST.formatted("dup"), "// stub");
        writeScript(root, "keypress", "dup-alias", MANIFEST.formatted("dup"), "// stub");
        writeScript(root, "event", "good", MANIFEST.formatted("good"), "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertTrue(manager.loaded("good").isPresent());

            List<String> messages = manager.errors().stream().map(ScriptLoadError::message).toList();
            assertEquals(4, manager.errors().size());
            assertTrue(messages.stream().anyMatch(m -> m.contains("no neomacro")));
            assertTrue(messages.stream().anyMatch(m -> m.contains("'author'")));
            assertTrue(messages.stream().anyMatch(m -> m.contains("no entry file")));
            assertTrue(messages.stream().anyMatch(m -> m.contains("duplicate script name 'dup'")));
        }
    }

    @Test
    void entryResolutionPrefersIndexThenMain(@TempDir Path root) throws Exception {
        Path folder = root.resolve("event").resolve("custom");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("neomacro.yml"),
                MANIFEST.formatted("custom") + "main: run/start.js\n");
        Files.createDirectories(folder.resolve("run"));
        Files.writeString(folder.resolve("run").resolve("start.js"), "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertTrue(manager.loaded("custom").isPresent());
            assertTrue(manager.loaded("custom").get().source()
                    .endsWith(root.resolve("event").resolve("custom").resolve("run").resolve("start.js")));
        }
    }

    @Test
    void entryMainCannotEscapeTheFolder(@TempDir Path root) throws Exception {
        Path folder = root.resolve("event").resolve("escape");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("neomacro.yml"),
                MANIFEST.formatted("escape") + "main: ../outside.js\n");
        Files.writeString(root.resolve("event").resolve("outside.js"), "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertTrue(manager.loaded("escape").isEmpty());
            assertTrue(manager.errors().stream().anyMatch(e -> e.message().contains("no entry file")));
        }
    }

    @Test
    void loadPhaseUnloadsNonMatchingLifecycle(@TempDir Path root) throws Exception {
        writeScript(root, "event", "gamma",
                MANIFEST.formatted("gamma") + "lifecycle: manual\n", "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertTrue(manager.loaded("gamma").isEmpty());
        }
    }

    @Test
    void loadByNameStartsManualScripts(@TempDir Path root) throws Exception {
        writeScript(root, "event", "manual",
                MANIFEST.formatted("manual") + "lifecycle: manual\n", "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            assertTrue(manager.loadByName("manual").isPresent());
            assertTrue(manager.loaded("manual").isPresent());
            assertTrue(manager.loadByName("unknown").isEmpty());
        }
    }

    @Test
    void triggerDispatchesToKeyHandlerExport(@TempDir Path root) throws Exception {
        writeScript(root, "keypress", "ping", MANIFEST.formatted("ping") + "key: key.keyboard.j\n", "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            engine.exposeOnKey.set(true);

            assertTrue(manager.trigger("ping", Map.of("key", "key.keyboard.j")));
            assertFalse(manager.trigger("absent", Map.of()));
            // Let the queued invocation run on the context loop.
            manager.loaded("ping").get().context().loop().awaitIdle(5000);
            assertEquals(1, engine.invokedPayloads.size());
        }
    }

    @Test
    void autoloadFilterSuppressesAutomaticAndManualLoads(@TempDir Path root) throws Exception {
        // NB: 'on'/'off' would be YAML booleans; identity names quote or avoid them.
        writeScript(root, "event", "disabled-one", MANIFEST.formatted("disabled-one"), "// stub");
        writeScript(root, "event", "enabled-one", MANIFEST.formatted("enabled-one"), "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.withAutoloadFilter(name -> name.equals("enabled-one"));
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertTrue(manager.loaded("enabled-one").isPresent());
            assertTrue(manager.loaded("disabled-one").isEmpty());
            assertTrue(manager.loadByName("disabled-one").isEmpty());
        }
    }

    @Test
    void watchScanLoadsNewReloadsChangedUnloadsDeleted(@TempDir Path root) throws Exception {
        writeScript(root, "event", "watched", MANIFEST.formatted("watched"), "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.checkForChanges(); // first scan only registers
            assertTrue(manager.loaded("watched").isEmpty());

            manager.checkForChanges(); // unchanged fingerprint, still nothing
            assertTrue(manager.loaded("watched").isEmpty());

            // A new folder appears.
            writeScript(root, "event", "fresh", MANIFEST.formatted("fresh"), "// stub");
            manager.checkForChanges();
            assertTrue(manager.loaded("fresh").isPresent());

            // A file inside the folder changes (mtime bump): a known but
            // previously unwatched-load folder loads on change.
            Path entry = root.resolve("event").resolve("watched").resolve("index.js");
            Files.setLastModifiedTime(entry, FileTime.fromMillis(System.currentTimeMillis() + 4000));
            manager.checkForChanges();
            assertTrue(manager.loaded("watched").isPresent());
            assertEquals(1, engine.imported.stream().filter("watched"::equals).count());

            // A second change reloads it.
            Files.setLastModifiedTime(entry, FileTime.fromMillis(System.currentTimeMillis() + 8000));
            manager.checkForChanges();
            assertEquals(2, engine.imported.stream().filter("watched"::equals).count());

            // The folder disappears.
            deleteRecursively(root.resolve("event").resolve("fresh"));
            manager.checkForChanges();
            assertTrue(manager.loaded("fresh").isEmpty());
        }
    }

    @Test
    void loadAndUnloadListenersFire(@TempDir Path root) throws Exception {
        writeScript(root, "event", "listened", MANIFEST.formatted("listened"), "// stub");
        var engine = new StubEngine();
        List<String> events = new ArrayList<>();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.onLoad(m -> events.add("load:" + m.meta().name()));
            manager.onUnload(m -> events.add("unload:" + m.meta().name()));
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            manager.unload("listened");
            assertEquals(List.of("load:listened", "unload:listened"), events);
        }
    }

    @Test
    void trackerIsAttributedDuringImport(@TempDir Path root) throws Exception {
        writeScript(root, "event", "epsilon", MANIFEST.formatted("epsilon"), "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertEquals(List.of("epsilon"), engine.trackerOwnerAtImport);
        }
    }

    @Test
    void loadFailureIsolatesAndCleansUp(@TempDir Path root) throws Exception {
        writeScript(root, "event", "zeta", MANIFEST.formatted("zeta"), "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.discover();
            ScriptEntry entry = manager.entries().get(0);
            engine.failNext.set(true);
            assertThrows(ScriptError.class, () -> manager.load(entry));
            assertTrue(manager.loaded().isEmpty());
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var files = Files.walk(path)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
