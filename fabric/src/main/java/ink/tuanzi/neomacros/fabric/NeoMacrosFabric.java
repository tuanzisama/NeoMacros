package ink.tuanzi.neomacros.fabric;

import com.mojang.brigadier.Command;
import ink.tuanzi.neomacros.adapter.NeomacrosAdapterImpl;
import ink.tuanzi.neomacros.core.NeoMacros;
import ink.tuanzi.neomacros.core.api.ChatApi;
import ink.tuanzi.neomacros.core.api.ClockApi;
import ink.tuanzi.neomacros.core.api.EventsApi;
import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.StorageApi;
import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.config.ConfigManager;
import ink.tuanzi.neomacros.core.config.CoreOptions;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.event.def.chat.ChatReceiveEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameQuitEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameStartEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.WorldJoinEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.WorldLeaveEvent;
import ink.tuanzi.neomacros.core.event.def.tick.TickEvent;
import ink.tuanzi.neomacros.core.event.SyncWatchdog;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.module.ScriptMeta;
import ink.tuanzi.neomacros.core.runtime.ScriptEngine;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.engine.GraalJsEngine;
import ink.tuanzi.neomacros.engine.ModuleResolver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Fabric client entrypoint: assembles the script runtime for this node
 * (engine, adapter ports, event framework, threading), wires the port sinks
 * to the event bus, and registers the root {@code /neomacros} client command.
 */
