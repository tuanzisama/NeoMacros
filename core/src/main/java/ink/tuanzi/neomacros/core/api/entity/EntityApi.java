package ink.tuanzi.neomacros.core.api.entity;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.SnapshotViews;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code neo.entity} facade: entity queries over the loaded world.
 * Queries return plain snapshots; {@link #lookup} hands out a live handle
 * whose getters read current state in real time.
 *
 * @zh {@code neo.entity} 门面：对已加载世界的实体查询。查询返回纯快照；{@code lookup} 发放活句柄，其属性实时回读当前状态。
 */
@ApiModule(name = "entity", category = "世界")
@ApiSince("0.1.0")
public final class EntityApi {

    /**
     * Looks up a live entity handle by its UUID; returns {@code null} when
     * absent or unloaded.
     *
     * @zh 按 UUID 查询实体句柄；不存在或已卸载时返回 null。
     * @zh-param uuid 实体的 UUID 字符串
     * @zh-return 实体句柄，或 null
     */
    @ApiSince("0.1.0")
    public EntityApi.Entity lookup(String uuid) {
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return adapter().byUuid(id).map(s -> new Entity(id)).orElse(null);
    }

    /**
     * Returns snapshots of every entity within {@code range} blocks of the
     * center point.
     *
     * @zh 查询中心点半径范围内的全部实体快照。
     * @zh-param x 中心点坐标 X
     * @zh-param y 中心点坐标 Y
     * @zh-param z 中心点坐标 Z
     * @zh-param range 半径（格）
     * @zh-return 实体快照数组
     */
    @ApiSince("0.1.0")
    public List<Entity> inRadius(double x, double y, double z, double range) {
        return adapter().inRadius(new Pos3d(x, y, z), range, snapshot -> true).stream()
                .map(snapshot -> new Entity(snapshot.uuid()))
                .toList();
    }

    /**
     * Returns handles of every loaded player in the world.
     *
     * @zh 返回世界中全部玩家的句柄。
     * @zh-return 玩家句柄数组
     */
    @ApiSince("0.1.0")
    public List<Entity> players() {
        return adapter().inRadius(new Pos3d(0, 0, 0), Double.MAX_VALUE,
                snapshot -> snapshot.type().equals("minecraft:player")).stream()
                .map(snapshot -> new Entity(snapshot.uuid()))
                .toList();
    }

    /**
     * Returns the nearest entity matching the predicate, or undefined.
     *
     * @zh 返回距离中心最近且满足条件的实体句柄；无命中时为 undefined。
     * @zh-param x 中心点坐标 X
     * @zh-param y 中心点坐标 Y
     * @zh-param z 中心点坐标 Z
     * @zh-param range 半径（格）
     * @zh-return 最近实体句柄或 undefined
     */
    @ApiSince("0.1.0")
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
                .map(snapshot -> new Entity(snapshot.uuid()))
                .orElse(null);
    }

    /** The entity port behind the facade (single adapter access point). */
    private static EntityPort adapter() {
        return ScriptApiEnvironment.adapter().entity();
    }

    /**
     * Live entity handle: getters re-query the world on every access; a
     * vanished entity reads back as {@code undefined} instead of raising.
     *
     * @zh 实体句柄：属性实时回读世界状态；实体消失后属性回读为 undefined 而不报错。
     */
    @ApiClass(name = "Entity", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.Entity")
    public static final class Entity implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        private final UUID uuid;

        /** Creates the handle for one entity UUID (public for cross-cell use). */
        public Entity(UUID uuid) {
            this.uuid = uuid;
        }

        /**
         * The entity UUID string.
         *
         * @zh 实体 UUID 字符串。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public String getUuid() {
            return uuid.toString();
        }

        /**
         * The entity type identifier, e.g. {@code minecraft:pig}; undefined
         * once the entity is gone.
         *
         * @zh 实体类型标识（如 {@code minecraft:pig}）；实体消失后为 undefined。
         */
        @ApiSince("0.1.0")
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
        @ApiReadOnly
        public String getName() {
            return current().map(EntitySnapshot::name).orElse(null);
        }

        /**
         * The X coordinate; undefined once the entity is gone.
         *
         * @zh X 坐标；实体消失后为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Double getX() {
            return pos(Pos3d::x);
        }

        /**
         * The Y coordinate; undefined once the entity is gone.
         *
         * @zh Y 坐标；实体消失后为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Double getY() {
            return pos(Pos3d::y);
        }

        /**
         * The Z coordinate; undefined once the entity is gone.
         *
         * @zh Z 坐标；实体消失后为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Double getZ() {
            return pos(Pos3d::z);
        }

        /**
         * The health value; undefined once the entity is gone.
         *
         * @zh 生命值；实体消失后为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Double getHealth() {
            return current().<Double>map(EntitySnapshot::health).orElse(null);
        }

        /**
         * Whether the entity is still loaded in the world.
         *
         * @zh 实体是否仍在世界中加载。
         */
        @ApiSince("0.1.0")
        public boolean isValid() {
            return current().isPresent();
        }

        /**
         * Escape hatch: the raw host entity object wrapped as a JavaObject
         * proxy. Requires the {@code java} permission.
         *
         * @zh 穿透口：原始宿主实体对象（需 java 权限）。
         */
        @ApiSince("0.1.0")
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
        private Optional<EntitySnapshot> current() {
            return adapter().byUuid(uuid);
        }

        /** Reads one position component, or null when the entity is gone. */
        private Double pos(java.util.function.ToDoubleFunction<Pos3d> component) {
            return current().map(s -> component.applyAsDouble(s.pos())).orElse(null);
        }
    }
}
