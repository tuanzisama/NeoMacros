package ink.tuanzi.neomacros.core.api.entity;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.SnapshotViews;
import ink.tuanzi.neomacros.core.api.types.BlockPos;
import ink.tuanzi.neomacros.core.api.types.ChunkPos;
import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.core.api.types.Pos2D;
import ink.tuanzi.neomacros.core.api.types.Pos3D;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.model.BoundingBoxData;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code neo.entity} module: entity queries over the loaded world.
 * Queries return live handles whose getters read current state in real time;
 * a vanished entity reads back as {@code undefined} instead of raising.
 *
 * @zh {@code neo.entity}：对已加载世界的实体查询。查询返回活句柄，属性实时回读当前状态；实体消失后属性回读为 undefined 而不报错。
 */
@ApiModule(name = "entity", category = "世界")
@ApiSince("0.1.0")
@ApiLoader("*")
@ApiMC("*")
public final class EntityApi {

    /**
     * Looks up a live entity handle by its UUID; undefined when absent or
     * unloaded.
     *
     * @zh 按 UUID 查询实体句柄；不存在或已卸载时为 undefined。
     * @zh-param uuid 实体的 UUID 字符串
     * @zh-return 实体句柄，或 undefined
     */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Entity lookup(String uuid) {
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return wrap(id);
    }

    /**
     * Returns handles of every entity within {@code range} blocks of the
     * center point, optionally filtered by entity type ids.
     *
     * @zh 查询中心点半径范围内的全部实体句柄，可按实体类型过滤。
     * @zh-param x 中心点坐标 X
     * @zh-param y 中心点坐标 Y
     * @zh-param z 中心点坐标 Z
     * @zh-param range 半径（格）
     * @zh-param types 实体类型 id（字符串或字符串数组），如 {@code "minecraft:sheep"}；省略时不过滤
     * @zh-return 实体句柄数组
     */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public List<Entity> inRadius(double x, double y, double z, double range,
            @ApiOptional @ApiType("string | Array<string>") Object types) {
        java.util.Set<String> wanted = typeSet(types);
        return adapter().inRadius(new Pos3d(x, y, z), range,
                snapshot -> wanted == null || wanted.contains(snapshot.type())).stream()
                .map(snapshot -> wrap(snapshot.uuid()))
                .toList();
    }

    /**
     * Returns handles of every entity inside the axis-aligned box spanned by
     * {@code min} and {@code max}, optionally filtered by entity type ids.
     *
     * @zh 查询由 {@code min} 与 {@code max} 张成的轴对齐盒内的全部实体句柄，可按实体类型过滤。
     * @zh-param options 查询选项：{@code {min: {x,y,z}, max: {x,y,z}, types?: string|string[]}}
     * @zh-return 实体句柄数组
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public List<Entity> inBox(
            @ApiType("EntityInBoxOptions") java.util.Map<String, Object> options) {
        if (options == null) {
            throw new IllegalArgumentException("inBox options are required");
        }
        if (!(options.get("min") instanceof java.util.Map<?, ?> min)
                || !(options.get("max") instanceof java.util.Map<?, ?> max)) {
            throw new IllegalArgumentException("inBox needs min and max {x, y, z}");
        }
        Pos3d minPos = new Pos3d(
                asDouble(min.get("x")), asDouble(min.get("y")), asDouble(min.get("z")));
        Pos3d maxPos = new Pos3d(
                asDouble(max.get("x")), asDouble(max.get("y")), asDouble(max.get("z")));
        if (minPos.x() > maxPos.x() || minPos.y() > maxPos.y() || minPos.z() > maxPos.z()) {
            throw new IllegalArgumentException("inBox min must not exceed max");
        }
        java.util.Set<String> wanted = typeSet(options.get("types"));
        return adapter().inBox(minPos, maxPos,
                snapshot -> wanted == null || wanted.contains(snapshot.type())).stream()
                .map(snapshot -> wrap(snapshot.uuid()))
                .toList();
    }

    /** Normalizes one types option (string or array) into an id set. */
    private static java.util.Set<String> typeSet(Object types) {
        if (types == null) {
            return null;
        }
        if (types instanceof java.util.List<?> list) {
            java.util.Set<String> out = new java.util.LinkedHashSet<>();
            for (Object entry : list) {
                if (entry != null) {
                    out.add(String.valueOf(entry));
                }
            }
            return out.isEmpty() ? null : out;
        }
        return java.util.Set.of(String.valueOf(types));
    }

    /** Coerces one option number, defaulting absent values to zero. */
    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    /**
     * Returns handles of every loaded player in the world.
     *
     * @zh 返回世界中全部玩家的句柄。
     * @zh-return 玩家句柄数组
     */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public List<Entity> players() {
        return adapter().inRadius(new Pos3d(0, 0, 0), Double.MAX_VALUE,
                snapshot -> snapshot.type().equals("minecraft:player")).stream()
                .map(snapshot -> wrap(snapshot.uuid()))
                .toList();
    }

