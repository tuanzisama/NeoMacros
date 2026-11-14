package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of one enchantment's pairwise relations against the
 * whole enchantment registry: the identifiers it conflicts with and the
 * identifiers it combines with.
 *
 * <p>Compatibility never holds for an enchantment against itself, so the
 * conflicting list contains the enchantment's own identifier while the
 * compatible list does not. Both lists are copied defensively and stored
 * unmodifiable.</p>
 *
 * @param conflictIds   identifiers incompatible with the enchantment, including itself
 * @param compatibleIds identifiers combinable with the enchantment, excluding itself
 */
public record EnchantmentRelations(List<String> conflictIds, List<String> compatibleIds) {

    public EnchantmentRelations {
        Objects.requireNonNull(conflictIds, "conflictIds cannot be null");
        Objects.requireNonNull(compatibleIds, "compatibleIds cannot be null");
        conflictIds = List.copyOf(conflictIds);
        compatibleIds = List.copyOf(compatibleIds);
    }
}
