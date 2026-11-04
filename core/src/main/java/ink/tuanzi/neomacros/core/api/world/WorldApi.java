package ink.tuanzi.neomacros.core.api.world;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.SnapshotViews;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code neo.world} facade: world queries over the loaded client world.
 * Positions use plain world coordinates; results are plain point-in-time
 * objects.
 *
 * @zh {@code neo.world} 门面：对已加载客户端世界的查询。位置使用普通世界坐标；结果为纯时点对象。
 */
@ApiModule(name = "world", category = "世界")
@ApiSince("0.1.0")
public final class WorldApi {

    /**
     * The world time of day in ticks.
     *
     * @zh 当前世界时间（tick）。
     */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public long getTime() {
        return ScriptApiEnvironment.adapter().world().time();
    }

    /**
     * The dimension identifier of the loaded world, e.g. {@code minecraft:overworld}.
     *
     * @zh 当前维度标识，如 {@code minecraft:overworld}。
     */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public String getDimension() {
        return ScriptApiEnvironment.adapter().world().dimension();
    }

    /**
     * Whether rain is currently falling.
     *
     * @zh 当前是否在下雨。
     */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public boolean isRaining() {
        return ScriptApiEnvironment.adapter().world().isRaining();
    }

    /**
     * Returns the block at the coordinates, or {@code null} when outside the
     * loaded world.
     *
     * @zh 查询坐标处方块；不在已加载世界内时返回 null。
     * @zh-param x 世界坐标 X
     * @zh-param y 世界坐标 Y
     * @zh-param z 世界坐标 Z
     * @zh-return {@code {id, pos:{x,y,z}}} 或 null
     */
    @ApiSince("0.1.0")
    public ink.tuanzi.neomacros.core.api.types.Block blockAt(double x, double y, double z) {
        Optional<BlockData> block = ScriptApiEnvironment.adapter().world()
                .blockAt(new Pos3d(x, y, z));
        return block.map(data -> new ink.tuanzi.neomacros.core.api.types.Block(
                new ink.tuanzi.neomacros.core.api.types.BlockPos(
                        (int) Math.floor(data.pos().x()),
                        (int) Math.floor(data.pos().y()),
                        (int) Math.floor(data.pos().z())))).orElse(null);
    }

    /**
     * Casts a ray from one point toward another and returns the first hit,
     * or {@code null} when unobstructed or no world is loaded.
     *
     * @zh 从起点向终点方向投射射线，返回第一个命中；无命中或无世界时返回 null。
     * @zh-param fromX 起点坐标 X
     * @zh-param fromY 起点坐标 Y
     * @zh-param fromZ 起点坐标 Z
     * @zh-param toX 终点坐标 X
     * @zh-param toY 终点坐标 Y
     * @zh-param toZ 终点坐标 Z
     * @zh-return {@code {block|entity, pos, distance}} 或 null
     */
    @ApiSince("0.1.0")
    public ink.tuanzi.neomacros.core.api.types.RayHit raycast(double fromX, double fromY,
            double fromZ, double toX, double toY, double toZ) {
        Pos3d from = new Pos3d(fromX, fromY, fromZ);
        Pos3d to = new Pos3d(toX, toY, toZ);
        Pos3d direction = new Pos3d(to.x() - from.x(), to.y() - from.y(), to.z() - from.z());
        double reach = Math.sqrt(Math.pow(direction.x(), 2) + Math.pow(direction.y(), 2)
                + Math.pow(direction.z(), 2));
        var options = new ink.tuanzi.neomacros.port.model.RayOptions(reach, true, true, false);
        return new ink.tuanzi.neomacros.core.api.types.RayHit(
                new ink.tuanzi.neomacros.core.api.types.Pos3D(fromX, fromY, fromZ),
                new ink.tuanzi.neomacros.core.api.types.Pos3D(
                        direction.x(), direction.y(), direction.z()),
                options);
    }


    /**
     * Whether thunder is currently falling.
     *
     * @zh 当前是否在打雷。
     */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public boolean isThundering() {
        return ScriptApiEnvironment.adapter().world().isThundering();
    }

    /**
     * The current day count.
     *
     * @zh 当前天数。
     */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public long getDay() {
        return ScriptApiEnvironment.adapter().world().day();
    }

    /**
     * The total world age in ticks.
     *
     * @zh 世界总龄（tick）。
     */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public long getAge() {
        return ScriptApiEnvironment.adapter().world().age();
    }

    /**
     * The difficulty identifier, e.g. {@code normal}.
     *
     * @zh 难度标识，如 {@code normal}。
     */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public String getDifficulty() {
        return ScriptApiEnvironment.adapter().world().difficulty();
    }

    /**
     * The biome identifier at the position.
     *
     * @zh 查询坐标处群系标识。
     */
    @ApiSince("0.1.0")
    public String biomeAt(double x, double y, double z) {
        return ScriptApiEnvironment.adapter().world().biomeAt(new Pos3d(x, y, z))
                .orElse(null);
    }

    /**
     * Whether the chunk containing the position is loaded.
     *
     * @zh 判断坐标所在区块是否已加载。
     */
    @ApiSince("0.1.0")
    public boolean loaded(double x, double y, double z) {
        return ScriptApiEnvironment.adapter().world().loaded(new Pos3d(x, y, z));
    }

    /**
     * Whether the chunk is loaded.
     *
     * @zh 判断区块是否已加载。
     * @zh-param chunk 区块坐标
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public boolean chunkLoaded(ink.tuanzi.neomacros.core.api.types.ChunkPos chunk) {
        return ScriptApiEnvironment.adapter().world().loaded(new Pos3d(
                chunk.getMinX() + 0.5, 64, chunk.getMinZ() + 0.5));
    }
}