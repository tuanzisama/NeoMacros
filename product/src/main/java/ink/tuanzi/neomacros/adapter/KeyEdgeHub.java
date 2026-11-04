package ink.tuanzi.neomacros.adapter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fan-out point for raw keyboard edges; the key-mapping mixin forwards the
 * vanilla key-state updates here and the bootstrap registers the routing
 * sink that drives keydown/keyup macros. Down/up transitions are
 * deduplicated (the vanilla store also receives repeat notifications).
 */
public final class KeyEdgeHub {

    /** One deduplicated keyboard edge. */
    public record RawKey(String keyName, int keyCode, boolean down) {
    }

    private static final List<Consumer<RawKey>> SINKS = new CopyOnWriteArrayList<>();
    private static final Map<String, Boolean> LAST_DOWN = new ConcurrentHashMap<>();

    private KeyEdgeHub() {
    }

    /** Registers a raw key sink. */
    public static void register(Consumer<RawKey> sink) {
        SINKS.add(sink);
    }

    /** Delivers one key-state update to every registered sink on transitions. */
    public static void fire(String keyName, int keyCode, boolean down) {
        Boolean last = LAST_DOWN.put(keyName, down);
        if (last != null && last == down) {
            return;
        }
        if (SINKS.isEmpty()) {
            return;
        }
        RawKey event = new RawKey(keyName, keyCode, down);
        for (Consumer<RawKey> sink : SINKS) {
            sink.accept(event);
        }
    }
}
