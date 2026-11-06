package ink.tuanzi.neomacros.core.api.world;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.SnapshotViews;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.BossBarSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ScoreboardData;
import ink.tuanzi.neomacros.port.model.TabListData;
import ink.tuanzi.neomacros.port.model.TpsData;

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
@ApiMC("*")
@ApiLoader("*")
public final class WorldApi {

    /**
     * The world time of day in ticks.
     *
     * @zh 当前世界时间（tick）。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiMC("*")
    @ApiLoader("*")
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
    @ApiLoader("*")
    @ApiMC("*")
    public boolean chunkLoaded(ink.tuanzi.neomacros.core.api.types.ChunkPos chunk) {
        return ScriptApiEnvironment.adapter().world().loaded(new Pos3d(
                chunk.getMinX() + 0.5, 64, chunk.getMinZ() + 0.5));
    }

    /**
     * The loaded save or server name; an empty string without a world.
     *
     * @zh 当前存档或服务器名称；未加载世界时为空字符串。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getLevelName() {
        return ScriptApiEnvironment.adapter().world().levelName();
    }

    /**
     * The current moon phase index (0 = full moon, 4 = new moon).
     *
     * @zh 当前月相序号（0 为满月，4 为新月）。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public int getMoonPhase() {
        return ScriptApiEnvironment.adapter().world().moonPhase();
    }

    /**
     * The remote tick rate estimate sampled from time sync packets:
     * {@code {instant, m1, m5, m15}} in ticks per second, or {@code null}
     * before the first sample.
     *
     * @zh 由时间同步包采样得到的远端 tick 速率估计 {@code {instant, m1, m5, m15}}（每秒 tick 数）；尚无采样时为 null。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public java.util.Map<String, Object> getTps() {
        return ScriptApiEnvironment.adapter().world().tps()
                .map(WorldApi::tpsView).orElse(null);
    }

    /**
     * The client tab list as {@code {header, footer, entries}}; entries carry
     * {@code uuid, name, latency, gamemode, displayName}, or {@code null}
     * without a world.
     *
     * @zh 客户端 Tab 列表 {@code {header, footer, entries}}；条目含 {@code uuid, name, latency, gamemode, displayName}；无世界时为 null。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public java.util.Map<String, Object> getTabList() {
        return ScriptApiEnvironment.adapter().world().tabList()
                .map(WorldApi::tabListView).orElse(null);
    }

    /**
     * The boss bars currently displayed as
     * {@code [{uuid, name, progress, color}]}; empty list when none.
     *
     * @zh 当前显示的 Boss 条数组 {@code [{uuid, name, progress, color}]}；无 Boss 条时为空数组。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public java.util.List<java.util.Map<String, Object>> getBossBars() {
        return ScriptApiEnvironment.adapter().world().bossBars().stream()
                .map(WorldApi::bossBarView).toList();
    }

    /**
     * The client scoreboard as {@code {objectives, scores}}; objectives carry
     * {@code name, displayName, criteria, renderType}, scores carry
     * {@code objective, owner, value}, or {@code null} without a world.
     *
     * @zh 客户端记分板 {@code {objectives, scores}}；objectives 条目含 {@code name, displayName, criteria, renderType}，scores 条目含 {@code objective, owner, value}；无世界时为 null。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public java.util.Map<String, Object> getScoreboards() {
        return ScriptApiEnvironment.adapter().world().scoreboards()
                .map(WorldApi::scoreboardView).orElse(null);
    }

    /**
     * Plays a sound locally. With coordinates the sound plays at the world
     * position (requires a loaded world); otherwise it plays through the UI
     * sound channel. Unknown identifiers throw.
     *
     * @zh 本地播放声音：给出坐标且世界已加载时按世界位置播放，否则走 UI 声音通道；未知的 soundId 抛错。
     * @zh-param soundId 声音标识，可省略 {@code minecraft:} 命名空间
     * @zh-param x 世界坐标 X
     * @zh-param y 世界坐标 Y
     * @zh-param z 世界坐标 Z
     * @zh-param volume 音量，默认 1
     * @zh-param pitch 音调，默认 1
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void playSound(String soundId, double x, double y, double z,
            double volume, double pitch) {
        ScriptApiEnvironment.adapter().world().playSound(soundId,
                new Pos3d(x, y, z), volume, pitch);
    }

    /**
     * Spawns particles locally around the position; unknown identifiers or
     * data-carrying particle types throw.
     *
     * @zh 在坐标附近本地生成粒子；未知 particleId 或需要附加数据的粒子类型抛错。
     * @zh-param particleId 粒子类型标识，可省略 {@code minecraft:} 命名空间
     * @zh-param x 世界坐标 X
     * @zh-param y 世界坐标 Y
     * @zh-param z 世界坐标 Z
     * @zh-param count 粒子数量
     * @zh-param offsetX 粒子散布半径 X
     * @zh-param offsetY 粒子散布半径 Y
     * @zh-param offsetZ 粒子散布半径 Z
     * @zh-param speed 粒子初速
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void spawnParticle(String particleId, double x, double y, double z,
            int count, double offsetX, double offsetY, double offsetZ, double speed) {
        ScriptApiEnvironment.adapter().world().spawnParticle(particleId,
                new Pos3d(x, y, z), count,
                new Pos3d(offsetX, offsetY, offsetZ), speed);
    }

    /**
     * The loaded chunk containing the coordinates as a {@code Chunk}
     * handle, or {@code null} when unloaded.
     *
     * @zh 坐标所在已加载区块的 {@code Chunk} 句柄；未加载时为 null。
     * @zh-param x 世界坐标 X
     * @zh-param y 世界坐标 Y（决定所在子区块高度，通常取 64）
     * @zh-param z 世界坐标 Z
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public ink.tuanzi.neomacros.core.api.types.Chunk chunkAt(double x, double y, double z) {
        return ScriptApiEnvironment.adapter().world().chunkAt(new Pos3d(x, y, z))
                .map(data -> new ink.tuanzi.neomacros.core.api.types.Chunk(
                        data.chunkX(), data.chunkZ()))
                .orElse(null);
    }

    /**
     * Scans the cube around the center for blocks matching one of the given
     * block ids and returns their positions as {@code Block} handles. The
     * scan range is capped at 32 blocks and the result at 512 entries to
     * keep the main thread responsive; unknown ids are ignored.
     *
     * @zh 在中心周围立方体范围内扫描匹配方块 id 的方块，返回 {@code Block} 句柄数组。扫描半径上限 32、结果上限 512 以保持主线程响应；未知 id 忽略。
     * @zh-param options 扫描选项：{@code {types: string|string[], center: {x,y,z}, range?: number, limit?: number}}
     * @zh-return {@code Block[]} 数组
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public java.util.List<ink.tuanzi.neomacros.core.api.types.Block> findBlocks(
            java.util.Map<String, Object> options) {
        if (options == null) {
            throw new IllegalArgumentException("findBlocks options are required");
        }
        java.util.List<String> types = new java.util.ArrayList<>();
        Object rawTypes = options.get("types");
        if (rawTypes instanceof java.util.List<?> list) {
            for (Object entry : list) {
                if (entry != null) {
                    types.add(String.valueOf(entry));
                }
            }
        } else if (rawTypes != null) {
            types.add(String.valueOf(rawTypes));
        }
        if (types.isEmpty()) {
            throw new IllegalArgumentException("findBlocks needs at least one block type");
        }
        Object rawCenter = options.get("center");
        if (!(rawCenter instanceof java.util.Map<?, ?> center)) {
            throw new IllegalArgumentException("findBlocks needs a center {x, y, z}");
        }
        double cx = asDouble(center.get("x"), 0);
        double cy = asDouble(center.get("y"), 0);
        double cz = asDouble(center.get("z"), 0);
        int range = (int) asDouble(options.get("range"), 16);
        int limit = (int) asDouble(options.get("limit"), 128);
        return ScriptApiEnvironment.adapter().world()
                .findBlocks(types, new Pos3d(cx, cy, cz), range, limit)
                .stream()
                .map(pos -> new ink.tuanzi.neomacros.core.api.types.Block(
                        new ink.tuanzi.neomacros.core.api.types.BlockPos(
                                (int) Math.floor(pos.x()),
                                (int) Math.floor(pos.y()),
                                (int) Math.floor(pos.z()))))
                .toList();
    }

    /** Coerces one option number with a default for absent values. */
    private static double asDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    /** Renders the tick rate estimate as the script-facing shape. */
    private static java.util.Map<String, Object> tpsView(TpsData tps) {
        java.util.Map<String, Object> view = new LinkedHashMap<>();
        view.put("instant", tps.instant());
        view.put("m1", tps.m1());
        view.put("m5", tps.m5());
        view.put("m15", tps.m15());
        return view;
    }

