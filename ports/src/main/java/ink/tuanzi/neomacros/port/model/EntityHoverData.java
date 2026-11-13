package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Immutable identity capture for a show-entity hover: the entity UUID, its
 * type identifier, and the display name as of the moment the hover was set.
 * Neutral value object carried across port signatures; never a live game
 * reference.
 *
 * @param uuid entity UUID string
 * @param type entity type identifier, e.g. {@code minecraft:pig}
 * @param name display name captured when the hover was set
 */
public record EntityHoverData(String uuid, String type, String name) {

    public EntityHoverData {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }
}
