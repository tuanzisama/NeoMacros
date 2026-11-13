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
 * <p>Two slot index spaces stay distinct and every member documents which one
 * it takes:</p>
 * <ul>
 *   <li><b>Player inventory order</b> ({@link #slot(int)}, {@link #allItems()}):
 *       vanilla {@code net.minecraft.world.entity.player.Inventory} order -
 *       0-8 hotbar, 9-35 main, 36-39 armor (36 boots ... 39 helmet), 40
 *       offhand, 41-42 body/saddle on builds whose inventory reports 43 slots
 *       (verified identical on 1.21.6 and 26.1.2/26.2/26.3).</li>
 *   <li><b>Whole-screen menu order</b> ({@link #menuSlot(int)},
 *       {@link #regionOf(int)}, {@link #slotPos(int)}, {@link #slotUnderMouse()}):
 *       indices into the open menu's slot list, container rows first and the
 *       player rows last; the player's own menu is 46 slots (0 result, 1-4
 *       2x2 craft, 5-8 armor, 9-35 main, 36-44 hotbar, 45 offhand).</li>
 * </ul>
 * <p>Reads answer on the calling thread with a point-in-time snapshot; callers
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

    /** One click about to be applied: menu id, kind, slot, button, mode, quick-move flag. */
    record ClickSlotInfo(int containerId, String kind, int slot, int button, String mode,
            boolean shift) {
    }

    /** One slot sync about to be applied: menu id, kind, slot, full flag, old and new stacks. */
    record SlotUpdateInfo(int containerId, String kind, int slot, boolean full,
            ItemStackData oldStack, ItemStackData newStack) {
    }

    /** One incoming cursor-stack sync (JsMacros HELD type): menu id, kind, new stack. */
    record HeldUpdateInfo(int containerId, String kind, ItemStackData stack) {
    }

    /** One drop about to be applied: kind, source slot, whole-stack flag. */
    record DropSlotInfo(String kind, int slot, boolean all) {
    }

    /** One picked-up stack: the item snapshot (count = picked amount) and that amount. */
    record ItemPickupInfo(ItemStackData item, int amount) {
    }

    /**
     * Registers a click interceptor; returning {@code true} cancels the click
     * before its packet is sent. Sinks run on the game main thread in
     * registration order.
     */
    default void onClickSlot(java.util.function.Function<ClickSlotInfo, Boolean> sink) {
    }

    /**
     * Registers a slot-sync observer, called before the menu applies the
     * incoming stack.
     */
    default void onSlotUpdate(java.util.function.Consumer<SlotUpdateInfo> sink) {
    }

    /** Registers a cursor-stack observer, called before the menu replaces the carried stack. */
    default void onHeldUpdate(java.util.function.Consumer<HeldUpdateInfo> sink) {
    }

    /**
     * Registers a drop interceptor; returning {@code true} prevents the drop
     * (menu THROW clicks and the hotbar Q key).
     */
    default void onDropSlot(java.util.function.Function<DropSlotInfo, Boolean> sink) {
    }

    /** Registers a pickup observer, called while a pickup packet is processed. */
    default void onItemPickup(java.util.function.Consumer<ItemPickupInfo> sink) {
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

    // === container common face ===

    /**
     * The open container menu behind the {@code raw} escape hatch, or empty
     * outside a world. Handles are live views, so this answers for the menu
     * open right now rather than the one a handle was created against.
     */
    default java.util.Optional<Object> rawContainer() {
        return java.util.Optional.empty();
    }

    /**
     * The menu slot index under the mouse, or empty when no container screen
     * is open or the pointer sits over no slot.
     */
    default java.util.Optional<Integer> slotUnderMouse() {
        return java.util.Optional.empty();
    }

    /**
     * The screen-space top-left corner of one menu slot, or empty when no
     * container screen is open or the index is out of range.
     *
     * @param slot menu slot index (whole-screen slot order)
     */
    default java.util.Optional<ink.tuanzi.neomacros.port.model.Pos2d> slotPos(int slot) {
        return java.util.Optional.empty();
    }

    /**
     * The named region a menu slot belongs to, or empty for an index outside
     * the open menu. Vocabulary by menu family:
     * <ul>
     *   <li>player rows (every menu): {@code hotbar}, {@code main}; the
     *       player's own menu adds {@code offhand}, {@code helmet},
     *       {@code chestplate}, {@code leggings}, {@code boots},
     *       {@code crafting_in}, {@code craft_out}, and {@code delete} on the
     *       creative screen;</li>
     *   <li>container rows: {@code container}; furnace: {@code input},
     *       {@code fuel}, {@code output}; brewing: {@code input},
     *       {@code fuel}, {@code output}; crafting: {@code input},
     *       {@code output}; enchanting: {@code item}, {@code lapis}; loom:
     *       {@code banner}, {@code dye}, {@code pattern}, {@code output};
     *       stonecutter/anvil/smithing/grindstone/cartography/villager:
     *       {@code input}, {@code output}; beacon: {@code slot}; horse:
     *       {@code saddle}, {@code armor}, {@code container}.</li>
     * </ul>
     *
     * @param slot menu slot index (whole-screen slot order)
     */
    default java.util.Optional<String> regionOf(int slot) {
        return java.util.Optional.empty();
    }

    /**
     * Opens this inventory's screen on the client thread; {@code kind} picks
     * the target ({@code player} opens the survival inventory, other kinds
     * require their container screen to still be open).
     */
    default java.util.concurrent.CompletableFuture<Void> open(String kind) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Reads one slot of the player's own inventory menu (result slot and 2x2
     * craft grid) independent of the container open right now, or empty when
     * out of range or empty.
     *
     * @param index player inventory menu slot index
     */
    default Optional<ItemStackData> playerMenuSlot(int index) {
        return Optional.empty();
    }

    /** The open recipe menu's reported crafting grid width, or 0 without one. */
    default int craftingWidth() {
        return 0;
    }

    /** The open recipe menu's reported crafting grid height, or 0 without one. */
    default int craftingHeight() {
        return 0;
    }

    /**
     * Whether the open recipe-book screen shows its recipe book, or
     * {@code null} when no recipe-book screen is open.
     */
    default Boolean recipeBookOpen() {
        return null;
    }

    /**
     * Shows or hides the recipe book of the open recipe-book screen; a no-op
     * when no recipe-book screen is open or the state already matches.
     */
    default java.util.concurrent.CompletableFuture<Void> setRecipeBookOpen(boolean open) {
        throw new UnsupportedOperationException("inventory actions");
    }

    // === container menu face ===

    /**
     * The rename-box text of the open anvil screen, or empty without an
     * anvil screen. This is box text, not a guarantee that the anvil
     * produces a result.
     */
    default java.util.Optional<String> anvilItemName() {
        return java.util.Optional.empty();
    }

    /**
     * Writes the rename-box text of the open anvil screen, re-running the
     * menu's result and cost computation; a no-op without an anvil screen.
     */
    default java.util.concurrent.CompletableFuture<Void> setAnvilItemName(String name) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Whether the stack burns as furnace fuel in the current level, or
     * {@code null} when no level is loaded.
     */
    default Boolean isFuel(ItemStackData stack) {
        return null;
    }

    /**
     * Whether the open furnace menu accepts the stack as a smelting input,
     * or {@code null} without an open furnace-family menu.
     */
    default Boolean isSmeltable(ItemStackData stack) {
        return null;
    }

    /**
     * The level's fuel values keyed by item id with burn ticks as the
     * value; an empty map when the level does not expose them.
     */
    default java.util.Map<String, Integer> fuelValues() {
        return java.util.Map.of();
    }

    /**
     * Whether the stack holds a potion type used as the result of a brewing
     * recipe, or {@code null} when brewing recipes are unavailable.
     */
    default Boolean isBrewablePotion(ItemStackData potion) {
        return null;
    }

    /**
     * Whether the stack is a valid brewing ingredient, or {@code null} when
     * brewing recipes are unavailable.
     */
    default Boolean isValidIngredient(ItemStackData ingredient) {
        return null;
    }

    /**
     * Whether the potion and the ingredient can be brewed together, or
     * {@code null} when brewing recipes are unavailable.
     */
    default Boolean isValidRecipe(ItemStackData potion, ItemStackData ingredient) {
        return null;
    }

    /**
     * The stack the potion becomes when the ingredient is brewed into it,
     * or empty when no mix applies.
     */
    default java.util.Optional<ItemStackData> previewPotion(
            ItemStackData potion, ItemStackData ingredient) {
        return java.util.Optional.empty();
    }

    /**
     * Highlights the primary effect on the open beacon screen (client-side
     * until applied); resolves {@code false} when the id matches nothing
     * reachable at the current beacon level.
     */
    default java.util.concurrent.CompletableFuture<Boolean> selectBeaconPrimaryEffect(String id) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Highlights the secondary effect on the open beacon screen (client-side
     * until applied); resolves {@code false} when the id matches nothing
     * reachable at the current beacon level.
     */
    default java.util.concurrent.CompletableFuture<Boolean> selectBeaconSecondaryEffect(String id) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Sends the beacon's selected effects to the server and closes the
     * screen; resolves {@code false} when the payment slot is empty.
     */
    default java.util.concurrent.CompletableFuture<Boolean> applyBeaconEffects() {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Requests the enchanting table to buy one of its three options;
     * resolves {@code false} when the local menu check refuses the index.
     */
    default java.util.concurrent.CompletableFuture<Boolean> doEnchant(int index) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Selects one of the stonecutter's currently visible recipes by index;
     * an out-of-range index is a no-op.
     */
    default java.util.concurrent.CompletableFuture<Void> selectStonecutterRecipe(int index) {
        throw new UnsupportedOperationException("inventory actions");
    }

    // === container specialized face ===

    /** One creative tab: its localized display name and tab type label. */
    record CreativeTab(String name, String type) {
    }

    /**
     * The creative tabs in display order; empty without an open creative
     * screen.
     */
    default List<CreativeTab> creativeTabs() {
        return List.of();
    }

    /**
     * The stacks the open creative list currently holds, in display order;
     * empty entries surface as air stacks. Empty without an open creative
     * screen.
     */
    default List<ItemStackData> creativeShownItems() {
        return List.of();
    }

    /**
     * Scrolls the creative item list by a relative amount; a no-op without
     * a scrollable creative screen.
     */
    default java.util.concurrent.CompletableFuture<Void> creativeScrollBy(double amount) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Sets the creative item list's scroll position (clamped to {@code 0..1});
     * a no-op without a scrollable creative screen.
     */
    default java.util.concurrent.CompletableFuture<Void> creativeScrollTo(double position) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Replaces the creative search box text and refreshes the results; a
     * no-op unless the search tab is selected.
     */
    default java.util.concurrent.CompletableFuture<Void> creativeSearch(String text) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Selects the creative tab at its display-order index; an unknown index
     * is a no-op.
     */
    default java.util.concurrent.CompletableFuture<Void> creativeSelectTab(int index) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Clears the stack carried by the creative menu cursor. */
    default java.util.concurrent.CompletableFuture<Void> creativeDestroyHeld() {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Clears every player inventory slot through the creative item-add
     * operation (the vanilla trash-all path).
     */
    default java.util.concurrent.CompletableFuture<Void> creativeDestroyAll() {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Sets the stack carried by the creative menu cursor. */
    default java.util.concurrent.CompletableFuture<Void> creativeSetCursor(ItemStackData stack) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Saves the current hotbar into a saved-hotbar slot (0-8). */
    default java.util.concurrent.CompletableFuture<Void> creativeSaveHotbar(int index) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /** Restores a saved-hotbar slot (0-8) onto the current hotbar. */
    default java.util.concurrent.CompletableFuture<Void> creativeRestoreHotbar(int index) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * The stacks stored in a saved-hotbar slot (0-8), hotbar order; an
     * empty list for an out-of-range index.
     */
    default List<ItemStackData> creativeSavedHotbar(int index) {
        return List.of();
    }

    /**
     * The banner pattern registry ids the open loom offers for the pattern
     * slot item, in selection order; empty without an open loom.
     */
    default List<String> loomPatterns() {
        return List.of();
    }

    /**
     * Applies the offered loom pattern at its zero-based position and asks
     * the server to apply it; resolves {@code false} when the index is out
     * of range or the pattern list is not displayed.
     */
    default java.util.concurrent.CompletableFuture<Boolean> loomSelectPattern(int index) {
        throw new UnsupportedOperationException("inventory actions");
    }

    /**
     * Selects a villager trade by its zero-based offer position; an
     * out-of-range index or a closed trader screen is a no-op.
     */
    default java.util.concurrent.CompletableFuture<Void> selectVillagerTrade(int index) {
        throw new UnsupportedOperationException("inventory actions");
    }
}
