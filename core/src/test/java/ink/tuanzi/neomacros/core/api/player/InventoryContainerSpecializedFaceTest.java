package ink.tuanzi.neomacros.core.api.player;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.entity.EntityApi;
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
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos3d;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The container specialized face over a canned port: the loom pattern
 * offer, the villager restock/level reads and trade selection, the mount
 * state and carried slots, the creative tabs/list/scroll/search/hotbar
 * surface with its equipment reuse, and the absent reads that must stay
 * undefined instead of throwing.
 */
class InventoryContainerSpecializedFaceTest {

    private CannedInventory port;
    private PlayerApi.LoomInventory loom;
    private PlayerApi.VillagerInventory villager;
    private PlayerApi.HorseInventory horse;
    private PlayerApi.CreativeInventory creative;
    private PlayerApi.ContainerInventory chest;

    @BeforeEach
    void install() throws Exception {
        port = new CannedInventory();
        ScriptApiEnvironment.install(null, new InventoryEntityAdapter(port));
        loom = new PlayerApi.LoomInventory("Loom", "loom", 40);
        villager = new PlayerApi.VillagerInventory("Villager", "villager", 39);
        horse = new PlayerApi.HorseInventory("Horse", "horse", 40);
        creative = new PlayerApi.CreativeInventory("Inventory", "creative", 46);
        chest = new PlayerApi.ContainerInventory("Chest", "chest", 63);
    }

