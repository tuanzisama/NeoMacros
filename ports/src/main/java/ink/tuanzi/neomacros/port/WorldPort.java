package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.RayHit;
import ink.tuanzi.neomacros.port.model.RayOptions;

import java.util.Optional;

/**
 * World data services: time, weather, dimension, block queries, and raycast.
 *
 * <p>Query results are point-in-time neutral snapshots. Implementations that
 * require the game main thread say so in their implementation; core is
 * responsible for marshaling calls accordingly.</p>
 */
public interface WorldPort {

    /** Returns the world time of day in ticks. */
    long time();

    /** Returns the dimension identifier of the current world, e.g. {@code minecraft:overworld}. */
    String dimension();

    /** Returns whether rain is currently falling in the world. */
    boolean isRaining();

    /** Returns the block at the position, or empty when outside the loaded world. */
    Optional<BlockData> blockAt(Pos3d pos);

    /**
     * Casts a ray from {@code origin} along {@code direction} and returns
     * the first hit (block or entity per the options), or empty when the ray
     * reaches its end unobstructed or no world is loaded.
     */
    Optional<RayHit> raycast(Pos3d origin, Pos3d direction, RayOptions options);

    /** Returns whether thunder is currently falling in the world. */
    default boolean isThundering() {
        return false;
    }

    /** Returns the current day count (world day number). */
    default long day() {
        return 0;
    }

    /** Returns the total world age in ticks. */
    default long age() {
        return 0;
    }

    /** Returns the difficulty identifier, e.g. {@code normal}. */
    default String difficulty() {
        return "unknown";
    }

    /** Returns the biome identifier at the position, or empty when unloaded. */
    default java.util.Optional<String> biomeAt(Pos3d pos) {
        return java.util.Optional.empty();
    }

    /** Returns whether the chunk containing the position is loaded. */
    default boolean loaded(Pos3d pos) {
        return false;
    }
}
