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
     * name; an empty map when the entity is absent or not living. A
     * {@code null} value marks an absent member (an empty armor slot, no
     * custom name).
     */
    default java.util.Map<String, Object> livingExtras(java.util.UUID uuid) {
        return java.util.Map.of();
    }

    /**
     * Returns the base-entity live state keyed by the script-facing member
     * names: {@code glowing}, {@code glowColor}, {@code customName},
     * {@code customNameVisible}, {@code air}, {@code maxAir} and
     * {@code speed}; an empty map when the entity is absent. Entries read on
     * every entity kind, so the values survive on plain (non-living) entities
     * too. A {@code null} value marks an absent member.
     */
    default java.util.Map<String, Object> entityState(java.util.UUID uuid) {
        return java.util.Map.of();
    }

    // --- Client-side write face: every action below writes local state on
    // the game main thread and resolves once processed. Servers keep
    // authority over the real value, so a later sync may overwrite it.

    /**
     * Forces the glow outline on or off for the entity, overriding the
     * game's own decision; the future resolves once the write landed.
     */
    default java.util.concurrent.CompletableFuture<Void> setGlowing(
            java.util.UUID uuid, boolean glowing) {
        return unsupported("setGlowing");
    }

    /** Restores the game's own glow decision for the entity. */
    default java.util.concurrent.CompletableFuture<Void> resetGlowing(java.util.UUID uuid) {
        return unsupported("resetGlowing");
    }

    /**
     * Overrides the glow outline colour with the packed RGB value for the
     * entity, leaving whether it glows untouched.
     */
    default java.util.concurrent.CompletableFuture<Void> setGlowingColor(
            java.util.UUID uuid, int color) {
        return unsupported("setGlowingColor");
    }

    /** Restores the team-derived glow outline colour for the entity. */
    default java.util.concurrent.CompletableFuture<Void> resetGlowingColor(
            java.util.UUID uuid) {
        return unsupported("resetGlowingColor");
    }

    /**
     * Sets the entity's custom name locally; a null name clears it. The
     * server owns the real value and may overwrite the local write on the
     * next sync.
     */
    default java.util.concurrent.CompletableFuture<Void> setCustomName(
            java.util.UUID uuid, String name) {
        return unsupported("setCustomName");
    }

    /**
     * Sets whether the custom name is always shown above the entity
     * (client-side write, see {@link #setCustomName}).
     */
    default java.util.concurrent.CompletableFuture<Void> setCustomNameVisible(
            java.util.UUID uuid, boolean visible) {
        return unsupported("setCustomNameVisible");
    }

    // --- Predicates that need a second handle.

    /**
     * Whether the game permits the entity to attack the target; false when
     * either handle is gone or the target is not a living entity.
     */
    default boolean canAttack(java.util.UUID uuid, java.util.UUID target) {
        return false;
    }

    /**
     * Whether Minecraft considers the entity eligible for the status effect
     * with the id (namespace defaults to {@code minecraft}); false when the
     * id is unknown or either handle is gone.
     */
    default boolean canBeAffected(java.util.UUID uuid, String effectId) {
        return false;
    }

    /**
     * Whether the animal accepts the item with the id as food (the id is
     * already namespaced); false for unknown ids, non-animals, or a gone
     * entity.
     */
    default boolean isFood(java.util.UUID uuid, String itemId) {
        return false;
    }

    /**
     * Whether nothing blocks the view from the entity to the target. The
     * simple cast tests three points only; the extended cast additionally
     * samples four pillars around the target.
     */
    default boolean canSee(java.util.UUID uuid, java.util.UUID target, boolean simpleCast) {
        return false;
    }

    /**
     * Whether the game's mating check accepts the other animal for this one;
     * false when either handle is gone or either side is not an animal.
     */
    default boolean canBreedWith(java.util.UUID uuid, java.util.UUID other) {
        return false;
    }

    /** The rejected future returned by a write the adapter does not serve. */
    private static java.util.concurrent.CompletableFuture<Void> unsupported(String member) {
        java.util.concurrent.CompletableFuture<Void> future =
                new java.util.concurrent.CompletableFuture<>();
        future.completeExceptionally(new UnsupportedOperationException(
                member + " requires the fabric adapter"));
        return future;
    }
}