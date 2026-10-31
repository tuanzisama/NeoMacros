package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.LifecyclePort;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lifecycle sources backed by the Fabric client lifecycle, tick, and play
 * connection events. Sinks run on the game main thread, in registration
 * order.
 */
public final class FabricLifecyclePort implements LifecyclePort {

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
                sink.accept(tick);
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            for (Runnable sink : gameStartSinks) {
                sink.run();
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            for (Runnable sink : gameQuitSinks) {
                sink.run();
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            for (Runnable sink : worldJoinSinks) {
                sink.run();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            for (Runnable sink : worldLeaveSinks) {
                sink.run();
            }
        });
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
    public Supplier<Boolean> inWorld() {
        return () -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.level != null;
        };
    }
}
