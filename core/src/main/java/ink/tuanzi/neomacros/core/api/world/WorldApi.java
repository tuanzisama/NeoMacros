package ink.tuanzi.neomacros.core.api.world;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.SnapshotViews;
import ink.tuanzi.neomacros.doc.api.ApiModule;
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
 */
@ApiModule(name = "world", category = "世界")
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
    public Map<String, Object> blockAt(double x, double y, double z) {
        Optional<BlockData> block = ScriptApiEnvironment.adapter().world()
                .blockAt(new Pos3d(x, y, z));
        return block.map(WorldApi::blockView).orElse(null);
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
    public Map<String, Object> raycast(double fromX, double fromY, double fromZ,
                                       double toX, double toY, double toZ) {
        Pos3d from = new Pos3d(fromX, fromY, fromZ);
        Pos3d to = new Pos3d(toX, toY, toZ);
        Pos3d direction = new Pos3d(to.x() - from.x(), to.y() - from.y(), to.z() - from.z());
        var options = new ink.tuanzi.neomacros.port.model.RayOptions(
                Math.sqrt(Math.pow(to.x() - from.x(), 2) + Math.pow(to.y() - from.y(), 2) + Math.pow(to.z() - from.z(), 2)), true, true, false);
        Optional<ink.tuanzi.neomacros.port.model.RayHit> hit = ScriptApiEnvironment
                .adapter().world().raycast(from, direction, options);
        return hit.map(WorldApi::rayView).orElse(null);
    }

    /** Renders one ray hit as the plain script object shape. */
    private static Map<String, Object> rayView(ink.tuanzi.neomacros.port.model.RayHit hit) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (hit.block() != null) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("id", hit.block().id());
            block.put("pos", SnapshotViews.pos(hit.block().pos()));
            view.put("block", block);
        }
        if (hit.entity() != null) {
            view.put("entity", SnapshotViews.entity(hit.entity()));
        }
        view.put("pos", SnapshotViews.pos(hit.pos()));
        view.put("distance", hit.distance());
        return view;
    }

    /** Renders one block result as the plain script object shape. */
    private static Map<String, Object> blockView(Object block) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (block instanceof BlockData data) {
            view.put("id", data.id());
            view.put("pos", SnapshotViews.pos(data.pos()));
        } else if (block instanceof BlockInteraction hit) {
            view.put("id", hit.id());
            view.put("pos", SnapshotViews.pos(hit.pos()));
        }
        return view;
    }
}
