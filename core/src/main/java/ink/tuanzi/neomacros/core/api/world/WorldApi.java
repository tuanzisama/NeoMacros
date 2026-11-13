package ink.tuanzi.neomacros.core.api.world;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.SnapshotViews;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.BossBarSnapshot;
import ink.tuanzi.neomacros.port.model.LightData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ScoreboardData;
import ink.tuanzi.neomacros.port.model.TabListData;
import ink.tuanzi.neomacros.port.model.TpsData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code neo.world} module: world queries over the loaded client world.
 * Positions use plain world coordinates; results are plain point-in-time
 * objects.
 *
 * @zh {@code neo.world}：对已加载客户端世界的查询。位置使用普通世界坐标；结果为纯时点对象。
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
     * The world spawn or respawn-data position as a {@code BlockPos}, or
     * {@code null} without a loaded world. This is the position the level
     * records for respawning; it does not predict where the player will
     * eventually respawn.
     *
     * @zh 世界出生点/重生数据位置的 {@code BlockPos}；未加载世界时为 null。取世界记录的重生数据，不预测玩家最终重生结果。
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public ink.tuanzi.neomacros.core.api.types.BlockPos getRespawnPos() {
        return ScriptApiEnvironment.adapter().world().respawnPos()
                .map(pos -> new ink.tuanzi.neomacros.core.api.types.BlockPos(
                        (int) Math.floor(pos.x()),
                        (int) Math.floor(pos.y()),
                        (int) Math.floor(pos.z())))
                .orElse(null);
    }

    /**
     * The light levels at the block position as {@code {sky, block}}, each
     * layer reading 0 to 15, or {@code null} without a loaded world. The two
     * layers are independent; sky light does not account for time-of-day or
     * weather darkening.
     *
     * @zh 坐标处方块光照 {@code {sky, block}}，各层 0 到 15；未加载世界时为 null。两层相互独立，sky 不含昼夜与天气的变暗修正。
     * @zh-param x 世界坐标 X
     * @zh-param y 世界坐标 Y
     * @zh-param z 世界坐标 Z
     * @zh-return {@code {sky, block}} 或 null
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    public LightData lightAt(double x, double y, double z) {
        return ScriptApiEnvironment.adapter().world().lightAt(new Pos3d(x, y, z))
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
    public TpsData getTps() {
        return ScriptApiEnvironment.adapter().world().tps().orElse(null);
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
    public TabListData getTabList() {
        return ScriptApiEnvironment.adapter().world().tabList().orElse(null);
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
    public java.util.List<BossBarSnapshot> getBossBars() {
        return ScriptApiEnvironment.adapter().world().bossBars();
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
    public ScoreboardData getScoreboards() {
        return ScriptApiEnvironment.adapter().world().scoreboards().orElse(null);
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
     * Plays an audio file through the host sound mixer and resolves once
     * playback starts. Unlike {@code playSound}, which plays registered sound
     * events through the game sound engine (positional, honoring game volume
     * settings), this plays the file itself outside the game sound engine.
     * The path may be absolute or relative to the game directory. Volume is
     * {@code 0..1} mapped onto the mixer's master-gain range. The promise
     * rejects when the file cannot be read, decoded, or played.
     *
     * @zh 通过宿主混音器播放音频文件，播放启动后 resolve。与 playSound（游戏声音引擎内播放注册声音事件，可定位、受游戏音量设置约束）不同，本方法播放文件本身，不经游戏声音引擎。路径可为绝对路径或相对游戏目录；volume 为 0..1，映射到混音器主增益范围；文件不可读、无法解码或无法播放时 reject。
     * @zh-param path 音频文件路径（绝对路径，或相对游戏目录）
     * @zh-param volume 音量 0..1（映射到主增益范围）
     */
    @ApiSince("0.7.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<void>")
    public java.util.concurrent.CompletableFuture<Void> playSoundFile(String path, double volume) {
        return ScriptApiEnvironment.adapter().world().playSoundFile(path, volume);
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
     * Scans the cube (or sphere) around the center for blocks matching the
     * given block ids or id glob pattern, optionally filtered by block state
     * properties, and returns their positions as {@code Block} handles. The
     * scan range is capped at 48 blocks and the result at 1024 entries to
     * keep the main thread responsive; unknown ids are ignored.
     *
     * @zh 在中心周围立方体（或球体）范围内扫描匹配方块 id 或 id 通配模式（可再按方块状态属性过滤）的方块，返回 {@code Block} 句柄数组。扫描半径上限 48、结果上限 1024 以保持主线程响应；未知 id 忽略。
     * @zh-param options 扫描选项：{@code {types?: string|string[], idPattern?: string, states?: Record<string, string|number|boolean>, center: {x,y,z}, range?: number, limit?: number, shape?: "cube"|"sphere"}}
     * @zh-return {@code Block[]} 数组
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public java.util.List<ink.tuanzi.neomacros.core.api.types.Block> findBlocks(
            @ApiType("FindBlocksOptions") java.util.Map<String, Object> options) {
        ParsedScan scan = parseScan(options, "findBlocks", false);
        return ScriptApiEnvironment.adapter().world()
                .findBlocksMatching(scan.types(), scan.idPattern(), scan.states(),
                        scan.center(), scan.range(), scan.limit(), scan.sphere())
                .stream()
                .map(WorldApi::toBlock)
                .toList();
    }

    /**
     * Starts a large-volume block scan with the same matching rules as
     * {@code findBlocks} and resolves the promise with the matched
     * {@code Block} handles. The scan runs in per-tick slices on the main
     * thread (about 4 ms per tick), so big volumes never stall the frame:
     * the range may go up to 256 (16-million-block volume budget) with up
     * to 65536 results and a 10-second deadline. When {@code center} is
     * omitted the local player position is used. At most four scans run at
     * once; the promise rejects when the budget is exceeded, the deadline
     * passes, or the world changes mid-scan.
     *
     * @zh 启动一次大范围方块扫描（匹配规则同 {@code findBlocks}），以 Promise 返回 {@code Block} 句柄数组。扫描按 tick 在主线程分片执行（每 tick 约 4 ms），不会卡帧：半径上限 256（1600 万方块体积预算）、结果上限 65536、超时 10 秒。{@code center} 省略时取本地玩家坐标。同时最多 4 个扫描任务；超预算、超时或世界切换时 Promise 拒绝。
     * @zh-param options 扫描选项：{@code {types?: string|string[], idPattern?: string, states?: Record<string, string|number|boolean>, center?: {x,y,z}, range?: number, limit?: number, shape?: "cube"|"sphere"}}
     * @zh-return {@code Promise<Block[]>}
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public java.util.concurrent.CompletableFuture<java.util.List<ink.tuanzi.neomacros.core.api.types.Block>> scanBlocks(
            @ApiType("ScanBlocksOptions") java.util.Map<String, Object> options) {
        ParsedScan scan = parseScan(options, "scanBlocks", true);
        return ScriptApiEnvironment.adapter().world()
                .scanBlocks(scan.types(), scan.idPattern(), scan.states(),
                        scan.center(), scan.range(), scan.limit(), scan.sphere())
                .thenApply(list -> list.stream().map(WorldApi::toBlock).toList());
    }

    /** Wraps one scanned position as a live block handle. */
    private static ink.tuanzi.neomacros.core.api.types.Block toBlock(Pos3d pos) {
        return new ink.tuanzi.neomacros.core.api.types.Block(
                new ink.tuanzi.neomacros.core.api.types.BlockPos(
                        (int) Math.floor(pos.x()),
                        (int) Math.floor(pos.y()),
                        (int) Math.floor(pos.z())));
    }

    /** One parsed scan request shared by findBlocks and scanBlocks. */
    private record ParsedScan(java.util.List<String> types, String idPattern,
            java.util.Map<String, Object> states, Pos3d center, int range,
            int limit, boolean sphere) {
    }

    /** Parses and validates one scan options object. */
    private static ParsedScan parseScan(java.util.Map<String, Object> options,
            String api, boolean async) {
        if (options == null) {
            throw new IllegalArgumentException(api + " options are required");
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
        String idPattern = options.get("idPattern") != null
                ? String.valueOf(options.get("idPattern")) : null;
        if (types.isEmpty() && (idPattern == null || idPattern.isBlank())) {
            throw new IllegalArgumentException(
                    api + " needs block types or an idPattern");
        }
        java.util.Map<String, Object> states = new java.util.LinkedHashMap<>();
        if (options.get("states") instanceof java.util.Map<?, ?> rawStates) {
            for (Map.Entry<?, ?> entry : rawStates.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    states.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        Pos3d center;
        if (options.get("center") instanceof java.util.Map<?, ?> rawCenter) {
            center = new Pos3d(asDouble(rawCenter.get("x"), 0),
                    asDouble(rawCenter.get("y"), 0), asDouble(rawCenter.get("z"), 0));
        } else if (async) {
            center = ScriptApiEnvironment.adapter().player().self()
                    .map(ink.tuanzi.neomacros.port.model.PlayerSnapshot::pos)
                    .orElseThrow(() -> new IllegalArgumentException(
                            api + " needs a center {x, y, z} (no local player is loaded)"));
        } else {
            throw new IllegalArgumentException(api + " needs a center {x, y, z}");
        }
        int range = (int) asDouble(options.get("range"), 16);
        int limit = (int) asDouble(options.get("limit"), async ? 65536 : 128);
        boolean sphere = "sphere".equalsIgnoreCase(String.valueOf(options.get("shape")));
        return new ParsedScan(types, idPattern, states, center, range, limit, sphere);
    }

    /** Coerces one option number with a default for absent values. */
    private static double asDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    /**
     * One findBlocks scan: the block ids (string or string array) or id
     * glob pattern, the state-property filters, the center position, the
     * scan range, the result cap, and the shape.
     *
     * @zh 一次 findBlocks 扫描：方块 id（字符串或字符串数组）或 id 通配模式、状态属性过滤、中心坐标、扫描半径、结果上限与形状。
     */
    @ApiClass(name = "FindBlocksOptions", kind = ApiClassKind.DATA)
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record FindBlocksOptions(
            @ApiOptional @ApiType("string | Array<string>") Object types,
            @ApiOptional String idPattern,
            @ApiOptional @ApiType("Record<string, string | number | boolean>")
                    java.util.Map<String, Object> states,
            @ApiType("{ x: number; y: number; z: number }") Object center,
            @ApiOptional Double range,
            @ApiOptional Integer limit,
            @ApiOptional @ApiType("\"cube\" | \"sphere\"") String shape) {
    }

    /**
     * One scanBlocks scan: the same filters and shape as findBlocks with a
     * much larger range budget; the center defaults to the local player.
     *
     * @zh 一次 scanBlocks 扫描：过滤与形状同 findBlocks，预算大得多；center 省略时取本地玩家坐标。
     */
    @ApiClass(name = "ScanBlocksOptions", kind = ApiClassKind.DATA)
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record ScanBlocksOptions(
            @ApiOptional @ApiType("string | Array<string>") Object types,
            @ApiOptional String idPattern,
            @ApiOptional @ApiType("Record<string, string | number | boolean>")
                    java.util.Map<String, Object> states,
            @ApiOptional @ApiType("{ x: number; y: number; z: number }") Object center,
            @ApiOptional Double range,
            @ApiOptional Integer limit,
            @ApiOptional @ApiType("\"cube\" | \"sphere\"") String shape) {
    }
}