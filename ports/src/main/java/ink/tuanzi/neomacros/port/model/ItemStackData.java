package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable copy of one item stack: item identifier, stack size, display
 * name, damage, and decoration data.
 *
 * <p>Neutral value object carried across port signatures and event payloads;
 * identifiers use the namespaced form ({@code minecraft:diamond}).</p>
 *
 * @param id            namespaced item identifier
 * @param count         stack size
 * @param name          display name (styled text flattened)
 * @param damage        current durability damage (0 for undamageable items)
 * @param maxDamage     maximum durability (0 for undamageable items)
 * @param lore          lore lines (flattened text, in order)
 * @param enchantments enchantments in stored order
 */
public record ItemStackData(String id, int count, String name, int damage, int maxDamage,
        List<String> lore, List<EnchantmentData> enchantments) {

    public ItemStackData {
        Objects.requireNonNull(id, "id cannot be null");
        lore = List.copyOf(lore == null ? List.of() : lore);
        enchantments = List.copyOf(enchantments == null ? List.of() : enchantments);
    }

    /** Convenience constructor for id/count/name stacks (no damage data). */
    public ItemStackData(String id, int count, String name) {
        this(id, count, name, 0, 0, List.of(), List.of());
    }
}
