package ink.tuanzi.neomacros.core.module;

import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.event.ProductionEventSet;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Module lifecycle over a stub engine: folder discovery with manifests,
 * config-block validation, entry resolution, category lifecycles, invalid-
 * folder isolation, load-phase pre-filtering, manual loads, startup dispatch,
 * manifest event auto-subscription, the autoload filter, watch scans, and
 * unload teardown.
 */
class ModuleManagerTest {

    /** Stub engine recording imports, the exports, and startup invocations. */
    static final class StubEngine implements ScriptEngine {
        final List<String> imported = new ArrayList<>();
        final List<String> trackerOwnerAtImport = new ArrayList<>();
        final List<Object> invokedPayloads = new ArrayList<>();
        final AtomicInteger disposeCalls = new AtomicInteger();
        final AtomicBoolean failNext = new AtomicBoolean();
        final AtomicBoolean exposeStartup = new AtomicBoolean();
        final AtomicBoolean hideStartup = new AtomicBoolean();
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
                    if (exportName.equals("dispose")) {
                        return Optional.of(disposeCalls::incrementAndGet);
                    }
                    if (exportName.equals(ModuleManager.STARTUP_EXPORT) && !hideStartup.get()) {
                        return Optional.of(() -> { });
                    }
                    return Optional.empty();
                }

