package ink.tuanzi.neomacros.core.api.player;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.SnapshotViews;
import ink.tuanzi.neomacros.doc.api.ApiHandle;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code neo.player} facade: the local player. {@code self} is a live
 * handle reading current state through the player port; outside a world it
 * reads back as {@code undefined}.
 */
@ApiModule(name = "player", category = "世界")
public final class PlayerApi {

    /**
     * The local player handle, or {@code undefined} when not in a world.
     *
     * @zh 本地玩家句柄；不在世界内时为 undefined。
     */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public PlayerApi.PlayerHandle getSelf() {
        return current().map(s -> new PlayerApi.PlayerHandle(s.uuid())).orElse(null);
    }

    /** The player port behind the facade (single adapter access point). */
    private static ink.tuanzi.neomacros.port.PlayerPort adapter() {
        return ScriptApiEnvironment.adapter().player();
    }

    /** The latest local player snapshot, or empty when not in a world. */
    private static Optional<PlayerSnapshot> current() {
        return adapter().self();
    }

    /**
     * Live local-player handle: getters re-query the player port on every
     * access; outside a world everything reads back as {@code undefined}.
     *
     * @zh 本地玩家句柄：属性实时回读；不在世界内时属性回读为 undefined。
     */
    @ApiHandle("PlayerHandle")
    public static final class PlayerHandle implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        private final UUID uuid;

        PlayerHandle(UUID uuid) {
            this.uuid = uuid;
        }

        /**
         * The player UUID string.
         *
         * @zh 玩家 UUID 字符串。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public String getUuid() {
            return uuid.toString();
        }

        /**
         * The player name; undefined outside a world.
         *
         * @zh 玩家名称；不在世界内时为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public String getName() {
            return current().map(PlayerSnapshot::name).orElse(null);
        }

        /**
         * The X coordinate; undefined outside a world.
         *
         * @zh X 坐标；不在世界内时为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Double getX() {
            return pos(Pos3d::x);
        }

        /**
         * The Y coordinate; undefined outside a world.
         *
         * @zh Y 坐标；不在世界内时为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Double getY() {
            return pos(Pos3d::y);
        }

        /**
         * The Z coordinate; undefined outside a world.
         *
         * @zh Z 坐标；不在世界内时为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Double getZ() {
            return pos(Pos3d::z);
        }

        /**
         * The health value; undefined outside a world.
         *
         * @zh 生命值；不在世界内时为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Double getHealth() {
            return current().<Double>map(PlayerSnapshot::health).orElse(null);
        }

        /**
         * The hunger value; undefined outside a world.
         *
         * @zh 饥饿值；不在世界内时为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Integer getHunger() {
            return current().<Integer>map(PlayerSnapshot::hunger).orElse(null);
        }

        /**
         * The air (breath) value; undefined outside a world.
         *
         * @zh 氧气值；不在世界内时为 undefined。
         */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Integer getAir() {
            return current().<Integer>map(PlayerSnapshot::air).orElse(null);
        }

        /**
         * Whether the local player is currently in a world.
         *
         * @zh 本地玩家当前是否在世界内。
         */
        @ApiSince("0.1.0")
        public boolean isValid() {
            return current().isPresent();
        }

        /**
         * Returns a point-in-time snapshot copy, or {@code null} when not in
         * a world.
         *
         * @zh 返回时点快照；不在世界内时返回 null。
         */
        @ApiSince("0.1.0")
        public PlayerSnapshot snapshot() {
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
            return SnapshotViews.player(current().orElse(null));
        }

        /** Human-readable form. */
        @Override
        public String toString() {
            return "PlayerHandle(uuid=" + uuid + ")";
        }

        /** Reads one position component, or null when not in a world. */
        private Double pos(java.util.function.ToDoubleFunction<Pos3d> component) {
            return current().map(s -> component.applyAsDouble(s.pos())).orElse(null);
        }
    }
}
