package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Immutable copy of one item stack: item identifier, stack size, and display
 * name.
 *
 * <p>Neutral value object carried across port signatures and event payloads;
 * identifiers use the namespaced form ({@code minecraft:diamond}).</p>
 */
public record ItemStackData(String id, int count, String displayName) {

    public ItemStackData {
        Objects.requireNonNull(id, "id cannot be null");
    }
}
