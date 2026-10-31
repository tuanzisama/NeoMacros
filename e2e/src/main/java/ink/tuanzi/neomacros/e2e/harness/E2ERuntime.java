package ink.tuanzi.neomacros.e2e.harness;

import ink.tuanzi.neomacros.core.api.ChatApi;
import ink.tuanzi.neomacros.core.api.ClockApi;
import ink.tuanzi.neomacros.core.api.EventsApi;
import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.StorageApi;
import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.def.chat.ChatReceiveEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameQuitEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameStartEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.WorldJoinEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.WorldLeaveEvent;
import ink.tuanzi.neomacros.core.event.def.tick.TickEvent;
import ink.tuanzi.neomacros.core.event.EventCatalog;
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
        catalog.registerAll(List.of(
                ink.tuanzi.neomacros.core.event.def.tick.TickEvent.class,
                ink.tuanzi.neomacros.core.event.def.chat.ChatSendEvent.class,
                ink.tuanzi.neomacros.core.event.def.chat.ChatReceiveEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.GameStartEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.GameQuitEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.WorldJoinEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.WorldLeaveEvent.class));
        var pool = new ScriptThreadPool(2);
        // Synchronous executor: publishes dispatch inline, keeping scenarios deterministic.
        var bus = new EventBus(catalog, Runnable::run);
        var registry = new BindingRegistry("26.3");
        var bridge = new TestBridge();
        registry.registerAll(List.of(EventsApi.class, ClockApi.class, ChatApi.class,
                StorageApi.class, TestBridge.class));
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
        var modules = new ModuleManager(runtime, registry, PermissionView.ALLOW_ALL,
                scriptsDir, m -> log("WARN " + m));

        wireEventSinks(adapter, runtime, modules);

        var harness = new E2ERuntime(adapter, runtime, registry, modules,
                (TestBridge) registry.instance("__test"), catalog);
        return harness;
    }

    /** The production event wiring, reproduced over the stub adapter. */
    private static void wireEventSinks(StubAdapter adapter, ScriptRuntime rt,
                                       ModuleManager modules) {
        adapter.lifecyclePort().onTick(tick -> {
            rt.tickClock().tick();
            rt.eventBus().publish("tick", new TickEvent(tick));
        });
        adapter.lifecyclePort().onGameStart(() -> {
            modules.loadPhase(ScriptMeta.Lifecycle.ALWAYS);
            rt.eventBus().publish("game:start", new GameStartEvent());
        });
        adapter.lifecyclePort().onGameQuit(() -> {
            rt.eventBus().publish("game:quit", new GameQuitEvent());
            modules.unloadPhase(ScriptMeta.Lifecycle.ALWAYS);
        });
        adapter.lifecyclePort().onWorldJoin(() -> {
            modules.loadPhase(ScriptMeta.Lifecycle.WORLD);
            rt.eventBus().publish("world:join", new WorldJoinEvent());
        });
        adapter.lifecyclePort().onWorldLeave(() -> {
            rt.eventBus().publish("world:leave", new WorldLeaveEvent());
            modules.unloadPhase(ScriptMeta.Lifecycle.WORLD);
        });
        adapter.chatPort().onChatReceive(message ->
                rt.eventBus().publish("chat:receive", new ChatReceiveEvent(message)));
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

    /** Loads one fixture script through the real module manager. */
    public void loadScript(Path file) throws Exception {
        log("LOAD " + file + " exists=" + java.nio.file.Files.exists(file)
                + " size=" + (java.nio.file.Files.exists(file) ? java.nio.file.Files.size(file) : -1));
        var loaded = modules.load(file);
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
