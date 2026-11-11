package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Entity query services over neutral snapshots.
 *
 * <p>Live access is layered on top: the entity API wraps snapshots as
 * handles and re-queries through this port for real-time getters.</p>
 */
public interface EntityPort {

    /** Returns the entity with the UUID, or empty when absent or unloaded. */
    Optional<EntitySnapshot> byUuid(UUID uuid);

    /**
     * Returns snapshots of all entities within {@code range} blocks of the
     * center that pass the filter.
     */
    List<EntitySnapshot> inRadius(Pos3d center, double range, Predicate<EntitySnapshot> filter);

    /**
     * Returns snapshots of all entities inside the axis-aligned box spanned
     * by {@code min} and {@code max} (inclusive) that pass the filter; an
     * empty list without a loaded world or when the box is inverted.
     */
    default List<EntitySnapshot> inBox(Pos3d min, Pos3d max, Predicate<EntitySnapshot> filter) {
        return List.of();
    }


    /**
     * Returns the raw host object behind the entity, or empty when unloaded.
     * The port is neutral: the returned Object is opaque to core but is the
     * real game entity in the adapter. The escape layer wraps it as a
     * JavaObject proxy for the guest.
     */
    default java.util.Optional<Object> rawByUuid(java.util.UUID uuid) {
        return java.util.Optional.empty();
    }

    /**
     * Returns the species-specific live state of one entity as a flat map
     * keyed by the script-facing member names (e.g. {@code color},
     * {@code fuse}, {@code target} as a UUID string); an empty map for
     * species without extra state or without a loaded entity.
     */
    default java.util.Map<String, Object> speciesState(java.util.UUID uuid) {
        return java.util.Map.of();
    }

    /**
     * Returns live living-entity extras (e.g. {@code absorption},
     * {@code armorValue}, {@code noGravity}, {@code baby}) keyed by member
     * name; an empty map when the entity is absent or not living.
     */
    default java.util.Map<String, Object> livingExtras(java.util.UUID uuid) {
        return java.util.Map.of();
    }
}