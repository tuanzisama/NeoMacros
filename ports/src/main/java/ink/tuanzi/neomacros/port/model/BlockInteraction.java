package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Immutable copy of one block hit: the namespaced block identifier and its
 * position. Produced by block raycasts and block interaction sources.
 *
 * <p>Neutral value object; identifiers use the namespaced form
 * ({@code minecraft:stone}).</p>
 */
public record BlockInteraction(String id, Pos3d pos) {
    public BlockInteraction {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(pos, "pos cannot be null");
    }
}
