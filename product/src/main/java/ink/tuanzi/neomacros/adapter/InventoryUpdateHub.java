package ink.tuanzi.neomacros.adapter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fan-out point for server container sync packets. The client packet mixin
 * calls {@link #fire}; the bootstrap publishes inventory:update from here.
 * Sinks run on the game main thread in registration order.
 */
public final class InventoryUpdateHub {

    /** One slot sync: menu id, inventory kind, slot index, full-menu flag. */
    public record SlotUpdate(int containerId, String kind, int slot, boolean full) {

        /** The update mode label ({@code slot} or {@code all}). */
        public String mode() {
            return full ? "all" : "slot";
        }
    }

    private static final List<Consumer<SlotUpdate>> SINKS = new CopyOnWriteArrayList<>();

    private InventoryUpdateHub() {
    }

    /** Registers an update sink. */
    public static void register(Consumer<SlotUpdate> sink) {
        SINKS.add(sink);
    }

    /** Delivers one slot sync to every registered sink. */
    public static void fire(SlotUpdate update) {
        for (Consumer<SlotUpdate> sink : SINKS) {
            sink.accept(update);
        }
    }

    /** Convenience overload resolving the inventory kind from the open menu. */
    public static void fire(int containerId, int slot, boolean full) {
        var menu = FabricInventoryPort.openMenu();
        fire(new SlotUpdate(containerId,
                menu != null ? FabricInventoryPort.menuKind(menu) : "player",
                slot, full));
    }
}
