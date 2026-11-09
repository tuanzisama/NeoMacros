package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable point-in-time copy of an entity's state.
 *
 * <p>Neutral value object carried across port signatures and event payloads;
 * never a live game reference. Live access goes through the entity API,
 * which queries {@code EntityPort} and wraps results as handles. Absent
 * data uses neutral defaults: a zero origin vector for direction/velocity on
 * plain entities, an empty passenger list, and {@code null} bounding box /
 * vehicle / held stacks when the entity kind carries none.</p>
 */
public record EntitySnapshot(UUID uuid, String type, String name, Pos3d pos, double health,
        double maxHealth, Pos3d velocity, Pos3d direction, Rot2d rotation, double eyeHeight,
        boolean onGround, boolean sneaking, boolean sprinting, boolean swimming, boolean gliding,
        boolean inWater, boolean inLava, boolean onFire, String pose, String facing,
        String dimension, BoundingBoxData boundingBox, List<UUID> passengers, UUID vehicle,
        ItemStackData heldItem, ItemStackData offhandItem, boolean living) {

    public EntitySnapshot {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(pos, "pos cannot be null");
        passengers = List.copyOf(passengers == null ? List.of() : passengers);
        if (pose == null) {
            pose = "standing";
        }
        if (facing == null) {
            facing = "south";
        }
        if (dimension == null) {
            dimension = "";
        }
    }

    /** Convenience constructor for minimal snapshots (compat for existing
     * wiring and tests); extended fields carry neutral defaults.
     * @zh 兼容构造：仅核心字段，扩展字段用中性默认值。 */
    public EntitySnapshot(UUID uuid, String type, String name, Pos3d pos, double health) {
        this(uuid, type, name, pos, health, 0, new Pos3d(0, 0, 0), new Pos3d(0, 0, 0),
                new Rot2d(0, 0), 0, false, false, false, false, false, false, false, false,
                "standing", "south", "", null, List.of(), null, null, null, false);
    }
}
