package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;

import java.util.Optional;
import java.util.function.Function;

/**
 * Local player state service and interaction event sources.
 *
 * <p>Covers the client's own player only; other players are ordinary
 * entities queried through {@link EntityPort}. The interaction sinks feed
 * the cancellable attack/use events: the sink returns {@code true} to cancel
 * the game action, and its verdict is read by the adapter implementation.</p>
 */
public interface PlayerPort {

    /** Returns a snapshot of the local player, or empty when not in a world. */
    Optional<PlayerSnapshot> self();

    /**
     * Registers a sink invoked when the player starts breaking a block; the
     * sink returns {@code true} to cancel the break. Sinks run on the game
     * main thread in registration order; cancellation is the OR of all sinks.
     */
    void onAttackBlock(Function<BlockInteraction, Boolean> sink);

    /**
     * Registers a sink invoked when the player attacks an entity; the sink
     * returns {@code true} to cancel the attack. Same threading and
     * cancellation rules as {@link #onAttackBlock}.
     */
    void onAttackEntity(Function<EntitySnapshot, Boolean> sink);

    /**
     * Registers a sink invoked when the player uses an item on a block; the
     * sink returns {@code true} to cancel the use. Same threading and
     * cancellation rules as {@link #onAttackBlock}.
     */
    void onInteractBlock(Function<BlockInteraction, Boolean> sink);

    /**
     * Registers a sink invoked when the player interacts with an entity; the
     * sink returns {@code true} to cancel the interaction. Same threading and
     * cancellation rules as {@link #onAttackBlock}.
     */
    void onInteractEntity(Function<EntitySnapshot, Boolean> sink);
}
