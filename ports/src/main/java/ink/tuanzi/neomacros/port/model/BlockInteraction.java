package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Immutable copy of one block hit: the namespaced block identifier and its
 * position. Produced by block raycasts and block interaction sources.
 *
 * <p>Neutral value object; identifiers use the namespaced form
 * ({@code minecraft:stone}).</p>
 *
 * @param id     namespaced block identifier
 * @param pos    the block grid position
 * @param face   the hit face name ({@code up}, {@code north}, ...), or null when absent
 * @param inside whether the hit landed strictly inside the block bounds
 *               (raycast sources only; interaction sources report false)
 */
public record BlockInteraction(String id, Pos3d pos, String face, boolean inside) {
    public BlockInteraction {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(pos, "pos cannot be null");
    }

    /** Convenience constructor without the interaction face (compat). */
    public BlockInteraction(String id, Pos3d pos) {
        this(id, pos, null, false);
    }

    /** Convenience constructor carrying only the block hit, no inside flag (compat). */
    public BlockInteraction(String id, Pos3d pos, String face) {
        this(id, pos, face, false);
    }
}
