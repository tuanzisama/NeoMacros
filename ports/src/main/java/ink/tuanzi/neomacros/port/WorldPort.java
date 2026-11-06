package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.ChunkData;
import ink.tuanzi.neomacros.port.model.BossBarSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.RayHit;
import ink.tuanzi.neomacros.port.model.RayOptions;
import ink.tuanzi.neomacros.port.model.ScoreboardData;
import ink.tuanzi.neomacros.port.model.TabListData;
import ink.tuanzi.neomacros.port.model.TpsData;

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

    /**
     * Returns the raw host block state at the position, or empty when
     * unloaded. Port-neutral: the returned Object is opaque to core.
     */
    default java.util.Optional<Object> rawByPos(Pos3d pos) {
        return java.util.Optional.empty();
    }

    /** Returns the loaded save or server name, empty string without a world. */
    default String levelName() {
        return "";
    }

    /** Returns the current moon phase index (0 = full moon, 4 = new moon). */
    default int moonPhase() {
        return 0;
    }

    /** Returns the client tab list snapshot, or empty without a world. */
    default java.util.Optional<TabListData> tabList() {
        return java.util.Optional.empty();
    }

    /** Returns the boss bars currently displayed, empty list when none. */
    default java.util.List<BossBarSnapshot> bossBars() {
        return java.util.List.of();
    }

    /** Returns the client scoreboard snapshot, or empty without a world. */
    default java.util.Optional<ScoreboardData> scoreboards() {
        return java.util.Optional.empty();
    }

    /**
     * Returns the remote tick rate estimate sampled from time sync packets,
     * or empty before the first sample.
     */
    default java.util.Optional<TpsData> tps() {
        return java.util.Optional.empty();
    }

    /**
     * Plays a sound locally: world-positioned when the world is loaded with a
     * position, otherwise through the UI sound channel. Position may be null
     * for the UI channel.
     */
    default void playSound(String soundId, Pos3d pos, double volume, double pitch) {
    }

    /** Spawns particles locally around the position. */
    default void spawnParticle(String particleId, Pos3d pos, int count, Pos3d offset,
            double speed) {
    }

    /**
     * Returns the loaded chunk containing the position, or empty when the
     * chunk is not loaded.
     */
    default java.util.Optional<ChunkData> chunkAt(Pos3d pos) {
        return java.util.Optional.empty();
    }

    /**
     * Scans up to {@code limit} blocks whose id matches one of
     * {@code blockIds} (full namespaced ids) inside the cube around the
     * center; adapters cap the scanned volume to keep the main thread
     * responsive.
     */
    default java.util.List<Pos3d> findBlocks(java.util.List<String> blockIds,
            Pos3d center, int range, int limit) {
        return java.util.List.of();
    }
}