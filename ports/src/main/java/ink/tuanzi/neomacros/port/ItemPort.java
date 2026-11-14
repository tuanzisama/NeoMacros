package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.ItemStackData;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Item-type queries that cannot be answered from a neutral stack snapshot
 * alone: creative-tab membership, registry lookups (default stack, tags,
 * crafting remainder), repair/tool evaluations against a block state, and
 * the local-player cooldown and attack-damage reads.
 *
 * <p>Every member takes neutral identifiers and returns {@link Optional};
 * an empty optional reads back as {@code undefined} on the script surface.
 * All methods degrade gracefully by default so adapters that predate a
 * feature keep older nodes compiling.</p>
 */
public interface ItemPort {

    /**
     * Shared instance answering every query as unsupported: new adapters
     * inherit these defaults until they override a member.
     */
    ItemPort NONE = new ItemPort() {
    };

    /**
     * Display names of the creative tabs containing the item, in tab order;
     * right-aligned (search) tabs are excluded. Empty when the item sits in
     * no tab; empty when the item id is unknown.
     */
    default Optional<List<String>> creativeTabs(String itemId) {
        return Optional.empty();
    }

    /**
     * Icon stacks of the creative tabs containing the item, in the same
     * order as {@link #creativeTabs(String)}.
     */
    default Optional<List<ItemStackData>> groupIcons(String itemId) {
        return Optional.empty();
    }

    /**
     * Whether a stack of the material item repairs items of the given item
     * at an anvil. False when the item accepts no repair material; empty
     * when either id is unknown.
     */
    default Optional<Boolean> canBeRepairedWith(String itemId, String materialId) {
        return Optional.empty();
    }

    /**
     * Whether the item is the correct tool for drops against the block
     * state (id plus vanilla properties; empty props mean the block's
     * default state). Empty when either id is unknown.
     */
    default Optional<Boolean> correctToolForDrops(String itemId, String blockId,
            Map<String, String> props) {
        return Optional.empty();
    }

    /**
     * The mining-speed multiplier the item yields against the block state
     * (id plus vanilla properties; empty props mean the default state).
     * Empty when either id is unknown.
     */
    default Optional<Float> destroySpeed(String itemId, String blockId,
            Map<String, String> props) {
        return Optional.empty();
    }

    /**
     * A fresh default stack of one item (count 1), or empty for an unknown
     * item id.
     */
    default Optional<ItemStackData> defaultStack(String itemId) {
        return Optional.empty();
    }

    /**
     * The crafting remainder of one item, or empty when the item has no
     * remainder or the id is unknown.
     */
    default Optional<ItemStackData> recipeRemainder(String itemId) {
        return Optional.empty();
    }

    /**
     * Whether one item has a crafting remainder at all; empty when the item
     * id is unknown (the presence face stays three-state where the value
     * face collapses to undefined).
     */
    default Optional<Boolean> hasRecipeRemainder(String itemId) {
        return Optional.empty();
    }

    /**
     * The main-hand attack damage a stack of the item yields with the
     * local player's base attribute, or empty without a player or for an
     * unknown item id.
     */
    default Optional<Double> attackDamage(String itemId) {
        return Optional.empty();
    }

    /**
     * Whether the local player currently has a cooldown for the item's
     * cooldown group, or empty without a player or for an unknown id.
     */
    default Optional<Boolean> onCooldown(String itemId) {
        return Optional.empty();
    }

    /**
     * The remaining cooldown fraction (1 at the start, 0 when expired or
     * absent), or empty without a player or for an unknown item id.
     */
    default Optional<Float> cooldownProgress(String itemId) {
        return Optional.empty();
    }

    /**
     * The full registry identifiers of the item tags the item belongs to;
     * empty for an unknown item id.
     */
    default Optional<List<String>> itemTags(String itemId) {
        return Optional.empty();
    }
}
