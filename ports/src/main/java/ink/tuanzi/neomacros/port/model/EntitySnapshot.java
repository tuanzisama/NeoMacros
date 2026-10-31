package ink.tuanzi.neomacros.port.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable point-in-time copy of an entity's state.
 *
 * <p>Neutral value object carried across port signatures and event payloads;
 * never a live game reference. Live access goes through the entity facade,
 * which queries {@code EntityPort} and wraps results as handles.</p>
 */
public record EntitySnapshot(UUID uuid, String type, String displayName, Pos3d pos, double health) {

    public EntitySnapshot {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(pos, "pos cannot be null");
    }
}
