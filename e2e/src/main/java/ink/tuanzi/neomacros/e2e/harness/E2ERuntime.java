package ink.tuanzi.neomacros.e2e.harness;

import ink.tuanzi.neomacros.core.api.ChatApi;
import ink.tuanzi.neomacros.core.api.ClockApi;
import ink.tuanzi.neomacros.core.api.EventsApi;
import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.StorageApi;
import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.player.PlayerApi;
import ink.tuanzi.neomacros.core.api.world.WorldApi;
import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.event.PortEventWiring;
import ink.tuanzi.neomacros.core.event.ProductionEventSet;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.module.ScriptMeta;
import ink.tuanzi.neomacros.core.runtime.ScriptEngine;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.engine.GraalJsEngine;
import ink.tuanzi.neomacros.engine.ModuleResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Application-level E2E harness: assembles the real engine, the real event
 * framework, and the real module manager over a stub adapter, injects the
 * {@code __test} bridge, and exposes event injection plus await helpers.
 * Scenarios load fixture scripts exactly the way production bootstrap does.
 */
public final class E2ERuntime implements AutoCloseable {

    private final StubAdapter adapter;
    private final ScriptRuntime runtime;
    private final BindingRegistry registry;
    private final ModuleManager modules;
    private final TestBridge bridge;
    private final EventCatalog catalog;

    private E2ERuntime(StubAdapter adapter, ScriptRuntime runtime, BindingRegistry registry,
                       ModuleManager modules, TestBridge bridge, EventCatalog catalog) {
        this.adapter = adapter;
        this.runtime = runtime;
        this.registry = registry;
        this.modules = modules;
        this.bridge = bridge;
        this.catalog = catalog;
    }

    /**
     * Creates a harness over one scripts directory (fixtures are written
     * there by the scenario) and one storage directory.
     */
    public static E2ERuntime create(Path scriptsDir, Path storageDir) {
        var adapter = new StubAdapter();
        var catalog = new EventCatalog();
        catalog.registerAll(ProductionEventSet.records());
        var pool = new ScriptThreadPool(2);
        // Synchronous executor: publishes dispatch inline, keeping scenarios deterministic.
        var bus = new EventBus(catalog, Runnable::run);
        var registry = new BindingRegistry("26.3");
        var bridge = new TestBridge();
        registry.registerAll(List.of(EventsApi.class, ClockApi.class, ChatApi.class,
                StorageApi.class, TestBridge.class, WorldApi.class, EntityApi.class, PlayerApi.class,
                ink.tuanzi.neomacros.core.api.java.JavaApi.class));
        registry.registerGuestType("Entity", EntityApi.Entity.class);
        registry.registerGuestType("Player", PlayerApi.Player.class);
        registry.registerGuestType("LocalPlayer", PlayerApi.LocalPlayer.class);
        registry.registerGuestType("Vec3", ink.tuanzi.neomacros.core.api.types.Vec3.class);
        registry.registerGuestType("BlockPos", ink.tuanzi.neomacros.core.api.types.BlockPos.class);
        registry.registerGuestType("Block", ink.tuanzi.neomacros.core.api.types.Block.class);
        registry.registerGuestType("BlockState", ink.tuanzi.neomacros.core.api.types.BlockState.class);
        registry.registerGuestType("RayHit", ink.tuanzi.neomacros.core.api.types.RayHit.class);
        registry.promoteRootMembers("clock", List.of("sleep", "ticks", "nextTick", "runOnMain"));
        ScriptApiEnvironment.install(bus, adapter);
        ScriptApiEnvironment.installStorage(storageDir);

        ScriptEngine engine = new GraalJsEngine(new ModuleResolver() {
            @Override
            public Optional<String> virtualSource(String specifier) {
                return registry.virtualSourceFor(specifier, PermissionView.ALLOW_ALL);
            }

            @Override
            public Optional<Path> fileFor(String specifier) {
                return Optional.empty();
            }
        });
        var runtime = new ScriptRuntime(engine, adapter, catalog, bus, pool, 500);
        // Script-side failures must surface in test output, never vanish.
        runtime.setErrorHandler(error -> {
            log("SCRIPT-ERROR " + error);
            StringBuilder trace = new StringBuilder();
            for (Throwable t = error; t != null; t = t.getCause()) {
                trace.append("  caused by ").append(t).append('\n');
                for (StackTraceElement frame : t.getStackTrace()) {
                    trace.append("    at ").append(frame).append('\n');
                    if (trace.length() > 4000) {
                        break;
                    }
                }
            }
            log(trace.toString());
        });
        var modules = new ModuleManager(runtime, registry, PermissionView.ALLOW_ALL,
                scriptsDir, m -> log("WARN " + m));

        PortEventWiring.wire(adapter, bus, modules, runtime.tickClock());

        // The stub input's fire helpers dispatch onto the first context's
        // loop so GraalJS never sees cross-thread context access.
        modules.onLoad(module ->
                adapter.inputPort().setLoopDispatcher(
                        action -> module.context().loop().execute(action)));

        var harness = new E2ERuntime(adapter, runtime, registry, modules,
                (TestBridge) registry.instance("__test"), catalog);
        return harness;
    }

    /** The stub adapter (event injection + output assertions). */
    public StubAdapter adapter() {
        return adapter;
    }

    /** The event bus (publish helper for tests). */
    public EventBus eventBus() {
        return runtime.eventBus();
    }

    /** The test bridge (case reports). */
    public TestBridge bridge() {
        return bridge;
    }

    /** The event catalog. */
    public EventCatalog eventCatalog() {
        return catalog;
    }

    /**
     * Loads one fixture through the real module manager. The path may be the
     * entry file itself or the fixture's legacy flat name; it resolves by
     * entry file first, then by manifest name.
     */
    public void loadScript(Path file) throws Exception {
        log("LOAD " + file + " exists=" + java.nio.file.Files.exists(file));
        Path target = file.toAbsolutePath().normalize();
        String name = file.getFileName().toString().replaceAll("\\.js$", "");
        var entry = modules.discover().stream()
                .filter(e -> e.entryFile().toAbsolutePath().normalize().equals(target)
                        || e.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no discovered script entry for " + file));
        var loaded = modules.load(entry);
        log("LOADED " + loaded.meta().name() + " lifecycle=" + loaded.meta().lifecycle());
    }

    private static void log(String line) {
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("build/e2e-harness.log"),
                    line + "\n", java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    /**
     * Waits until every loaded context's loop has drained its queue plus a
     * grace period for scheduler-driven promise resolution.
     */
    public void awaitSettle() throws InterruptedException {
        for (var module : modules.loaded()) {
            try {
                module.context().loop().awaitIdle(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Thread.sleep(50);
    }

    /** Loads every discovered fixture whose lifecycle matches the phase. */
    public void loadPhase(ScriptMeta.Lifecycle phase) {
        modules.loadPhase(phase);
    }

    /** The module manager (reload/unload scenarios). */
    public ModuleManager modules() {
        return modules;
    }

    @Override
    public void close() {
        ScriptApiEnvironment.closeStorages();
        modules.close();
        runtime.close();
    }

}
