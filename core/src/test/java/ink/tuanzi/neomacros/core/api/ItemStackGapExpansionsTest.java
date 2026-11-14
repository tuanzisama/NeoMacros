package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.types.BlockState;
import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.core.api.types.ItemStackBuilder;
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
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockTypeData;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.RayHit;
import ink.tuanzi.neomacros.port.model.RayOptions;
import ink.tuanzi.neomacros.port.model.ChunkData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The G-027 item face: the item-type facts captured with the stack
 * snapshot (tool/wearable/fireproof flags, block-item identity, tags,
 * enchantability, tooltip hidden flags, adventure restrictions), the
 * registry and evaluation reads served through the item port (creative
 * tabs, default stack, remainder, cooldown, attack damage, tool checks),
 * and the absent boundaries (facts missing read undefined, a missing port
 * reads undefined, unknown arguments are rejected loudly).
 */
class ItemStackGapExpansionsTest {

    private CannedItem item;
    private TypeWorld world;

    @BeforeEach
    void install() {
        item = new CannedItem();
        world = new TypeWorld();
        ScriptApiEnvironment.install(null, new ItemAdapter(item, world));
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void itemFactsReadThroughTheSnapshot() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("tool", true);
        extras.put("wearable", false);
        extras.put("fireproof", true);
        extras.put("blockItem", true);
        extras.put("blockId", "minecraft:chest");
        extras.put("canFitInsideContainerItems", false);
        extras.put("enchantability", 15);
        extras.put("enchantmentsHidden", true);
        extras.put("modifiersHidden", false);
        extras.put("unbreakableHidden", true);
        extras.put("canDestroyHidden", false);
        extras.put("canPlaceHidden", true);
        extras.put("dyeHidden", false);
        extras.put("hasDestroyRestrictions", true);
        extras.put("destroyRestrictions", List.of("minecraft:stone", "minecraft:dirt"));
        extras.put("hasPlaceRestrictions", false);
        extras.put("placeRestrictions", List.of("minecraft:oak_planks"));
        ItemStack stack = stackWith(extras);
        world.type = chestType();

        assertEquals(true, stack.isTool(), "tool");
        assertEquals(false, stack.isWearable(), "wearable");
        assertEquals(true, stack.isFireproof(), "fireproof");
        assertEquals(true, stack.isBlockItem(), "blockItem");
        assertEquals(false, stack.canFitInsideContainerItems(), "canFitInsideContainerItems");
        assertEquals(15, stack.getEnchantability(), "enchantability");
        assertEquals(true, stack.isEnchantmentsHidden(), "enchantmentsHidden");
        assertEquals(false, stack.isModifiersHidden(), "modifiersHidden");
        assertEquals(true, stack.isUnbreakableHidden(), "unbreakableHidden");
        assertEquals(false, stack.isCanDestroyHidden(), "canDestroyHidden");
        assertEquals(true, stack.isCanPlaceHidden(), "canPlaceHidden");
        assertEquals(false, stack.isDyeHidden(), "dyeHidden");
        assertEquals(true, stack.hasDestroyRestrictions(), "hasDestroyRestrictions");
        assertEquals(false, stack.hasPlaceRestrictions(), "hasPlaceRestrictions");
        assertEquals(List.of("minecraft:stone", "minecraft:dirt"),
                stack.getDestroyRestrictions(), "destroyRestrictions");
        assertEquals(List.of("minecraft:oak_planks"),
                stack.getPlaceRestrictions(), "placeRestrictions");
        assertEquals("minecraft:chest", stack.getBlock().getId(), "block id");
        assertEquals(Map.of("facing", "north"), stack.getBlock().getProps(), "block props");
    }

