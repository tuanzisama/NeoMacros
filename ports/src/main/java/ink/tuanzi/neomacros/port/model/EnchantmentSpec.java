package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of one enchantment's registry definition: the level
 * bounds, the selection weight, the curse and treasure flags, and the item
 * types the enchantment supports.
 *
 * <p>The lists are copied defensively and stored unmodifiable; an unknown
 * enchantment identifier never produces a spec, so callers answer
 * {@code undefined} from an absent optional.</p>
 *
 * @param id                namespaced enchantment identifier ({@code minecraft:sharpness})
 * @param minLevel          lowest level the enchantment defines
 * @param maxLevel          highest level the enchantment defines
 * @param weight            selection weight used by the enchanting table
 * @param cursed            whether the enchantment carries the curse tag
 * @param treasure          whether the enchantment carries the treasure tag
 * @param acceptableItemIds item identifiers this enchantment supports
 */
public record EnchantmentSpec(String id, int minLevel, int maxLevel, int weight,
                              boolean cursed, boolean treasure,
                              List<String> acceptableItemIds) {

    public EnchantmentSpec {
        Objects.requireNonNull(id, "id cannot be null");
        acceptableItemIds = List.copyOf(acceptableItemIds);
    }
}