public final class NeoMacrosFabric implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(NeoMacros.MOD_ID);

    private static ScriptRuntime runtime;
    private static java.nio.file.Path configDir;
    private static boolean quitOnDone;

    @Override
    public void onInitializeClient() {
        LOGGER.info("NeoMacros bootstrap");
        configDir = FabricLoader.getInstance().getConfigDir().resolve(NeoMacros.MOD_ID);
        var configManager = new ConfigManager(configDir.resolve("options.json"),
                message -> LOGGER.warn("neomacros config: {}", message));
        var options = new CoreOptions();
        configManager.register(options);
        configManager.load();

        Path scriptsDir = options.scriptsDir.isEmpty()
                ? configDir.resolve("scripts") : Path.of(options.scriptsDir);

        var adapter = new NeomacrosAdapterImpl();
        var catalog = new EventCatalog();
        // Explicit registration: the classpath scanner cannot see mod classes
        // under Knot (java.class.path does not include the mod jar), so the
        // event list is declared here. Keep in sync with core/event/def.
        catalog.registerAll(java.util.List.of(
                ink.tuanzi.neomacros.core.event.def.tick.TickEvent.class,
                ink.tuanzi.neomacros.core.event.def.chat.ChatSendEvent.class,
                ink.tuanzi.neomacros.core.event.def.chat.ChatReceiveEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.GameStartEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.GameQuitEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.WorldJoinEvent.class,
                ink.tuanzi.neomacros.core.event.def.lifecycle.WorldLeaveEvent.class));
        var pool = new ScriptThreadPool(Runtime.getRuntime().availableProcessors());
        var bus = new EventBus(catalog, pool.workers());
        var registry = new BindingRegistry(mcVersion());
        registry.registerAll(java.util.List.of(EventsApi.class, ClockApi.class, ChatApi.class, StorageApi.class));
        registry.promoteRootMembers("clock", java.util.List.of("sleep", "ticks", "nextTick", "runOnMain"));
        ScriptApiEnvironment.install(bus, adapter);
        ScriptApiEnvironment.installStorage(configDir.resolve("storage"));

        ScriptEngine engine = new GraalJsEngine(new ScriptsModuleResolver(scriptsDir, registry));
        runtime = new ScriptRuntime(engine, adapter, catalog, bus, pool, options.maxSyncTime);
        var extraIgnores = new java.util.ArrayList<>(EventBus.DEFAULT_WILDCARD_IGNORES);
        extraIgnores.addAll(options.wildcardIgnoresExtra);
        runtime.eventBus().setWildcardIgnores(extraIgnores);

        var modules = new ModuleManager(runtime, registry, PermissionView.ALLOW_ALL, scriptsDir,
                message -> LOGGER.warn("neomacros scripts: {}", message));

        quitOnDone = Boolean.getBoolean("neomacros.e2e.quitOnDone");

        wireEventSinks(adapter, runtime, modules, options);
        ClientTickEvents.START_CLIENT_TICK.register(mc -> runtime.mainThread().runQueuedTasks());
        registerCommands(scriptsDir, modules);
    }

    /** The running Minecraft version (node trimming key for ApiMC ranges). */
    private static String mcVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("26.3");
    }

    /**
     * Feeds the event bus from the adapter sinks (one publish per source) and
     * drives the script lifecycle phases: always-phase scripts load once the
     * client has started (before the game:start publish so scripts observe
     * it), world-phase scripts load and unload with the world session.
     */
    private static void wireEventSinks(NeomacrosAdapterImpl adapter, ScriptRuntime rt, ModuleManager modules, CoreOptions options) {
        adapter.lifecycle().onTick(tick -> {
            rt.tickClock().tick();
            rt.eventBus().publish("tick", new TickEvent(tick));
            if (options.scriptWatch && tick % options.scriptWatchInterval == 0) {
                modules.checkForChanges();
            }
            if (quitOnDone && tick % 100 == 0 && tick > 0) {
                Path resultsFile = configDir.resolve("storage").resolve("e2e-suite.json");
                if (Files.exists(resultsFile)) {
                    LOGGER.info("NeoMacros E2E results present; shutting down client");
                    adapter.lifecycle().requestShutdown();
                }
            }
        });
        adapter.lifecycle().onGameStart(() -> {
            modules.loadPhase(ScriptMeta.Lifecycle.ALWAYS);
            LOGGER.info("NeoMacros: {} scripts loaded", modules.loaded().size());
            rt.eventBus().publish("game:start", new GameStartEvent());
        });
        adapter.lifecycle().onGameQuit(() -> {
            rt.eventBus().publish("game:quit", new GameQuitEvent());
            modules.unloadPhase(ScriptMeta.Lifecycle.ALWAYS);
            ScriptApiEnvironment.closeStorages();
        });
        adapter.lifecycle().onWorldJoin(() -> {
            modules.loadPhase(ScriptMeta.Lifecycle.WORLD);
            rt.eventBus().publish("world:join", new WorldJoinEvent());
        });
        adapter.lifecycle().onWorldLeave(() -> {
            rt.eventBus().publish("world:leave", new WorldLeaveEvent());
            modules.unloadPhase(ScriptMeta.Lifecycle.WORLD);
        });
        adapter.chat().onChatReceive(message -> rt.eventBus().publish("chat:receive", new ChatReceiveEvent(message)));
    }

    /** Registers the root {@code /neomacros} command and its subcommands. */
    private static void registerCommands(Path scriptsDir, ModuleManager modules) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("neomacros")
                    .then(literal("status").executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal(
                                "NeoMacros " + NeoMacros.VERSION + " | events registered: "
                                        + runtime.eventCatalog().all().size() + " | scripts loaded: "
                                        + modules.loaded().size() + " | ticks: "
                                        + runtime.tickClock().currentTick()));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(literal("list").executes(ctx -> {
                        var names = modules.loaded().stream()
                                .map(m -> m.meta().name() + " (" + m.meta().lifecycle().name()
                                        .toLowerCase(java.util.Locale.ROOT) + ")")
                                .toList();
                        ctx.getSource().sendFeedback(Component.literal(
                                "Loaded scripts: " + (names.isEmpty() ? "(none)" : String.join(", ", names))));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(literal("reload").executes(ctx -> {
                        modules.reloadAll();
                        ctx.getSource().sendFeedback(Component.literal(
                                "Reloaded; " + modules.loaded().size() + " scripts loaded."));
                        return Command.SINGLE_SUCCESS;
                    })));
        });
    }

    /** Lists the script files below the scripts directory (one level). */
    private static String listScripts(Path scriptsDir) {
        if (!Files.isDirectory(scriptsDir)) {
            return "(directory not created yet)";
        }
        try (Stream<Path> files = Files.list(scriptsDir)) {
            var names = files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".js"))
                    .toList();
            return names.isEmpty() ? "(none)" : String.join(", ", names);
        } catch (IOException e) {
            return "(unreadable: " + e.getMessage() + ")";
        }
    }

    /**
     * Module source resolver over the scripts directory. Virtual module
     * specifiers resolve through the binding registry once script loading
     * exists; file specifiers resolve to {@code <scriptsDir>/<name>.js}.
     */
    private record ScriptsModuleResolver(Path scriptsDir, BindingRegistry registry) implements ModuleResolver {
        @Override
        public Optional<String> virtualSource(String specifier) {
            return registry.virtualSourceFor(specifier, PermissionView.ALLOW_ALL);
        }

        @Override
        public Optional<Path> fileFor(String specifier) {
            Path file = scriptsDir.resolve(specifier);
            return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
        }
    }
}
