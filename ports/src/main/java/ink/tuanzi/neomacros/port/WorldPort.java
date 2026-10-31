package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.Optional;

/**
 * World data services: time, dimension, and block queries.
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

    /** Returns the block at the position, or empty when outside the loaded world. */
    Optional<BlockData> blockAt(Pos3d pos);
}
