package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Immutable copy of one enchantment entry: identifier and level.
 *
 * @param id    namespaced enchantment identifier ({@code minecraft:sharpness})
 * @param level enchantment level (1-based)
 */
public record EnchantmentData(String id, int level) {

    public EnchantmentData {
        Objects.requireNonNull(id, "id cannot be null");
    }
}