    /**
     * Returns the nearest entity, or undefined when none matches.
     *
     * @zh 返回距离中心最近的实体句柄；无命中时为 undefined。
     * @zh-param x 中心点坐标 X
     * @zh-param y 中心点坐标 Y
     * @zh-param z 中心点坐标 Z
     * @zh-param range 半径（格）
     * @zh-return 最近实体句柄或 undefined
     */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Entity nearest(double x, double y, double z, double range) {
        double cx = x;
        double cy = y;
        double cz = z;
        return adapter().inRadius(new Pos3d(cx, cy, cz), range, snapshot -> true).stream()
                .min(java.util.Comparator.comparingDouble(s -> {
                    var pos = s.pos();
                    return Math.pow(pos.x() - cx, 2) + Math.pow(pos.y() - cy, 2)
                            + Math.pow(pos.z() - cz, 2);
                }))
                .map(snapshot -> wrap(snapshot.uuid()))
                .orElse(null);
    }

    /** The entity port behind the API (single adapter access point). */
    private static EntityPort adapter() {
        return ScriptApiEnvironment.adapter().entity();
    }

    /**
     * Wraps one entity UUID as the kind-specific handle: players become
     * {@code Player}, dropped stacks {@code ItemEntity}, registered species
     * their species handle, living entities {@code LivingEntity}, everything
     * else the plain {@code Entity}.
     */
    static Entity wrap(UUID uuid) {
        return adapter().byUuid(uuid)
                .map(snapshot -> {
                    if (snapshot.type().equals("minecraft:player")) {
                        return (Entity) new ink.tuanzi.neomacros.core.api.player.PlayerApi.Player(uuid);
                    }
                    if (snapshot.type().equals("minecraft:item")) {
                        return (Entity) new ItemEntity(uuid);
                    }
                    var species = SpeciesEntities.registry().get(snapshot.type());
                    if (species != null) {
                        return species.apply(uuid);
                    }
                    if (snapshot.living()) {
                        return (Entity) new LivingEntity(uuid);
                    }
                    return (Entity) new Entity(uuid);
                })
                .orElse(null);
    }

    /**
     * Live entity handle: getters re-query the world on every access; a
     * vanished entity reads back as {@code undefined} instead of raising.
     *
     * @zh 实体句柄：属性实时回读世界状态；实体消失后属性回读为 undefined 而不报错。
     */
    @ApiClass(name = "Entity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.Entity")
    public static class Entity implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        /** The wrapped entity UUID (subclasses across API packages read it). */
        protected final UUID uuid;

        /** Creates the handle for one entity UUID (cross-cell factory use). */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public Entity(UUID uuid) {
            this.uuid = uuid;
        }

        /** Creates the handle (engine factory instantiation). */
        public Entity() {
            this.uuid = null;
        }

        /**
         * The species-specific live state behind this handle, keyed by the
         * script-facing member names; an empty map for plain entities.
         * Species subclasses read their members through this channel.
         *
         * @zh 句柄背后的物种专属实时状态，按脚本成员名键控；普通实体为空映射。物种子类经此通道读取成员。
         */
        /** The living-entity extras channel (absorption/armor/noGravity/...). */
        protected java.util.Map<String, Object> livingExtras() {
            return uuid != null
                    ? adapter().livingExtras(uuid)
                    : java.util.Map.of();
        }

        protected java.util.Map<String, Object> speciesState() {
            return uuid != null
                    ? adapter().speciesState(uuid)
                    : java.util.Map.of();
        }

        /** Reads one boolean species-state entry, or null when absent. */
        protected Boolean speciesBool(String key) {
            Object value = speciesState().get(key);
            return value instanceof Boolean b ? b : null;
        }

        /** Reads one string species-state entry, or null when absent. */
        protected String speciesString(String key) {
            Object value = speciesState().get(key);
            return value instanceof String v ? v : null;
        }

        /** Reads one integer species-state entry, or null when absent. */
        protected Integer speciesInt(String key) {
            Object value = speciesState().get(key);
            return value instanceof Integer i ? i : null;
        }

        /** Reads one numeric species-state entry, or null when absent. */
        protected Double speciesDouble(String key) {
            Object value = speciesState().get(key);
            return value instanceof Number n ? n.doubleValue() : null;
        }

        /**
         * Wraps one species-state UUID string as an entity handle, or null
         * when the entry is absent or the entity is gone.
         */
        protected EntityApi.Entity speciesEntity(String key) {
            Object value = speciesState().get(key);
            return value instanceof String id ? EntityApi.wrap(UUID.fromString(id)) : null;
        }

        /** The base-entity state channel (glowing/air/speed/custom name). */
        protected java.util.Map<String, Object> entityState() {
            return uuid != null ? adapter().entityState(uuid) : java.util.Map.of();
        }

        /** Reads one base-entity boolean entry, or null when absent. */
        protected Boolean entityBool(String key) {
            Object value = entityState().get(key);
            return value instanceof Boolean b ? b : null;
        }

