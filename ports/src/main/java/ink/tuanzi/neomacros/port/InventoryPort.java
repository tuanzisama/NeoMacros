package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.ItemStackData;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Inventory queries and actions over the local player: hotbar and main
 * inventory slots, the currently held item, and clicks over the open menu.
 *
 * <p>Slot indices follow the vanilla container order (0-8 hotbar, 9-35 main).
 * Reads answer on the calling thread with a point-in-time snapshot; callers
 * needing main-thread isolation marshal through the lifecycle port's
 * main-thread bridge. Actions dispatch on the main thread and resolve once
 * processed.</p>
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

    /** The stack held on the mouse cursor over the open menu, or empty. */
    default Optional<ItemStackData> carriedItem() {
        return Optional.empty();
    }

    /** The selected hotbar slot (0-8), or {@code null} when unknown. */
    default Integer selectedHotbarSlot() {
        return null;
    }

    /** Left/right-clicks a container slot (shift = quick move, button 2 = middle-click clone in creative). */
    default java.util.concurrent.CompletableFuture<Void> click(
            int slot, int button, boolean shift) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Quick-moves a container slot to the other inventory. */
    default java.util.concurrent.CompletableFuture<Void> quickMove(int slot) {
        return click(slot, 0, true);
    }

    /** Drops the stack from a container slot (all = the whole stack, else one item). */
    default java.util.concurrent.CompletableFuture<Void> drop(int slot, boolean all) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Swaps a container slot with a hotbar slot (0-8, or 40 for the offhand). */
    default java.util.concurrent.CompletableFuture<Void> swapHotbar(int slot, int hotbarIndex) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Closes the currently open container screen. */
    default java.util.concurrent.CompletableFuture<Void> close() {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Selects the active hotbar slot (0-8). */
    default java.util.concurrent.CompletableFuture<Void> setSelectedHotbarSlot(int index) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Clicks a slot with the PICKUP_ALL action (gather matching stacks onto the cursor). */
    default java.util.concurrent.CompletableFuture<Void> pickupAll(int slot) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Distributes the cursor stack over the given menu slots with a drag
     * (QUICK_CRAFT); button 0 splits evenly, other buttons fill greedily.
     */
    default java.util.concurrent.CompletableFuture<Void> dragClick(List<Integer> slots, int button) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Quick-moves every slot that can merge with the given slot's stack
     * (same backing container); returns the moved item count.
     */
    default java.util.concurrent.CompletableFuture<Integer> quickAll(int slot, int button) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Drops the cursor-held stack (if any) and closes the open screen. */
    default java.util.concurrent.CompletableFuture<Void> closeAndDrop() {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Picks the slot up, then gathers all matching stacks onto the cursor
     * (the double-click collect semantics).
     */
    default java.util.concurrent.CompletableFuture<Void> grabAll(int slot) {
        return click(slot, 0, false).thenCompose(v -> pickupAll(slot));
    }

    /**
     * Places the cursor stack split across two empty slots: one item into
     * the first, the rest into the second.
     */
    default java.util.concurrent.CompletableFuture<Void> split(int slot1, int slot2) {
        return click(slot1, 1, false).thenCompose(v -> click(slot2, 0, false));
    }
}
