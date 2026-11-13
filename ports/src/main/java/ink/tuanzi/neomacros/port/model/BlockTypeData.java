package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable copy of one block type's registry-level facts: identifier,
 * display name, tag membership, physics multipliers, the default state, and
 * every defined state of the block.
 *
 * <p>Neutral value object; identifiers use the namespaced form
 * ({@code minecraft:stone}) and states carry only their vanilla property
 * maps (every state of a block shares the type identifier).</p>
 *
 * @param id                   namespaced block identifier
 * @param name                 translated display name
 * @param tags                 full block tag identifiers, e.g. {@code minecraft:base_stone_overworld}
 * @param blastResistance      explosion resistance factor
 * @param jumpVelocityMultiplier jump boost multiplier
 * @param slipperiness         friction value
 * @param velocityMultiplier   entity movement speed factor
 * @param canMobSpawnInside    whether mobs can spawn inside the default state
 * @param hasDynamicBounds     whether the block shape is marked dynamic
 * @param defaultState         vanilla properties of the registered default state
 * @param states               vanilla properties of every possible state
 * @param defaultItemStack     the block's item form, or null when the block has no item
 */
public record BlockTypeData(String id, String name, List<String> tags,
        float blastResistance, float jumpVelocityMultiplier, float slipperiness,
        float velocityMultiplier, boolean canMobSpawnInside, boolean hasDynamicBounds,
        Map<String, String> defaultState, List<Map<String, String>> states,
        ItemStackData defaultItemStack) {

    public BlockTypeData {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        tags = List.copyOf(tags == null ? List.of() : tags);
        defaultState = Map.copyOf(defaultState == null ? Map.of() : defaultState);
        states = List.copyOf(states == null ? List.of() : states);
    }
}
