package ink.tuanzi.neomacros.fabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
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
import ink.tuanzi.neomacros.core.event.PortEventWiring;
import ink.tuanzi.neomacros.core.event.ProductionEventSet;
import ink.tuanzi.neomacros.core.event.SyncWatchdog;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.module.ScriptEntry;
import ink.tuanzi.neomacros.core.module.manifest.ScriptCategory;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Fabric client entrypoint: assembles the script runtime for this node
 * (engine, adapter ports, event framework, threading), prepares the macros
 * folder layout with first-install examples, wires manifest key bindings,
 * and registers the root {@code /neomacros} client command.
 */
public final class NeoMacrosFabric implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(NeoMacros.MOD_ID);

    /** A few common keys offered as suggestions for keypress bind commands. */
    private static final List<String> COMMON_KEYS = List.of(
            "key.keyboard.j", "key.keyboard.k", "key.keyboard.r", "key.keyboard.g",
            "key.keyboard.h", "key.keyboard.v", "key.keyboard.b", "key.keyboard.n");

    private static ScriptRuntime runtime;
    private static ConfigManager configManager;
    private static CoreOptions options;
    private static Path configDir;
    private static Path macrosDir;
    private static ModuleManager modules;
    private static NeomacrosAdapterImpl adapter;
    private static TrustManager trustManager;
    private static java.util.concurrent.ConcurrentHashMap<String, String> pendingTrusts;
    private static boolean quitOnDone;
    private static ink.tuanzi.neomacros.fabric.web.WebService webService;
    private static ink.tuanzi.neomacros.fabric.web.WebRuntime webRuntime;

    @Override
    public void onInitializeClient() {
        LOGGER.info("NeoMacros bootstrap");
        configDir = FabricLoader.getInstance().getConfigDir().resolve(NeoMacros.MOD_ID);
        // Carry a pre-rename options.json over so player settings survive.
        Path configFile = configDir.resolve("config.json");
        Path legacyConfigFile = configDir.resolve("options.json");
        if (!Files.exists(configFile) && Files.exists(legacyConfigFile)) {
            try {
                Files.move(legacyConfigFile, configFile);
                LOGGER.info("neomacros: migrated options.json to config.json");
            } catch (IOException e) {
                LOGGER.warn("neomacros: cannot migrate options.json to config.json: {}",
                        e.getMessage());
            }
        }
        configManager = new ConfigManager(configFile,
                message -> LOGGER.warn("neomacros config: {}", message));
        options = new CoreOptions();
        configManager.register(options);
        configManager.load();
        configManager.createIfMissing();

        macrosDir = options.macrosDir.isEmpty()
                ? configDir.resolve("macros") : Path.of(options.macrosDir);
        boolean seeded = ExampleSeeder.ensureLayout(macrosDir,
                message -> LOGGER.info("neomacros: {}", message));
        if (!seeded) {
            noteLegacyScriptsDir();
        }

        adapter = new NeomacrosAdapterImpl();
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
        var inputApi = new InputApi(new InputApi.InputBridge() {
            @Override
            public Object registerKeybind(String keyName, java.util.function.Consumer<Integer> sink) {
                return adapter.input().registerKeybind(keyName, sink);
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
                adapter.input().unregister(handle);
            }
        });
        registry.registerInstance(inputApi);
        registry.promoteRootMembers("clock", java.util.List.of("sleep", "ticks", "nextTick", "runOnMain"));
        ScriptApiEnvironment.install(bus, adapter);
        ScriptApiEnvironment.installStorage(configDir.resolve("storage"));

        var commandPort = new FabricCommandPort();
        var commandApi = new CommandApi(commandPort);
        registry.registerInstance(commandApi);

        ScriptEngine engine = new GraalJsEngine(new ScriptsModuleResolver(macrosDir, registry));
        runtime = new ScriptRuntime(engine, adapter, catalog, bus, pool, options.maxSyncTime);
        var extraIgnores = new java.util.ArrayList<>(EventBus.DEFAULT_WILDCARD_IGNORES);
        extraIgnores.addAll(options.wildcardIgnoresExtra);
        runtime.eventBus().setWildcardIgnores(extraIgnores);

        trustManager = new TrustManager(options);
        // Pending first-load trust requests by script name; /neomacros trust
        // <name> and the management panel resolve the grant through this map.
        pendingTrusts = new java.util.concurrent.ConcurrentHashMap<>();
        trustManager.onPrompt(prompt -> {
            String name = prompt.scriptName() != null ? prompt.scriptName() : prompt.contentHash();
            pendingTrusts.put(name, prompt.contentHash());
            String caps = String.join(", ", prompt.requested());
            adapter.chat().logClickable(
                    Msg.line(UiText.tr("neomacros.chat.trust_prompt",
                            Msg.name(name), Msg.num(caps))),
                    "/neomacros trust " + name);
        });

        var viewResolver = new java.util.function.BiFunction<String, List<String>, PermissionView>() {
            @Override
            public PermissionView apply(String scriptName, List<String> declared) {
                if (declared == null || declared.isEmpty()) {
                    return PermissionView.ALLOW_ALL;
                }
                java.nio.file.Path source = modules.entries().stream()
                        .filter(entry -> entry.name().equals(scriptName))
                        .map(ScriptEntry::entryFile)
                        .findFirst()
                        .orElse(null);
                if (source == null || !Files.isRegularFile(source)) {
                    return PermissionView.DENY_ALL;
                }
                try {
                    String hash = TrustManager.hashOf(Files.readString(source));
                    return trustManager.viewFor(hash, declared);
                } catch (IOException e) {
                    LOGGER.warn("neomacros: cannot hash {} for trust: {}", scriptName, e.getMessage());
                    return PermissionView.DENY_ALL;
                }
            }
        };

        modules = new ModuleManager(runtime, registry, viewResolver, macrosDir,
                message -> LOGGER.warn("neomacros scripts: {}", message));
        // An explicit enable/disable (panel or command) always wins; without
        // one, the manifest's autostart is the initial default (null = true).
        modules.withAutoloadFilter(name -> {
            Boolean saved = options.autoload.get(name);
            if (saved != null) {
                return saved;
            }
            return modules.entries().stream()
                    .filter(entry -> entry.name().equals(name))
                    .findFirst()
                    .map(entry -> entry.manifest().autostart() == null
                            || entry.manifest().autostart())
                    .orElse(true);
        });
        modules.onLoad(NeoMacrosFabric::registerScriptBinding);
        modules.onUnload(module -> adapter.input().unregisterScriptBinding(module.meta().name()));

        // Web panel subsystem: state backend over the module manager, device
        // registry beside options.json, and the service mapping core.web. The
        // events facade carries the panel broadcasts to whatever hub the
        // current service start owns.
        var panelEvents = new ink.tuanzi.neomacros.fabric.web.sse.PanelEvents();
        webRuntime = new ink.tuanzi.neomacros.fabric.web.WebRuntime(
                modules, options, configManager, macrosDir, runtime.mainThread(), panelEvents,
                NeoMacros.VERSION, mcVersion());
        modules.onLoadError(webRuntime::moduleLoadError);
        modules.onLoad(webRuntime::moduleLoaded);
        modules.onUnload(webRuntime::moduleUnloaded);
        modules.onFolderChange(webRuntime::folderChanged);
        var devices = new ink.tuanzi.neomacros.fabric.web.auth.DeviceRegistry(
                configDir.resolve("web-devices.json"),
                message -> LOGGER.warn("neomacros web-devices: {}", message));
        webService = new ink.tuanzi.neomacros.fabric.web.WebService(
                webRuntime, webRuntime, devices,
                () -> options.web.listen, () -> options.web.port,
                () -> options.web.panelUrl, () -> options.web.allowedOrigins, panelEvents);
        webRuntime.serverInfo(() -> new ink.tuanzi.neomacros.fabric.web.v1.ServerInfo(
                webService.port(), webService.status().uptimeMs(), options.web.listen));
        // Error presentation chain: script failures become a throttled
        // clickable chat line pointing at the recent-errors viewer screen.
        var errorPresenter = new ink.tuanzi.neomacros.core.runtime.ErrorPresenter(
                line -> adapter.chat().logClickable(
                        Msg.line(UiText.tr("neomacros.chat.error_line", Msg.err(line))),
                        "/neomacros errors"),
                3000);
        runtime.setErrorHandler(error -> {
            webRuntime.recordRuntimeError(error);
            errorPresenter.record(error);
        });

        quitOnDone = Boolean.getBoolean("neomacros.e2e.quitOnDone");

        PortEventWiring.wire(adapter, bus, modules, runtime.tickClock());
        // Fabric-local extras ride as additional sinks: the scripts-loaded log,
        // the config-driven script file watch, and the E2E quit-on-done poll.
        adapter.lifecycle().onGameStart(() ->
                LOGGER.info("NeoMacros: {} scripts loaded", modules.loaded().size()));
        adapter.lifecycle().onGameQuit(() -> {
            webService.stop();
            webService.saveDevices();
            configManager.save();
        });
        adapter.lifecycle().onTick(tick -> {
            if (options.scriptWatch && tick % options.scriptWatchInterval == 0) {
                modules.checkForChanges();
            }
        });
        // The persisted intent (last start/stop) or autoStart brings the web
        // service up with the client; a bind failure stays a log line here.
        if (options.web.autoStart || options.web.enabled) {
            try {
                int port = webService.start();
                LOGGER.info("neomacros: web panel auto-started on port {}", port);
            } catch (java.io.IOException e) {
                LOGGER.warn("neomacros: web panel auto-start failed: {}", e.getMessage());
            }
        }
        if (quitOnDone) {
            adapter.lifecycle().onTick(tick -> {
                if (tick % 100 == 0 && tick > 0
                        && Files.exists(configDir.resolve("storage").resolve("e2e-suite.json"))) {
                    LOGGER.info("NeoMacros E2E results present; shutting down client");
                    webService.stop();
                    webService.saveDevices();
                    configManager.save();
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
        // Unload sinks receive the uuid resolved from the tracking id; an
        // entity never seen loading (stale login-replaced entries) has none
        // and cannot form a valid snapshot, so the event is skipped.
        ink.tuanzi.neomacros.adapter.EntityLifecycleHub.onUnload(uuid -> {
            if (uuid == null) {
                return;
            }
            bus.publish("entity:unload", new ink.tuanzi.neomacros.core.event.def.entity.EntityUnloadEvent(
                    new ink.tuanzi.neomacros.port.model.EntitySnapshot(
                            java.util.UUID.fromString(uuid), "", "",
                            new ink.tuanzi.neomacros.port.model.Pos3d(0, 0, 0), 0)));
        });
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
        registerCommands(trustManager, errorPresenter, pendingTrusts);
        // Controls-menu key (default N): opens the management panel.
        ink.tuanzi.neomacros.adapter.OpenPanelKeybind.register(() -> {
            var screen = createConfigPanel(null);
            if (screen != null) {
                ScriptManagerScreen.show(screen);
            }
        });
        preRegisterScriptBindings();
    }

    /**
     * Registers one key mapping per discovered keypress macro during
     * client init, while fabric api still accepts registrations. When the
     * script actually loads, the binding hook reuses the existing mapping
     * and only re-attaches its sink.
     */
    private static void preRegisterScriptBindings() {
        for (ScriptEntry entry : modules.discover()) {
            if (entry.category() != ScriptCategory.KEYPRESS
                    || !options.autoload.getOrDefault(entry.name(), true)) {
                continue;
            }
            String key = entry.effectiveKey(options.keybinds);
            if (key.isEmpty()) {
                continue;
            }
            String name = entry.name();
            adapter.input().registerScriptBinding(name, key,
                    clicks -> modules.trigger(name, Map.of("key", key, "clicks", clicks)));
        }
    }

    /** Registers (or re-registers) one loaded keypress script's key binding. */
    private static void registerScriptBinding(LoadedModule module) {
        if (module.entry().category() != ScriptCategory.KEYPRESS) {
            return;
        }
        String key = module.entry().effectiveKey(options.keybinds);
        if (key.isEmpty()) {
            return;
        }
        String name = module.meta().name();
        adapter.input().registerScriptBinding(name, key,
                clicks -> modules.trigger(name, Map.of("key", key, "clicks", clicks)));
    }

    /** Logs a one-time notice when the legacy flat scripts directory still holds scripts. */
    private static void noteLegacyScriptsDir() {
        Path legacy = configDir.resolve("scripts");
        if (!Files.isDirectory(legacy)) {
            return;
        }
        long count;
        try (Stream<Path> files = Files.list(legacy)) {
            count = files.filter(p -> p.getFileName().toString().endsWith(".js")).count();
        } catch (IOException e) {
            return;
        }
        if (count > 0) {
            LOGGER.warn("neomacros: {} flat script(s) remain in the legacy folder {} and are no"
                    + " longer loaded; move each one into macros/<category>/<name>/ with a"
                    + " neomacro.yml manifest", count, legacy);
        }
    }

    /** The running Minecraft version (node trimming key for ApiMC ranges). */
    private static String mcVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("26.3");
    }

    // ---- command helpers -------------------------------------------------

    /** Suggests script names from one category directory. */
    private static SuggestionProvider<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> suggestCategory(
            ScriptCategory category) {
        return (ctx, builder) -> {
            modules.discover().stream()
                    .filter(e -> e.category() == category)
                    .map(ScriptEntry::name)
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    /** Suggests script names across every category. */
    private static final SuggestionProvider<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>
            SUGGEST_ANY = (ctx, builder) -> {
        modules.discover().forEach(e -> builder.suggest(e.name()));
        return builder.buildFuture();
    };

    /** Suggests the category directory names. */
    private static final SuggestionProvider<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>
            SUGGEST_CATEGORY = (ctx, builder) -> {
        for (ScriptCategory category : ScriptCategory.values()) {
            builder.suggest(category.dirName());
        }
        return builder.buildFuture();
    };

    /** Suggests a few common keyboard keys (free-form names still accepted). */
    private static final SuggestionProvider<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>
            SUGGEST_KEY = (ctx, builder) -> {
        COMMON_KEYS.forEach(builder::suggest);
        return builder.buildFuture();
    };

    /** The discovered entry with the given script name, if any. */
    private static Optional<ScriptEntry> entry(String name) {
        return modules.discover().stream()
                .filter(e -> e.name().equals(name))
                .findFirst();
    }

    private static String string(CommandContext<?> ctx, String arg) {
        return StringArgumentType.getString(ctx, arg);
    }

    /** One chat line: the lang key resolved with tagged accents. */
    private static void feedback(CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx,
                                 String key, Object... args) {
        feedbackText(ctx, UiText.tr(key, args));
    }

    /** One chat line from a pre-resolved (already colored) message. */
    private static void feedbackText(CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx,
                                     String message) {
        ctx.getSource().sendFeedback(Component.literal(Msg.line(message)));
    }

    /** Registers the root {@code /neomacros} command and its subcommands. */
    private static void registerCommands(TrustManager trustManager,
            ink.tuanzi.neomacros.core.runtime.ErrorPresenter errorPresenter,
            java.util.concurrent.ConcurrentHashMap<String, String> pendingTrusts) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("neomacros")
                    .then(literal("status").executes(ctx -> {
                        feedback(ctx, "neomacros.cmd.status",
                                Msg.name(NeoMacros.VERSION),
                                Msg.num(runtime.eventCatalog().all().size()),
                                Msg.num(modules.loaded().size()),
                                Msg.num(modules.errors().size()),
                                Msg.num(runtime.tickClock().currentTick()));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(literal("list")
                            .executes(ctx -> {
                                feedbackText(ctx, describeScripts(null));
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(argument("category", StringArgumentType.word())
                                    .suggests(SUGGEST_CATEGORY)
                                    .executes(ctx -> {
                                        ScriptCategory category = ScriptCategory
                                                .fromDirName(string(ctx, "category"));
                                        if (category == null) {
                                            feedback(ctx, "neomacros.cmd.unknown_category",
                                                    Msg.name(string(ctx, "category")));
                                            return 0;
                                        }
                                        feedbackText(ctx, describeScripts(category));
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(literal("reload").executes(ctx -> {
                        modules.reloadAll();
                        feedback(ctx, "neomacros.cmd.reloaded", Msg.num(modules.loaded().size()));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(literal("keypress")
                            .then(literal("bind")
                                    .then(argument("script", StringArgumentType.word())
                                            .suggests(suggestCategory(ScriptCategory.KEYPRESS))
                                            .then(argument("key", StringArgumentType.word())
                                                    .suggests(SUGGEST_KEY)
                                                    .executes(ctx -> {
                                                        String name = string(ctx, "script");
                                                        String key = string(ctx, "key");
                                                        if (entry(name).isEmpty()) {
                                                            feedback(ctx,
                                                                    "neomacros.cmd.unknown_script",
                                                                    Msg.name(name));
                                                            return 0;
                                                        }
                                                        options.keybinds.put(name, key);
                                                        configManager.save();
                                                        reloadIfLoaded(name);
                                                        feedback(ctx, "neomacros.ui.panel.bound",
                                                                Msg.name(name), Msg.num(key));
                                                        return Command.SINGLE_SUCCESS;
                                                    }))))
                            .then(literal("unbind")
                                    .then(argument("script", StringArgumentType.word())
                                            .suggests(suggestCategory(ScriptCategory.KEYPRESS))
                                            .executes(ctx -> {
                                                String name = string(ctx, "script");
                                                if (entry(name).isEmpty()) {
                                                    feedback(ctx,
                                                            "neomacros.cmd.unknown_script",
                                                            Msg.name(name));
                                                    return 0;
                                                }
                                                // The empty value is a persisted unbind:
                                                // without it the manifest key would return.
                                                options.keybinds.put(name, "");
                                                configManager.save();
                                                reloadIfLoaded(name);
                                                feedback(ctx, "neomacros.ui.panel.unbound",
                                                        Msg.name(name));
                                                return Command.SINGLE_SUCCESS;
                                            })))
                            .then(literal("list").executes(ctx -> {
                                feedback(ctx, describeScripts(ScriptCategory.KEYPRESS));
                                return Command.SINGLE_SUCCESS;
                            })))
                    .then(literal("service")
                            .then(literal("start")
                                    .then(argument("script", StringArgumentType.word())
                                            .suggests(suggestCategory(ScriptCategory.SERVICES))
                                            .executes(ctx -> {
                                                String name = string(ctx, "script");
                                                boolean started = modules.loadByName(name).isPresent();
                                                feedback(ctx, started
                                                        ? "neomacros.cmd.started" : "neomacros.cmd.cannot_start",
                                                        Msg.name(name));
                                                return started ? Command.SINGLE_SUCCESS : 0;
                                            })))
                            .then(literal("stop")
                                    .then(argument("script", StringArgumentType.word())
                                            .suggests(suggestCategory(ScriptCategory.SERVICES))
                                            .executes(ctx -> {
                                                String name = string(ctx, "script");
                                                if (modules.loaded(name).isEmpty()) {
                                                    feedback(ctx, "neomacros.cmd.not_running", Msg.name(name));
                                                    return 0;
                                                }
                                                modules.unload(name);
                                                feedback(ctx, "neomacros.cmd.stopped", Msg.name(name));
                                                return Command.SINGLE_SUCCESS;
                                            })))
                            .then(literal("restart")
                                    .then(argument("script", StringArgumentType.word())
                                            .suggests(suggestCategory(ScriptCategory.SERVICES))
                                            .executes(ctx -> {
                                                String name = string(ctx, "script");
                                                modules.unload(name);
                                                boolean started = modules.loadByName(name).isPresent();
                                                feedback(ctx, started
                                                        ? "neomacros.cmd.restarted" : "neomacros.cmd.cannot_start",
                                                        Msg.name(name));
                                                return started ? Command.SINGLE_SUCCESS : 0;
                                            })))
                            .then(literal("list").executes(ctx -> {
                                feedback(ctx, describeScripts(ScriptCategory.SERVICES));
                                return Command.SINGLE_SUCCESS;
                            })))
                    .then(literal("trust")
                            .then(literal("list").executes(ctx -> {
                                feedback(ctx, "neomacros.cmd.trusted_hashes",
                                        Msg.num(trustManager.trustedHashes().size()));
                                return Command.SINGLE_SUCCESS;
                            }))
                            .then(argument("script", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String name = string(ctx, "script");
                                        String hash = pendingTrusts.get(name);
                                        if (hash == null) {
                                            feedback(ctx, "neomacros.cmd.no_pending_trust_from",
                                                    Msg.name(name));
                                            return 0;
                                        }
                                        trustManager.trust(hash);
                                        pendingTrusts.remove(name);
                                        configManager.save();
                                        feedback(ctx, "neomacros.cmd.trusted_apply", Msg.name(name));
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(literal("edit")
                            .then(argument("script", StringArgumentType.word())
                                    .suggests(SUGGEST_ANY)
                                    .executes(ctx -> {
                                        Optional<ScriptEntry> found = entry(string(ctx, "script"));
                                        if (found.isEmpty()) {
                                            feedback(ctx, "neomacros.cmd.unknown_script_panel");
                                            return 0;
                                        }
                                        ScriptEditorScreen.open(found.get().entryFile(), modules::reloadAll);
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(literal("panel").executes(ctx -> {
                        openPanel();
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(literal("errors").executes(ctx -> {
                        var recent = errorPresenter.recent();
                        var lines = new java.util.ArrayList<String>(recent);
                        for (ink.tuanzi.neomacros.core.module.ScriptLoadError error : modules.errors()) {
                            lines.add(UiText.tr("neomacros.cmd.invalid_folder",
                                    error.folder().getFileName(), error.message()));
                        }
                        ScriptScreen.open(UiText.tr("neomacros.cmd.recent_errors"),
                                lines.isEmpty() ? UiText.tr("neomacros.cmd.no_recent_errors")
                                        : String.join("\n\n", lines));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(literal("open")
                            .then(argument("script", StringArgumentType.word())
                                    .suggests(SUGGEST_ANY)
                                    .executes(ctx -> {
                                        Optional<ScriptEntry> found = entry(string(ctx, "script"));
                                        if (found.isEmpty()) {
                                            feedback(ctx, "neomacros.cmd.unknown_script",
                                                    Msg.name(string(ctx, "script")));
                                            return 0;
                                        }
                                        try {
                                            ScriptScreen.open(found.get().name(),
                                                    Files.readString(found.get().entryFile()));
                                        } catch (IOException e) {
                                            feedback(ctx, "neomacros.cmd.cannot_read",
                                                    Msg.err(e.getMessage()));
                                        }
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(WebCommands.subtree(webContext())));
        });
    }

    /** The web commands' collaborators over the live runtime. */
    private static WebCommands.Context webContext() {
        WebCommands.LinkBuilder links = () -> {
            if (!webService.running()) {
                throw new IllegalStateException("the web service is not running");
            }
            String panel = options.web.panelUrl == null || options.web.panelUrl.isBlank()
                    ? "https://panel.neomacros.app" : options.web.panelUrl.strip();
            while (panel.endsWith("/")) {
                panel = panel.substring(0, panel.length() - 1);
            }
            int hash = panel.indexOf('#');
            if (hash >= 0) {
                panel = panel.substring(0, hash);
            }
            // Query-parameter deep link: the panel reads search params and
            // clears them with history.replaceState. The one-time 120s token
            // plus the in-game approve gate are the security boundary.
            return new StringBuilder(panel)
                    .append("/?host=127.0.0.1:").append(webService.port())
                    .append("&view=editor&pair=")
                    .append(webService.newPairingToken())
                    .toString();
        };
        return new WebCommands.Context(webService, links);
    }

    /** Reloads one script when it is currently loaded (live binding application). */
    private static void reloadIfLoaded(String name) {
        if (modules.loaded(name).isPresent()) {
            modules.reload(name);
        }
    }

    /** One-line digest of the discovered scripts, optionally per category. */
    private static String describeScripts(ScriptCategory category) {
        modules.discover();
        var lines = new java.util.ArrayList<String>();
        for (ScriptCategory cat : ScriptCategory.values()) {
            if (category != null && cat != category) {
                continue;
            }
            List<ScriptEntry> entries = modules.entries().stream()
                    .filter(e -> e.category() == cat)
                    .toList();
            lines.add("§f[" + cat.dirName() + "]§r");
            for (ScriptEntry entry : entries) {
                String state = entryState(entry.name());
                String key = entry.category() == ScriptCategory.KEYPRESS
                        ? entry.effectiveKey(options.keybinds) : "";
                lines.add("  " + Msg.name(entry.name()) + " "
                        + Msg.dim(UiText.tr("neomacros.ui.state.version",
                                entry.manifest().version(), entry.manifest().author()))
                        + (key.isEmpty() ? "" : "  " + Msg.num(key))
                        + "  " + state);
            }
            if (entries.isEmpty()) {
                lines.add("  " + Msg.dim(UiText.tr("neomacros.cmd.none")));
            }
        }
        for (ink.tuanzi.neomacros.core.module.ScriptLoadError error : modules.errors()) {
            lines.add("  " + UiText.tr("neomacros.cmd.invalid_folder",
                    Msg.err(error.folder().getFileName()), Msg.err(error.message())));
        }
        return String.join("\n", lines);
    }

    /** The localized run-state word for one script (loaded/stopped/disabled). */
    private static String entryState(String name) {
        if (!options.autoload.getOrDefault(name, true)) {
            return "§8" + UiText.tr("neomacros.ui.state.disabled") + "§r";
        }
        return modules.loaded(name).isPresent()
                ? Msg.ok(UiText.tr("neomacros.ui.state.loaded"))
                : Msg.dim(UiText.tr("neomacros.ui.state.stopped"));
    }

    /** Builds the panel row snapshot: category headers, scripts, and error rows. */
    private static List<ScriptManagerScreen.RowData> buildPanelRows(
            java.util.concurrent.ConcurrentHashMap<String, String> pendingTrusts) {
        modules.discover();
        List<ScriptManagerScreen.RowData> rows = new java.util.ArrayList<>();
        for (ScriptCategory category : ScriptCategory.values()) {
            rows.add(new ScriptManagerScreen.RowData("", "[" + category.dirName() + "]",
                    "", false, true, false, null));
            for (ScriptEntry entry : modules.entries().stream()
                    .filter(e -> e.category() == category).toList()) {
                String name = entry.name();
                String state = entryState(name);
                if (category == ScriptCategory.KEYPRESS) {
                    String key = entry.effectiveKey(options.keybinds);
                    state += "  " + UiText.tr("neomacros.ui.state.key",
                            key.isEmpty() ? UiText.tr("neomacros.ui.state.unbound") : key);
                }
                state += "  " + UiText.tr("neomacros.ui.state.version",
                        entry.manifest().version(), entry.manifest().author());
                rows.add(new ScriptManagerScreen.RowData(name,
                        name + "  (" + entry.folder().getFileName() + ")", state,
                        pendingTrusts.containsKey(name), false,
                        category == ScriptCategory.KEYPRESS, entry.entryFile()));
            }
            for (ink.tuanzi.neomacros.core.module.ScriptLoadError error : modules.errors()) {
                if (error.folder().getParent().getFileName().toString().equals(category.dirName())) {
                    rows.add(new ScriptManagerScreen.RowData("",
                            "  " + error.folder().getFileName(), error.message(),
                            false, false, false, null));
                }
            }
        }
        return rows;
    }

    /**
     * Builds the management panel for the ModMenu config button. Returns
     * null before bootstrap has run (ModMenu then offers no config screen).
     */
    public static net.minecraft.client.gui.screens.Screen createConfigPanel(
            net.minecraft.client.gui.screens.Screen parent) {
        if (modules == null || trustManager == null || pendingTrusts == null) {
            return null;
        }
        return ScriptManagerScreen.create(() -> buildPanelRows(pendingTrusts),
                pendingTrusts,
                (name, key) -> {
                    options.keybinds.put(name, key);
                    configManager.save();
                    reloadIfLoaded(name);
                },
                name -> {
                    options.keybinds.put(name, "");
                    configManager.save();
                    reloadIfLoaded(name);
                },
                (name, enabled) -> {
                    options.autoload.put(name, enabled);
                    configManager.save();
                    if (enabled) {
                        if (modules.loaded(name).isEmpty()) {
                            modules.loadByName(name);
                        }
                    } else {
                        modules.unload(name);
                    }
                },
                categoryDir -> {
                    createScaffold(categoryDir);
                    modules.checkForChanges();
                },
                modules::reloadAll);
    }

    /** Config bridge for the optional Cloth settings screen. */
    static CoreOptions configOptions() {
        return options;
    }

    /** Saves the config; used as the Cloth screen save hook. */
    static void saveConfig() {
        configManager.save();
    }

    /**
     * Applies changed web settings to a running panel by restarting it;
     * a bind failure (e.g. the new port is taken) stays a log line and the
     * panel stays stopped.
     */
    static void restartWebIfRunning() {
        if (webService == null || !webService.running()) {
            return;
        }
        webService.stop();
        try {
            webService.start();
        } catch (java.io.IOException e) {
            LOGGER.warn("neomacros: web panel restart failed: {}", e.getMessage());
        }
    }

    /** Opens the management screen on the client thread. */
    private static void openPanel() {
        var screen = createConfigPanel(null);
        if (screen != null) {
            ScriptManagerScreen.show(screen);
        }
    }

    /** Creates a minimal new-script folder (manifest + index.js) in a category. */
    private static void createScaffold(String categoryDirName) {
        Path categoryDir = macrosDir.resolve(categoryDirName);
        String base = "new-script";
        Path folder = categoryDir.resolve(base);
        int n = 2;
        while (Files.exists(folder)) {
            folder = categoryDir.resolve(base + "-" + n++);
        }
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("neomacro.yml"), String.join("\n",
                    "name: " + folder.getFileName(),
                    "version: 0.0.1",
                    "author: your-name",
                    "description: (describe this macro)",
                    ""));
            Files.writeString(folder.resolve("index.js"), String.join("\n",
                    "// Entry script: top-level code runs once at load.",
                    "import { chat } from '@neomacros/chat';",
                    "",
                    "chat.log('" + folder.getFileName() + " loaded');",
                    ""));
        } catch (IOException e) {
            LOGGER.warn("neomacros: cannot create scaffold: {}", e.getMessage());
        }
    }

    /**
     * Module source resolver over the macros tree. Virtual module specifiers
     * resolve through the binding registry; file specifiers resolve relative
     * to the macros root so scripts can import their own folder siblings.
     */
    private record ScriptsModuleResolver(Path macrosDir, BindingRegistry registry) implements ModuleResolver {
        @Override
        public Optional<String> virtualSource(String specifier) {
            return registry.virtualSourceFor(specifier, PermissionView.ALLOW_ALL);
        }

        @Override
        public Optional<Path> fileFor(String specifier) {
            Path file = macrosDir.resolve(specifier);
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
