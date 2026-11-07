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
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.model.AabbData;
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
     * center point.
     *
     * @zh 查询中心点半径范围内的全部实体句柄。
     * @zh-param x 中心点坐标 X
     * @zh-param y 中心点坐标 Y
     * @zh-param z 中心点坐标 Z
     * @zh-param range 半径（格）
     * @zh-return 实体句柄数组
     */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public List<Entity> inRadius(double x, double y, double z, double range) {
        return adapter().inRadius(new Pos3d(x, y, z), range, snapshot -> true).stream()
                .map(snapshot -> wrap(snapshot.uuid()))
                .toList();
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
        public Map<String, Object> getBoundingBox() {
            return current()
                    .<Map<String, Object>>map(s -> {
                        AabbData box = s.boundingBox();
                        if (box == null) {
                            return null;
                        }
                        Map<String, Object> view = new LinkedHashMap<>();
                        view.put("min", new Pos3D(box.min().x(), box.min().y(), box.min().z()));
                        view.put("max", new Pos3D(box.max().x(), box.max().y(), box.max().z()));
                        return view;
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
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isGliding() {
            return current().map(EntitySnapshot::gliding).orElse(false);
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
        public double distanceTo(Object target) {
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
         * JSON form of {@link #snapshot}; lets JSON.stringify render the
         * handle directly.
         *
         * @zh 等价 snapshot 的 JSON 形式；JSON.stringify 可直接序列化句柄。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public Map<String, Object> toJSON() {
            return SnapshotViews.entity(current().orElse(null));
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

        /** Whether the entity is currently glowing (outline).
         * @zh 实体当前是否发光（描边）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isGlowing() {
            Object value = livingExtras().get("glowing");
            return value instanceof Boolean b ? b : null;
        }

        /** Whether the entity is hostile (implements the Enemy marker). */
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
        public java.util.List<Map<String, Object>> getEffects() {
            Object value = livingExtras().get("effects");
            if (!(value instanceof java.util.List<?> list)) {
                return java.util.List.of();
            }
            java.util.List<Map<String, Object>> effects = new java.util.ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    effects.add(typed);
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

        /** The remaining air ticks; 300 max in vanilla water.
         * @zh 剩余空气刻；原版水中小上限 300。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getAir() {
            Object value = livingExtras().get("air");
            return value instanceof Integer i ? i : null;
        }

        /** The maximum air ticks (300 in vanilla).
         * @zh 最大空气刻（原版 300）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getMaxAir() {
            Object value = livingExtras().get("maxAir");
            return value instanceof Integer i ? i : null;
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
}
