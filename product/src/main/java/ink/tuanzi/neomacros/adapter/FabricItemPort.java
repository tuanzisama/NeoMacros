package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.ItemPort;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Item face queries backed by the item registry and the local player:
 * creative-tab membership, registry lookups (default stack, tags, crafting
 * remainder), tool and repair evaluations, and the cooldown and attack
 * damage reads. Registry lookups answer on the calling thread; the
 * cooldown and attribute reads observe the client player directly, the
 * same thread contract the inventory port uses.
 */
public final class FabricItemPort implements ItemPort {

    @Override
    public Optional<List<String>> creativeTabs(String itemId) {
        ItemStack stack = defaultStackOf(itemId).orElse(null);
        if (stack == null) {
            return Optional.empty();
        }
        List<String> out = new ArrayList<>();
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab.isAlignedRight() || !containsItem(tab, stack)) {
                continue;
            }
            out.add(tab.getDisplayName().getString());
        }
        return Optional.of(List.copyOf(out));
    }

    @Override
    public Optional<List<ItemStackData>> groupIcons(String itemId) {
        ItemStack stack = defaultStackOf(itemId).orElse(null);
        if (stack == null) {
            return Optional.empty();
        }
        List<ItemStackData> out = new ArrayList<>();
        for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
            if (tab.isAlignedRight() || !containsItem(tab, stack)) {
                continue;
            }
            out.add(FabricInventoryPort.stackDataOf(tab.getIconItem()));
        }
        return Optional.of(List.copyOf(out));
    }

    /** Whether a tab's display items hold an item equal to the stack. */
    private static boolean containsItem(CreativeModeTab tab, ItemStack stack) {
        for (ItemStack entry : tab.getDisplayItems()) {
            if (ItemStack.isSameItem(entry, stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Boolean> canBeRepairedWith(String itemId, String materialId) {
        ItemStack receiver = defaultStackOf(itemId).orElse(null);
        ItemStack material = defaultStackOf(materialId).orElse(null);
        if (receiver == null || material == null) {
            return Optional.empty();
        }
        var repair = receiver.get(net.minecraft.core.component.DataComponents.REPAIRABLE);
        if (repair == null) {
            return Optional.of(false);
        }
        return Optional.of(repair.isValidRepairItem(material));
    }

    @Override
    public Optional<Boolean> correctToolForDrops(String itemId, String blockId,
            Map<String, String> props) {
        ItemStack stack = defaultStackOf(itemId).orElse(null);
        net.minecraft.world.level.block.state.BlockState state = stateOf(blockId, props);
        if (stack == null || state == null) {
            return Optional.empty();
        }
        return Optional.of(stack.isCorrectToolForDrops(state));
    }

    @Override
    public Optional<Float> destroySpeed(String itemId, String blockId,
            Map<String, String> props) {
        ItemStack stack = defaultStackOf(itemId).orElse(null);
        net.minecraft.world.level.block.state.BlockState state = stateOf(blockId, props);
        if (stack == null || state == null) {
            return Optional.empty();
        }
        return Optional.of(stack.getDestroySpeed(state));
    }

    @Override
    public Optional<ItemStackData> defaultStack(String itemId) {
        return defaultStackOf(itemId).map(FabricInventoryPort::stackDataOf);
    }

    @Override
    public Optional<ItemStackData> recipeRemainder(String itemId) {
        Item item = itemOf(itemId);
        if (item == null) {
            return Optional.empty();
        }
        //? if >=26.1 {
        var template = item.getCraftingRemainder();
        if (template == null) {
            return Optional.empty();
        }
        ItemStack remainder = template.create();
        //?} else {
        /*
        ItemStack remainder = item.getCraftingRemainder();
        */
        //?}
        if (remainder.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(FabricInventoryPort.stackDataOf(remainder));
    }

    @Override
    public Optional<Boolean> hasRecipeRemainder(String itemId) {
        Item item = itemOf(itemId);
        if (item == null) {
            return Optional.empty();
        }
        //? if >=26.1 {
        var template = item.getCraftingRemainder();
        if (template == null) {
            return Optional.of(false);
        }
        return Optional.of(!template.create().isEmpty());
        //?} else {
        /*
        return Optional.of(!item.getCraftingRemainder().isEmpty());
        */
        //?}
    }

    @Override
    public Optional<Double> attackDamage(String itemId) {
        ItemStack stack = defaultStackOf(itemId).orElse(null);
        var player = Minecraft.getInstance().player;
        if (stack == null || player == null) {
            return Optional.empty();
        }
        ItemAttributeModifiers modifiers = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY);
        double base = player.getAttributeBaseValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        //? if >=1.21.11 {
        return Optional.of(modifiers.compute(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                base, EquipmentSlot.MAINHAND));
        //?} else {
        /*
        return Optional.of(modifiers.compute(base, EquipmentSlot.MAINHAND));
        */
        //?}
    }

    @Override
    public Optional<Boolean> onCooldown(String itemId) {
        ItemStack stack = defaultStackOf(itemId).orElse(null);
        var player = Minecraft.getInstance().player;
        if (stack == null || player == null) {
            return Optional.empty();
        }
        return Optional.of(player.getCooldowns().isOnCooldown(stack));
    }

    @Override
    public Optional<Float> cooldownProgress(String itemId) {
        ItemStack stack = defaultStackOf(itemId).orElse(null);
        var player = Minecraft.getInstance().player;
        if (stack == null || player == null) {
            return Optional.empty();
        }
        float partialTick = Minecraft.getInstance().getDeltaTracker()
                .getGameTimeDeltaPartialTick(false);
        return Optional.of(player.getCooldowns().getCooldownPercent(stack, partialTick));
    }

    @Override
    public Optional<List<String>> itemTags(String itemId) {
        Item item = itemOf(itemId);
        if (item == null) {
            return Optional.empty();
        }
        var stack = item.getDefaultInstance();
        List<String> out = new ArrayList<>();
        //? if >=26.1 {
        stack.typeHolder().tags().forEach(tag -> out.add(tag.location().toString()));
        //?} else {
        /*
        stack.getItemHolder().tags().forEach(tag -> out.add(tag.location().toString()));
        */
        //?}
        return Optional.of(List.copyOf(out));
    }

    /** Resolves one item id to its default stack, or empty when unknown. */
    private static Optional<ItemStack> defaultStackOf(String itemId) {
        Item item = itemOf(itemId);
        return item == null ? Optional.empty() : Optional.of(item.getDefaultInstance());
    }

    /** Resolves one item id through the registry, or null when unknown. */
    private static Item itemOf(String itemId) {
        Identifier id = parseId(itemId);
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /**
     * Rebuilds one block state from its identifier and vanilla properties;
     * unknown identifiers and unparseable values fall through to null so
     * callers answer undefined.
     */
    private static net.minecraft.world.level.block.state.BlockState stateOf(
            String blockId, Map<String, String> props) {
        Identifier id = parseId(blockId);
        if (id == null) {
            return null;
        }
        var block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            return null;
        }
        net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
        if (props != null) {
            for (Map.Entry<String, String> entry : props.entrySet()) {
                var property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (property != null) {
                    state = withProperty(state, property, entry.getValue());
                }
            }
        }
        return state;
    }

    /** Applies one parsed property value to the state (absent values keep the state). */
    private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState
            withProperty(net.minecraft.world.level.block.state.BlockState state,
                    net.minecraft.world.level.block.state.properties.Property<T> property,
                    String raw) {
        return property.getValue(raw).map(value -> state.setValue(property, value)).orElse(state);
    }

    /** Parses one namespaced identifier, or null when malformed. */
    private static Identifier parseId(String id) {
        return id == null || id.isBlank() ? null : Identifier.tryParse(id);
    }
}
