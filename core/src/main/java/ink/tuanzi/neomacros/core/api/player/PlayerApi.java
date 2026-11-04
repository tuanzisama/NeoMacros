package ink.tuanzi.neomacros.core.api.player;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.SnapshotViews;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code neo.player} facade: the local player. {@code self} is a live
 * handle reading current state through the player port; outside a world it
 * reads back as {@code undefined}.
 *
 * @zh {@code neo.player} 门面：本地玩家。{@code self} 是经玩家端口回读当前状态的活句柄；不在世界内时回读为 undefined。
 */
@ApiModule(name = "player", category = "世界")
@ApiSince("0.1.0")
public final class PlayerApi {

    /**
     * The local player handle, or {@code undefined} when not in a world.
     *
     * @zh 本地玩家句柄；不在世界内时为 undefined。
     */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public PlayerApi.LocalPlayer getSelf() {
        return current().map(s -> new PlayerApi.LocalPlayer(s.uuid())).orElse(null);
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
     * Live local-player data handle: getters re-query the player port on
     * every access; outside a world everything reads back as undefined.
     * The guest class tree roots here for the local player.
     *
     * @zh 本地玩家数据句柄：属性实时回读；不在世界内时属性回读为 undefined。
     */
    @ApiClass(name = "Player", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.player.LocalPlayer")
    public static class Player implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        private final UUID uuid;

        Player(UUID uuid) {
            this.uuid = uuid;
        }

        /** The player UUID string.
         * @zh 玩家 UUID 字符串。 */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public String getUuid() {
            return uuid.toString();
        }

        /** The player name; undefined outside a world.
         * @zh 玩家名称；不在世界内时为 undefined。 */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public String getName() {
            return current().map(PlayerSnapshot::name).orElse(null);
        }

        /** The X coordinate; undefined outside a world.
         * @zh X 坐标；不在世界内时为 undefined。 */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Double getX() {
            return pos(Pos3d::x);
        }

        /** The Y coordinate; undefined outside a world.
         * @zh Y 坐标；不在世界内时为 undefined。 */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Double getY() {
            return pos(Pos3d::y);
        }

        /** The Z coordinate; undefined outside a world.
         * @zh Z 坐标；不在世界内时为 undefined。 */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Double getZ() {
            return pos(Pos3d::z);
        }

        /** The health value; undefined outside a world.
         * @zh 生命值；不在世界内时为 undefined。 */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Double getHealth() {
            return current().<Double>map(PlayerSnapshot::health).orElse(null);
        }

        /** The hunger value; undefined outside a world.
         * @zh 饥饿值；不在世界内时为 undefined。 */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Integer getHunger() {
            return current().<Integer>map(PlayerSnapshot::hunger).orElse(null);
        }

        /** The air (breath) value; undefined outside a world.
         * @zh 氧气值；不在世界内时为 undefined。 */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public Integer getAir() {
            return current().<Integer>map(PlayerSnapshot::air).orElse(null);
        }

        /**
         * Escape hatch: the raw host player object. Requires the
         * {@code java} permission.
         *
         * @zh 穿透口：原始宿主玩家对象（需 java 权限）。
         */
        @ApiSince("0.1.0")
        @ApiPermission("java")
        public Object getRaw() {
            return adapter().rawSelf().orElse(null);
        }

        /** Whether the local player is currently in a world.
         * @zh 本地玩家当前是否在世界内。 */
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

        /** JSON form of {@link #snapshot}; lets JSON.stringify render the handle directly.
         * @zh 等价 snapshot 的 JSON 形式；JSON.stringify 可直接序列化句柄。 */
        @ApiSince("0.1.0")
        public Map<String, Object> toJSON() {
            return SnapshotViews.player(current().orElse(null));
        }

        /** Human-readable form.
         * @zh 人类可读形式。 */
        @Override
        public String toString() {
            return snapshot() == null ? "Player(unloaded)" : "Player(" + getName() + ")";
        }

        /** Reads one position component, or null when not in a world. */
        private Double pos(java.util.function.ToDoubleFunction<Pos3d> component) {
            return current().map(s -> component.applyAsDouble(s.pos())).orElse(null);
        }
    }

    /**
     * The action surface of the local player: attack, interact, swing, and
     * drop dispatch through the game on the main thread and resolve once
     * processed.
     *
     * @zh 本地玩家动作面：attack/interact/swing/drop 经游戏主线程派发。
     */
    @ApiClass(name = "LocalPlayer", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.player.LocalPlayer")
    public static final class LocalPlayer extends Player {

        LocalPlayer(UUID uuid) {
            super(uuid);
        }

        /** Attacks the entity behind the target handle.
         * @zh 攻击目标句柄背后的实体。 */
        @ApiSince("0.1.0")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> attack(EntityApi.Entity target) {
            EntitySnapshot targetData = target == null ? null : target.snapshot();
            if (targetData == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("target entity is not loaded"));
            }
            return adapter().attack(targetData);
        }

        /** Uses (right-clicks) the entity behind the target handle.
         * @zh 与目标句柄背后的实体交互（右键）。 */
        @ApiSince("0.1.0")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> interactEntity(EntityApi.Entity target) {
            EntitySnapshot targetData = target == null ? null : target.snapshot();
            if (targetData == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("target entity is not loaded"));
            }
            return adapter().useEntity(targetData);
        }

        /** Uses (right-clicks) the block behind the position handle.
         * @zh 与坐标句柄背后的方块交互（右键）。 */
        @ApiSince("0.1.0")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> interactBlock(
                ink.tuanzi.neomacros.core.api.types.BlockPos pos) {
            var hit = new BlockInteraction("minecraft:use", new Pos3d(
                    pos.getIntX(), pos.getIntY(), pos.getIntZ()));
            return adapter().useBlock(hit);
        }

        /** Swings the main hand.
         * @zh 挥动主手。 */
        @ApiSince("0.1.0")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> swingHand() {
            return adapter().swingHand();
        }

        /** Drops the held item stack ({@code all} = the whole stack).
         * @zh 丢弃手持物品（all = 整组）。 */
        @ApiSince("0.1.0")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> dropHeld(boolean all) {
            return adapter().dropHeld(all);
        }

        /** Human-readable form.
         * @zh 人类可读形式。 */
        @Override
        public String toString() {
            var snapshot = snapshot();
            return snapshot == null ? "LocalPlayer(unloaded)" : "LocalPlayer(" + getName() + ")";
        }
    }
}