                @Override
                public CompletableFuture<Boolean> invokeExport(String exportName, Object payload) {
                    if (exposeStartup.get()) {
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

    private static final String EVENT_CONFIG = "config:\n  event:\n    on: [tick]\n";

    private static final String KEYPRESS_CONFIG = "config:\n  keypress: {}\n";

    @Test
    void loadsFolderAndBuildsMetaFromManifest(@TempDir Path root) throws Exception {
        writeScript(root, "event", "hello",
                MANIFEST.formatted("hello") + EVENT_CONFIG + "permissions:\n  - fs\n", "// stub");
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
        writeScript(root, "keypress", "key", MANIFEST.formatted("key") + KEYPRESS_CONFIG, "// stub");
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
    void serviceLifecycleOverridesCategoryDefault(@TempDir Path root) throws Exception {
        writeScript(root, "services", "late",
                MANIFEST.formatted("late") + "config:\n  service:\n    lifecycle: world\n", "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.ALWAYS);
            assertTrue(manager.loaded("late").isEmpty());
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertTrue(manager.loaded("late").isPresent());
            assertEquals(ScriptMeta.Lifecycle.WORLD, manager.loaded("late").get().meta().lifecycle());
        }
    }

    @Test
    void unloadRunsDisposeUnsubscribesAndCloses(@TempDir Path root) throws Exception {
        writeScript(root, "event", "beta", MANIFEST.formatted("beta") + EVENT_CONFIG, "// stub");
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
        writeScript(root, "event", "no-entry", MANIFEST.formatted("no-entry") + EVENT_CONFIG, null);
        writeScript(root, "services", "dup", MANIFEST.formatted("dup"), "// stub");
        writeScript(root, "keypress", "dup-alias", MANIFEST.formatted("dup") + KEYPRESS_CONFIG, "// stub");
        writeScript(root, "event", "good", MANIFEST.formatted("good") + EVENT_CONFIG, "// stub");
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
    void keypressFoldersRequireTheKeypressConfig(@TempDir Path root) throws Exception {
        writeScript(root, "keypress", "bare", MANIFEST.formatted("bare"), "// stub");
        writeScript(root, "keypress", "fine", MANIFEST.formatted("fine") + KEYPRESS_CONFIG, "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertTrue(manager.loaded("bare").isEmpty());
            assertTrue(manager.loaded("fine").isPresent());
            assertTrue(manager.errors().stream()
                    .anyMatch(e -> e.message().contains("config.keypress")));
        }
    }

    @Test
    void entryResolutionPrefersIndexThenMain(@TempDir Path root) throws Exception {
        Path folder = root.resolve("event").resolve("custom");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("neomacro.yml"),
                MANIFEST.formatted("custom") + EVENT_CONFIG + "main: run/start.js\n");
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
                MANIFEST.formatted("escape") + EVENT_CONFIG + "main: ../outside.js\n");
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
    void loadPhasePrefiltersNonMatchingLifecycle(@TempDir Path root) throws Exception {
        writeScript(root, "keypress", "gamma", MANIFEST.formatted("gamma") + KEYPRESS_CONFIG, "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.ALWAYS);
            assertTrue(manager.loaded("gamma").isEmpty());
            // Pre-filtered before load: no phantom module ever instantiated.
            assertTrue(engine.imported.isEmpty());
        }
    }

    @Test
    void loadByNameStartsManualScripts(@TempDir Path root) throws Exception {
        writeScript(root, "services", "manual",
                MANIFEST.formatted("manual") + "config:\n  service:\n    lifecycle: manual\n", "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.ALWAYS);
            assertTrue(manager.loaded("manual").isEmpty());
            assertTrue(manager.loadByName("manual").isPresent());
            assertTrue(manager.loaded("manual").isPresent());
            assertTrue(manager.loadByName("unknown").isEmpty());
        }
    }

    /** Bounded poll: waits until the condition holds (test-side async settle). */
    private static void assertEventually(java.util.function.Supplier<Boolean> condition,
                                         String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(20);
        }
        assertTrue(condition.get(), message);
    }

    @Test
    void triggerDispatchesToStartupExport(@TempDir Path root) throws Exception {
        writeScript(root, "keypress", "ping",
                MANIFEST.formatted("ping") + "config:\n  keypress:\n    default-key: key.keyboard.j\n",
                "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            engine.exposeStartup.set(true);

            assertTrue(manager.trigger("ping", Map.of("key", "key.keyboard.j")));
            assertFalse(manager.trigger("absent", Map.of()));
            // Let the queued invocation run on the context loop.
            assertEventually(() -> engine.invokedPayloads.size() == 1,
                    "payloads were: " + engine.invokedPayloads);
        }
    }

    @Test
    void eventOnAutoSubscribesStartup(@TempDir Path root) throws Exception {
        writeScript(root, "event", "sub", MANIFEST.formatted("sub") + EVENT_CONFIG, "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            engine.exposeStartup.set(true);
            LoadedModule module = manager.loaded("sub").get();

            rt.eventBus().publish("tick", Map.of("n", 1));

            assertEventually(() -> engine.invokedPayloads.size() == 1,
                    "payloads were: " + engine.invokedPayloads);
            assertEquals(Map.of("n", 1), engine.invokedPayloads.get(0));

            // Unload tears the auto-subscription down: no further dispatch.
            manager.unload("sub");
            rt.eventBus().publish("tick", Map.of("n", 2));
            Thread.sleep(100);
            assertEquals(1, engine.invokedPayloads.size(),
                    "payloads were: " + engine.invokedPayloads);
        }
    }

    @Test
    void eventOnNamesValidateAgainstTheCatalog(@TempDir Path root) throws Exception {
        writeScript(root, "event", "bad-event",
                MANIFEST.formatted("bad-event") + "config:\n  event:\n    on: [totally:bogus]\n", "// stub");
        writeScript(root, "event", "good-event",
                MANIFEST.formatted("good-event") + EVENT_CONFIG, "// stub");
        var engine = new StubEngine();
        EventCatalog catalog = new EventCatalog();
        catalog.registerAll(ProductionEventSet.records());

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            List<String> loadErrors = new ArrayList<>();
            manager.onLoadError((name, error) -> loadErrors.add(error.getMessage()));
            manager.withEventCatalog(catalog);
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertTrue(manager.loaded("bad-event").isEmpty());
            assertTrue(manager.loaded("good-event").isPresent());
            assertEquals(1, loadErrors.size());
            assertTrue(loadErrors.get(0).contains("unknown event 'totally:bogus'"), loadErrors.get(0));
            assertTrue(loadErrors.get(0).contains("available events:"), loadErrors.get(0));
            assertTrue(loadErrors.get(0).contains("tick"), loadErrors.get(0));
        }
    }

    @Test
    void missingStartupExportIsALoadError(@TempDir Path root) throws Exception {
        writeScript(root, "event", "old-style", MANIFEST.formatted("old-style") + EVENT_CONFIG, "// stub");
        var engine = new StubEngine();
        engine.hideStartup.set(true);

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            List<String> loadErrors = new ArrayList<>();
            manager.onLoadError((name, error) -> loadErrors.add(error.getMessage()));
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertTrue(manager.loaded("old-style").isEmpty());
            assertEquals(1, loadErrors.size());
            assertTrue(loadErrors.get(0).contains("must export"), loadErrors.get(0));
            assertTrue(loadErrors.get(0).contains("startup"), loadErrors.get(0));
        }
    }

