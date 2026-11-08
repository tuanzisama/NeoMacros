package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.EffectSnapshot;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;

import java.util.List;
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
     * Returns the local player's currently active status effects, or an
     * empty list when not in a world.
     */
    List<EffectSnapshot> activeEffects();

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

    /** Returns the current gamemode identifier (survival/creative/adventure/spectator). */
    default String gamemode() {
        return "survival";
    }

    /** Returns the current experience level. */
    default int expLevel() {
        return 0;
    }

    /** Returns the current experience progress (0.0-1.0 within the level). */
    default float expProgress() {
        return 0;
    }

    /** Returns the total experience points collected. */
    default int expTotal() {
        return 0;
    }

    /**
     * Attacks the entity identified by the snapshot's UUID. The future
     * completes on the main thread once the game processed the attack.
     */
    java.util.concurrent.CompletableFuture<Void> attack(EntitySnapshot target);

    /**
     * Uses (right-clicks) the entity identified by the snapshot's UUID with
     * the named hand ({@code main} or {@code off}).
     */
    java.util.concurrent.CompletableFuture<Void> useEntity(EntitySnapshot target, String hand);

    /**
     * Uses (right-clicks) the block identified by the interaction hit with
     * the named hand ({@code main} or {@code off}); the hit carries the face.
     */
    java.util.concurrent.CompletableFuture<Void> useBlock(BlockInteraction hit, String hand);

    /**
     * Uses (right-clicks) the held item in the air with the named hand
     * ({@code main} or {@code off}).
     */
    java.util.concurrent.CompletableFuture<Void> useItem(String hand);

    /**
     * Starts breaking the block identified by the interaction hit: swings
     * and begins the destroy path (instant-break blocks complete at once).
     */
    java.util.concurrent.CompletableFuture<Void> attackBlock(BlockInteraction hit);

    /** Releases the ongoing block breaking. */
    java.util.concurrent.CompletableFuture<Void> stopBreaking();

    /** Swings the named hand ({@code main} or {@code off}). */
    java.util.concurrent.CompletableFuture<Void> swingHand(String hand);

    /** Drops the held item stack (all = the whole stack). */
    java.util.concurrent.CompletableFuture<Void> dropHeld(boolean all);

    /**
     * Returns the raw host player object, or empty when not in a world.
     * Port-neutral: the returned Object is opaque to core.
     */
    default java.util.Optional<Object> rawSelf() {
        return java.util.Optional.empty();
    }

    /**
     * Returns the client-side statistics as {@code statTypeId:valueId ->
     * value} entries; only non-zero values appear. Empty without a world.
     */
    default java.util.Map<String, Integer> statistics() {
        return java.util.Map.of();
    }

    /** Returns the block interaction range in blocks. */
    default double blockInteractionRange() {
        return 0;
    }

    /** Returns the entity interaction range in blocks. */
    default double entityInteractionRange() {
        return 0;
    }

    /** Returns whether the local player is currently breaking a block. */
    default boolean isBreakingBlock() {
        return false;
    }

    /** Returns the entity UUID the local player rides, or empty on foot. */
    default java.util.Optional<java.util.UUID> vehicleUuid() {
        return java.util.Optional.empty();
    }

    /**
     * Returns the current crosshair pick: the first block or entity within
     * the interaction ranges, or empty on a miss or without a world.
     */
    default java.util.Optional<ink.tuanzi.neomacros.port.model.RayHit> target() {
        return java.util.Optional.empty();
    }

    /**
     * Returns the display data of every client-known advancement with a
     * visible display entry; empty without a server connection.
     */
    default java.util.List<ink.tuanzi.neomacros.port.model.AdvancementData> advancements() {
        return java.util.List.of();
    }

    /**
     * Returns the client-known progress of one advancement id, or empty when
     * the id is unknown or no server is connected.
     */
    default java.util.Optional<ink.tuanzi.neomacros.port.model.AdvancementProgressData>
            advancementProgress(String id) {
        return java.util.Optional.empty();
    }

    /**
     * Returns the four armor slots in feet, legs, chest, head order; empty
     * slots read as {@code minecraft:air}.
     */
    default java.util.List<ink.tuanzi.neomacros.port.model.ItemStackData> armorSlots() {
        return java.util.List.of();
    }

    // --- Local-only write face: every action below writes client-side
    // state on the game main thread. Multiplayer servers keep authority
    // over flying and position, so writes may be corrected shortly after.

    /**
     * Sets whether the local player is flying (client-side toggle backed by
     * the abilities state). Servers may revoke this outside creative mode.
     */
    default java.util.concurrent.CompletableFuture<Void> setFlying(boolean value) {
        throw new UnsupportedOperationException("setFlying requires the fabric adapter");
    }

    /** Sets the fly speed multiplier (client-side abilities state). */
    default java.util.concurrent.CompletableFuture<Void> setFlySpeed(float value) {
        throw new UnsupportedOperationException("setFlySpeed requires the fabric adapter");
    }

    /** Sets the walking speed multiplier (client-side abilities state). */
    default java.util.concurrent.CompletableFuture<Void> setWalkingSpeed(float value) {
        throw new UnsupportedOperationException("setWalkingSpeed requires the fabric adapter");
    }

    /** Turns the camera to the yaw (horizontal) and pitch (vertical) angles. */
    default java.util.concurrent.CompletableFuture<Void> look(float yaw, float pitch) {
        throw new UnsupportedOperationException("look requires the fabric adapter");
    }

    /** Turns the camera to look at the entity behind the snapshot's UUID. */
    default java.util.concurrent.CompletableFuture<Void> lookAt(EntitySnapshot target) {
        throw new UnsupportedOperationException("lookAt requires the fabric adapter");
    }

    /**
     * Moves the local player to the given position (client-side write;
     * multiplayer servers correct the position back).
     */
    default java.util.concurrent.CompletableFuture<Void> teleport(double x, double y, double z) {
        throw new UnsupportedOperationException("teleport requires the fabric adapter");
    }

    /** Sets the local player's movement velocity vector. */
    default java.util.concurrent.CompletableFuture<Void> setVelocity(double x, double y, double z) {
        throw new UnsupportedOperationException("setVelocity requires the fabric adapter");
    }

    /**
     * Returns the item cooldown progress for the item id, 0.0 when not on
     * cooldown (and 0 when the item is unknown).
     */
    default float itemCooldownPercent(String itemId) {
        return 0;
    }
}
