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

    /** Metadata of the currently open container screen (menu id 0 is the player inventory). */
    record ContainerInfo(String title, String kind, int size, int containerId) {
    }

    /** The currently open container, or empty when no screen is open. */
    default Optional<ContainerInfo> openedContainer() {
        return Optional.empty();
    }

    /**
     * Reads one slot of the currently open container menu (menu slot order,
     * not the player inventory order), or empty without an open menu.
     */
    default Optional<ItemStackData> menuSlot(int index) {
        return Optional.empty();
    }

    /**
     * Returns the live container state (e.g. furnace {@code isLit},
     * {@code burnProgress}, {@code litProgress}) keyed by member name; an
     * empty map for menus without state.
     */
    default java.util.Map<String, Object> containerState() {
        return java.util.Map.of();
    }

    /** Left/right-clicks a container slot (shift = quick move). */
    default java.util.concurrent.CompletableFuture<Void> click(
            int slot, int button, boolean shift) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Quick-moves a container slot to the other inventory. */
    default java.util.concurrent.CompletableFuture<Void> quickMove(int slot) {
        return click(slot, 0, true);
    }

    /** Drops the stack from a container slot. */
    default java.util.concurrent.CompletableFuture<Void> drop(int slot, boolean all) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Swaps a container slot with a hotbar slot. */
    default java.util.concurrent.CompletableFuture<Void> swapHotbar(int slot, int hotbarIndex) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Closes the currently open container screen. */
    default java.util.concurrent.CompletableFuture<Void> close() {
        throw new UnsupportedOperationException("inventory actions");
    }
}