    @Test
    void absentFactsReadUndefined() {
        ItemStack stack = stackWith(Map.of());

        assertNull(stack.isTool(), "tool");
        assertNull(stack.isWearable(), "wearable");
        assertNull(stack.isFireproof(), "fireproof");
        assertNull(stack.isBlockItem(), "blockItem");
        assertNull(stack.getBlock(), "block");
        assertNull(stack.canFitInsideContainerItems(), "canFitInsideContainerItems");
        assertNull(stack.getEnchantability(), "enchantability");
        assertNull(stack.isEnchantmentsHidden(), "enchantmentsHidden");
        assertNull(stack.isModifiersHidden(), "modifiersHidden");
        assertNull(stack.isUnbreakableHidden(), "unbreakableHidden");
        assertNull(stack.isCanDestroyHidden(), "canDestroyHidden");
        assertNull(stack.isCanPlaceHidden(), "canPlaceHidden");
        assertNull(stack.isDyeHidden(), "dyeHidden");
        assertNull(stack.hasDestroyRestrictions(), "hasDestroyRestrictions");
        assertNull(stack.hasPlaceRestrictions(), "hasPlaceRestrictions");
        assertNull(stack.getDestroyRestrictions(), "destroyRestrictions");
        assertNull(stack.getPlaceRestrictions(), "placeRestrictions");
    }

    @Test
    void blockReadComposesTheBlockIdWithTheRegistryType() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("blockItem", true);
        extras.put("blockId", "minecraft:chest");

        world.type = chestType();
        BlockState block = stackWith(extras).getBlock();
        assertEquals("minecraft:chest", block.getId(), "block id");
        assertEquals(Map.of("facing", "north"), block.getProps(), "default props");

        world.type = null;
        assertNull(stackWith(extras).getBlock(), "unknown block type reads undefined");