    /** Renders the tab list snapshot as the script-facing shape. */
    private static java.util.Map<String, Object> tabListView(TabListData data) {
        java.util.Map<String, Object> view = new LinkedHashMap<>();
        view.put("header", data.header());
        view.put("footer", data.footer());
        java.util.List<java.util.Map<String, Object>> entries = new java.util.ArrayList<>();
        for (TabListData.TabListEntry entry : data.entries()) {
            java.util.Map<String, Object> row = new LinkedHashMap<>();
            row.put("uuid", entry.uuid());
            row.put("name", entry.name());
            row.put("latency", entry.latency());
            row.put("gamemode", entry.gamemode());
            row.put("displayName", entry.displayName());
            entries.add(row);
        }
        view.put("entries", entries);
        return view;
    }

    /** Renders one boss bar snapshot as the script-facing shape. */
    private static java.util.Map<String, Object> bossBarView(BossBarSnapshot bar) {
        java.util.Map<String, Object> view = new LinkedHashMap<>();
        view.put("uuid", bar.uuid());
        view.put("name", bar.name());
        view.put("progress", bar.progress());
        view.put("color", bar.color());
        return view;
    }

    /** Renders the scoreboard snapshot as the script-facing shape. */
    private static java.util.Map<String, Object> scoreboardView(ScoreboardData data) {
        java.util.List<java.util.Map<String, Object>> objectives = new java.util.ArrayList<>();
        for (ScoreboardData.ObjectiveData objective : data.objectives()) {
            java.util.Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", objective.name());
            row.put("displayName", objective.displayName());
            row.put("criteria", objective.criteria());
            row.put("renderType", objective.renderType());
            objectives.add(row);
        }
        java.util.List<java.util.Map<String, Object>> scores = new java.util.ArrayList<>();
        for (ScoreboardData.ScoreEntryData score : data.scores()) {
            java.util.Map<String, Object> row = new LinkedHashMap<>();
            row.put("objective", score.objective());
            row.put("owner", score.owner());
            row.put("value", score.value());
            scores.add(row);
        }
        java.util.Map<String, Object> view = new LinkedHashMap<>();
        view.put("objectives", objectives);
        view.put("scores", scores);
        return view;
    }
}