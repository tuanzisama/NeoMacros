package ink.tuanzi.neomacros.core.api.player;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.core.api.types.Pos2D;
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
import ink.tuanzi.neomacros.port.model.Pos2d;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The container common face over a canned port: sync id, region and slot
 * coordinates, the raw menu escape hatch, the player equipment/hotbar reads
 * in player inventory order, the craft-grid reads in menu order, and the
 * recipe-book state and toggles.
 */
class InventoryContainerFaceTest {

    private CannedInventory port;
    private PlayerApi.Inventory chest;
    private PlayerApi.PlayerInventory player;
    private PlayerApi.CraftingInventory crafting;

    @BeforeEach
    void install() {
        port = new CannedInventory();
        ScriptApiEnvironment.install(null, new InventoryOnlyAdapter(port));
        chest = new PlayerApi.Inventory("Chest", "chest", 63);
        player = new PlayerApi.PlayerInventory();
        crafting = new PlayerApi.CraftingInventory("Crafting Table", "crafting", 46);
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void containerFaceReadsCannedOpenMenu() throws Exception {
        port.opened = Optional.of(new InventoryPort.ContainerInfo("Chest", "chest", 63, 7));
        port.regions.put(0, "container");
        port.regions.put(54, "hotbar");
        port.slotPositions.put(0, new Pos2d(7, 9));
        port.underMouse = 9;
        port.rawMenu = new Object();

        assertEquals(7, chest.getContainerId());
        assertEquals("container", chest.regionOf(0));
        assertEquals("hotbar", chest.regionOf(54));
        assertNull(chest.regionOf(63), "an out-of-range slot has no region");
        Pos2D pos = chest.getSlotPos(0);
        assertNotNull(pos);
        assertEquals(7, pos.getX());
        assertEquals(9, pos.getY());
        assertEquals(9, chest.getSlotUnderMouse());
        assertNotNull(chest.getRaw(), "the canned menu crosses the raw hatch");
    }

    @Test
    void absentStateReadsUndefined() {
        assertNull(chest.getContainerId());
        assertNull(chest.regionOf(0));
        assertNull(chest.getSlotPos(0));
        assertNull(chest.getSlotUnderMouse());
        assertNull(chest.getRaw());
    }

    @Test
    void openDispatchesTheHandleKind() throws Exception {
        assertTrue(chest.open().get(1, TimeUnit.SECONDS) == null);
        assertEquals(List.of("open:chest"), port.actions);
        assertTrue(player.open().get(1, TimeUnit.SECONDS) == null);
        assertEquals(List.of("open:chest", "open:player"), port.actions);
    }

    @Test
    void playerEquipmentReadsPlayerInventoryOrder() {
        port.put(40, new ItemStackData("minecraft:shield", 1, "Shield"));
        port.put(39, new ItemStackData("minecraft:diamond_helmet", 1, "Helmet"));
        port.put(38, new ItemStackData("minecraft:iron_chestplate", 1, "Chestplate"));
        port.put(37, new ItemStackData("minecraft:leather_leggings", 1, "Leggings"));
        port.put(36, new ItemStackData("minecraft:boots", 1, "Boots"));

        assertEquals("minecraft:shield", player.getOffhand().getId());
        assertEquals("minecraft:diamond_helmet", player.getHelmet().getId());
        assertEquals("minecraft:iron_chestplate", player.getChestplate().getId());
        assertEquals("minecraft:leather_leggings", player.getLeggings().getId());
        assertEquals("minecraft:boots", player.getBoots().getId());

        assertTrue(player.isInHotbar(0));
        assertTrue(player.isInHotbar(8));
        assertTrue(player.isInHotbar(40), "the offhand counts as hotbar-adjacent");
        assertFalse(player.isInHotbar(9));
        assertFalse(player.isInHotbar(36));
    }

    @Test
    void playerCraftGridIsTheFixedTwoByTwo() {
        assertEquals(2, player.getCraftingWidth());
        assertEquals(2, player.getCraftingHeight());
        assertEquals(4, player.getCraftingSlotCount());
        assertEquals(4, player.getInputSize());

        port.playerMenu.put(1, new ItemStackData("minecraft:oak_planks", 2, "Planks"));
        port.playerMenu.put(4, new ItemStackData("minecraft:stick", 1, "Stick"));
        ItemStack corner = player.getInput(0, 0);
        assertNotNull(corner);
        assertEquals("minecraft:oak_planks", corner.getId());
        assertNull(player.getInput(1, 0), "an empty cell reads undefined");
        assertNull(player.getInput(5, 5), "outside the grid reads undefined");

        List<List<ItemStack>> grid = player.getInput();
        assertEquals(2, grid.size());
        assertEquals(2, grid.get(0).size());
        assertEquals("minecraft:stick", grid.get(1).get(1).getId());
    }

    @Test
    void craftingHandleReadsTheOpenMenuGrid() {
        port.craftingW = 3;
        port.craftingH = 3;
        port.menuSlots.put(8, new ItemStackData("minecraft:diamond", 1, "Diamond"));

        assertEquals(3, crafting.getCraftingWidth());
        assertEquals(3, crafting.getCraftingHeight());
        assertEquals(9, crafting.getCraftingSlotCount());
        assertEquals(9, crafting.getInputSize());

        ItemStack cell = crafting.getInput(1, 2);
        assertNotNull(cell, "menu slot 1 + x + z * width");
        assertEquals("minecraft:diamond", cell.getId());
        assertEquals(3, crafting.getInput().size());
        assertEquals(3, crafting.getInput().get(0).size());
    }

    @Test
    void recipeBookStateAndToggles() throws Exception {
        port.recipeBook = true;
        assertEquals(Boolean.TRUE, player.isRecipeBookOpened());

        assertTrue(player.toggleRecipeBook().get(1, TimeUnit.SECONDS) == null);
        assertEquals("recipeBook:false", port.actions.get(0));

        assertTrue(player.setRecipeBook(true).get(1, TimeUnit.SECONDS) == null);
        assertEquals("recipeBook:true", port.actions.get(1));

        port.recipeBook = null;
        assertNull(player.isRecipeBookOpened(), "no book screen reads undefined");
    }

    @Test
    void craftingGridDefaultsToZeroWithoutAMenu() {
        assertEquals(0, crafting.getCraftingWidth());
        assertEquals(0, crafting.getCraftingSlotCount());
        assertTrue(crafting.getInput().isEmpty());
        assertNull(crafting.getInput(0, 0));
    }

    /** Canned inventory port serving injected container-common values. */
    private static final class CannedInventory implements InventoryPort {
        final Map<Integer, ItemStackData> slots = new LinkedHashMap<>();
        final Map<Integer, ItemStackData> menuSlots = new LinkedHashMap<>();
        final Map<Integer, ItemStackData> playerMenu = new LinkedHashMap<>();
        final Map<Integer, String> regions = new LinkedHashMap<>();
        final Map<Integer, Pos2d> slotPositions = new LinkedHashMap<>();
        final List<String> actions = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile Optional<ContainerInfo> opened = Optional.empty();
        volatile Object rawMenu = null;
        volatile Integer underMouse = null;
        volatile int craftingW = 0;
        volatile int craftingH = 0;
        volatile Boolean recipeBook = null;

        void put(int slot, ItemStackData stack) {
            slots.put(slot, stack);
        }

        private CompletableFuture<Void> record(String what) {
            actions.add(what);
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
        public Supplier<List<IndexedStack>> allItems() {
            return () -> slots.entrySet().stream()
                    .map(entry -> new IndexedStack(entry.getKey(), entry.getValue()))
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
        public Optional<ContainerInfo> openedContainer() {
            return opened;
        }

        @Override
        public Optional<Object> rawContainer() {
            return Optional.ofNullable(rawMenu);
        }

        @Override
        public Optional<Integer> slotUnderMouse() {
            return Optional.ofNullable(underMouse);
        }

        @Override
        public Optional<Pos2d> slotPos(int slot) {
            return Optional.ofNullable(slotPositions.get(slot));
        }

        @Override
        public Optional<String> regionOf(int slot) {
            return Optional.ofNullable(regions.get(slot));
        }

        @Override
        public CompletableFuture<Void> open(String kind) {
            return record("open:" + kind);
        }

        @Override
        public Optional<ItemStackData> menuSlot(int index) {
            return Optional.ofNullable(menuSlots.get(index));
        }

        @Override
        public Optional<ItemStackData> playerMenuSlot(int index) {
            return Optional.ofNullable(playerMenu.get(index));
        }

        @Override
        public int craftingWidth() {
            return craftingW;
        }

        @Override
        public int craftingHeight() {
            return craftingH;
        }

        @Override
        public Boolean recipeBookOpen() {
            return recipeBook;
        }

        @Override
        public CompletableFuture<Void> setRecipeBookOpen(boolean open) {
            return record("recipeBook:" + open);
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
