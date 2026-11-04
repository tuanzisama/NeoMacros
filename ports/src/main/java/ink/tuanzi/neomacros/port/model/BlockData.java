package ink.tuanzi.neomacros.port.model;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable copy of one block's state: block identifier, position, and the
 * vanilla state properties.
 *
 * <p>Neutral value object; identifiers use the namespaced form
 * ({@code minecraft:stone}).</p>
 *
 * @param id         namespaced block identifier
 * @param pos        the block grid position
 * @param properties vanilla state properties ({@code facing=open}, ...)
 */
public record BlockData(String id, Pos3d pos, Map<String, String> properties) {

    public BlockData {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(pos, "pos cannot be null");
        properties = Map.copyOf(properties == null ? Map.of() : properties);
    }

    /** Convenience constructor for id/position without properties. */
    public BlockData(String id, Pos3d pos) {
        this(id, pos, Map.of());
    }
}
