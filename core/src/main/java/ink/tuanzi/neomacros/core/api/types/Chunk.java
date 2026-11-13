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
     * The world position of the chunk's local origin (northwest corner at
     * y = 0), available without a loaded world.
     *
     * @zh 区块局部原点的世界坐标（西北角，y=0）；无需加载世界即可读取。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public BlockPos getMinBlock() {
        return new BlockPos(chunkX << 4, 0, chunkZ << 4);
    }

    /**
     * The world block position from a local x/z offset and an absolute y;
     * offsets outside {@code 0..15} address neighbouring chunks.
     *
     * @zh 由区块内 x/z 偏移与绝对 y 得到的世界方块坐标；x/z 超出 {@code 0..15} 时指向相邻区块。
     * @zh-param xOffset 区块内 x 偏移
     * @zh-param y 绝对 y 坐标
     * @zh-param zOffset 区块内 z 偏移
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public BlockPos offsetBlock(int xOffset, int y, int zOffset) {
        return new BlockPos((chunkX << 4) + xOffset, y, (chunkZ << 4) + zOffset);
    }

    /**
     * The chunk's build height in blocks, or {@code null} when unloaded.
     *
     * @zh 区块的可建造高度（格数）；未加载时为 null。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getHeight() {
        ChunkData data = current();
        return data != null ? data.maxY() - data.minY() + 1 : null;
    }

    /**
     * The first available y of one heightmap column (local x/z offsets),
     * or {@code null} when unloaded, the local offsets fall outside
     * {@code 0..15}, or the heightmap type is unknown.
     *
     * @zh 某高度图列的首个可用 y（区块内 x/z 偏移）；未加载、偏移越界或高度图类型未知时为 null。
     * @zh-param xOffset 区块内 x 偏移（0-15）
     * @zh-param zOffset 区块内 z 偏移（0-15）
     * @zh-param heightmap 高度图类型名：{@code world_surface} / {@code ocean_floor} / {@code motion_blocking} / {@code motion_blocking_no_leaves}
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer topYAt(int xOffset, int zOffset, String heightmap) {
        ChunkData data = current();
        if (data == null || heightmap == null
                || xOffset < 0 || xOffset > 15 || zOffset < 0 || zOffset > 15) {
            return null;
        }
        int[] heights = data.heightmaps()
                .get(heightmap.toLowerCase(java.util.Locale.ROOT));
        if (heights == null || heights.length < 256) {
            return null;
        }
        return heights[xOffset + zOffset * 16];
    }

    /**
     * The entities inside the chunk's bounds, empty list when unloaded.
     *
     * @zh 区块边界内的实体；未加载时为空数组。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public java.util.List<ink.tuanzi.neomacros.core.api.entity.EntityApi.Entity> getEntities() {
        ChunkData data = current();
        if (data == null) {
            return List.of();
        }
        Pos3d min = new Pos3d(data.chunkX() * 16, data.minY(), data.chunkZ() * 16);
        Pos3d max = new Pos3d(data.chunkX() * 16 + 15, data.maxY(), data.chunkZ() * 16 + 15);
        return ScriptApiEnvironment.adapter().entity()
                .inBox(min, max, snapshot -> true).stream()
                .map(snapshot -> new ink.tuanzi.neomacros.core.api.entity.EntityApi.Entity(
                        snapshot.uuid()))
                .toList();
    }

    /**
     * Whether the chunk contains any of the given block ids; an unloaded
     * chunk answers undefined, an empty request answers false.
     *
     * @zh 区块是否包含任一指定方块 id；未加载区块为 undefined，空请求为 false。
     * @zh-param blocks 方块 id（字符串或字符串数组），可省略命名空间
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Boolean containsAny(
            @ApiType("string | Array<string>") Object blocks) {
        return contains(blocks, false);
    }

    /**
     * Whether the chunk contains every one of the given block ids; an
     * unloaded chunk answers undefined, an empty request answers true, and
     * an unresolvable id fails the check.
     *
     * @zh 区块是否包含全部指定方块 id；未加载区块为 undefined，空请求为 true，无法解析的 id 判定失败。
     * @zh-param blocks 方块 id（字符串或字符串数组），可省略命名空间
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Boolean containsAll(
            @ApiType("string | Array<string>") Object blocks) {
        return contains(blocks, true);
    }

    /** Runs one chunk containment query, normalizing the id request first. */
    private Boolean contains(Object blocks, boolean requireAll) {
        java.util.List<String> ids = idList(blocks);
        if (ids.isEmpty()) {
            // Vacuous truth: all-of holds, any-of does not.
            return requireAll;
        }
        return ScriptApiEnvironment.adapter().world()
                .chunkContains(chunkX, chunkZ, ids, requireAll).orElse(null);
    }

    /** Normalizes one id request: a single string or an array of strings. */
    private static java.util.List<String> idList(Object blocks) {
        if (blocks == null) {
            return List.of();
        }
        if (blocks instanceof java.util.List<?> list) {
            java.util.List<String> out = new ArrayList<>();
            for (Object entry : list) {
                if (entry != null) {
                    out.add(String.valueOf(entry));
                }
            }
            return out;
        }
        if (blocks instanceof String single) {
            return List.of(single);
        }
        return List.of(String.valueOf(blocks));
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
    @ApiSince("0.3.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String toJSON() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("chunkX", chunkX);
        view.put("chunkZ", chunkZ);
        view.put("loaded", current() != null);
        return ink.tuanzi.neomacros.core.util.JsonLite.write(view);
    }

    @Override
    public String toString() {
        return "Chunk(" + chunkX + ", " + chunkZ + ")";
    }

}
