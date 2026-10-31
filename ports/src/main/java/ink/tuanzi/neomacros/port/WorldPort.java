package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.Pos3d;

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
     * Casts a ray from {@code from} toward {@code to} and returns the first
     * solid block hit, or empty when the ray reaches its end unobstructed or
     * no world is loaded.
     */
    Optional<BlockInteraction> raycast(Pos3d from, Pos3d to);
}