        assertNull(stackWith(Map.of()).getBlock(), "non-block item reads undefined");
    }

    @Test
    void portBackedFacesReadThroughTheItemPort() {
        item.creativeTabs = List.of("Decoration Blocks");
        item.groupIcons = List.of(new ItemStackData("minecraft:chest", 1, "Chest"));
        item.tags = List.of("minecraft:planks", "minecraft:wooden_materials");
        item.defaultStack = new ItemStackData("minecraft:diamond_sword", 1, "Diamond Sword");
        item.recipeRemainder = new ItemStackData("minecraft:bowl", 1, "Bowl");
        item.hasRecipeRemainder = Boolean.TRUE;
        item.attackDamage = 7.5D;
        item.onCooldown = Boolean.FALSE;
        item.cooldownProgress = 0.25F;
        item.correctToolForDrops = Boolean.TRUE;
        item.destroySpeed = 2.0F;
        item.repairable = Boolean.TRUE;
        ItemStack stack = stackWith(Map.of());
        ItemStack material = stackWith(Map.of());
        BlockState stone = new BlockState("minecraft:stone", Map.of());

        assertEquals(List.of("Decoration Blocks"), stack.getCreativeTab(), "creativeTab");
        assertEquals(1, stack.getGroupIcon().size(), "groupIcon size");
        assertEquals("minecraft:chest", stack.getGroupIcon().get(0).getId(), "groupIcon id");
        assertEquals(List.of("minecraft:planks", "minecraft:wooden_materials"),
                stack.getTags(), "tags");
        assertEquals("minecraft:diamond_sword", stack.getDefaultStack().getId(), "defaultStack");
        assertEquals("minecraft:bowl", stack.getRecipeRemainder().getId(), "recipeRemainder");
        assertEquals(true, stack.hasRecipeRemainder(), "hasRecipeRemainder");
        assertEquals(7.5D, stack.getAttackDamage(), "attackDamage");
        assertEquals(false, stack.isOnCooldown(), "onCooldown");
        assertEquals(0.25F, stack.getCooldownProgress(), "cooldownProgress");
        assertEquals(true, stack.isCorrectToolForDrops(stone), "isCorrectToolForDrops(state)");
        assertEquals(2.0F, stack.getDestroySpeed(stone), "getDestroySpeed(state)");
        assertEquals(true, stack.canBeRepairedWith(material), "canBeRepairedWith");
    }

    @Test
    void portBackedFacesReadUndefinedWithoutThePort() {
        ScriptApiEnvironment.install(null, new BareAdapter(world));
        ItemStack stack = stackWith(Map.of());
        BlockState stone = new BlockState("minecraft:stone", Map.of());

        assertNull(stack.getCreativeTab(), "creativeTab");
        assertNull(stack.getGroupIcon(), "groupIcon");
        assertNull(stack.getTags(), "tags");
        assertNull(stack.getDefaultStack(), "defaultStack");
        assertNull(stack.getRecipeRemainder(), "recipeRemainder");
        assertNull(stack.hasRecipeRemainder(), "hasRecipeRemainder");
        assertNull(stack.getAttackDamage(), "attackDamage");
        assertNull(stack.isOnCooldown(), "onCooldown");
        assertNull(stack.getCooldownProgress(), "cooldownProgress");
        assertNull(stack.isCorrectToolForDrops(stone), "isCorrectToolForDrops");
        assertNull(stack.getDestroySpeed(stone), "getDestroySpeed");
        assertNull(stack.canBeRepairedWith(stack), "canBeRepairedWith");
    }

    @Test
    void unknownAnswersFromThePortReadUndefined() {
        ItemStack stack = stackWith(Map.of());

        assertNull(stack.getCreativeTab(), "creativeTab");
        assertNull(stack.getTags(), "tags");
        assertNull(stack.getDefaultStack(), "defaultStack");
        assertNull(stack.hasRecipeRemainder(), "hasRecipeRemainder");
        assertNull(stack.getAttackDamage(), "attackDamage");
        assertNull(stack.isOnCooldown(), "onCooldown");
    }

    @Test
    void toDataRoundTripKeepsTheFacts() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("tool", true);
        extras.put("enchantability", 10);
        ItemStack stack = stackWith(extras);

        ItemStack copy = ItemStack.of(stack.toData());
        assertEquals(true, copy.isTool(), "tool survives the round trip");
        assertEquals(10, copy.getEnchantability(), "enchantability survives the round trip");
        assertEquals(stack, copy, "equal stacks stay equal");
    }

    @Test
    void copyFromKeepsFactsOnTheBuiltStack() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("tool", true);
        extras.put("fireproof", false);
        ItemStack source = stackWith(extras);

        ItemStack copied = new ItemStackBuilder("minecraft:diamond_sword")
                .copyFrom(source)
                .build();
        assertEquals(true, copied.isTool(), "copy keeps the tool fact");
        assertEquals(false, copied.isFireproof(), "copy keeps the fireproof fact");

        ItemStack fresh = new ItemStackBuilder("minecraft:diamond_sword").build();
        assertNull(fresh.isTool(), "a fresh builder stack has no facts (undefined)");
    }

    @Test
    void foreignArgumentsAreRejectedLoudly() {
        item.correctToolForDrops = Boolean.TRUE;
        ItemStack stack = stackWith(Map.of());
        BlockState stone = new BlockState("minecraft:stone", Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> stack.isCorrectToolForDrops("minecraft:stone"), "foreign argument");
        assertThrows(IllegalArgumentException.class,
                () -> stack.canBeRepairedWith(null), "missing material");
        assertNull(stack.isCorrectToolForDrops(null), "undefined target reads undefined");
        assertNull(stack.getDestroySpeed(null), "undefined target reads undefined");
        assertEquals(true, stack.isCorrectToolForDrops(stone), "state argument accepted");
    }

    /** One stack carrying the given neutral facts. */
    private static ItemStack stackWith(Map<String, Object> extras) {
        return ItemStack.of(new ItemStackData("minecraft:diamond_sword", 1, "Diamond Sword",
                0, 0, List.of(), List.of(), extras));
    }

    /** The canned default state of the chest block type. */
    private static BlockTypeData chestType() {
        return new BlockTypeData("minecraft:chest", "Chest",
                List.of("minecraft:wooden_materials"),
                2.5f, 0.6f, 0.6f, 1.0f, false, false,
                Map.of("facing", "north"),
                List.of(Map.of("facing", "north")),
                new ItemStackData("minecraft:chest", 1, "Chest"));
    }

    /** Item port over canned fields the tests mutate directly. */
    static final class CannedItem implements ItemPort {
        List<String> creativeTabs;
        List<ItemStackData> groupIcons;
        List<String> tags;
        ItemStackData defaultStack;
        ItemStackData recipeRemainder;
        Boolean hasRecipeRemainder;
        Double attackDamage;
        Boolean onCooldown;
        Float cooldownProgress;
        Boolean correctToolForDrops;
        Float destroySpeed;
        Boolean repairable;

        @Override
        public Optional<List<String>> creativeTabs(String itemId) {
            return Optional.ofNullable(creativeTabs);
        }

        @Override
        public Optional<List<ItemStackData>> groupIcons(String itemId) {
            return Optional.ofNullable(groupIcons);
        }

        @Override
        public Optional<List<String>> itemTags(String itemId) {
            return Optional.ofNullable(tags);
        }

        @Override
        public Optional<ItemStackData> defaultStack(String itemId) {
            return Optional.ofNullable(defaultStack);
        }

        @Override
        public Optional<ItemStackData> recipeRemainder(String itemId) {
            return Optional.ofNullable(recipeRemainder);
        }

        @Override
        public Optional<Boolean> hasRecipeRemainder(String itemId) {
            return Optional.ofNullable(hasRecipeRemainder);
        }

        @Override
        public Optional<Double> attackDamage(String itemId) {
            return Optional.ofNullable(attackDamage);
        }

        @Override
        public Optional<Boolean> onCooldown(String itemId) {
            return Optional.ofNullable(onCooldown);
        }

        @Override
        public Optional<Float> cooldownProgress(String itemId) {
            return Optional.ofNullable(cooldownProgress);
        }

        @Override
        public Optional<Boolean> correctToolForDrops(String itemId, String blockId,
                Map<String, String> props) {
            return Optional.ofNullable(correctToolForDrops);
        }

        @Override
        public Optional<Float> destroySpeed(String itemId, String blockId,
                Map<String, String> props) {
            return Optional.ofNullable(destroySpeed);
        }

        @Override
        public Optional<Boolean> canBeRepairedWith(String itemId, String materialId) {
            return Optional.ofNullable(repairable);
        }
    }

    /** World port answering only the registry type query the block face needs. */
    static final class TypeWorld implements WorldPort {
        BlockTypeData type;
        BlockData block;

        @Override
        public long time() {
            return 0;
        }

        @Override
        public String dimension() {
            return "minecraft:overworld";
        }

        @Override
        public boolean isRaining() {
            return false;
        }

        @Override
        public Optional<BlockData> blockAt(Pos3d pos) {
            return Optional.ofNullable(block);
        }

        @Override
        public Optional<RayHit> raycast(Pos3d origin, Pos3d direction, RayOptions options) {
            return Optional.empty();
        }

        @Override
        public Optional<BlockTypeData> blockType(String blockId) {
            return Optional.ofNullable(type);
        }

        @Override
        public Optional<ChunkData> chunkAt(Pos3d pos) {
            return Optional.empty();
        }
    }

    /** Adapter serving the item and world ports; the rest is unused here. */
    record ItemAdapter(CannedItem item, TypeWorld world) implements NeomacrosAdapter {
        @Override
        public LifecyclePort lifecycle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
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
        public InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientPort client() {
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

    /** Adapter without an item port: every item face must read undefined. */
    record BareAdapter(TypeWorld world) implements NeomacrosAdapter {
        @Override
        public LifecyclePort lifecycle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
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
        public InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientPort client() {
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