    @AfterEach
    void uninstall() throws Exception {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void loomListsPatternsAndDispatchesSelection() throws Exception {
        port.loomPatternIds.add("minecraft:base");
        port.loomPatternIds.add("minecraft:gradient");

        assertEquals(List.of("minecraft:base", "minecraft:gradient"), loom.getPatterns());

        assertTrue(loom.selectPattern(1).get(1, TimeUnit.SECONDS));
        assertTrue(loom.selectPatternId("minecraft:base").get(1, TimeUnit.SECONDS));
        assertFalse(loom.selectPatternId("minecraft:nope").get(1, TimeUnit.SECONDS));
        assertFalse(loom.selectPattern(9).get(1, TimeUnit.SECONDS));
        assertEquals(List.of("loomPattern:1", "loomPattern:0"), port.actions);
    }

    @Test
    void villagerReadsRestockLevelAndSelectsATrade() throws Exception {
        port.state.put("canRestock", Boolean.TRUE);
        port.state.put("leveled", Boolean.FALSE);
        port.state.put("futureTraderXp", 7);

        assertEquals(Boolean.TRUE, villager.canRestock());
        assertEquals(Boolean.FALSE, villager.isLeveled());
        assertEquals(7, villager.getFutureTraderXp());

        villager.selectTrade(2).get(1, TimeUnit.SECONDS);
        assertEquals(List.of("selectTrade:2"), port.actions);
    }

    @Test
    void horseReadsMountStateCarriedSlotsAndTheMountHandle() throws Exception {
        port.state.put("canBeSaddled", Boolean.TRUE);
        port.state.put("isSaddled", Boolean.FALSE);
        port.state.put("hasArmorSlot", Boolean.TRUE);
        port.state.put("hasChest", Boolean.TRUE);
        port.state.put("inventorySize", 3);
        port.state.put("horse", UUID.randomUUID().toString());
        port.menuSlots.put(2, new ItemStackData("minecraft:hay_block", 16, "Hay Bale"));
        port.menuSlots.put(3, new ItemStackData("minecraft:golden_carrot", 4, "Carrot"));
        // Slot 4 stays empty and must surface as a null list entry.

        assertEquals(Boolean.TRUE, horse.canBeSaddled());
        assertEquals(Boolean.FALSE, horse.isSaddled());
        assertEquals(Boolean.TRUE, horse.hasArmorSlot());
        assertEquals(Boolean.TRUE, horse.hasChest());
        assertEquals(3, horse.getInventorySize());

        List<ItemStack> carried = horse.getHorseInventory();
        assertEquals(3, carried.size());
        assertEquals("minecraft:hay_block", carried.get(0).getId());
        assertEquals("minecraft:golden_carrot", carried.get(1).getId());
        assertNull(carried.get(2));

        assertNotNull(horse.getHorse());
        assertTrue(horse.getHorse() instanceof EntityApi.Entity);
    }

    @Test
    void creativeListsTabsItemsAndScrolls() throws Exception {
        port.creativeTabs.add(new InventoryPort.CreativeTab("Building Blocks", "CATEGORY"));
        port.creativeTabs.add(new InventoryPort.CreativeTab("Search", "SEARCH"));
        port.creativeTabs.add(new InventoryPort.CreativeTab("Inventory", "INVENTORY"));
        port.creativeTabs.add(new InventoryPort.CreativeTab("Hotbar", "HOTBAR"));
        port.shownItems.add(new ItemStackData("minecraft:diamond", 1, "Diamond"));
        port.shownItems.add(new ItemStackData("minecraft:air", 0, "Air"));

        assertEquals(List.of("Building Blocks", "Search", "Inventory", "Hotbar"),
                creative.getTabNames());
        assertEquals(4, creative.getTabTexts().size());
        assertEquals("Building Blocks", creative.getTabTexts().get(0).getContent());

        List<ItemStack> shown = creative.getShownItems();
        assertEquals(2, shown.size());
        assertEquals("minecraft:diamond", shown.get(0).getId());

        creative.scroll(0.25).get(1, TimeUnit.SECONDS);
        creative.scrollTo(0.5).get(1, TimeUnit.SECONDS);
        creative.search("diamond").get(1, TimeUnit.SECONDS);
        assertEquals(List.of("scrollBy:0.25", "scrollTo:0.5", "search:diamond"),
                port.actions);
    }

    @Test
    void creativeSelectsTabsByTypeAndByName() throws Exception {
        port.creativeTabs.add(new InventoryPort.CreativeTab("Building Blocks", "CATEGORY"));
        port.creativeTabs.add(new InventoryPort.CreativeTab("Search", "SEARCH"));
        port.creativeTabs.add(new InventoryPort.CreativeTab("Inventory", "INVENTORY"));
        port.creativeTabs.add(new InventoryPort.CreativeTab("Hotbar", "HOTBAR"));

        creative.selectSearch().get(1, TimeUnit.SECONDS);
        creative.selectInventory().get(1, TimeUnit.SECONDS);
        creative.selectHotbar().get(1, TimeUnit.SECONDS);
        assertTrue(creative.selectTab("Hotbar").get(1, TimeUnit.SECONDS));
        assertFalse(creative.selectTab("No Such Tab").get(1, TimeUnit.SECONDS));
        assertEquals(List.of("selectTab:1", "selectTab:2", "selectTab:3", "selectTab:3"),
                port.actions);
    }

    @Test
    void creativeSavesAndRestoresHotbarsAndReadsTheSavedSlot() throws Exception {
        port.savedHotbar.add(new ItemStackData("minecraft:bow", 1, "Bow"));

        assertEquals(1, creative.getSavedHotbar(0).size());
        assertEquals("minecraft:bow", creative.getSavedHotbar(0).get(0).getId());
        assertTrue(creative.getSavedHotbar(5).isEmpty());

        creative.saveHotbar(2).get(1, TimeUnit.SECONDS);
        creative.restoreHotbar(2).get(1, TimeUnit.SECONDS);
        assertEquals(List.of("saveHotbar:2", "restoreHotbar:2"), port.actions);
    }

    @Test
    void creativeDestroysAndSetsTheCursorStack() throws Exception {
        creative.destroyHeldItem().get(1, TimeUnit.SECONDS);
        creative.destroyAllItems().get(1, TimeUnit.SECONDS);
        creative.setCursorStack(ItemStack.of(
                new ItemStackData("minecraft:diamond", 1, "Diamond"))).get(1,
                TimeUnit.SECONDS);
        creative.setCursorStack(null).get(1, TimeUnit.SECONDS);
        assertEquals(List.of("destroyHeld", "destroyAll",
                "setCursor:minecraft:diamond", "setCursor:none"), port.actions);
    }

    @Test
    void creativeReusesThePlayerEquipmentAndHotbarQueries() throws Exception {
        port.slots.put(36, new ItemStackData("minecraft:iron_boots", 1, "Boots"));
        port.slots.put(37, new ItemStackData("minecraft:iron_leggings", 1, "Leggings"));
        port.slots.put(38, new ItemStackData("minecraft:iron_chestplate", 1, "Chestplate"));
        port.slots.put(39, new ItemStackData("minecraft:iron_helmet", 1, "Helmet"));
        port.slots.put(40, new ItemStackData("minecraft:shield", 1, "Shield"));

        assertEquals("minecraft:iron_boots", creative.getBoots().getId());
        assertEquals("minecraft:iron_leggings", creative.getLeggings().getId());
        assertEquals("minecraft:iron_chestplate", creative.getChestplate().getId());
        assertEquals("minecraft:iron_helmet", creative.getHelmet().getId());
        assertEquals("minecraft:shield", creative.getOffhand().getId());

        assertTrue(creative.isInHotbar(0));
        assertTrue(creative.isInHotbar(40));
        assertFalse(creative.isInHotbar(9));
    }

    @Test
    void absentSpecializedStateReadsUndefinedInsteadOfThrowing() throws Exception {
        assertNull(villager.canRestock());
        assertNull(villager.isLeveled());
        assertNull(horse.canBeSaddled());
        assertNull(horse.isSaddled());
        assertNull(horse.hasArmorSlot());
        assertNull(horse.hasChest());
        assertNull(horse.getInventorySize());
        assertTrue(horse.getHorseInventory().isEmpty());
        assertNull(horse.getHorse());

        assertTrue(loom.getPatterns().isEmpty());
        assertFalse(loom.selectPattern(0).get(1, TimeUnit.SECONDS));

        assertTrue(creative.getTabNames().isEmpty());
        assertTrue(creative.getTabTexts().isEmpty());
        assertTrue(creative.getShownItems().isEmpty());
        assertTrue(creative.getSavedHotbar(0).isEmpty());
        assertFalse(creative.selectTab("Search").get(1, TimeUnit.SECONDS));

        assertEquals("chest", chest.getKind());
    }

    /** Canned inventory port serving injected specialized data and actions. */
    private static final class CannedInventory implements InventoryPort {
        final Map<Integer, ItemStackData> slots = new LinkedHashMap<>();
        final Map<Integer, ItemStackData> menuSlots = new LinkedHashMap<>();
        final Map<String, Object> state = new LinkedHashMap<>();
        final List<String> actions = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<CreativeTab> creativeTabs = new java.util.ArrayList<>();
        final List<ItemStackData> shownItems = new java.util.ArrayList<>();
        final List<String> loomPatternIds = new java.util.ArrayList<>();
        final List<ItemStackData> savedHotbar = new java.util.ArrayList<>();
        volatile Optional<ContainerInfo> opened = Optional.empty();
        volatile Boolean loomSelectAnswer = Boolean.TRUE;

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
            return List::of;
        }

        @Override
        public int countOf(String item) {
            return 0;
        }

        @Override
        public Optional<ContainerInfo> openedContainer() {
            return opened;
        }

        @Override
        public Optional<ItemStackData> menuSlot(int index) {
            return Optional.ofNullable(menuSlots.get(index));
        }

        @Override
        public Map<String, Object> containerState() {
            return state;
        }

        @Override
        public List<String> loomPatterns() {
            return loomPatternIds;
        }

        @Override
        public CompletableFuture<Boolean> loomSelectPattern(int index) {
            if (index < 0 || index >= loomPatternIds.size()) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            actions.add("loomPattern:" + index);
            return CompletableFuture.completedFuture(loomSelectAnswer);
        }

        @Override
        public CompletableFuture<Void> selectVillagerTrade(int index) {
            return record("selectTrade:" + index);
        }

        @Override
        public List<CreativeTab> creativeTabs() {
            return creativeTabs;
        }

        @Override
        public List<ItemStackData> creativeShownItems() {
            return shownItems;
        }

        @Override
        public CompletableFuture<Void> creativeScrollBy(double amount) {
            return record("scrollBy:" + amount);
        }

        @Override
        public CompletableFuture<Void> creativeScrollTo(double position) {
            return record("scrollTo:" + position);
        }

        @Override
        public CompletableFuture<Void> creativeSearch(String text) {
            return record("search:" + text);
        }

        @Override
        public CompletableFuture<Void> creativeSelectTab(int index) {
            return record("selectTab:" + index);
        }

        @Override
        public CompletableFuture<Void> creativeDestroyHeld() {
            return record("destroyHeld");
        }

        @Override
        public CompletableFuture<Void> creativeDestroyAll() {
            return record("destroyAll");
        }

        @Override
        public CompletableFuture<Void> creativeSetCursor(ItemStackData stack) {
            return record("setCursor:" + (stack == null ? "none" : stack.id()));
        }

        @Override
        public CompletableFuture<Void> creativeSaveHotbar(int index) {
            return record("saveHotbar:" + index);
        }

        @Override
        public CompletableFuture<Void> creativeRestoreHotbar(int index) {
            return record("restoreHotbar:" + index);
        }

        @Override
        public List<ItemStackData> creativeSavedHotbar(int index) {
            return index == 0 ? savedHotbar : List.of();
        }
    }

    /**
     * Adapter double exposing the inventory port plus an entity port that
     * answers the mount lookup for {@code getHorse()}.
     */
    private static final class InventoryEntityAdapter implements NeomacrosAdapter {
        private final CannedInventory inventory;
        private final EntityPort entity = new EntityPort() {
            @Override
            public Optional<EntitySnapshot> byUuid(UUID uuid) {
                return Optional.of(new EntitySnapshot(
                        uuid, "minecraft:horse", "Horse", new Pos3d(0, 64, 0), 22));
            }

            @Override
            public List<EntitySnapshot> inRadius(Pos3d center, double range,
                    java.util.function.Predicate<EntitySnapshot> filter) {
                return List.of();
            }
        };

        InventoryEntityAdapter(CannedInventory inventory) {
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
            return entity;
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
        public InventoryPort inventory() {
            return inventory;
        }

        @Override
        public ClientPort client() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPort render() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DialogPort dialog() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Draw3DPort draw3d() {
            throw new UnsupportedOperationException();
        }
    }
}