        /** Reads one base-entity integer entry, or null when absent. */
        protected Integer entityInt(String key) {
            Object value = entityState().get(key);
            return value instanceof Integer i ? i : null;
        }

        /** Reads one base-entity numeric entry, or null when absent. */
        protected Double entityDouble(String key) {
            Object value = entityState().get(key);
            return value instanceof Number n ? n.doubleValue() : null;
        }

        /** Reads one base-entity string entry, or null when absent. */
        protected String entityString(String key) {
            Object value = entityState().get(key);
            return value instanceof String s ? s : null;
        }

        /**
         * The entity UUID string.
         *
         * @zh 实体 UUID 字符串。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getUuid() {
            return String.valueOf(uuid);
        }

        /**
         * The entity type identifier, e.g. {@code minecraft:pig}; undefined
         * once the entity is gone.
         *
         * @zh 实体类型标识（如 {@code minecraft:pig}）；实体消失后为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getType() {
            return current().map(EntitySnapshot::type).orElse(null);
        }

        /**
         * The display name; undefined once the entity is gone.
         *
         * @zh 显示名称；实体消失后为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getName() {
            return current().map(EntitySnapshot::name).orElse(null);
        }

        /**
         * The world position; undefined once the entity is gone.
         *
         * @zh 世界坐标位置；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Pos3D getPos() {
            return pos(EntitySnapshot::pos);
        }

        /**
         * The block-grid position (floored); undefined once the entity is
         * gone.
         *
         * @zh 方块网格坐标（向下取整）；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public BlockPos getBlockPos() {
            return current()
                    .map(s -> s.pos())
                    .map(p -> new BlockPos(Math.floor(p.x()), Math.floor(p.y()), Math.floor(p.z())))
                    .orElse(null);
        }

        /**
         * The chunk coordinate containing this entity; undefined once the
         * entity is gone.
         *
         * @zh 实体所在区块坐标；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public ChunkPos getChunk() {
            BlockPos block = getBlockPos();
            return block == null ? null : new ChunkPos(block);
        }

        /**
         * The velocity vector; undefined once the entity is gone.
         *
         * @zh 速度向量；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Pos3D getVelocity() {
            return pos(EntitySnapshot::velocity);
        }

        /**
         * The eye position; undefined once the entity is gone.
         *
         * @zh 眼睛位置；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Pos3D getEyePos() {
            return current()
                    .<Pos3D>map(s -> new Pos3D(s.pos().x(),
                            s.pos().y() + s.eyeHeight(), s.pos().z()))
                    .orElse(null);
        }

        /**
         * The eye height; undefined once the entity is gone.
         *
         * @zh 眼睛高度；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getEyeHeight() {
            return current().map(EntitySnapshot::eyeHeight)
                    .<Double>map(Double::valueOf).orElse(null);
        }

        /**
         * The view direction unit vector; undefined once the entity is gone.
         *
         * @zh 视线方向单位向量；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Pos3D getDirection() {
            return pos(EntitySnapshot::direction);
        }

        /**
         * The rotation as {@code Pos2D(yaw, pitch)} in degrees; undefined
         * once the entity is gone.
         *
         * @zh 朝向 {@code Pos2D(yaw, pitch)}（角度）；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Pos2D getRotation() {
            return current()
                    .<Pos2D>map(s -> new Pos2D(s.rotation().yawDeg(), s.rotation().pitchDeg()))
                    .orElse(null);
        }

        /**
         * The yaw angle in degrees; undefined once the entity is gone.
         *
         * @zh 偏航角（角度）；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getYaw() {
            return current().<Double>map(s -> (double) s.rotation().yawDeg()).orElse(null);
        }

        /**
         * The pitch angle in degrees; undefined once the entity is gone.
         *
         * @zh 俯仰角（角度）；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getPitch() {
            return current().<Double>map(s -> (double) s.rotation().pitchDeg()).orElse(null);
        }

        /**
         * The axis-aligned bounding box as {@code {min, max}} points;
         * undefined once the entity is gone.
         *
         * @zh 轴对齐包围盒 {@code {min, max}}；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public BoundingBox getBoundingBox() {
            return current()
                    .<BoundingBox>map(s -> {
                        BoundingBoxData box = s.boundingBox();
                        if (box == null) {
                            return null;
                        }
                        return new BoundingBox(
                                new Pos3D(box.min().x(), box.min().y(), box.min().z()),
                                new Pos3D(box.max().x(), box.max().y(), box.max().z()));
                    })
                    .orElse(null);
        }

        /** Whether the entity stands on the ground.
         * @zh 实体是否站在地面上。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isOnGround() {
            return current().map(EntitySnapshot::onGround).orElse(false);
        }

        /** Whether the entity is in water.
         * @zh 实体是否在水中。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isInWater() {
            return current().map(EntitySnapshot::inWater).orElse(false);
        }

        /** Whether the entity is in lava.
         * @zh 实体是否在熔岩中。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isInLava() {
            return current().map(EntitySnapshot::inLava).orElse(false);
        }

        /** Whether the entity is on fire.
         * @zh 实体是否着火。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isOnFire() {
            return current().map(EntitySnapshot::onFire).orElse(false);
        }

        /** Whether the entity is sneaking.
         * @zh 实体是否在潜行。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isSneaking() {
            return current().map(EntitySnapshot::sneaking).orElse(false);
        }

        /** Whether the entity is sprinting.
         * @zh 实体是否在疾跑。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isSprinting() {
            return current().map(EntitySnapshot::sprinting).orElse(false);
        }

        /** Whether the entity is swimming.
         * @zh 实体是否在游泳。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isSwimming() {
            return current().map(EntitySnapshot::swimming).orElse(false);
        }

        /** Whether the entity is gliding with an elytra.
         * @zh 实体是否在鞘翅滑翔。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        public boolean isGliding() {
            return current().map(EntitySnapshot::gliding).orElse(false);
        }

        /**
         * Whether the entity currently carries a glow outline; re-read on
         * every access, undefined once the entity is gone.
         *
         * @zh 实体当前是否带发光描边；每次访问实时回读，实体消失后为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean getGlowing() {
            return entityBool("glowing");
        }

        /**
         * The packed RGB colour the glow outline is drawn in (the team
         * colour when nothing overrides it); undefined once the entity is
         * gone.
         *
         * @zh 发光描边所用的打包 RGB 颜色（未覆盖时为队伍颜色）；实体消失后为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getGlowColor() {
            return entityInt("glowColor");
        }

        /**
         * The custom name shown above the entity, or undefined when the
         * entity carries none or is gone.
         *
         * @zh 实体头顶显示的自定义名；未设置或实体消失时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getCustomName() {
            return entityString("customName");
        }

        /**
         * Whether the custom name is always shown, ignoring distance;
         * undefined once the entity is gone.
         *
         * @zh 自定义名是否始终显示（不受距离影响）；实体消失后为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isCustomNameVisible() {
            return entityBool("customNameVisible");
        }

        /**
         * The remaining air in ticks (300 max in vanilla water); undefined
         * once the entity is gone.
         *
         * @zh 剩余空气刻（原版水中小上限 300）；实体消失后为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getAir() {
            return entityInt("air");
        }

        /**
         * The maximum air in ticks (300 in vanilla); undefined once the
         * entity is gone.
         *
         * @zh 最大空气刻（原版 300）；实体消失后为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getMaxAir() {
            return entityInt("maxAir");
        }

        /**
         * The horizontal movement since the previous tick, scaled to blocks
         * per second; undefined once the entity is gone.
         *
         * @zh 上一 tick 起的水平位移，换算为格/秒；实体消失后为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getSpeed() {
            return entityDouble("speed");
        }

        /**
         * Forces the glow outline on or off, overriding the game's own
         * decision; {@link #resetGlowing()} restores it. Local write: a later
         * server sync may take the decision back.
         *
         * @zh 强制开启或关闭发光描边，覆盖游戏自身的判定；{@link #resetGlowing()} 恢复。本地写入，服务器后续同步可能收回。
         * @zh-param glowing 是否强制发光
         * @zh-return 写入完成后落定的 Promise
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public java.util.concurrent.CompletableFuture<Void> setGlowing(boolean glowing) {
            return adapter().setGlowing(uuid, glowing);
        }

        /**
         * Restores the game's own glow decision after {@link #setGlowing}.
         * Local write.
         *
         * @zh 在 {@link #setGlowing()} 之后恢复游戏自身的发光判定。本地写入。
         * @zh-return 写入完成后落定的 Promise
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public java.util.concurrent.CompletableFuture<Void> resetGlowing() {
            return adapter().resetGlowing(uuid);
        }

        /**
         * Draws the glow outline in the packed RGB colour, leaving whether
         * the entity glows untouched; {@link #resetGlowingColor()} restores
         * the team-derived colour. Local write.
         *
         * @zh 以打包 RGB 颜色绘制发光描边，不改变是否发光；{@link #resetGlowingColor()} 恢复队伍颜色。本地写入。
         * @zh-param color 打包的 RGB 颜色值
         * @zh-return 写入完成后落定的 Promise
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public java.util.concurrent.CompletableFuture<Void> setGlowingColor(int color) {
            return adapter().setGlowingColor(uuid, color);
        }

        /**
         * Restores the team-derived glow colour after
         * {@link #setGlowingColor(int)}. Local write.
         *
         * @zh 在 {@link #setGlowingColor(int)} 之后恢复队伍派生的发光颜色。本地写入。
         * @zh-return 写入完成后落定的 Promise
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public java.util.concurrent.CompletableFuture<Void> resetGlowingColor() {
            return adapter().resetGlowingColor(uuid);
        }

        /**
         * Sets the custom name shown above the entity; an omitted or null
         * name clears it. Local write: the server owns the real value and
         * may overwrite it on the next sync.
         *
         * @zh 设置实体头顶显示的自定义名；省略或传 null 表示清除。本地写入：真实值归服务器所有，下次同步可能被覆盖。
         * @zh-param name 自定义名，省略或 null 清除
         * @zh-return 写入完成后落定的 Promise
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public java.util.concurrent.CompletableFuture<Void> setCustomName(
                @ApiOptional @ApiType("string | null") String name) {
            return adapter().setCustomName(uuid, name);
        }

        /**
         * Sets whether the custom name is always shown above the entity,
         * ignoring distance. Local write.
         *
         * @zh 设置自定义名是否始终显示（不受距离影响）。本地写入。
         * @zh-param visible 是否始终显示
         * @zh-return 写入完成后落定的 Promise
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public java.util.concurrent.CompletableFuture<Void> setCustomNameVisible(
                boolean visible) {
            return adapter().setCustomNameVisible(uuid, visible);
        }


        /**
         * The entity poses riding on this one, as entity handles; empty when
         * none.
         *
         * @zh 骑在本实体上的乘客句柄数组；无乘客时为空数组。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public List<Entity> getPassengers() {
            return current()
                    .map(s -> s.passengers().stream()
                            .map(id -> (Entity) wrap(id))
                            .filter(java.util.Objects::nonNull)
                            .toList())
                    .orElse(List.of());
        }

        /**
         * The entity this one is riding, or undefined.
         *
         * @zh 本实体骑乘的载具句柄；无骑乘时为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Entity getVehicle() {
            return current()
                    .map(EntitySnapshot::vehicle)
                    .flatMap(id -> Optional.ofNullable(wrap(id)))
                    .orElse(null);
        }

        /**
         * The entity pose name ({@code standing}, {@code crouching}, ...);
         * undefined once the entity is gone.
         *
         * @zh 实体姿态名（{@code standing}、{@code crouching} 等）；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getPose() {
            return current().map(EntitySnapshot::pose).orElse(null);
        }

        /**
         * The discretized horizontal facing ({@code north/south/east/west});
         * undefined once the entity is gone.
         *
         * @zh 离散水平朝向（{@code north/south/east/west}）；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getFacing() {
            return current().map(EntitySnapshot::facing).orElse(null);
        }

        /**
         * The dimension identifier the entity lives in; undefined once the
         * entity is gone.
         *
         * @zh 实体所在维度标识；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getDimension() {
            String dimension = current().map(EntitySnapshot::dimension).orElse(null);
            return dimension == null || dimension.isEmpty() ? null : dimension;
        }

        /**
         * Whether the entity is still loaded in the world.
         *
         * @zh 实体是否仍在世界中加载。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isValid() {
            return current().isPresent();
        }

        /**
         * Whether the entity is loaded and alive (living entities must hold
         * positive health).
         *
         * @zh 实体是否加载且存活（生物需生命值大于零）。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isAlive() {
            Optional<EntitySnapshot> snapshot = current();
            return snapshot.isPresent()
                    && (!snapshot.get().living() || snapshot.get().health() > 0);
        }

        /**
         * The distance to another entity or point.
         *
         * @zh 到另一实体或坐标点的距离。
         * @zh-param target 实体句柄或 {@code Pos3D}
         * @zh-return 距离（格）
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public double distanceTo(@ApiType("Entity | Pos3D") Object target) {
            Pos3d from = current().map(EntitySnapshot::pos).orElse(null);
            if (from == null) {
                throw new IllegalStateException("handle no longer valid");
            }
            double[] to = coordsOf(target);
            if (to == null) {
                throw new IllegalArgumentException("target must be an Entity or Pos3D");
            }
            return Math.sqrt(Math.pow(to[0] - from.x(), 2) + Math.pow(to[1] - from.y(), 2)
                    + Math.pow(to[2] - from.z(), 2));
        }

        /**
         * Escape hatch: the raw host entity object wrapped as a JavaObject
         * proxy. Requires the {@code java} permission.
         *
         * @zh 穿透口：原始宿主实体对象（需 java 权限）。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiPermission("java")
        @ApiType("JavaObject")
        public Object getRaw() {
            var raw = adapter().rawByUuid(uuid);
            return raw.orElse(null);
        }

        /**
         * Returns a point-in-time snapshot copy, or {@code null} when the
         * entity is gone.
         *
         * @zh 返回时点快照；实体消失时返回 null。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public EntitySnapshot snapshot() {
            return current().orElse(null);
        }

        /**
         * JSON string of the point-in-time snapshot; {@code JSON.stringify}
         * renders the handle through it.
         *
         * @zh 时点快照的 JSON 字符串；{@code JSON.stringify} 经此渲染句柄。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public String toJSON() {
            var view = SnapshotViews.entity(current().orElse(null));
            return view == null ? null
                    : ink.tuanzi.neomacros.core.util.JsonLite.write(view);
        }

        /** Human-readable form.
         * @zh 人类可读形式。 */
        @Override
        public String toString() {
            return "Entity(uuid=" + uuid + ")";
        }

