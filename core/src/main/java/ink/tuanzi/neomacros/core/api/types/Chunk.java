package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.model.ChunkData;
import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live chunk handle: coordinates, height bounds, primed heightmaps, block
 * entity positions, and biome reads over the loaded chunk. Getters re-query
 * the world on every access; an unloaded chunk reads back as
 * {@code undefined} instead of raising.
 *
 * @zh 区块活句柄：区块坐标、高度界、已就绪的高度图、方块实体位置与群系读取，全部实时回读；区块卸载后属性回读为 undefined 而不报错。
 */
@ApiClass(name = "Chunk", kind = ApiClassKind.HANDLE,
        wraps = "net.minecraft.world.level.chunk.LevelChunk")
@ApiSince("0.2.0")
@ApiMC("*")
@ApiLoader("*")
public final class Chunk implements ScriptHandle {

    private final int chunkX;
    private final int chunkZ;

    /** Creates the handle for one chunk coordinate pair. */
    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    /** Re-reads the live chunk data, or null when unloaded. */
    private ChunkData current() {
        Pos3d anchor = new Pos3d(chunkX * 16 + 8, 64, chunkZ * 16 + 8);
        return ScriptApiEnvironment.adapter().world().chunkAt(anchor).orElse(null);
    }

    /** The chunk x coordinate.
     * @zh 区块 x 坐标。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public int getChunkX() {
        return chunkX;
    }

    /** The chunk z coordinate.
     * @zh 区块 z 坐标。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public int getChunkZ() {
        return chunkZ;
    }

    /**
     * The lowest buildable y of the chunk, or {@code null} when unloaded.
     *
     * @zh 区块最低可建造 y；未加载时为 null。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getMinY() {
        ChunkData data = current();
        return data != null ? data.minY() : null;
    }

    /**
     * The highest buildable y of the chunk, or {@code null} when unloaded.
     *
     * @zh 区块最高可建造 y；未加载时为 null。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getMaxY() {
        ChunkData data = current();
        return data != null ? data.maxY() : null;
    }

    /**
     * The inhabited ticks reported by the chunk, or {@code null} when
     * unloaded.
     *
     * @zh 区块驻留 tick 数；未加载时为 null。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Long getInhabitedTime() {
        ChunkData data = current();
        return data != null ? data.inhabitedTime() : null;
    }

    /**
     * The primed heightmaps as {@code {type: number[256]}} indexed by
     * {@code localX + localZ * 16}, or {@code null} when unloaded.
     *
     * @zh 已就绪高度图 {@code {type: number[256]}}，索引 {@code localX + localZ * 16}；未加载时为 null。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Map<String, int[]> getHeightmaps() {
        ChunkData data = current();
        if (data == null) {
            return null;
        }
        Map<String, int[]> view = new LinkedHashMap<>();
        data.heightmaps().forEach((key, value) -> view.put(key, value.clone()));
        return view;
    }

    /**
     * The surface height (world surface) at the world coordinates inside
     * this chunk, or {@code null} when unloaded.
     *
     * @zh 区块内世界坐标处的地表高度（world surface）；未加载时为 null。
     * @zh-param x 世界坐标 X
     * @zh-param z 世界坐标 Z
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer heightAt(int x, int z) {
        ChunkData data = current();
        if (data == null) {
            return null;
        }
        int[] heights = data.heightmaps().get("world_surface");
        if (heights == null) {
            heights = data.heightmaps().get("MOTION_BLOCKING");
        }
        if (heights == null) {
            return null;
        }
        int localX = x - chunkX * 16;
        int localZ = z - chunkZ * 16;
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15) {
            return null;
        }
        return heights[localX + localZ * 16];
    }

    /**
     * The block entity positions inside the chunk, empty list when unloaded
     * or without block entities.
     *
     * @zh 区块内方块实体位置；未加载或无方块实体时为空数组。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<BlockPos> getBlockEntities() {
        ChunkData data = current();
        if (data == null) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>();
        for (Pos3d pos : data.blockEntities()) {
            out.add(new BlockPos((int) Math.floor(pos.x()),
                    (int) Math.floor(pos.y()), (int) Math.floor(pos.z())));
        }
        return out;
    }

    /**
     * Whether the world position lies inside this chunk.
     *
     * @zh 判断世界坐标是否落在本区块内。
     * @zh-param x 世界坐标 X
     * @zh-param y 世界坐标 Y
     * @zh-param z 世界坐标 Z
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean contains(double x, double y, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        return blockX >> 4 == chunkX && blockZ >> 4 == chunkZ;
    }

    /**
     * The biome identifier at the position inside this chunk, or
     * {@code null} when unloaded.
     *
     * @zh 区块内坐标处的群系标识；未加载时为 null。
     * @zh-param x 世界坐标 X
     * @zh-param y 世界坐标 Y
     * @zh-param z 世界坐标 Z
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String biomeAt(double x, double y, double z) {
        return ScriptApiEnvironment.adapter().world().biomeAt(new Pos3d(x, y, z))
                .orElse(null);
    }

    /** Plain-object rendering used by {@code JSON.stringify}.
     * @zh {@code JSON.stringify} 的纯对象渲染。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("ChunkJson")
    public Map<String, Object> toJSON() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("chunkX", chunkX);
        view.put("chunkZ", chunkZ);
        view.put("loaded", current() != null);
        return view;
    }

    @Override
    public String toString() {
        return "Chunk(" + chunkX + ", " + chunkZ + ")";
    }

    /**
     * The JSON form of one chunk handle: the chunk coordinates and whether
     * the chunk is currently loaded.
     *
     * @zh 区块句柄的 JSON 形式：区块坐标与当前是否已加载。
     */
    @ApiClass(name = "ChunkJson", kind = ApiClassKind.DATA)
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record ChunkJson(int chunkX, int chunkZ, boolean loaded) {
    }
}
