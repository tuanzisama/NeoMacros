package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Point-in-time copy of one loaded chunk: coordinates, height bounds, the
 * primed heightmap arrays, and the block-entity positions.
 *
 * @param chunkX       chunk x coordinate
 * @param chunkZ       chunk z coordinate
 * @param minY         lowest buildable y in the chunk
 * @param maxY         highest buildable y in the chunk
 * @param inhabitedTime inhabited ticks reported by the chunk
 * @param heightmaps   heightmap arrays keyed by type name, 256 entries each
 * @param blockEntities block-entity positions inside the chunk
 */
public record ChunkData(int chunkX, int chunkZ, int minY, int maxY, long inhabitedTime,
        Map<String, int[]> heightmaps, List<Pos3d> blockEntities) {

    public ChunkData {
        Objects.requireNonNull(heightmaps, "heightmaps cannot be null");
        Objects.requireNonNull(blockEntities, "blockEntities cannot be null");
        heightmaps = Map.copyOf(heightmaps);
        blockEntities = List.copyOf(blockEntities);
    }
}
