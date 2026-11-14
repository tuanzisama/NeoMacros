package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.types.Enchantment;
import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.core.api.types.ItemStackBuilder;
import ink.tuanzi.neomacros.core.api.types.TextComponent;
import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.InputPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.ItemPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import ink.tuanzi.neomacros.port.model.EnchantmentData;
import ink.tuanzi.neomacros.port.model.EnchantmentRelations;
import ink.tuanzi.neomacros.port.model.EnchantmentSpec;
import ink.tuanzi.neomacros.port.model.FoodData;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The G-027B item face: the enchantment handle served through the item
 * port (definition, relations, pairwise verdicts, styled level name), the
 * food component read, the creative write face on the builder (durability,
 * enchantments, lore, tooltip-hidden flags), and the absent boundaries
 * (no port reads undefined, foreign arguments are rejected loudly).
 */
class ItemStackEnchantmentFoodCreativeTest {

    private CannedItem item;

    @BeforeEach
    void install() {
        item = new CannedItem();
        ScriptApiEnvironment.install(null, new ItemAdapter(item));
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void enchantmentEntriesExposeTheDefinitionFace() {
        item.spec = new EnchantmentSpec("minecraft:sharpness", 1, 5, 10,
                false, false, List.of("minecraft:diamond_sword", "minecraft:iron_sword"));
        item.relations = new EnchantmentRelations(
                List.of("minecraft:sharpness", "minecraft:smite"),
                List.of("minecraft:unbreaking"));
        item.compatible = Boolean.FALSE;
        item.levelName = new ChatComponent("Sharpness V", "gray",
                null, null, null, null, null, List.of());

        ItemStack stack = enchantedStack();
        Enchantment sharpness = stack.getEnchantments().get(0);

        assertEquals("minecraft:sharpness", sharpness.getId(), "id");
        assertEquals(5, sharpness.getLevel(), "level");
        assertEquals(1, sharpness.getMinLevel(), "minLevel");
        assertEquals(5, sharpness.getMaxLevel(), "maxLevel");
        assertEquals(10, sharpness.getWeight(), "weight");
        assertEquals(false, sharpness.isCursed(), "cursed");
        assertEquals(false, sharpness.isTreasure(), "treasure");
        assertEquals(List.of("minecraft:diamond_sword", "minecraft:iron_sword"),
                sharpness.getAcceptableItems(), "acceptableItems");

        assertEquals(2, sharpness.getConflictingEnchantments().size(), "conflicting size");
        assertEquals("minecraft:sharpness",
                sharpness.getConflictingEnchantments().get(0).getId(), "conflicting self");
        assertEquals(0, sharpness.getConflictingEnchantments().get(0).getLevel(),
                "registry-only entries carry level zero");
        assertEquals(List.of("minecraft:unbreaking"),
                idsOf(sharpness.getCompatibleEnchantments()), "compatible ids");

        assertEquals(false, sharpness.isCompatible("minecraft:smite"), "isCompatible by id");
        assertEquals(true, sharpness.conflictsWith("minecraft:smite"), "conflictsWith by id");
        assertEquals(false, sharpness.isCompatible(sharpness), "never compatible with itself");
        assertEquals(true, sharpness.conflictsWith(sharpness), "always conflicts with itself");
        assertEquals(false, sharpness.canBeApplied(new ItemStackBuilder("minecraft:stick").build()),
                "unsupported item type");

        TextComponent name = sharpness.getRomanLevelName();
        assertEquals("Sharpness V", name.getPlain(), "roman level name at the stored level");
        assertEquals("Sharpness V", sharpness.getRomanLevelName(5).getPlain(),
                "roman level name at an explicit level");
        assertEquals("gray", name.getColor(), "styled level name keeps its colour");
    }

    @Test
    void stackSideEnchantmentQueriesReadThroughThePort() {
        item.spec = new EnchantmentSpec("minecraft:sharpness", 1, 5, 10,
                false, false, List.of("minecraft:diamond_sword"));
        item.relations = new EnchantmentRelations(List.of("minecraft:sharpness"),
                List.of("minecraft:unbreaking"));
        item.compatible = Boolean.TRUE;
        item.possibleEnchantments = List.of("minecraft:sharpness", "minecraft:unbreaking");

        ItemStack stack = enchantedStack();

        assertEquals(true, stack.canBeApplied(stack.getEnchantments().get(0)),
                "canBeApplied on the stack side");
        assertEquals(List.of("minecraft:sharpness", "minecraft:unbreaking"),
                idsOf(stack.getPossibleEnchantments()), "possibleEnchantments");
        assertEquals(List.of("minecraft:sharpness", "minecraft:unbreaking"),
                idsOf(stack.getPossibleEnchantmentsFromTable()), "possibleEnchantmentsFromTable");
        assertNull(stack.canBeApplied(null), "an absent enchantment reads undefined");
    }

    @Test
    void enchantmentFacesReadUndefinedWithoutThePort() {
        ScriptApiEnvironment.install(null, new BareAdapter());
        ItemStack stack = enchantedStack();
        Enchantment sharpness = stack.getEnchantments().get(0);

        assertNull(sharpness.getMinLevel(), "minLevel");
        assertNull(sharpness.getMaxLevel(), "maxLevel");
        assertNull(sharpness.getWeight(), "weight");
        assertNull(sharpness.isCursed(), "cursed");
        assertNull(sharpness.isTreasure(), "treasure");
        assertNull(sharpness.getAcceptableItems(), "acceptableItems");
        assertNull(sharpness.getConflictingEnchantments(), "conflictingEnchantments");
        assertNull(sharpness.getCompatibleEnchantments(), "compatibleEnchantments");
        assertNull(sharpness.getRomanLevelName(), "romanLevelName");
        assertNull(sharpness.isCompatible("minecraft:smite"), "isCompatible");
        assertNull(sharpness.conflictsWith("minecraft:smite"), "conflictsWith");
        assertNull(sharpness.canBeApplied("minecraft:diamond_sword"), "canBeApplied");
        assertNull(stack.getPossibleEnchantments(), "possibleEnchantments");
        assertNull(stack.getPossibleEnchantmentsFromTable(), "possibleEnchantmentsFromTable");
        assertNull(stack.canBeApplied(sharpness), "stack canBeApplied");
        assertEquals(1, stack.getEnchantments().size(), "entries themselves stay readable");
        assertEquals("minecraft:sharpness", sharpness.getId(), "id stays readable");
        assertEquals(5, sharpness.getLevel(), "level stays readable");
    }

    @Test
    void foodComponentReadsThroughThePort() {
        item.food = new FoodData(4, 0.6F, true);

        assertEquals(4, stackWith(Map.of()).getFood().hunger(), "hunger");
        assertEquals(0.6F, stackWith(Map.of()).getFood().saturation(), "saturation");
        assertEquals(true, stackWith(Map.of()).getFood().alwaysEdible(), "alwaysEdible");

        item.food = null;
        assertNull(stackWith(Map.of()).getFood(), "a non-food item reads undefined");
    }

    @Test
    void creativeWritesLandOnTheBuiltStack() throws Exception {
        ItemStackBuilder builder = new ItemStackBuilder("minecraft:diamond_sword")
                .maxDamage(250);

        assertTrue(builder.setDurability(100).isDone(), "writes settle immediately");
        builder.addEnchantment("minecraft:sharpness", 5).get();
        builder.addEnchantment(unbreakingHandle(), null).get();
        builder.addLore(List.of("first", 2, "third")).get();
        builder.hideEnchantments(true).get();
        builder.hideModifiers(false).get();
        builder.hideUnbreakable(true).get();
        builder.hideCanDestroy(true).get();
        builder.hideCanPlace(false).get();
        builder.hideDye(true).get();

        ItemStack built = builder.build();
        assertEquals(150, built.getDamage(), "setDurability stores max minus durability");
        assertEquals(2, built.getEnchantments().size(), "both enchantments landed");
        assertEquals("minecraft:sharpness", built.getEnchantments().get(0).getId(),
                "first enchantment");
        assertEquals(5, built.getEnchantments().get(0).getLevel(), "first level");
        assertEquals("minecraft:unbreaking", built.getEnchantments().get(1).getId(),
                "handle enchantment");
        assertEquals(3, built.getEnchantments().get(1).getLevel(), "handle level");
        assertEquals(List.of("first", "2", "third"), built.getLore(), "addLore stringifies");
        assertEquals(true, built.isEnchantmentsHidden(), "enchantmentsHidden");
        assertEquals(false, built.isModifiersHidden(), "modifiersHidden");
        assertEquals(true, built.isUnbreakableHidden(), "unbreakableHidden");
        assertEquals(true, built.isCanDestroyHidden(), "canDestroyHidden");
        assertEquals(false, built.isCanPlaceHidden(), "canPlaceHidden");
        assertEquals(true, built.isDyeHidden(), "dyeHidden");
    }

    @Test
    void creativeWritesReplaceAndClearEntries() throws Exception {
        ItemStackBuilder builder = new ItemStackBuilder("minecraft:diamond_sword");
        builder.addEnchantment("minecraft:sharpness", 1).get();
        builder.addEnchantment("minecraft:sharpness", 5).get();
        assertEquals(1, builder.build().getEnchantments().size(),
                "an upgrade replaces the same enchantment");

        builder.removeEnchantment("minecraft:sharpness").get();
        assertEquals(0, builder.build().getEnchantments().size(), "removeEnchantment by id");

        builder.addEnchantment("minecraft:unbreaking", 3).get();
        builder.removeEnchantment(unbreakingHandle()).get();
        assertEquals(0, builder.build().getEnchantments().size(), "removeEnchantment by handle");

        builder.addEnchantment("minecraft:unbreaking", 3).get();
        builder.clearEnchantments().get();
        assertEquals(0, builder.build().getEnchantments().size(), "clearEnchantments");

        builder.addLore("keep").get();
        builder.clearLore().get();
        assertTrue(builder.build().getLore().isEmpty(), "clearLore");
    }

    @Test
    void creativeDraftCopiesTheSourceStack() {
        Map<String, Object> extras = Map.of("enchantmentsHidden", true, "tool", true);
        ItemStack source = stackWith(extras);

        ItemStackBuilder draft = source.getCreative();
        draft.hideDye(true);
        ItemStack edited = draft.build();

        assertEquals(true, edited.isEnchantmentsHidden(), "copied hidden flag");
        assertEquals(true, edited.isTool(), "copied tool fact");
        assertEquals(true, edited.isDyeHidden(), "draft write lands");
        assertEquals(source.getId(), edited.getId(), "draft keeps the item id");
        assertEquals(source.getCount(), edited.getCount(), "draft keeps the count");
    }

    @Test
    void foreignArgumentsAreRejectedLoudly() {
        item.spec = new EnchantmentSpec("minecraft:sharpness", 1, 5, 10,
                false, false, List.of());
        Enchantment sharpness = enchantedStack().getEnchantments().get(0);

        assertThrows(IllegalArgumentException.class,
                () -> sharpness.canBeApplied(42), "foreign argument");
        assertThrows(IllegalArgumentException.class,
                () -> sharpness.isCompatible(42), "foreign argument");
        assertThrows(IllegalArgumentException.class,
                () -> new ItemStackBuilder("minecraft:stick").addEnchantment(42, 1),
                "foreign enchantment");
        assertThrows(IllegalArgumentException.class,
                () -> new ItemStackBuilder("minecraft:stick").addEnchantment("sharpness", null),
                "a missing level");
        assertNull(sharpness.canBeApplied(null), "an absent argument reads undefined");
        assertNull(sharpness.isCompatible(null), "an absent argument reads undefined");
    }

    /** The identifiers of a list of enchantment entries. */
    private static List<String> idsOf(List<Enchantment> entries) {
        return entries.stream().map(Enchantment::getId).toList();
    }

    /** An unbreaking III handle reached through a stack entry. */
    private static Enchantment unbreakingHandle() {
        return ItemStack.of(new ItemStackData("minecraft:diamond_sword", 1, "Diamond Sword",
                0, 0, List.of(),
                List.of(new EnchantmentData("minecraft:unbreaking", 3)), Map.of()))
                .getEnchantments().get(0);
    }

    /** One stack carrying a single sharpness entry. */
    private static ItemStack stackWith(Map<String, Object> extras) {
        return ItemStack.of(new ItemStackData("minecraft:diamond_sword", 1, "Diamond Sword",
                0, 0, List.of(), List.of(), extras));
    }

    /** One stack carrying a single sharpness V entry. */
    private static ItemStack enchantedStack() {
        return ItemStack.of(new ItemStackData("minecraft:diamond_sword", 1, "Diamond Sword",
                0, 0, List.of(),
                List.of(new EnchantmentData("minecraft:sharpness", 5)), Map.of()));
    }

    /** Item port over canned fields the tests mutate directly. */
    static final class CannedItem implements ItemPort {
        EnchantmentSpec spec;
        EnchantmentRelations relations;
        Boolean compatible;
        FoodData food;
        List<String> possibleEnchantments;
        ChatComponent levelName;

        @Override
        public Optional<EnchantmentSpec> enchantmentSpec(String enchantmentId) {
            return Optional.ofNullable(spec);
        }

        @Override
        public Optional<EnchantmentRelations> enchantmentRelations(String enchantmentId) {
            return Optional.ofNullable(relations);
        }

        @Override
        public Optional<Boolean> enchantmentsCompatible(String firstId, String secondId) {
            return Optional.ofNullable(compatible);
        }

        @Override
        public Optional<FoodData> food(String itemId) {
            return Optional.ofNullable(food);
        }

        @Override
        public Optional<List<String>> possibleEnchantments(String itemId, boolean enchantingTable) {
            return Optional.ofNullable(possibleEnchantments);
        }

        @Override
        public Optional<ChatComponent> enchantmentLevelName(String enchantmentId, int level) {
            return Optional.ofNullable(levelName);
        }
    }

    /** Adapter serving only the item port; the rest is unused here. */
    static final class ItemAdapter implements NeomacrosAdapter {

        private final CannedItem item;

        ItemAdapter(CannedItem item) {
            this.item = item;
        }
        @Override
        public LifecyclePort lifecycle() {
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
        public PlayerPort player() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientPort client() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InventoryPort inventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ink.tuanzi.neomacros.port.DialogPort dialog() {
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

        @Override
        public ItemPort item() {
            return item;
        }
    }

    /** Adapter with no item port at all: every query degrades to undefined. */
    static final class BareAdapter implements NeomacrosAdapter {
        @Override
        public LifecyclePort lifecycle() {
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
        public PlayerPort player() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientPort client() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InventoryPort inventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ink.tuanzi.neomacros.port.DialogPort dialog() {
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
