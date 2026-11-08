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

    /**
     * Requests a graceful client shutdown (test harness quit-on-done; the
     * game may decline or delay). Implementations that cannot shut the game
     * down throw {@link UnsupportedPortFeature}.
     */
    /** Returns whether a screen (GUI) is currently open. */
    default boolean isScreenOpen() {
        return false;
    }

    /** Returns the title of the currently open screen, or empty. */
    default java.util.Optional<String> screenTitle() {
        return java.util.Optional.empty();
    }

    /** One neutral snapshot of the currently open screen (MC class name, kind label, title). */
    record ScreenInfo(String screenClass, String kind, String title) {
    }

    /** The currently open screen snapshot, or empty when no screen is open. */
    default java.util.Optional<ScreenInfo> screenInfo() {
        return java.util.Optional.empty();
    }

    /** The text currently typed into an open chat screen, or empty. */
    default java.util.Optional<String> chatText() {
        return java.util.Optional.empty();
    }

    /** The front-face text of an open sign editor, or empty. */
    default java.util.Optional<String> signText() {
        return java.util.Optional.empty();
    }

    /** The page text currently edited in an open book editor, or empty. */
    default java.util.Optional<String> bookEditPage() {
        return java.util.Optional.empty();
    }

    /** Closes the currently open screen (the same path as pressing Esc). */
    default void closeScreen() {
        throw new UnsupportedPortFeature("closeScreen");
    }

    default void requestShutdown() {
        throw new UnsupportedPortFeature("shutdown");
    }
}
