package ink.tuanzi.neomacros.port.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable point-in-time copy of the local player's state.
 *
 * <p>Neutral value object; the live form is the player facade handle, which
 * reads through {@code PlayerPort} in real time.</p>
 */
public record PlayerSnapshot(UUID uuid, String name, Pos3d pos, Rot2d look,
                             double health, int hunger, int air, float saturation,
                             boolean sneaking, boolean sprinting, boolean gliding) {

    public PlayerSnapshot {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(pos, "pos cannot be null");
        Objects.requireNonNull(look, "look cannot be null");
    }

    /** Convenience constructor for the core seven fields (compat for
     * existing wiring and tests).
     * @zh 兼容构造：核心七字段，扩展字段用中性默认值。 */
    public PlayerSnapshot(UUID uuid, String name, Pos3d pos, Rot2d look,
            double health, int hunger, int air) {
        this(uuid, name, pos, look, health, hunger, air, 0, false, false, false);
    }
}
