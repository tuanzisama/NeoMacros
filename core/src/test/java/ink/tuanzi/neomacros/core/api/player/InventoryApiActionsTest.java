package ink.tuanzi.neomacros.core.api.player;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.DialogPort;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.ItemStackData;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inventory API action composition and free-slot computation over a canned
 * recording port: click sequences land in order, reads aggregate the canned
 * item map, and bare-port defaults reject as designed.
 */
class InventoryApiActionsTest {

    private RecordingInventory port;
    private PlayerApi.Inventory inventory;

    @BeforeEach
    void install() {
        port = new RecordingInventory();
        ScriptApiEnvironment.install(null, new InventoryOnlyAdapter(port));
        inventory = new PlayerApi.Inventory("Chest", "chest", 27);
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void grabAllPicksUpThenGathers() throws Exception {
        assertTrue(inventory.grabAll(5).get(1, TimeUnit.SECONDS) == null);
        assertEquals(List.of("click:5:0", "pickupAll:5"), port.calls);
    }

    @Test
    void splitPlacesOneThenTheRest() throws Exception {
        assertTrue(inventory.split(3, 7).get(1, TimeUnit.SECONDS) == null);
        assertEquals(List.of("click:3:1", "click:7:0"), port.calls);
    }

    @Test
    void quickAllDefaultsButtonToZero() throws Exception {
        assertEquals(2, inventory.quickAll(2, null).get(1, TimeUnit.SECONDS));
        assertEquals("quickAll:2:0", port.calls.get(0));
    }

    @Test
    void quickAllPassesExplicitButton() throws Exception {
        inventory.quickAll(4, 1).get(1, TimeUnit.SECONDS);
        assertEquals("quickAll:4:1", port.calls.get(0));
    }

    @Test
    void findFreeHotbarSlotSkipsFilledSlots() {
        port.put(0, new ItemStackData("minecraft:stone", 1, "Stone"));
        port.put(1, new ItemStackData("minecraft:stone", 1, "Stone"));
        port.put(3, new ItemStackData("minecraft:stone", 1, "Stone"));
        assertEquals(2, inventory.findFreeHotbarSlot());
    }

    @Test
    void findFreeHotbarSlotReturnsUndefinedWhenFull() {
        for (int i = 0; i < 9; i++) {
            port.put(i, new ItemStackData("minecraft:stone", 1, "Stone"));
        }
        assertNull(inventory.findFreeHotbarSlot());
    }

    @Test
    void findFreeInventorySlotStartsAfterHotbar() {
        for (int i = 0; i < 9; i++) {
            port.put(i, new ItemStackData("minecraft:stone", 1, "Stone"));
        }
        port.put(9, new ItemStackData("minecraft:stone", 1, "Stone"));
        assertNull(inventory.findFreeHotbarSlot());
        assertEquals(10, inventory.findFreeInventorySlot());
    }

    @Test
    void getItemCountAggregatesById() {
        port.put(1, new ItemStackData("minecraft:diamond", 3, "Diamond"));
        port.put(5, new ItemStackData("minecraft:diamond", 2, "Diamond"));
        port.put(2, new ItemStackData("minecraft:apple", 1, "Apple"));
        Map<String, Integer> counts = inventory.getItemCount();
        assertEquals(5, counts.get("minecraft:diamond"));
        assertEquals(1, counts.get("minecraft:apple"));
        assertEquals(2, counts.size());
    }

    @Test
    void getCarriedMapsCannedStackOrUndefined() {
        assertNull(inventory.getCarried());
        port.carried = new ItemStackData("minecraft:diamond", 3, "Diamond");
        ItemStack carried = inventory.getCarried();
        assertEquals("minecraft:diamond", carried.getId());
        assertEquals(3, carried.getCount());
    }

    @Test
    void hotbarSelectionRoundTrips() throws Exception {
        port.selectedSlot = 4;
        assertEquals(4, inventory.getSelectedHotbarSlot());
        assertTrue(inventory.setSelectedHotbarSlot(7).get(1, TimeUnit.SECONDS) == null);
        assertEquals("select:7", port.calls.get(0));
    }

    @Test
    void dragClickCoercesNumberArray() throws Exception {
        assertTrue(inventory.dragClick(List.of(1, 2, 3), 0).get(1, TimeUnit.SECONDS) == null);
        assertEquals("drag:[1, 2, 3]:0", port.calls.get(0));
    }

    @Test
    void dragClickRejectsNonArray() {
        assertThrows(IllegalArgumentException.class, () -> inventory.dragClick("nope", 0));
        assertThrows(IllegalArgumentException.class,
                () -> inventory.dragClick(List.of(1, "x"), 0));
    }

    @Test
    void barePortDefaultsReject() {
        ScriptApiEnvironment.install(null, new InventoryOnlyAdapter(new InventoryPort() {
            @Override
            public Supplier<Optional<ItemStackData>> heldItem() {
                return Optional::empty;
            }

            @Override
            public Optional<ItemStackData> slot(int index) {
                return Optional.empty();
            }

            @Override
            public Supplier<List<InventoryPort.IndexedStack>> allItems() {
                return List::of;
            }

            @Override
            public int countOf(String item) {
                return 0;
            }
        }));
        PlayerApi.Inventory bare = new PlayerApi.Inventory("Chest", "chest", 27);
        // port defaults throw synchronously, before any future exists
        assertThrows(UnsupportedOperationException.class, () -> bare.grabAll(0));
        assertThrows(UnsupportedOperationException.class, () -> bare.dragClick(List.of(1), 0));
        assertThrows(UnsupportedOperationException.class, () -> bare.quickAll(0, null));
        assertThrows(UnsupportedOperationException.class, () -> bare.setSelectedHotbarSlot(1));
        assertThrows(UnsupportedOperationException.class, bare::closeAndDrop);
    }

    private static void assertRejects(CompletableFuture<?> future) {
        try {
            future.get(1, TimeUnit.SECONDS);
            throw new AssertionError("expected rejection");
        } catch (java.util.concurrent.ExecutionException expected) {
            // rejected as designed
        } catch (Exception e) {
            throw new AssertionError("unexpected failure", e);
        }
    }

    /** Canned inventory port recording action calls in order. */
    private static final class RecordingInventory implements InventoryPort {
        final Map<Integer, ItemStackData> slots = new LinkedHashMap<>();
        volatile ItemStackData carried = null;
        volatile Integer selectedSlot = 0;
        final List<String> calls = new java.util.concurrent.CopyOnWriteArrayList<>();

        void put(int slot, ItemStackData stack) {
            slots.put(slot, stack);
        }

        private CompletableFuture<Void> record(String what) {
            calls.add(what);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Supplier<Optional<ItemStackData>> heldItem() {
            return Optional::empty;
        }

        @Override
        public Optional<ItemStackData> slot(int index) {
            return Optional.ofNullable(slots.get(index));
        }

        @Override
        public Supplier<List<InventoryPort.IndexedStack>> allItems() {
            return () -> slots.entrySet().stream()
                    .map(entry -> new InventoryPort.IndexedStack(entry.getKey(), entry.getValue()))
                    .toList();
        }

        @Override
        public int countOf(String item) {
            int count = 0;
            for (ItemStackData stack : slots.values()) {
                if (stack.id().equals(item)) {
                    count += stack.count();
                }
            }
            return count;
        }

        @Override
        public Optional<ItemStackData> carriedItem() {
            return Optional.ofNullable(carried);
        }

        @Override
        public Integer selectedHotbarSlot() {
            return selectedSlot;
        }

        @Override
        public CompletableFuture<Void> click(int slot, int button, boolean shift) {
            return record("click:" + slot + ":" + button + (shift ? ":shift" : ""));
        }

        @Override
        public CompletableFuture<Void> setSelectedHotbarSlot(int index) {
            return record("select:" + index);
        }

        @Override
        public CompletableFuture<Void> pickupAll(int slot) {
            return record("pickupAll:" + slot);
        }

        @Override
        public CompletableFuture<Void> dragClick(List<Integer> slotList, int button) {
            return record("drag:" + slotList + ":" + button);
        }

        @Override
        public CompletableFuture<Integer> quickAll(int slot, int button) {
            calls.add("quickAll:" + slot + ":" + button);
            return CompletableFuture.completedFuture(2);
        }

        @Override
        public CompletableFuture<Void> closeAndDrop() {
            return record("closeAndDrop");
        }

        @Override
        public CompletableFuture<Void> drop(int slot, boolean all) {
            return record("drop:" + slot + ":" + all);
        }

        @Override
        public CompletableFuture<Void> swapHotbar(int slot, int hotbarIndex) {
            return record("swap:" + slot + ":" + hotbarIndex);
        }

        @Override
        public CompletableFuture<Void> close() {
            return record("close");
        }
    }

    /** Minimal adapter double exposing only the inventory port. */
    private static final class InventoryOnlyAdapter implements NeomacrosAdapter {
        private final InventoryPort inventory;

        InventoryOnlyAdapter(InventoryPort inventory) {
            this.inventory = inventory;
        }

        @Override
        public LifecyclePort lifecycle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorldPort world() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityPort entity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ink.tuanzi.neomacros.port.PlayerPort player() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ink.tuanzi.neomacros.port.InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientPort client() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InventoryPort inventory() {
            return inventory;
        }

        @Override
        public DialogPort dialog() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPort render() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Draw3DPort draw3d() {
            throw new UnsupportedOperationException();
        }
    }
}
