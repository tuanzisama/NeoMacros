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
 * <p>Live access is layered on top: the entity facade wraps snapshots as
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
     * Returns the raw host object behind the entity, or empty when unloaded.
     * The port is neutral: the returned Object is opaque to core but is the
     * real game entity in the adapter. The escape layer wraps it as a
     * JavaObject proxy for the guest.
     */
    default java.util.Optional<Object> rawByUuid(java.util.UUID uuid) {
        return java.util.Optional.empty();
    }
}
