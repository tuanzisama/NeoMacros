package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.PlayerSnapshot;

import java.util.Optional;

/**
 * Local player state service.
 *
 * <p>Covers the client's own player only; other players are ordinary
 * entities queried through {@link EntityPort}.</p>
 */
public interface PlayerPort {

    /** Returns a snapshot of the local player, or empty when not in a world. */
    Optional<PlayerSnapshot> self();
}