        /** The latest snapshot of the entity, or empty when gone. */
        protected Optional<EntitySnapshot> current() {
            return uuid == null ? Optional.empty() : adapter().byUuid(uuid);
        }

        /** Reads one mapped point component, or null when the entity is gone. */
        private Pos3D pos(java.util.function.Function<EntitySnapshot, Pos3d> mapper) {
            return current()
                    .map(s -> point(mapper.apply(s)))
                    .orElse(null);
        }

        /** Converts the neutral point into the script type. */
        private static Pos3D point(Pos3d value) {
            return new Pos3D(value.x(), value.y(), value.z());
        }

        /** Reads target coordinates from an entity handle or a point. */
        private static double[] coordsOf(Object target) {
            if (target instanceof Entity entity) {
                return entity.current()
                        .map(s -> new double[]{s.pos().x(), s.pos().y(), s.pos().z()})
                        .orElse(null);
            }
            if (target instanceof Pos3D point) {
                return new double[]{point.getX(), point.getY(), point.getZ()};
            }
            return null;
        }
    }

    /**
     * Dropped item stack handle.
     *
     * @zh 掉落物句柄。
     */
    @ApiClass(name = "ItemEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.item.ItemEntity")
    public static final class ItemEntity extends Entity {

        /** Creates the handle for one entity UUID. */
        public ItemEntity(UUID uuid) {
            super(uuid);
        }

        /** Creates the handle (engine factory instantiation). */
        public ItemEntity() {
            super();
        }

        /**
         * The dropped item stack; undefined once the entity is gone.
         *
         * @zh 掉落的物品堆；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public ItemStack getStack() {
            return current()
                    .map(EntitySnapshot::heldItem)
                    .map(ItemStack::of)
                    .orElse(null);
        }
    }

    /**
     * Living entity handle: adds the health and equipment read face on top
     * of the entity base.
     *
     * @zh 生物句柄：在实体基类之上增加生命与装备读面。
     */
    @ApiClass(name = "LivingEntity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.LivingEntity")
        public static class LivingEntity extends Entity {

    /** The absorption hearts from golden apples and effects.
         * @zh 吸收心血量（金苹果/效果赋予）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getAbsorption() {
            Object value = livingExtras().get("absorption");
            return value instanceof Number n ? n.doubleValue() : null;
        }

        /** The armor points from worn gear (0-20 scale).
         * @zh 已穿戴护甲值（0-20 刻度）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getArmorValue() {
            Object value = livingExtras().get("armorValue");
            return value instanceof Number n ? n.doubleValue() : null;
        }

        /** Whether gravity is disabled for the entity.
         * @zh 实体是否无重力。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isNoGravity() {
            Object value = livingExtras().get("noGravity");
            return value instanceof Boolean b ? b : null;
        }

        /** Whether the entity is hostile (implements the Enemy marker).
         * @zh 实体是否为敌对（实现 Enemy 标记接口）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isHostile() {
            Object value = livingExtras().get("hostile");
            return value instanceof Boolean b ? b : null;
        }

        /**
         * The active status effects as {@code {id, duration, amplifier,
         * infinite}} entries; empty once the entity is gone.
         *
         * @zh 生效中的状态效果 {@code {id, duration, amplifier, infinite}}；实体消失后为空。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public java.util.List<StatusEffectData> getEffects() {
            Object value = livingExtras().get("effects");
            if (!(value instanceof java.util.List<?> list)) {
                return java.util.List.of();
            }
            java.util.List<StatusEffectData> effects = new java.util.ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    effects.add(new StatusEffectData(
                            map.get("id") instanceof String id ? id : null,
                            map.get("duration") instanceof Number n ? n.intValue() : null,
                            map.get("amplifier") instanceof Number n ? n.intValue() : null,
                            map.get("infinite") instanceof Boolean b ? b : null));
                }
            }
            return effects;
        }

        /** Whether a status effect with the given id is active.
         * @zh 指定 id 的状态效果是否生效中。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasEffect(String id) {
            if (id == null) {
                return null;
            }
            String wanted = id.startsWith("minecraft:") ? id : "minecraft:" + id;
            Object value = livingExtras().get("effects");
            if (!(value instanceof java.util.List<?> list)) {
                return null;
            }
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map && wanted.equals(map.get("id"))) {
                    return true;
                }
            }
            return false;
        }

        /** The remaining use ticks while eating, drinking, or blocking.
         * @zh 进食/饮水/格挡时的剩余使用刻。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getUseItemRemainingTicks() {
            Object value = livingExtras().get("useItemRemainingTicks");
            return value instanceof Integer i ? i : null;
        }

        /**
         * The stack in the head armor slot; undefined for an empty slot or a
         * gone entity.
         *
         * @zh 头盔槽物品；空槽或实体消失时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getHeadArmor() {
            return livingStack("headArmor");
        }

        /**
         * The stack in the chest armor slot; undefined for an empty slot or
         * a gone entity.
         *
         * @zh 胸甲槽物品；空槽或实体消失时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getChestArmor() {
            return livingStack("chestArmor");
        }

        /**
         * The stack in the leggings slot; undefined for an empty slot or a
         * gone entity.
         *
         * @zh 护腿槽物品；空槽或实体消失时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getLegArmor() {
            return livingStack("legArmor");
        }

        /**
         * The stack in the boots slot; undefined for an empty slot or a gone
         * entity.
         *
         * @zh 靴子槽物品；空槽或实体消失时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getFootArmor() {
            return livingStack("footArmor");
        }

        /**
         * The armor-toughness attribute value; undefined once the entity is
         * gone or carries no such attribute.
         *
         * @zh 护甲韧性属性值；实体消失或没有该属性时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getArmorToughness() {
            Object value = livingExtras().get("armorToughness");
            return value instanceof Number n ? n.doubleValue() : null;
        }

        /**
         * The base (unmodified) maximum health of the entity; undefined once
         * the entity is gone or carries no such attribute.
         *
         * @zh 实体的最大生命属性基准值（不含修饰）；实体消失或没有该属性时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getDefaultMaxHealth() {
            Object value = livingExtras().get("defaultMaxHealth");
            return value instanceof Number n ? n.doubleValue() : null;
        }

        /**
         * Whether the entity can breathe underwater by its own behaviour
         * (breathing equipment does not count); undefined once the entity is
         * gone.
         *
         * @zh 实体按自身行为能否在水下呼吸（不含呼吸装备）；实体消失后为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isBreathesUnderwater() {
            Object value = livingExtras().get("breathesUnderwater");
            return value instanceof Boolean b ? b : null;
        }

        /**
         * Whether the entity discards friction (its "no drag" flag), not
         * whether it is currently sliding; undefined once the entity is gone.
         *
         * @zh 实体是否免除摩擦（无阻力旗标），而非当前是否在滑动；实体消失后为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isNoDrag() {
            Object value = livingExtras().get("noDrag");
            return value instanceof Boolean b ? b : null;
        }

        /**
         * Whether the entity counts as part of the game: alive and not a
         * spectator; undefined once the entity is gone.
         *
         * @zh 实体是否算作游戏的一部分：存活且非旁观者；实体消失后为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isPartOfGame() {
            Object value = livingExtras().get("partOfGame");
            return value instanceof Boolean b ? b : null;
        }

        /**
         * Whether the entity type belongs to the undead tag; undefined once
         * the entity is gone.
         *
         * @zh 实体类型是否属于亡灵标签；实体消失后为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isUndead() {
            Object value = livingExtras().get("undead");
            return value instanceof Boolean b ? b : null;
        }

        /**
         * Whether the entity is a baby; undefined once the entity is gone.
         *
         * @zh 实体是否为幼体；实体消失后为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isBaby() {
            Object value = livingExtras().get("baby");
            return value instanceof Boolean b ? b : null;
        }

        /**
         * The bow draw power of the held item (0 to 1), or 0 when the main
         * hand holds no bow; undefined once the entity is gone.
         *
         * @zh 手持物品的拉弓力度（0 到 1），主手非弓时为 0；实体消失后为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getBowPull() {
            Object value = livingExtras().get("bowPull");
            return value instanceof Number n ? n.doubleValue() : null;
        }

        /**
         * Whether Minecraft considers the entity eligible for the status
         * effect with the id; this does not check whether the effect is
         * active. Unknown ids answer false.
         *
         * @zh Minecraft 是否认为该实体可被指定状态效果影响；不检查效果是否已生效。未知 id 返回 false。
         * @zh-param effectId 状态效果 id（命名空间可省略）
         * @zh-return 是否可被该效果影响
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public boolean canBeAffected(String effectId) {
            if (effectId == null) {
                return false;
            }
            String wanted = effectId.startsWith("minecraft:") ? effectId : "minecraft:" + effectId;
            return adapter().canBeAffected(uuid, wanted);
        }

        /**
         * Whether the game permits this entity to attack the target; an
         * eligibility check, not a statement of intent. False when either
         * handle is gone or the target is not living.
         *
         * @zh 游戏是否允许本实体攻击目标；这是资格判定，不代表攻击意图。任一句柄失效或目标非生物时为 false。
         * @zh-param target 目标实体句柄
         * @zh-return 是否可攻击
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public boolean canAttack(EntityApi.Entity target) {
            if (target == null || target.uuid == null || uuid == null) {
                return false;
            }
            return adapter().canAttack(uuid, target.uuid);
        }

        /**
         * Whether nothing blocks the view from this entity to the target.
         * The simple cast tests three points only; passing false additionally
         * samples four pillars around the target. False when either handle is
         * gone.
         *
         * @zh 本实体到目标之间是否视线无阻。简单投射只测三个点；传 false 会再围绕目标采样四根立柱。任一句柄失效时为 false。
         * @zh-param target 目标实体句柄
         * @zh-param simpleCast 是否只用简单投射（省略为 true）
         * @zh-return 是否看得见
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public boolean hasLineOfSight(EntityApi.Entity target,
                @ApiOptional Boolean simpleCast) {
            if (target == null || target.uuid == null || uuid == null) {
                return false;
            }
            return adapter().canSee(uuid, target.uuid, simpleCast == null || simpleCast);
        }

        /** Reads one living-entity stack entry, or null when absent. */
        private ItemStack livingStack(String key) {
            Object value = livingExtras().get(key);
            return value instanceof ItemStackData data ? ItemStack.of(data) : null;
        }

        /** The command tags attached to the entity.
         * @zh 实体携带的命令标签。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public java.util.List<String> getTags() {
            Object value = livingExtras().get("tags");
            if (!(value instanceof java.util.List<?> list)) {
                return java.util.List.of();
            }
            java.util.List<String> tags = new java.util.ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof String tag) {
                    tags.add(tag);
                }
            }
            return tags;
        }

        /** Creates the handle for one entity UUID. */
        public LivingEntity(UUID uuid) {
            super(uuid);
        }

        /** Creates the handle (engine factory instantiation). */
        public LivingEntity() {
            super();
        }

        /**
         * The health value; undefined once the entity is gone.
         *
         * @zh 生命值；实体消失后为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getHealth() {
            return current()
                    .<Double>map(s -> s.living() ? s.health() : null)
                    .orElse(null);
        }

        /**
         * The maximum health value; undefined once the entity is gone.
         *
         * @zh 最大生命值；实体消失后为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getMaxHealth() {
            return current()
                    .<Double>map(s -> s.living() ? s.maxHealth() : null)
                    .orElse(null);
        }

        /** Whether the entity is sleeping in a bed.
         * @zh 实体是否睡在床上。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isSleeping() {
            return current()
                    .map(s -> s.pose() != null && s.pose().equals("sleeping"))
                    .orElse(false);
        }

        /**
         * The item in the main hand; undefined for an empty hand or a gone
         * entity.
         *
         * @zh 主手物品；空手或实体消失时为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public ItemStack getHeldItem() {
            return heldStack(EntitySnapshot::heldItem);
        }

        /**
         * The item in the off hand; undefined for an empty hand or a gone
         * entity.
         *
         * @zh 副手物品；空手或实体消失时为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public ItemStack getOffhandItem() {
            return heldStack(EntitySnapshot::offhandItem);
        }

        /**
         * Whether either hand holds an item of the given id (namespace
         * optional).
         *
         * @zh 主/副手任一是否持有指定 id 的物品（命名空间可省略）。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isHolding(String itemId) {
            String wanted = ItemStack.namespaced(itemId);
            ItemStackData main = current().map(EntitySnapshot::heldItem).orElse(null);
            ItemStackData off = current().map(EntitySnapshot::offhandItem).orElse(null);
            return (main != null && main.id().equals(wanted))
                    || (off != null && off.id().equals(wanted));
        }

        /** The main-hand stack data, or null for a gone entity. */
        private static ItemStackData handOf(EntitySnapshot snapshot) {
            return snapshot == null ? null : snapshot.heldItem();
        }

        /** The off-hand stack data, or null for a gone entity. */
        private static ItemStackData offhandOf(EntitySnapshot snapshot) {
            return snapshot == null ? null : snapshot.offhandItem();
        }

        /** Reads one hand stack, or null when absent. */
        private ItemStack heldStack(
                java.util.function.Function<EntitySnapshot, ItemStackData> hand) {
            return current()
                    .map(hand)
                    .map(ItemStack::of)
                    .orElse(null);
        }
    }

    /** Normalizes a script item id (namespace default) for hand comparisons. */
    private static final class ItemHoldingId {
        private ItemHoldingId() {
        }

        static String of(String itemId) {
            return itemId == null ? "" : ItemStack.namespaced(itemId);
        }
    }

    /**
     * One axis-aligned bounding box: its min and max corners.
     *
     * @zh 一个轴对齐包围盒：最小与最大角点。
     */
    @ApiClass(name = "BoundingBox", kind = ApiClassKind.DATA)
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record BoundingBox(Pos3D min, Pos3D max) {
    }

    /**
     * One active status effect: the effect id, remaining duration ticks,
     * amplifier level, and whether the duration is infinite.
     *
     * @zh 一条生效中的状态效果：效果 id、剩余时长 tick、增幅等级与是否无限时长。
     */
    @ApiClass(name = "StatusEffectData", kind = ApiClassKind.DATA)
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record StatusEffectData(String id, Integer duration, Integer amplifier,
            Boolean infinite) {
    }

    /**
     * One entity.inBox query: the box corner positions and an optional
     * entity type filter.
     *
     * @zh 一次 entity.inBox 查询：盒体角点与可选的实体类型过滤。
     */
    @ApiClass(name = "EntityInBoxOptions", kind = ApiClassKind.DATA)
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record EntityInBoxOptions(
            @ApiType("{ x: number; y: number; z: number }") Object min,
            @ApiType("{ x: number; y: number; z: number }") Object max,
            @ApiOptional @ApiType("string | Array<string>") Object types) {
    }

}