    @Test
    void servicesGetStartupOnActivation(@TempDir Path root) throws Exception {
        writeScript(root, "services", "boot", MANIFEST.formatted("boot"), "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            engine.exposeStartup.set(true);
            manager.loadPhase(ScriptMeta.Lifecycle.ALWAYS);
            assertEventually(() -> engine.invokedPayloads.size() == 1,
                    "payloads were: " + engine.invokedPayloads);
            assertEquals(Map.of(), engine.invokedPayloads.get(0));
        }
    }

    @Test
    void autoloadFilterSuppressesAutomaticAndManualLoads(@TempDir Path root) throws Exception {
        writeScript(root, "event", "disabled-one", MANIFEST.formatted("disabled-one") + EVENT_CONFIG, "// stub");
        writeScript(root, "event", "enabled-one", MANIFEST.formatted("enabled-one") + EVENT_CONFIG, "// stub");
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
    void taskRegistryTracksActivationsTriggersAndStops(@TempDir Path root) throws Exception {
        writeScript(root, "keypress", "ping", MANIFEST.formatted("ping") + KEYPRESS_CONFIG, "// stub");
        writeScript(root, "services", "boot", MANIFEST.formatted("boot"), "// stub");
        var engine = new StubEngine();
        var tasks = new ink.tuanzi.neomacros.core.task.TaskRegistry();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.withTaskRegistry(tasks);
            engine.exposeStartup.set(true);
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            manager.loadPhase(ScriptMeta.Lifecycle.ALWAYS);

            assertEquals(2, tasks.activeTasks().size());
            var ping = tasks.activeTasks().stream()
                    .filter(t -> t.macro().equals("ping")).findFirst().orElseThrow();
            var boot = tasks.activeTasks().stream()
                    .filter(t -> t.macro().equals("boot")).findFirst().orElseThrow();

            manager.trigger("ping", Map.of("key", "key.keyboard.j"));
            assertEventually(() -> ping.triggerCount() == 1, "trigger count was " + ping.triggerCount());
            // The service activation call counts as its first trigger.
            assertEventually(() -> boot.triggerCount() == 1, "service trigger count was " + boot.triggerCount());

            manager.unload("ping");
            assertEquals(1, tasks.activeTasks().size());
            assertEquals(1, tasks.historyTasks().size());
            assertEquals(ink.tuanzi.neomacros.core.task.ScriptTask.Status.STOPPED,
                    tasks.historyTasks().get(0).status());
            assertEquals(ping.taskId(), tasks.historyTasks().get(0).taskId());
            assertEquals("keypress", tasks.historyTasks().get(0).category().dirName());
        }
    }

    @Test
    void watchScanLoadsNewReloadsChangedUnloadsDeleted(@TempDir Path root) throws Exception {
        writeScript(root, "event", "watched", MANIFEST.formatted("watched") + EVENT_CONFIG, "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.checkForChanges(); // first scan only registers
            assertTrue(manager.loaded("watched").isEmpty());

            manager.checkForChanges(); // unchanged fingerprint, still nothing
            assertTrue(manager.loaded("watched").isEmpty());

            // A new folder appears.
            writeScript(root, "event", "fresh", MANIFEST.formatted("fresh") + EVENT_CONFIG, "// stub");
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
        writeScript(root, "event", "listened", MANIFEST.formatted("listened") + EVENT_CONFIG, "// stub");
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
        writeScript(root, "event", "epsilon", MANIFEST.formatted("epsilon") + EVENT_CONFIG, "// stub");
        var engine = new StubEngine();

        try (ScriptRuntime rt = runtime(engine);
             ModuleManager manager = manager(rt, root, m -> { })) {
            manager.loadPhase(ScriptMeta.Lifecycle.WORLD);
            assertEquals(List.of("epsilon"), engine.trackerOwnerAtImport);
        }
    }

    @Test
    void loadFailureIsolatesAndCleansUp(@TempDir Path root) throws Exception {
        writeScript(root, "event", "zeta", MANIFEST.formatted("zeta") + EVENT_CONFIG, "// stub");
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
