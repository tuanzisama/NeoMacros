package ink.tuanzi.neomacros.port;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Game lifecycle sources: startup and shutdown, world sessions, and the
 * per-tick heartbeat.
 *
 * <p>Sink registration methods feed the event framework: core registers a
 * publishing sink per source, and the adapter invokes every registered sink
 * from its game callbacks in registration order. The tick sink receives the
 * running tick count of the client session.</p>
 */
public interface LifecyclePort {

    /** Registers a sink invoked once per client tick with the tick count. */
    void onTick(Consumer<Long> sink);

    /** Registers a sink invoked when the game client has started. */
    void onGameStart(Runnable sink);

    /** Registers a sink invoked when the game client is shutting down. */
    void onGameQuit(Runnable sink);

    /** Registers a sink invoked when the player joins a world. */
    void onWorldJoin(Runnable sink);

    /** Registers a sink invoked when the player leaves a world. */
    void onWorldLeave(Runnable sink);

    /** Supplies whether the player is currently in a loaded world. */
    Supplier<Boolean> inWorld();
}
