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
import ink.tuanzi.neomacros.port.model.EnchantmentData;
import ink.tuanzi.neomacros.port.model.ItemStackData;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
 * The container menu face over a canned port: raw furnace tick counts and
 * fuel queries, the anvil rename box and cost, the beacon selection writes,
 * the brewing slots and previews, the enchanting clue rows, the grindstone
 * experience floor, the smithing slots, the stonecutter recipe list, and the
 * absent-menu reads that must stay undefined instead of throwing.
 */
class InventoryContainerMenuFaceTest {

    private CannedInventory port;
    private PlayerApi.FurnaceInventory furnace;
    private PlayerApi.AnvilInventory anvil;
    private PlayerApi.BeaconInventory beacon;
    private PlayerApi.BrewingStandInventory brewing;
    private PlayerApi.EnchantInventory enchanting;
    private PlayerApi.GrindstoneInventory grindstone;
    private PlayerApi.SmithingInventory smithing;
    private PlayerApi.StonecutterInventory stonecutter;
    private PlayerApi.ContainerInventory chest;

    @BeforeEach
    void install() {
        port = new CannedInventory();
        ScriptApiEnvironment.install(null, new InventoryOnlyAdapter(port));
        furnace = new PlayerApi.FurnaceInventory("Furnace", "furnace", 40);
        anvil = new PlayerApi.AnvilInventory("Anvil", "anvil", 40);
        beacon = new PlayerApi.BeaconInventory("Beacon", "beacon", 39);
        brewing = new PlayerApi.BrewingStandInventory("Brewing Stand", "brewing", 41);
        enchanting = new PlayerApi.EnchantInventory("Enchanting Table", "enchanting", 38);
        grindstone = new PlayerApi.GrindstoneInventory("Grindstone", "grindstone", 40);
        smithing = new PlayerApi.SmithingInventory("Smithing Table", "smithing", 40);
        stonecutter = new PlayerApi.StonecutterInventory("Stonecutter", "stonecutter", 40);
        chest = new PlayerApi.ContainerInventory("Chest", "chest", 63);
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void furnaceMenuFaceReadsRawTicksAndFuelQueries() {
        port.opened = Optional.of(new InventoryPort.ContainerInfo("Furnace", "furnace", 40, 3));
        port.state.put("isLit", Boolean.TRUE);
        port.state.put("fuelTime", 12);
        port.state.put("fuelTimeTotal", 200);
        port.state.put("smeltingTime", 30);
        port.state.put("smeltingTimeTotal", 200);
        port.menuSlots.put(0, new ItemStackData("minecraft:iron_ore", 1, "Iron Ore"));
        port.menuSlots.put(1, new ItemStackData("minecraft:coal", 1, "Coal"));
        port.fuelAnswer = Boolean.TRUE;
        port.smeltableAnswer = Boolean.FALSE;
        port.fuelValues.put("minecraft:coal", 1600);

        assertEquals(12, furnace.getFuelTime());
        assertEquals(200, furnace.getFuelTimeTotal());
        assertEquals(30, furnace.getSmeltingTime());
        assertEquals(200, furnace.getSmeltingTimeTotal());
        assertEquals(1, furnace.getCraftingWidth());
        assertEquals(1, furnace.getCraftingHeight());
        assertEquals(1, furnace.getCraftingSlotCount());
        assertEquals("minecraft:iron_ore", furnace.getSmeltedItem().getId());
        assertEquals("minecraft:iron_ore", furnace.getInput(0, 0).getId());
        assertEquals(1, furnace.getInput().size());
        assertEquals(1, furnace.getInput().get(0).size());
        assertEquals(Boolean.TRUE, furnace.isFuel(furnace.getSmeltedItem()));
        assertEquals(Boolean.FALSE, furnace.isSmeltable(furnace.getSmeltedItem()));
        assertEquals(1600, furnace.getFuelValues().get("minecraft:coal"));
        assertNull(furnace.isRecipeBookOpened(), "no recipe book screen reads undefined");
    }

    @Test
    void anvilMenuFaceReadsRenameBoxAndWritesIt() throws Exception {
        port.opened = Optional.of(new InventoryPort.ContainerInfo("Anvil", "anvil", 40, 3));
        port.anvilName = "Renamed Sword";
        port.state.put("cost", 7);

        assertEquals("Renamed Sword", anvil.getItemName());
        assertEquals(7, anvil.getCost(), "the level cost bridges onto cost");
        assertEquals(40, anvil.getCostLimit());

        assertTrue(anvil.setItemName("Excalibur").get(1, TimeUnit.SECONDS) == null);
        assertEquals(List.of("anvilName:Excalibur"), port.actions);
    }

    @Test
    void beaconMenuFaceReadsStateAndDispatchesSelection() throws Exception {
        port.opened = Optional.of(new InventoryPort.ContainerInfo("Beacon", "beacon", 39, 3));
        port.state.put("levels", 4);
        port.state.put("primaryEffect", "minecraft:haste");

        assertEquals(4, beacon.getLevels());
        assertEquals("minecraft:haste", beacon.getPrimaryEffect());
        assertNull(beacon.getSecondaryEffect(), "no secondary selection reads undefined");

        assertTrue(beacon.selectPrimary("minecraft:haste").get(1, TimeUnit.SECONDS));
        assertTrue(beacon.selectSecondary("minecraft:resistance").get(1, TimeUnit.SECONDS));
        assertTrue(beacon.applyEffects().get(1, TimeUnit.SECONDS));
        assertEquals(List.of(
                "beaconPrimary:minecraft:haste",
                "beaconSecondary:minecraft:resistance",
                "beaconApply"), port.actions);
    }

    @Test
    void brewingMenuFaceReadsSlotsRecipesAndPreview() {
        port.opened = Optional.of(new InventoryPort.ContainerInfo("Brewing Stand", "brewing", 41, 3));
        port.state.put("fuel", 5);
        port.state.put("brewingTicks", 100);
        port.state.put("totalFuel", 20);
        port.state.put("totalBrewingTicks", 400);
        port.menuSlots.put(0, new ItemStackData("minecraft:potion", 1, "Potion"));
        port.menuSlots.put(3, new ItemStackData("minecraft:gunpowder", 1, "Gunpowder"));
        port.menuSlots.put(4, new ItemStackData("minecraft:blaze_powder", 1, "Blaze Powder"));
        port.brewableAnswer = Boolean.TRUE;
        port.ingredientAnswer = Boolean.TRUE;
        port.recipeAnswer = Boolean.TRUE;
        port.previewResult = new ItemStackData("minecraft:splash_potion", 1, "Splash Potion");

        assertEquals(5, brewing.getFuelUses());
        assertEquals(100, brewing.getBrewingTicks(),
                "the remaining ticks bridge onto brewingTicks");
        assertEquals(20, brewing.getTotalFuelUses(),
                "the max fuel uses bridge onto totalFuelUses");
        assertEquals(400, brewing.getTotalBrewingTicks());
        assertEquals("minecraft:potion", brewing.getBottles().get(0).getId());
        assertEquals("minecraft:gunpowder", brewing.getIngredient().getId());
        assertEquals("minecraft:blaze_powder", brewing.getFuel().getId());
        assertEquals(Boolean.TRUE, brewing.canBrewCurrentInput());
        assertEquals(Boolean.TRUE, brewing.isValidIngredient(brewing.getIngredient()));
        assertEquals(Boolean.TRUE, brewing.isBrewablePotion(brewing.getIngredient()));
        assertEquals(Boolean.TRUE,
                brewing.isValidRecipe(brewing.getBottles().get(0), brewing.getIngredient()));
        assertEquals("minecraft:splash_potion",
                brewing.previewPotion(brewing.getBottles().get(0), brewing.getIngredient()).getId());
        assertEquals(3, brewing.previewPotions().size());
        assertEquals("minecraft:splash_potion", brewing.previewPotions().get(0).getId());
    }

    @Test
    void enchantingMenuFaceReadsCluesAndBuysAnOption() throws Exception {
        port.opened = Optional.of(
                new InventoryPort.ContainerInfo("Enchanting Table", "enchanting", 38, 3));
        port.state.put("costs", new int[] {10, 20, 30});
        port.state.put("goldCount", 3);
        List<EnchantmentData> clues = new ArrayList<>();
        clues.add(new EnchantmentData("minecraft:sharpness", 5));
        clues.add(null);
        clues.add(new EnchantmentData("minecraft:unbreaking", 3));
        port.state.put("enchantments", clues);

        assertEquals(List.of(10, 20, 30), enchanting.getCosts());
        assertEquals(3, enchanting.getGoldCount());
        assertEquals(3, enchanting.getEnchantments().size());
        assertEquals("minecraft:sharpness", enchanting.getEnchantments().get(0).id());
        assertEquals(5, enchanting.getEnchantments().get(0).level());
        assertNull(enchanting.getEnchantments().get(1), "an unresolved clue reads undefined");
        assertEquals(java.util.Arrays.asList("minecraft:sharpness", null, "minecraft:unbreaking"),
                enchanting.getEnchantmentIds());

        assertTrue(enchanting.doEnchant(0).get(1, TimeUnit.SECONDS));
        assertEquals(List.of("enchant:0"), port.actions);
    }

    @Test
    void grindstoneMenuFaceEstimatesTheExperienceFloor() {
        port.opened = Optional.of(
                new InventoryPort.ContainerInfo("Grindstone", "grindstone", 40, 3));
        port.state.put("simulatedXp", 7);
        port.menuSlots.put(0, new ItemStackData("minecraft:diamond_sword", 1, "Sword"));

        assertEquals(7, grindstone.xpEstimate());
        assertEquals("minecraft:diamond_sword", grindstone.getTopInput().getId());
    }

    @Test
    void smithingMenuFaceReadsTemplateAndBase() {
        port.opened = Optional.of(
                new InventoryPort.ContainerInfo("Smithing Table", "smithing", 40, 3));
        port.menuSlots.put(0, new ItemStackData("minecraft:netherite_upgrade_smithing_template",
                1, "Template"));
        port.menuSlots.put(1, new ItemStackData("minecraft:netherite_sword", 1, "Base"));

        assertEquals("minecraft:netherite_upgrade_smithing_template",
                smithing.getTemplate().getId());
        assertEquals("minecraft:netherite_sword", smithing.getBase().getId());
    }

    @Test
    void stonecutterMenuFaceReadsRecipesAndSelectsOne() throws Exception {
        port.opened = Optional.of(
                new InventoryPort.ContainerInfo("Stonecutter", "stonecutter", 40, 3));
        port.state.put("selectedRecipeIndex", 1);
        port.state.put("recipeCount", 3);
        port.state.put("hasInput", Boolean.TRUE);
        port.state.put("recipes", List.of(
                new ItemStackData("minecraft:stone_brick_stairs", 4, "Stairs"),
                new ItemStackData("minecraft:stone_brick_slab", 2, "Slab")));
        port.menuSlots.put(0, new ItemStackData("minecraft:stone", 16, "Stone"));

        assertEquals(1, stonecutter.getSelectedRecipeIndex());
        assertEquals(3, stonecutter.getRecipeCount());
        assertEquals(Boolean.TRUE, stonecutter.canCraft());
        assertEquals(2, stonecutter.getRecipes().size());
        assertEquals("minecraft:stone_brick_stairs", stonecutter.getRecipes().get(0).getId());
        assertEquals("minecraft:stone", stonecutter.getInput().getId());

        assertTrue(stonecutter.selectRecipe(0).get(1, TimeUnit.SECONDS) == null);
        assertEquals(List.of("stonecutter:0"), port.actions);
    }

    @Test
    void absentMenuDataReadsUndefinedWithoutThrowing() {
        assertNull(furnace.getFuelTime());
        assertNull(furnace.getFuelTimeTotal());
        assertNull(furnace.getSmeltingTime());
        assertNull(furnace.getSmeltingTimeTotal());
        assertNull(furnace.isFuel(null));
        assertNull(furnace.isSmeltable(null));
        assertTrue(furnace.getFuelValues().isEmpty());
        assertNull(anvil.getItemName());
        assertEquals(40, anvil.getCostLimit(), "the cost limit is a rule constant");
        assertNull(anvil.getCost());
        assertNull(beacon.getLevels());
        assertTrue(enchanting.getEnchantments().isEmpty());
        assertTrue(enchanting.getEnchantmentIds().stream().allMatch(v -> v == null));
        assertTrue(enchanting.getCosts().isEmpty());
        assertNull(grindstone.xpEstimate());
        assertNull(brewing.canBrewCurrentInput(), "no brewing stand reads undefined");
        assertTrue(brewing.previewPotions().isEmpty());
        assertNull(brewing.previewPotion(null, null));
        assertNull(stonecutter.canCraft());
        assertTrue(stonecutter.getRecipes().isEmpty());
        assertEquals(63, chest.getSize(), "the chest face is untouched by this batch");
    }

    @Test
    void writeActionsRecordTheirDispatch() throws Exception {
        port.opened = Optional.of(new InventoryPort.ContainerInfo("Anvil", "anvil", 40, 3));
        assertTrue(anvil.setItemName("Renamed").get(1, TimeUnit.SECONDS) == null);
        port.opened = Optional.of(
                new InventoryPort.ContainerInfo("Enchanting Table", "enchanting", 38, 3));
        assertTrue(enchanting.doEnchant(2).get(1, TimeUnit.SECONDS));
        port.opened = Optional.of(new InventoryPort.ContainerInfo("Beacon", "beacon", 39, 3));
        assertTrue(beacon.applyEffects().get(1, TimeUnit.SECONDS));
        port.opened = Optional.of(
                new InventoryPort.ContainerInfo("Stonecutter", "stonecutter", 40, 3));
        assertTrue(stonecutter.selectRecipe(1).get(1, TimeUnit.SECONDS) == null);

        assertEquals(List.of(
                "anvilName:Renamed",
                "enchant:2",
                "beaconApply",
                "stonecutter:1"), port.actions);
    }

    /** Canned inventory port serving injected menu data and recording actions. */
    private static final class CannedInventory implements InventoryPort {
        final Map<Integer, ItemStackData> menuSlots = new LinkedHashMap<>();
        final Map<String, Object> state = new LinkedHashMap<>();
        final Map<String, Integer> fuelValues = new LinkedHashMap<>();
        final List<String> actions = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile Optional<ContainerInfo> opened = Optional.empty();
        volatile String anvilName = null;
        volatile Boolean fuelAnswer = null;
        volatile Boolean smeltableAnswer = null;
        volatile Boolean brewableAnswer = null;
        volatile Boolean ingredientAnswer = null;
        volatile Boolean recipeAnswer = null;
        volatile ItemStackData previewResult = null;
        volatile Boolean beaconAnswer = Boolean.TRUE;
        volatile Boolean enchantAnswer = Boolean.TRUE;

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
            return Optional.empty();
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
        public Optional<String> anvilItemName() {
            return Optional.ofNullable(anvilName);
        }

        @Override
        public CompletableFuture<Void> setAnvilItemName(String name) {
            return record("anvilName:" + name);
        }

        @Override
        public Boolean isFuel(ItemStackData stack) {
            return fuelAnswer;
        }

        @Override
        public Boolean isSmeltable(ItemStackData stack) {
            return smeltableAnswer;
        }

        @Override
        public Map<String, Integer> fuelValues() {
            return fuelValues;
        }

        @Override
        public Boolean isBrewablePotion(ItemStackData potion) {
            return brewableAnswer;
        }

        @Override
        public Boolean isValidIngredient(ItemStackData ingredient) {
            return ingredientAnswer;
        }

        @Override
        public Boolean isValidRecipe(ItemStackData potion, ItemStackData ingredient) {
            return recipeAnswer;
        }

        @Override
        public Optional<ItemStackData> previewPotion(
                ItemStackData potion, ItemStackData ingredient) {
            return Optional.ofNullable(previewResult);
        }

        @Override
        public CompletableFuture<Boolean> selectBeaconPrimaryEffect(String id) {
            actions.add("beaconPrimary:" + id);
            return CompletableFuture.completedFuture(beaconAnswer);
        }

        @Override
        public CompletableFuture<Boolean> selectBeaconSecondaryEffect(String id) {
            actions.add("beaconSecondary:" + id);
            return CompletableFuture.completedFuture(beaconAnswer);
        }

        @Override
        public CompletableFuture<Boolean> applyBeaconEffects() {
            actions.add("beaconApply");
            return CompletableFuture.completedFuture(beaconAnswer);
        }

        @Override
        public CompletableFuture<Boolean> doEnchant(int index) {
            actions.add("enchant:" + index);
            return CompletableFuture.completedFuture(enchantAnswer);
        }

        @Override
        public CompletableFuture<Void> selectStonecutterRecipe(int index) {
            return record("stonecutter:" + index);
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
