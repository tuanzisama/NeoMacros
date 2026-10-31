package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Immutable copy of one block's state: block identifier and position.
 *
 * <p>Neutral value object; identifiers use the namespaced form
 * ({@code minecraft:stone}).</p>
 */
public record BlockData(String id, Pos3d pos) {

    public BlockData {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(pos, "pos cannot be null");
    }
}
