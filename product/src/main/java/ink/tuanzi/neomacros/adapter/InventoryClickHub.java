package ink.tuanzi.neomacros.adapter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fan-out point for processed container slot clicks (manual and
 * script-issued clicks share the game-mode path). The game-mode mixin calls
 * {@link #fire}; the bootstrap publishes inventory:click from here. Sinks
 * run on the game main thread in registration order.
 */
public final class InventoryClickHub {

    /** One processed click: menu id, inventory kind, slot, button, quick-move flag. */
    public record ClickInfo(int containerId, String kind, int slot, int button, boolean shift) {
    }

    private static final List<Consumer<ClickInfo>> SINKS = new CopyOnWriteArrayList<>();

    private InventoryClickHub() {
    }

    /** Registers a click sink. */
    public static void register(Consumer<ClickInfo> sink) {
        SINKS.add(sink);
    }

    /** Delivers one processed click to every registered sink. */
    public static void fire(ClickInfo click) {
        for (Consumer<ClickInfo> sink : SINKS) {
            sink.accept(click);
        }
    }
}
