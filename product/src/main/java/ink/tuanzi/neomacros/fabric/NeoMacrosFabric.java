package ink.tuanzi.neomacros.fabric;

import com.mojang.brigadier.Command;
import ink.tuanzi.neomacros.adapter.FabricCommandPort;
import ink.tuanzi.neomacros.adapter.NeomacrosAdapterImpl;
import ink.tuanzi.neomacros.core.NeoMacros;
import ink.tuanzi.neomacros.core.api.ChatApi;
import ink.tuanzi.neomacros.core.api.ClockApi;
import ink.tuanzi.neomacros.core.api.CommandApi;
import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.EventsApi;
import ink.tuanzi.neomacros.core.api.FsApi;
import ink.tuanzi.neomacros.core.api.InputApi;
import ink.tuanzi.neomacros.core.api.NetApi;
import ink.tuanzi.neomacros.core.api.player.PlayerApi;
import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.StorageApi;
import ink.tuanzi.neomacros.core.api.world.WorldApi;
import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.config.ConfigManager;
import ink.tuanzi.neomacros.core.config.CoreOptions;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.event.Payloads;
import ink.tuanzi.neomacros.core.event.PortEventWiring;
import ink.tuanzi.neomacros.core.event.ProductionEventSet;
import ink.tuanzi.neomacros.core.event.SyncWatchdog;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.module.ScriptMeta;
import ink.tuanzi.neomacros.core.runtime.ScriptEngine;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.security.TrustManager;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.engine.GraalJsEngine;
import ink.tuanzi.neomacros.engine.ModuleResolver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
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
        // in nested-jar deployments (java.class.path is not mod-resolved), so
        // the event list comes from the shared production set. Keep in sync
        // with core/event/def.
        catalog.registerAll(ProductionEventSet.records());
        var pool = new ScriptThreadPool(Runtime.getRuntime().availableProcessors());
        var bus = new EventBus(catalog, pool.workers());
        var registry = new BindingRegistry(mcVersion());

        var ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "neomacros-io");
            thread.setDaemon(true);
            return thread;
        });
        registry.registerAll(java.util.List.of(
                EventsApi.class, ClockApi.class, ChatApi.class, StorageApi.class,
                WorldApi.class, EntityApi.class, PlayerApi.class));
        registry.registerInstance(new FsApi(new GameDirFsBridge(), ioExecutor));
        registry.registerInstance(new NetApi(ioExecutor));
        registry.registerInstance(new ink.tuanzi.neomacros.core.api.HudApi(
                new ink.tuanzi.neomacros.adapter.FabricHudRegistry(adapter.render(), Minecraft.getInstance()::execute)));
        registry.registerInstance(new ink.tuanzi.neomacros.core.api.render.RenderApi(
                adapter.draw3d()));
        var inputApi = new ink.tuanzi.neomacros.core.api.InputApi(
                new ink.tuanzi.neomacros.core.api.InputApi.InputBridge() {
                    @Override
                    public Object registerKeybind(String keyName, java.util.function.Consumer<Integer> sink) {
                        adapter.input().registerKeybind(keyName, sink);
                        return keyName;
                    }

                    @Override
                    public Object registerKeyPress(String keyName, Runnable sink) {
                        adapter.input().registerKeyPress(keyName, sink);
                        return keyName;
                    }

                    @Override
                    public void onScroll(java.util.function.DoubleConsumer sink) {
                        adapter.input().onScroll(sink::accept);
                    }

                    @Override
                    public void unregister(Object handle) {
                        // per-keybind removal lands with the input management increment
                    }
                });
        registry.registerInstance(inputApi);
        registry.promoteRootMembers("clock", java.util.List.of("sleep", "ticks", "nextTick", "runOnMain"));
        ScriptApiEnvironment.install(bus, adapter);
        ScriptApiEnvironment.installStorage(configDir.resolve("storage"));

        var commandPort = new FabricCommandPort();
        var commandApi = new CommandApi(commandPort);
        registry.registerInstance(commandApi);

        ScriptEngine engine = new GraalJsEngine(new ScriptsModuleResolver(scriptsDir, registry));
        runtime = new ScriptRuntime(engine, adapter, catalog, bus, pool, options.maxSyncTime);
        var extraIgnores = new java.util.ArrayList<>(EventBus.DEFAULT_WILDCARD_IGNORES);
        extraIgnores.addAll(options.wildcardIgnoresExtra);
        runtime.eventBus().setWildcardIgnores(extraIgnores);

        var trustManager = new ink.tuanzi.neomacros.core.security.TrustManager(options);
        trustManager.onPrompt(prompt -> {
            String name = prompt.scriptName() != null ? prompt.scriptName() : prompt.contentHash();
            String caps = String.join(", ", prompt.requested());
            adapter.chat().logClickable(
                    "[NeoMacros] script '" + name + "' requests: " + caps
                            + " -> click to trust, or run /neomacros trust " + name,
                    "/neomacros trust " + name);
        });

        var viewResolver = new java.util.function.BiFunction<String, List<String>, PermissionView>() {
            @Override
            public PermissionView apply(String scriptName, List<String> declared) {
                String hash = ink.tuanzi.neomacros.core.security.TrustManager.hashOf(
                        scriptName + ":" + String.join(",", declared));
                if (trustManager.isTrusted(hash)) {
                    return PermissionView.of(new java.util.HashSet<>(declared));
                }
                return PermissionView.DENY_ALL;
            }
        };

        var modules = new ModuleManager(runtime, registry, viewResolver, scriptsDir,
                message -> LOGGER.warn("neomacros scripts: {}", message));

        // Error presentation chain: script failures become a throttled
        // clickable chat line pointing at the recent-errors viewer screen.
        var errorPresenter = new ink.tuanzi.neomacros.core.runtime.ErrorPresenter(
                line -> adapter.chat().logClickable(
                        "[NeoMacros] script error: " + line + " -> click to view",
                        "/neomacros errors"),
                3000);
        runtime.setErrorHandler(errorPresenter::record);

        quitOnDone = Boolean.getBoolean("neomacros.e2e.quitOnDone");

        PortEventWiring.wire(adapter, bus, modules, runtime.tickClock());
        // Fabric-local extras ride as additional sinks: the scripts-loaded log,
        // the config-driven script file watch, and the E2E quit-on-done poll.
        adapter.lifecycle().onGameStart(() ->
                LOGGER.info("NeoMacros: {} scripts loaded", modules.loaded().size()));
        adapter.lifecycle().onTick(tick -> {
            if (options.scriptWatch && tick % options.scriptWatchInterval == 0) {
                modules.checkForChanges();
            }
        });
        if (quitOnDone) {
            adapter.lifecycle().onTick(tick -> {
                if (tick % 100 == 0 && tick > 0
                        && Files.exists(configDir.resolve("storage").resolve("e2e-suite.json"))) {
                    LOGGER.info("NeoMacros E2E results present; shutting down client");
                    adapter.lifecycle().requestShutdown();
                }
            });
        }
        // Hub→Bus wiring: adapter mixin event sources → EventBus.publish.
        ink.tuanzi.neomacros.adapter.EntityLifecycleHub.onLoad((uuid, typeId) ->
                bus.publish("entity:load", new ink.tuanzi.neomacros.core.event.def.entity.EntityLoadEvent(
                        new ink.tuanzi.neomacros.port.model.EntitySnapshot(
                                java.util.UUID.fromString(uuid), typeId, typeId,
                                new ink.tuanzi.neomacros.port.model.Pos3d(0, 64, 0), 20))));
        ink.tuanzi.neomacros.adapter.EntityLifecycleHub.onUnload(id ->
                bus.publish("entity:unload", new ink.tuanzi.neomacros.core.event.def.entity.EntityUnloadEvent(
                        new ink.tuanzi.neomacros.port.model.EntitySnapshot(
                                java.util.UUID.fromString(id), "", "", new ink.tuanzi.neomacros.port.model.Pos3d(0, 0, 0), 0))));
        ink.tuanzi.neomacros.adapter.SoundEventHub.register(data ->
                bus.publish("world:sound", new ink.tuanzi.neomacros.core.event.def.world.WorldSoundEvent(
                        data.soundId(), data.x(), data.y(), data.z(),
                        data.volume(), data.pitch())));
        ink.tuanzi.neomacros.adapter.WorldEventHub.onBlockUpdate(data ->
                bus.publish("world:blockUpdate",
                        new ink.tuanzi.neomacros.core.event.def.world.WorldBlockUpdateEvent(
                                data.x(), data.y(), data.z(), data.blockId())));
        ink.tuanzi.neomacros.adapter.ChunkEventHub.onLoad((chunkX, chunkZ) ->
                bus.publish("world:chunkLoad",
                        new ink.tuanzi.neomacros.core.event.def.world.WorldChunkLoadEvent(chunkX, chunkZ)));
        ink.tuanzi.neomacros.adapter.ChunkEventHub.onUnload((chunkX, chunkZ) ->
                bus.publish("world:chunkUnload",
                        new ink.tuanzi.neomacros.core.event.def.world.WorldChunkUnloadEvent(chunkX, chunkZ)));
        ink.tuanzi.neomacros.adapter.BossbarHub.register(data ->
                bus.publish("world:bossbar",
                        new ink.tuanzi.neomacros.core.event.def.world.WorldBossbarEvent(
                                data.uuid(), data.action(), data.name(), data.progress())));

        ClientTickEvents.START_CLIENT_TICK.register(mc -> runtime.mainThread().runQueuedTasks());
        registerCommands(scriptsDir, modules, trustManager, errorPresenter);
    }

    /** The running Minecraft version (node trimming key for ApiMC ranges). */
    private static String mcVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("26.3");
    }

    /** Registers the root {@code /neomacros} command and its subcommands. */
    private static void registerCommands(Path scriptsDir, ModuleManager modules, TrustManager trustManager,
            ink.tuanzi.neomacros.core.runtime.ErrorPresenter errorPresenter) {
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
                    }))
                    .then(literal("trust").then(literal("list").executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal(
                                "Trusted script hashes: " + trustManager.trustedHashes().size()));
                        return Command.SINGLE_SUCCESS;
                    })))
                    .then(literal("errors").executes(ctx -> {
                        var recent = errorPresenter.recent();
                        ScriptScreen.open("Recent errors",
                                recent.isEmpty() ? "(no recent errors)" : String.join("\n\n", recent));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(literal("open").then(argument("script",
                                    com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(ctx -> {
                                String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "script");
                                var file = scriptsDir.resolve(name.endsWith(".js") ? name : name + ".js");
                                if (!Files.exists(file)) {
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "Script not found: " + file.getFileName()));
                                    return 0;
                                }
                                try {
                                    ScriptScreen.open(name, Files.readString(file));
                                } catch (java.io.IOException e) {
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "Cannot read script: " + e.getMessage()));
                                }
                                return Command.SINGLE_SUCCESS;
                            }))));
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

    /**
     * Sandbox boundary for the fs facade: game-dir relative, traversal denied.
     */
    private record GameDirFsBridge() implements ink.tuanzi.neomacros.core.api.FsApi.FsBridge {
        @Override
        public Path resolve(String path) {
            Path gameDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
            Path resolved = gameDir.toAbsolutePath().resolve(path).normalize();
            if (!resolved.startsWith(gameDir.toAbsolutePath())) {
                throw new java.lang.SecurityException("path escapes the game directory: " + path);
            }
            return resolved;
        }
    }
}
