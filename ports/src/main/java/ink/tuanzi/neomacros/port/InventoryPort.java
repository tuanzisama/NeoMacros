package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.ItemStackData;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Inventory queries over the local player: hotbar and main inventory slots,
 * and the currently held item.
 *
 * <p>Slot indices follow the vanilla container order (0-8 hotbar, 9-35 main).
 * All methods answer on the calling thread with a point-in-time snapshot;
 * callers needing main-thread isolation marshal through the lifecycle port's
 * main-thread bridge.</p>
 */
public interface InventoryPort {

    /** Returns the held (selected hotbar) stack, or empty with no player. */
    java.util.function.Supplier<Optional<ItemStackData>> heldItem();

    /**
     * Reads one slot.
     *
     * @param index slot index 0-35 (vanilla container order)
     * @return snapshot of the stack, or empty when out of range or empty
     */
    Optional<ItemStackData> slot(int index);

    /** Returns all non-empty stacks (hotbar 0-8, main 9-35) with slot indices. */
    java.util.function.Supplier<List<IndexedStack>> allItems();

    /** Counts occurrences of one item id across the inventory. */
    int countOf(String item);

    /** One stack with its slot index. */
    record IndexedStack(int slot, ItemStackData stack) {
    }
}
