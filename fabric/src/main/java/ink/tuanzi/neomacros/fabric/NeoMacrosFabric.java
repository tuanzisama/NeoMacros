package ink.tuanzi.neomacros.fabric;

import com.mojang.brigadier.Command;
import ink.tuanzi.neomacros.adapter.NeomacrosAdapterImpl;
import ink.tuanzi.neomacros.core.NeoMacros;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.event.def.chat.ChatReceiveEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameQuitEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameStartEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.WorldJoinEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.WorldLeaveEvent;
import ink.tuanzi.neomacros.core.event.def.tick.TickEvent;
import ink.tuanzi.neomacros.core.event.SyncWatchdog;
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

    @Override
    public void onInitializeClient() {
        LOGGER.info("NeoMacros bootstrap");
        Path scriptsDir = FabricLoader.getInstance().getConfigDir()
                .resolve(NeoMacros.MOD_ID).resolve("scripts");

        var adapter = new NeomacrosAdapterImpl();
        var catalog = new EventCatalog();
        catalog.scanPackage("ink.tuanzi.neomacros.core.event.def");
        var pool = new ScriptThreadPool(Runtime.getRuntime().availableProcessors());
        var bus = new EventBus(catalog, pool.workers());
        ScriptEngine engine = new GraalJsEngine(new ScriptsModuleResolver(scriptsDir));
        runtime = new ScriptRuntime(engine, adapter, catalog, bus, pool, SyncWatchdog.DEFAULT_MAX_SYNC_MILLIS);

        wireEventSinks(adapter, runtime);
        ClientTickEvents.START_CLIENT_TICK.register(mc -> runtime.mainThread().runQueuedTasks());
        registerCommands(scriptsDir);
    }

    /** Feeds the event bus from the adapter sinks (one publish per source). */
    private static void wireEventSinks(NeomacrosAdapterImpl adapter, ScriptRuntime rt) {
        adapter.lifecycle().onTick(tick -> {
            rt.tickClock().tick();
            rt.eventBus().publish("tick", new TickEvent(tick));
        });
        adapter.lifecycle().onGameStart(() -> rt.eventBus().publish("game:start", new GameStartEvent()));
        adapter.lifecycle().onGameQuit(() -> rt.eventBus().publish("game:quit", new GameQuitEvent()));
        adapter.lifecycle().onWorldJoin(() -> rt.eventBus().publish("world:join", new WorldJoinEvent()));
        adapter.lifecycle().onWorldLeave(() -> rt.eventBus().publish("world:leave", new WorldLeaveEvent()));
        adapter.chat().onChatReceive(message -> rt.eventBus().publish("chat:receive", new ChatReceiveEvent(message)));
    }

    /** Registers the root {@code /neomacros} command and its subcommands. */
    private static void registerCommands(Path scriptsDir) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("neomacros")
                    .then(literal("status").executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal(
                                "NeoMacros " + NeoMacros.VERSION + " | events registered: "
                                        + runtime.eventCatalog().all().size() + " | ticks: "
                                        + runtime.tickClock().currentTick()));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(literal("list").executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal(
                                "Scripts in " + scriptsDir + ": " + listScripts(scriptsDir)));
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
    private record ScriptsModuleResolver(Path scriptsDir) implements ModuleResolver {
        @Override
        public Optional<String> virtualSource(String specifier) {
            return Optional.empty();
        }

        @Override
        public Optional<Path> fileFor(String specifier) {
            Path file = scriptsDir.resolve(specifier);
            return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
        }
    }
}
