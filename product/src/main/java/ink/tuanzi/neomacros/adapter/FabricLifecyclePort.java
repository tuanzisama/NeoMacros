package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.LifecyclePort;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lifecycle sources backed by the Fabric client lifecycle, tick, and play
 * connection events. Sinks run on the game main thread, in registration
 * order; a sink that throws is logged and skipped so it can neither break
 * the remaining sinks nor crash the game's own tick/shutdown path.
 */
public final class FabricLifecyclePort implements LifecyclePort {

    private static final Logger LOGGER = LoggerFactory.getLogger(FabricLifecyclePort.class);

    private final List<Consumer<Long>> tickSinks = new CopyOnWriteArrayList<>();
    private final List<Runnable> gameStartSinks = new CopyOnWriteArrayList<>();
    private final List<Runnable> gameQuitSinks = new CopyOnWriteArrayList<>();
    private final List<Runnable> worldJoinSinks = new CopyOnWriteArrayList<>();
    private final List<Runnable> worldLeaveSinks = new CopyOnWriteArrayList<>();
    private final AtomicLong tickCount = new AtomicLong();

    /** Creates the port and hooks the Fabric client events feeding the sinks. */
    public FabricLifecyclePort() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long tick = tickCount.incrementAndGet();
            for (Consumer<Long> sink : tickSinks) {
                runSafely("tick", () -> sink.accept(tick));
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            for (Runnable sink : gameStartSinks) {
                runSafely("game-start", sink);
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            for (Runnable sink : gameQuitSinks) {
                runSafely("game-quit", sink);
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            for (Runnable sink : worldJoinSinks) {
                runSafely("world-join", sink);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            for (Runnable sink : worldLeaveSinks) {
                runSafely("world-leave", sink);
            }
        });
    }

    /** Runs one sink, downgrading any failure to a log record. */
    private static void runSafely(String source, Runnable sink) {
        try {
            sink.run();
        } catch (Throwable t) {
            LOGGER.error("NeoMacros lifecycle sink failed during {}; continuing", source, t);
        }
    }

    @Override
    public void onTick(Consumer<Long> sink) {
        tickSinks.add(sink);
    }

    @Override
    public void onGameStart(Runnable sink) {
        gameStartSinks.add(sink);
    }

    @Override
    public void onGameQuit(Runnable sink) {
        gameQuitSinks.add(sink);
    }

    @Override
    public void onWorldJoin(Runnable sink) {
        worldJoinSinks.add(sink);
    }

    @Override
    public void onWorldLeave(Runnable sink) {
        worldLeaveSinks.add(sink);
    }

    @Override
    public void requestShutdown() {
        Minecraft.getInstance().stop();
    }

    @Override
    public Supplier<Boolean> inWorld() {
        return () -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.level != null;
        };
    }

    /** The current screen accessor moved onto Gui in 26.2 (same guard as the key hook). */
    private static net.minecraft.client.gui.screens.Screen currentScreen() {
        //? if >=26.2 {
        return Minecraft.getInstance().gui.screen();
        //?} else {
        /*
        return Minecraft.getInstance().screen;
        */
        //?}
    }

    @Override
    public boolean isScreenOpen() {
        return currentScreen() != null;
    }

    @Override
    public java.util.Optional<String> screenTitle() {
        var screen = currentScreen();
        if (screen == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(screen.getTitle() == null
                ? null : screen.getTitle().getString());
    }
}
