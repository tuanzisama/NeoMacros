package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.InventoryPort;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Fan-out points for the two drop paths (menu THROW clicks and the hotbar Q
 * key), the slot/held sync sources, and item pickups. Drop sinks run before
 * the drop is applied and any returning {@code true} cancels it; sync and
 * pickup sinks observe only. All sinks run on the game main thread in
 * registration order.
 */
public final class InventoryEventHubs {

    private static final List<Function<InventoryPort.DropSlotInfo, Boolean>> DROP_SINKS =
            new CopyOnWriteArrayList<>();
    private static final List<Consumer<InventoryPort.SlotUpdateInfo>> SLOT_SINKS =
            new CopyOnWriteArrayList<>();
    private static final List<Consumer<InventoryPort.HeldUpdateInfo>> HELD_SINKS =
            new CopyOnWriteArrayList<>();
    private static final List<Consumer<InventoryPort.ItemPickupInfo>> PICKUP_SINKS =
            new CopyOnWriteArrayList<>();

    private InventoryEventHubs() {
    }

    /** Registers a drop interceptor. */
    public static void onDrop(Function<InventoryPort.DropSlotInfo, Boolean> sink) {
        DROP_SINKS.add(sink);
    }

    /** Registers a slot-sync observer. */
    public static void onSlotUpdate(Consumer<InventoryPort.SlotUpdateInfo> sink) {
        SLOT_SINKS.add(sink);
    }

    /** Registers a cursor-stack observer. */
    public static void onHeldUpdate(Consumer<InventoryPort.HeldUpdateInfo> sink) {
        HELD_SINKS.add(sink);
    }

    /** Registers a pickup observer. */
    public static void onPickup(Consumer<InventoryPort.ItemPickupInfo> sink) {
        PICKUP_SINKS.add(sink);
    }

    /** Returns whether any drop sink cancels the drop. */
    public static boolean fireDrop(InventoryPort.DropSlotInfo drop) {
        boolean canceled = false;
        for (Function<InventoryPort.DropSlotInfo, Boolean> sink : DROP_SINKS) {
            if (Boolean.TRUE.equals(sink.apply(drop))) {
                canceled = true;
            }
        }
        return canceled;
    }

    /** Delivers one slot sync. */
    public static void fireSlotUpdate(InventoryPort.SlotUpdateInfo update) {
        for (Consumer<InventoryPort.SlotUpdateInfo> sink : SLOT_SINKS) {
            sink.accept(update);
        }
    }

    /** Delivers one cursor-stack sync. */
    public static void fireHeldUpdate(InventoryPort.HeldUpdateInfo update) {
        for (Consumer<InventoryPort.HeldUpdateInfo> sink : HELD_SINKS) {
            sink.accept(update);
        }
    }

    /** Delivers one pickup. */
    public static void firePickup(InventoryPort.ItemPickupInfo pickup) {
        for (Consumer<InventoryPort.ItemPickupInfo> sink : PICKUP_SINKS) {
            sink.accept(pickup);
        }
    }
}
