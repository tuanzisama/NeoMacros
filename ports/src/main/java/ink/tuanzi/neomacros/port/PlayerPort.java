package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.EffectSnapshot;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.SignEditData;
import ink.tuanzi.neomacros.port.model.SignEditVerdict;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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

    /**
     * Registers a sink invoked when the sign-edit editor is about to open
     * (the server sign-editor packet); the sink verdict skips opening the
     * editor (submitting the possibly replaced text directly) or replaces
     * the lines. Sinks run on the game main thread in registration order.
     */
    default void onSignEdit(Function<SignEditData, SignEditVerdict> sink) {
    }

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
     * Returns the definition data (requirement groups plus rewards) of one
     * advancement id, or empty when the id is unknown or no server is
     * connected.
     */
    default java.util.Optional<ink.tuanzi.neomacros.port.model.AdvancementDefinitionData>
            advancementDefinition(String id) {
        return java.util.Optional.empty();
    }

    /**
     * Returns the progress detail (criterion completion times plus the
     * vanilla progress text) of one advancement id, or empty when the id is
     * unknown, no progress entry exists, or no server is connected.
     */
    default java.util.Optional<ink.tuanzi.neomacros.port.model.AdvancementProgressDetailData>
            advancementProgressDetail(String id) {
        return java.util.Optional.empty();
    }

    /**
     * Returns one advancement's definition encoded as a JSON string, or empty
     * when the id is unknown, no server is connected, or the definition does
     * not encode.
     */
    default java.util.Optional<String> advancementJson(String id) {
        return java.util.Optional.empty();
    }

    /**
     * Returns the formatted counters as {@code statTypeId:valueId ->
     * formatted} entries; only non-zero values appear, keyed exactly like
     * {@link #statistics()}. Empty without a world.
     */
    default java.util.Map<String, String> formattedStatistics() {
        return java.util.Map.of();
    }

    /**
     * Returns the display name of the stat type behind one stat key (the
     * {@code statTypeId} prefix of {@code statTypeId:valueId}), or empty when
     * the key or its stat type is unknown.
     */
    default java.util.Optional<String> statTypeDisplayName(String statKey) {
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

    // --- Interaction override face (G-028): a script-set crosshair target,
    // the break-outcome override, and the held-use override. All three are
    // client-side script state over the local player's interaction loop. The
    // target override rewrites what target() reports (and therefore what the
    // crosshair-driven NeoMacros actions act on); it never rewrites the
    // vanilla crosshair, so it grants no server reach. Writes marshal onto
    // the game main thread and settle once applied.

    /**
     * Stores a block target override at the block center on the named face;
     * the face is the one recorded on the overridden pick.
     */
    default java.util.concurrent.CompletableFuture<Void> setTargetOverride(
            int x, int y, int z, String face) {
        throw new UnsupportedOperationException("setTargetOverride requires the fabric adapter");
    }

    /** Stores an entity target override for the loaded entity. */
    default java.util.concurrent.CompletableFuture<Void> setTargetEntityOverride(
            java.util.UUID entity) {
        throw new UnsupportedOperationException(
                "setTargetEntityOverride requires the fabric adapter");
    }

    /** Stores a fixed miss override: the pick reports nothing at all. */
    default java.util.concurrent.CompletableFuture<Void> setTargetMissedOverride() {
        throw new UnsupportedOperationException("setTargetMissedOverride requires the fabric adapter");
    }

    /** Clears the target override so {@link #target()} reports the real pick again. */
    default java.util.concurrent.CompletableFuture<Void> clearTargetOverride() {
        throw new UnsupportedOperationException("clearTargetOverride requires the fabric adapter");
    }

    /** Whether a target override is stored, including a stored miss. */
    default boolean hasTargetOverride() {
        return false;
    }

    /**
     * Configures the range check: {@code enabled} runs it during target
     * evaluation, {@code autoClear} drops an out-of-range override instead
     * of rendering it as a miss.
     */
    default java.util.concurrent.CompletableFuture<Void> setTargetRangeCheck(
            boolean enabled, boolean autoClear) {
        throw new UnsupportedOperationException("setTargetRangeCheck requires the fabric adapter");
    }

    /**
     * Configures the air check: {@code enabled} rejects an air block target,
     * {@code autoClear} drops it instead of rendering it as a miss.
     */
    default java.util.concurrent.CompletableFuture<Void> setTargetAirCheck(
            boolean enabled, boolean autoClear) {
        throw new UnsupportedOperationException("setTargetAirCheck requires the fabric adapter");
    }

    /**
     * Configures the empty-shape check: {@code enabled} rejects a block whose
     * collision shape is empty, {@code autoClear} drops it instead of
     * rendering it as a miss.
     */
    default java.util.concurrent.CompletableFuture<Void> setTargetShapeCheck(
            boolean enabled, boolean autoClear) {
        throw new UnsupportedOperationException("setTargetShapeCheck requires the fabric adapter");
    }

    /** Restores the three target checks to their defaults (see the facade). */
    default java.util.concurrent.CompletableFuture<Void> resetTargetChecks() {
        throw new UnsupportedOperationException("resetTargetChecks requires the fabric adapter");
    }

    /**
     * Tests the stored target override against the client interaction reach
     * at the interpolation fraction; false with no override, no player or no
     * game mode. A stored miss always passes.
     */
    default boolean targetInRange(float tickDelta) {
        return false;
    }

    /**
     * Breaks the block the pick currently reports (the override when one is
     * set), resolving with the break outcome.
     */
    default java.util.concurrent.CompletableFuture<ink.tuanzi.neomacros.port.model.BreakBlockResult>
            breakBlock() {
        throw new UnsupportedOperationException("breakBlock requires the fabric adapter");
    }

    /**
     * Breaks the block at the coordinates, resolving with the break outcome;
     * the crosshair is neither consulted nor moved.
     */
    default java.util.concurrent.CompletableFuture<ink.tuanzi.neomacros.port.model.BreakBlockResult>
            breakBlockAt(int x, int y, int z) {
        throw new UnsupportedOperationException("breakBlockAt requires the fabric adapter");
    }

    /**
     * Enables or disables the break override. Disabling reports {@code reason}
     * to a pending break (null position) and stops client-side breaking while
     * the attack key is up; enabling only arms the override.
     */
    default java.util.concurrent.CompletableFuture<Void> setBreakOverride(
            boolean value, String reason) {
        throw new UnsupportedOperationException("setBreakOverride requires the fabric adapter");
    }

    /** Whether the break override is currently armed and still valid. */
    default boolean hasBreakBlockOverride() {
        return false;
    }

    /** Cancels the active break override, reporting {@code CANCELLED}. */
    default java.util.concurrent.CompletableFuture<Void> cancelBreakBlock() {
        throw new UnsupportedOperationException("cancelBreakBlock requires the fabric adapter");
    }

    /**
     * Reports a client-side destruction call result to the active break
     * override: a true result ends the override and settles a pending break
     * with {@code SUCCESS} at the position; false leaves it unchanged.
     */
    default java.util.concurrent.CompletableFuture<Void> reportBreakBlock(
            int x, int y, int z, boolean broken) {
        throw new UnsupportedOperationException("reportBreakBlock requires the fabric adapter");
    }

    /**
     * Queues a one-shot outcome callback for the next break-override
     * settlement; {@code breaking} true arms the override (resetting the
     * tracked position, starting no break action), false leaves it unchanged.
     * Callbacks run once on the game main thread, then the queue clears.
     */
    default void addBreakCallback(
            Consumer<ink.tuanzi.neomacros.port.model.BreakBlockResult> callback,
            boolean breaking) {
        throw new UnsupportedOperationException("addBreakCallback requires the fabric adapter");
    }

    /** Starts or stops the continuous held-use override. */
    default java.util.concurrent.CompletableFuture<Void> setHoldInteract(boolean holding) {
        throw new UnsupportedOperationException("setHoldInteract requires the fabric adapter");
    }

    /**
     * Holds the use override for up to {@code ticks} unpaused ticks and then
     * releases it; resolves with the ticks that did not elapse (0 when the
     * full duration ran out, nonzero when the hold stopped early).
     */
    default java.util.concurrent.CompletableFuture<Integer> holdInteractFor(
            int ticks, boolean stopOnPause) {
        throw new UnsupportedOperationException("holdInteractFor requires the fabric adapter");
    }

    /** Whether the held-use override is currently active. */
    default boolean hasInteractOverride() {
        return false;
    }
}
