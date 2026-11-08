package ink.tuanzi.neomacros.core.api.player;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.SnapshotViews;
import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code neo.player} module: the local player, the open container
 * ({@code inventory}), the open screen ({@code screen}), and the open
 * dialog ({@code dialog}). {@code self} is a live handle reading current
 * state through the player port; outside a world it reads back as
 * {@code undefined}.
 *
 * @zh {@code neo.player}：本地玩家、打开的容器（{@code inventory}）、当前屏幕（{@code screen}）与当前对话框（{@code dialog}）。{@code self} 是经玩家端口回读当前状态的活句柄；不在世界内时回读为 undefined。
 */
@ApiModule(name = "player", category = "世界")
@ApiSince("0.1.0")
@ApiLoader("*")
@ApiMC("*")
public final class PlayerApi {

    /**
     * The local player handle, or {@code undefined} when not in a world.
     *
     * @zh 本地玩家句柄；不在世界内时为 undefined。
     */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public PlayerApi.LocalPlayer getSelf() {
        return current().map(s -> new PlayerApi.LocalPlayer(s.uuid())).orElse(null);
    }

    /**
     * The currently open container as an {@code Inventory} handle, or the
     * player's own inventory when no container screen is open (the same
     * handle shape as {@code player.self.inventory}); undefined outside a
     * world.
     *
     * @zh 当前打开的容器句柄；未开容器屏幕时回退为玩家自己的背包（与 {@code player.self.inventory} 同形句柄）；不在世界内为 undefined。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public Inventory getInventory() {
        if (current().isEmpty()) {
            return null;
        }
        return inventoryPort().openedContainer()
                .map(info -> openByKind(info.title(), info.kind(), info.size()))
                .orElseGet(PlayerInventory::new);
    }

    /**
     * The currently open screen as a {@code Screen} handle family member
     * ({@code ChatScreen}, {@code InventoryScreen}, {@code ContainerScreen},
     * {@code DialogScreen}, ...; every screen without a dedicated class
     * falls back to the base {@code Screen}), or undefined when no screen is
     * open.
     *
     * @zh 当前打开的屏幕句柄族成员（{@code ChatScreen}、{@code InventoryScreen}、{@code ContainerScreen}、{@code DialogScreen} 等；无专属类的屏幕回落基类 {@code Screen}）；无屏幕时为 undefined。
     */
    @ApiSince("0.3.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public Screen getScreen() {
        var info = ScriptApiEnvironment.adapter().lifecycle().screenInfo().orElse(null);
        return info == null ? null : openByScreenKind(info.kind(), info.screenClass(), info.title());
    }

    /**
     * The currently open dialog as a {@code DialogHandle}, or undefined
     * when no dialog screen is open.
     *
     * @zh 当前打开的对话框句柄；无对话框屏幕时为 undefined。
     */
    @ApiSince("0.3.0")
    @ApiLoader("*")
    @ApiMC(">=1.21.6")
    @ApiReadOnly
    public DialogHandle getDialog() {
        return ScriptApiEnvironment.adapter().dialog().currentDialog()
                .map(DialogHandle::of).orElse(null);
    }

    /**
     * Opens a client-side dialog from a plain config map and resolves with
     * the chosen action id once {@code player.dialog.clickAction} runs; Esc
     * or a manual button click resolves with {@code null} when the dialog
     * closes. Config keys: {@code type} ({@code notice},
     * {@code confirmation}, {@code multi_action}), {@code title},
     * {@code body} (string or string array), {@code canCloseWithEscape},
     * {@code actions} (array of {@code {label}}).
     *
     * @zh 从普通配置对象打开一个客户端对话框，脚本调用 {@code player.dialog.clickAction} 后以所选动作 id 落定；按 Esc 或手动点击按钮关闭时以 null 落定。配置键：{@code type}（{@code notice}/{@code confirmation}/{@code multi_action}）、{@code title}、{@code body}（字符串或字符串数组）、{@code canCloseWithEscape}、{@code actions}（{@code {label}} 数组）。
     */
    @ApiSince("0.3.0")
    @ApiLoader("*")
    @ApiMC(">=1.21.6")
    public CompletableFuture<String> showDialog(
            @ApiType("DialogConfig") Map<String, Object> config) {
        return ScriptApiEnvironment.adapter().dialog().show(config);
    }

    /** The player port behind the API (single adapter access point). */
    private static ink.tuanzi.neomacros.port.PlayerPort adapter() {
        return ScriptApiEnvironment.adapter().player();
    }

    /** The inventory port behind the API (container screen access). */
    private static ink.tuanzi.neomacros.port.InventoryPort inventoryPort() {
        return ScriptApiEnvironment.adapter().inventory();
    }

    /** The lifecycle port behind the screen face (screen reads, close). */
    private static ink.tuanzi.neomacros.port.LifecyclePort lifecyclePort() {
        return ScriptApiEnvironment.adapter().lifecycle();
    }

    /** The dialog port behind the dialog face (dialog reads and actions). */
    private static ink.tuanzi.neomacros.port.DialogPort dialogPort() {
        return ScriptApiEnvironment.adapter().dialog();
    }

    /** The latest local player snapshot, or empty when not in a world. */
    static Optional<PlayerSnapshot> current() {
        return adapter().self();
    }

    /** Resolves the handle class for one open screen kind. */
    private static Screen openByScreenKind(String kind, String mcClass, String title) {
        return switch (kind) {
            case "inventory" -> new InventoryScreen(mcClass, title);
            case "creative" -> new CreativeScreen(mcClass, title);
            case "container", "chest", "furnace", "crafting", "anvil", "enchanting",
                    "grindstone", "stonecutter", "smithing", "cartography", "loom",
                    "lectern", "dispenser", "hopper", "crafter", "beacon", "brewing",
                    "villager", "horse", "mount", "shulkerbox" ->
                    new ContainerScreen(mcClass, title);
            case "chat" -> new ChatScreen(mcClass, title);
            case "title" -> new TitleScreen(mcClass, title);
            case "pause" -> new PauseScreen(mcClass, title);
            case "death" -> new DeathScreen(mcClass, title);
            case "wingame" -> new WinScreen(mcClass, title);
            case "progress", "connecting" -> new ProgressScreen(mcClass, title);
            case "disconnected" -> new DisconnectedScreen(mcClass, title);
            case "confirm" -> new ConfirmScreen(mcClass, title);
            case "dialog" -> new DialogScreen(mcClass, title);
            case "sign" -> new SignScreen(mcClass, title);
            case "bookedit" -> new BookEditScreen(mcClass, title);
            case "bookview" -> new BookViewScreen(mcClass, title);
            case "commandblock" -> new CommandBlockScreen(mcClass, title);
            case "structureblock" -> new StructureBlockScreen(mcClass, title);
            case "jigsaw" -> new JigsawScreen(mcClass, title);
            default -> new Screen(mcClass, kind, title);
        };
    }

    /**
     * Live player handle: the entity read face comes through the entity port
     * (works for remote players too); the local-only extras (hunger, air,
     * gamemode, xp) read through the player port and are undefined for
     * remote players.
     *
     * @zh 玩家句柄：实体读面经实体端口（远端玩家同样可用）；饥饿/氧气/游戏模式/经验等本地字段经玩家端口，远端玩家读为 undefined。
     */
    private static Inventory openByKind(String title, String kind, int size) {
        return switch (kind) {
            case "player", "inventory" -> new PlayerInventory();
            case "furnace" -> new FurnaceInventory(title, kind, size);
            case "chest" -> new ContainerInventory(title, kind, size);
            case "crafting" -> new CraftingInventory(title, kind, size);
            case "anvil" -> new AnvilInventory(title, kind, size);
            case "enchanting" -> new EnchantInventory(title, kind, size);
            case "grindstone" -> new GrindstoneInventory(title, kind, size);
            case "stonecutter" -> new StonecutterInventory(title, kind, size);
            case "smithing" -> new SmithingInventory(title, kind, size);
            case "cartography" -> new CartographyInventory(title, kind, size);
            case "beacon" -> new BeaconInventory(title, kind, size);
            case "brewing" -> new BrewingStandInventory(title, kind, size);
            case "villager" -> new VillagerInventory(title, kind, size);
            case "horse" -> new HorseInventory(title, kind, size);
            default -> new Inventory(title, kind, size);
        };
    }

    /**
     * Player handle: the entity read face comes through the entity port
     * (works for remote players too); the local-only extras (hunger, air,
     * gamemode, xp) read through the player port and are undefined for
     * remote players.
     *
     * @zh 玩家句柄：实体读面经实体端口（远端玩家同样可用）；饥饿/氧气/游戏模式/经验等本地字段经玩家端口，远端玩家读为 undefined。
     */
    @ApiClass(name = "Player", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.player.Player")
    public static class Player extends EntityApi.LivingEntity {

        /** Creates the handle for one player UUID. */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public Player(UUID uuid) {
            super(uuid);
        }

        /** Creates the handle (engine factory instantiation). */
        public Player() {
            super();
        }

        /**
         * The health value; the local player reads through the player port
         * (its vitals live on the player snapshot), a remote player falls
         * back to the entity snapshot.
         *
         * @zh 生命值；本地玩家经玩家端口读取（生命在玩家快照上），远端玩家回退到实体快照。
         */
        @ApiSince("0.1.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getHealth() {
            Optional<PlayerSnapshot> self = local();
            if (self.isPresent()) {
                return self.get().health();
            }
            return current()
                    .<Double>map(s -> s.living() ? s.health() : null)
                    .orElse(null);
        }

        /** The hunger value; only the local player carries it.
         * @zh 饥饿值；仅本地玩家有值。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getHunger() {
            return local().<Integer>map(PlayerSnapshot::hunger).orElse(null);
        }

        /** The air (breath) value; only the local player carries it.
         * @zh 氧气值；仅本地玩家有值。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getAir() {
            return local().<Integer>map(PlayerSnapshot::air).orElse(null);
        }

        /** The food saturation value; only the local player carries it.
         * @zh 饱和度；仅本地玩家有值。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Double getSaturation() {
            return local().<Double>map(s -> (double) s.saturation()).orElse(null);
        }

        /**
         * The gamemode identifier ({@code survival/creative/adventure/
         * spectator}); only the local player carries it.
         *
         * @zh 游戏模式标识（{@code survival/creative/adventure/spectator}）；仅本地玩家有值。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getGamemode() {
            return isSelf() ? adapter().gamemode() : null;
        }

        /**
         * The experience as {@code {level, progress, total}}; only the local
         * player carries it.
         *
         * @zh 经验 {@code {level, progress, total}}；仅本地玩家有值。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public XpInfo getXp() {
            if (!isSelf()) {
                return null;
            }
            return new XpInfo(adapter().expLevel(), (double) adapter().expProgress(),
                    adapter().expTotal());
        }

        /**
         * The player's own inventory; only the local player carries it.
         *
         * @zh 玩家自己的物品栏；仅本地玩家有值。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public PlayerInventory getInventory() {
            return isSelf() ? new PlayerInventory() : null;
        }

        /**
         * Returns a point-in-time snapshot copy, or {@code null} when the
         * player is not loaded.
         *
         * @zh 返回时点快照；玩家未加载时返回 null。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public EntitySnapshot snapshot() {
            return current().orElse(null);
        }

        /** JSON form; the local player renders through the player view.
         * @zh JSON 形式；本地玩家经玩家视图输出。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiType("PlayerJson")
        public Map<String, Object> toJSON() {
            Optional<PlayerSnapshot> self = local();
            return self.map(SnapshotViews::player)
                    .orElseGet(() -> SnapshotViews.entity(current().orElse(null)));
        }

        /** Human-readable form.
         * @zh 人类可读形式。 */
        @Override
        public String toString() {
            return snapshot() == null ? "Player(unloaded)" : "Player(" + getName() + ")";
        }

        /** Whether this handle wraps the local player. */
        boolean isSelf() {
            return PlayerApi.current()
                    .map(PlayerSnapshot::uuid)
                    .map(id -> id.equals(uuid))
                    .orElse(false);
        }

        /** The local player snapshot when this handle wraps the local player. */
        Optional<PlayerSnapshot> local() {
            return isSelf() ? PlayerApi.current() : Optional.empty();
        }
    }

    /**
     * The local player abilities handle: the creative-mode flags and speed
     * multipliers, readable live and writable client-side. Multiplayer
     * servers keep authority over flying, so writes may be corrected.
     *
     * @zh 本地玩家能力句柄：创造模式旗标与速度倍率，实时可读、客户端可写。联机服务器保有飞行决定权，写入可能被纠正。
     */
    @ApiClass(name = "Abilities", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.entity.player.Abilities")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class Abilities {

        private final java.util.UUID uuid;

        /** Creates the handle for the local player. */
        public Abilities(java.util.UUID uuid) {
            this.uuid = uuid;
        }

        private java.util.Map<String, Object> state() {
            return ScriptApiEnvironment.adapter().entity().livingExtras(uuid);
        }

        /** Whether the player is immune to damage.
         * @zh 玩家是否免受伤害。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isInvulnerable() {
            Object value = state().get("invulnerable");
            return value instanceof Boolean b ? b : null;
        }

        /** Whether the player is currently flying.
         * @zh 玩家当前是否飞行中。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isFlying() {
            Object value = state().get("flying");
            return value instanceof Boolean b ? b : null;
        }

        /** Whether the player may toggle flight at all.
         * @zh 玩家是否允许切换飞行。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean mayFly() {
            Object value = state().get("mayFly");
            return value instanceof Boolean b ? b : null;
        }

        /** Whether the player has instant block breaking.
         * @zh 玩家是否瞬间破坏方块。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isInstabuild() {
            Object value = state().get("instabuild");
            return value instanceof Boolean b ? b : null;
        }

        /** Whether the player may build and break blocks.
         * @zh 玩家是否可建造与破坏。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean mayBuild() {
            Object value = state().get("mayBuild");
            return value instanceof Boolean b ? b : null;
        }

        /** The fly speed multiplier.
         * @zh 飞行速度倍率。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getFlySpeed() {
            Object value = state().get("flySpeed");
            return value instanceof Number n ? n.doubleValue() : null;
        }

        /** The walking speed multiplier.
         * @zh 行走速度倍率。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getWalkingSpeed() {
            Object value = state().get("walkingSpeed");
            return value instanceof Number n ? n.doubleValue() : null;
        }

        /**
         * Toggles flight (client-side write; servers may revoke it).
         *
         * @zh 切换飞行（客户端写；服务器可能收回）。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> setFlying(boolean value) {
            return adapter().setFlying(value);
        }

        /**
         * Sets the fly speed multiplier (client-side write).
         *
         * @zh 设置飞行速度倍率（客户端写）。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> setFlySpeed(float value) {
            return adapter().setFlySpeed(value);
        }

        /**
         * Sets the walking speed multiplier (client-side write).
         *
         * @zh 设置行走速度倍率（客户端写）。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> setWalkingSpeed(float value) {
            return adapter().setWalkingSpeed(value);
        }
    }

    /**
     * Local player action face: attack/interact/swing/drop dispatch through
     * the game on the main thread and resolve once processed.
     *
     * @zh 本地玩家动作面：attack/interact/swing/drop 经游戏主线程派发，处理完成后落定。
     */
    @ApiClass(name = "LocalPlayer", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.player.LocalPlayer")
    public static final class LocalPlayer extends Player {

        public LocalPlayer(UUID uuid) {
            super(uuid);
        }

        /**
         * The live abilities handle: flags and speed multipliers, readable
         * and writable client-side.
         *
         * @zh 实时能力句柄：旗标与速度倍率，可读且客户端可写。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Abilities getAbilities() {
            return new Abilities(uuid);
        }

        /**
         * The client-side statistics as {@code statTypeId:valueId -> value}
         * entries; only non-zero values appear. {@code null} without a world.
         *
         * @zh 客户端统计 {@code 类型id:条目id -> 数值}；仅含非零值；无世界时为 null。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Map<String, Integer> getStatistics() {
            Map<String, Integer> stats = adapter().statistics();
            return stats.isEmpty() ? null : stats;
        }

        /** The block interaction range in blocks.
         * @zh 方块交互距离（格）。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public double getBlockInteractionRange() {
            return adapter().blockInteractionRange();
        }

        /** The entity interaction range in blocks.
         * @zh 实体交互距离（格）。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public double getEntityInteractionRange() {
            return adapter().entityInteractionRange();
        }

        /** Whether the local player is currently breaking a block.
         * @zh 本地玩家是否正在挖掘方块。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public boolean isBreakingBlock() {
            return adapter().isBreakingBlock();
        }

        /**
         * Turns the camera to the yaw (horizontal) and pitch (vertical)
         * angles in degrees.
         *
         * @zh 将视角转向指定 yaw（水平）与 pitch（垂直）角度（度）。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> look(float yaw, float pitch) {
            return adapter().look(yaw, pitch);
        }

        /**
         * Turns the camera to look at the entity behind the target handle.
         *
         * @zh 将视角转向目标句柄背后的实体。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> lookAt(EntityApi.Entity target) {
            EntitySnapshot targetData = target == null ? null : target.snapshot();
            if (targetData == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("target entity is not loaded"));
            }
            return adapter().lookAt(targetData);
        }

        /**
         * Moves the local player to the position (client-side write;
         * multiplayer servers correct the position back).
         *
         * @zh 将本地玩家移动到该位置（客户端写；联机服务器会把位置纠正回去）。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> teleport(double x, double y, double z) {
            return adapter().teleport(x, y, z);
        }

        /**
         * Sets the movement velocity vector (client-side write).
         *
         * @zh 设置移动速度向量（客户端写）。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> setVelocity(double x, double y, double z) {
            return adapter().setVelocity(x, y, z);
        }

        /**
         * The item cooldown progress for the item id, {@code 0} when not on
         * cooldown (or the item is unknown).
         *
         * @zh 物品 id 的冷却进度；无冷却（或物品未知）时为 0。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public float itemCooldown(String itemId) {
            return adapter().itemCooldownPercent(itemId);
        }

        /** Attacks the entity behind the target handle.
         * @zh 攻击目标句柄背后的实体。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
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
        @ApiLoader("*")
        @ApiMC("*")
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
        @ApiLoader("*")
        @ApiMC("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> interactBlock(
                ink.tuanzi.neomacros.core.api.types.BlockPos pos) {
            var hit = new BlockInteraction("minecraft:use", new Pos3d(
                    pos.getIntX(), pos.getIntY(), pos.getIntZ()));
            return adapter().useBlock(hit);
        }

        /** Swings the main hand (offhand = the off hand).
         * @zh 挥动手（offhand = 副手）。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> swingHand(boolean offhand) {
            return adapter().swingHand();
        }

        /** Drops the held item stack ({@code all} = the whole stack).
         * @zh 丢弃手持物品（all = 整组）。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
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

    /**
     * The local player's own inventory: the always-available inventory
     * session (what {@code player.inventory} resolves to while the inventory
     * screen is open and when no container screen is open).
     *
     * @zh 本地玩家自己的物品栏：恒可用的物品栏会话（物品栏界面打开或未开容器时 {@code player.inventory} 解析到的句柄）。
     */
    @ApiClass(name = "PlayerInventory", kind = ApiClassKind.HANDLE)
    public static final class PlayerInventory extends Inventory {

        PlayerInventory() {
            super("Inventory", "player", 0);
        }
    }

    /**
     * A furnace-family container screen: input/fuel/output slots plus the
     * live burn state read from the open menu.
     *
     * @zh 熔炉族容器界面：输入/燃料/输出槽位与打开菜单的实时燃烧状态。
     */
    @ApiClass(name = "FurnaceInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.AbstractFurnaceMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class FurnaceInventory extends Inventory {

        FurnaceInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** Reads one menu slot as an item handle, or undefined. */
        private ItemStack menuStack(int index) {
            return inventoryPort().menuSlot(index).map(ItemStack::of).orElse(null);
        }

        /**
         * The smelting input stack, or {@code undefined}.
         *
         * @zh 冶炼输入物品；为空时 undefined。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getInput() {
            return menuStack(0);
        }

        /**
         * The fuel stack, or {@code undefined}.
         *
         * @zh 燃料物品；为空时 undefined。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getFuel() {
            return menuStack(1);
        }

        /**
         * The smelted output stack, or {@code undefined}.
         *
         * @zh 冶炼产出物品；为空时 undefined。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getOutput() {
            return menuStack(2);
        }

        /**
         * Whether the furnace is currently burning.
         *
         * @zh 熔炉当前是否在燃烧。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isLit() {
            return boolState("isLit");
        }

        /**
         * The fuel burn progress (0-1), or {@code undefined}.
         *
         * @zh 燃料燃烧进度（0-1）；无则为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getBurnProgress() {
            return floatState("burnProgress");
        }

        /**
         * The smelting arrow progress (0-1), or {@code undefined}.
         *
         * @zh 冶炼箭头进度（0-1）；无则为 undefined。
         */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Double getLitProgress() {
            return floatState("litProgress");
        }

        /** Reads one boolean from the container state channel. */
        private Boolean boolState(String key) {
            Object value = inventoryPort().containerState().get(key);
            return value instanceof Boolean b ? b : null;
        }

        /** Reads one float from the container state channel. */
        private Double floatState(String key) {
            Object value = inventoryPort().containerState().get(key);
            return value instanceof Number n ? n.doubleValue() : null;
        }
    }

    /**
     * A chest container screen: the container rows precede the player rows.
     *
     * @zh 箱子容器界面：容器行先于玩家行。
     */
    @ApiClass(name = "ContainerInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.ChestMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class ContainerInventory extends Inventory {

        ContainerInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** The first empty container slot index, or undefined when full.
         * @zh 第一个空容器槽位号；全满为 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        public Integer firstEmpty() {
            for (int i = 0; i < size; i++) {
                if (i < size && inventoryPort().menuSlot(i).isEmpty()) {
                    return i;
                }
            }
            return null;
        }
    }

    /**
     * A crafting table screen: the 3x3 grid and the result slot.
     *
     * @zh 工作台界面：3x3 格与结果槽。
     */
    @ApiClass(name = "CraftingInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.CraftingMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class CraftingInventory extends Inventory {

        CraftingInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** Reads one menu slot as an item handle, or undefined. */
        private ItemStack menuStack(int index) {
            return inventoryPort().menuSlot(index).map(ItemStack::of).orElse(null);
        }

        /** The 3x3 crafting grid (menu slots 1-9).
         * @zh 3x3 合成格（菜单槽 1-9）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        public List<ItemStack> getGrid() {
            List<ItemStack> out = new java.util.ArrayList<>();
            for (int i = 1; i <= 9; i++) {
                out.add(menuStack(i));
            }
            return out;
        }

        /** The crafting result stack, or undefined.
         * @zh 合成产出物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getResult() {
            return menuStack(0);
        }
    }

    /**
     * An anvil screen: the two input slots, result, and the repair cost.
     *
     * @zh 铁砧界面：两输入槽、产出与修复费用。
     */
    @ApiClass(name = "AnvilInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.AnvilMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class AnvilInventory extends Inventory {

        AnvilInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** Reads one menu slot as an item handle, or undefined. */
        private ItemStack menuStack(int index) {
            return inventoryPort().menuSlot(index).map(ItemStack::of).orElse(null);
        }

        /** The left (first) input stack, or undefined.
         * @zh 左侧输入物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getFirstInput() {
            return menuStack(0);
        }

        /** The right (second) input stack, or undefined.
         * @zh 右侧输入物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getSecondInput() {
            return menuStack(1);
        }

        /** The combined result stack, or undefined.
         * @zh 合成结果物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getResult() {
            return menuStack(2);
        }

        /** The repair/enchant level cost charged by the open anvil.
         * @zh 打开的铁砧收取的修复/附魔等级费用。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getCost() {
            Object value = inventoryPort().containerState().get("cost");
            return value instanceof Integer i ? i : null;
        }
    }

    /**
     * An enchanting table screen: the item, lapis, and the three cost rows.
     *
     * @zh 附魔台界面：物品、青金石与三档费用。
     */
    @ApiClass(name = "EnchantInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.EnchantmentMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class EnchantInventory extends Inventory {

        EnchantInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** The item being enchanted, or undefined.
         * @zh 待附魔物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getItem() {
            return inventoryPort().menuSlot(0).map(ItemStack::of).orElse(null);
        }

        /** The lapis stack, or undefined.
         * @zh 青金石；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getLapis() {
            return inventoryPort().menuSlot(1).map(ItemStack::of).orElse(null);
        }

        /** The three enchantment level costs shown on the table.
         * @zh 附魔台展示的三档等级费用。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public int[] getCosts() {
            Object value = inventoryPort().containerState().get("costs");
            return value instanceof int[] array ? array.clone() : new int[0];
        }

        /** The lapis count the table reports.
         * @zh 附魔台报告的青金石数量。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getGoldCount() {
            Object value = inventoryPort().containerState().get("goldCount");
            return value instanceof Integer i ? i : null;
        }
    }

    /**
     * A grindstone screen: the two input slots and the disenchanted result.
     *
     * @zh 砂轮界面：两输入槽与去魔结果。
     */
    @ApiClass(name = "GrindstoneInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.GrindstoneMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class GrindstoneInventory extends Inventory {

        GrindstoneInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** Reads one menu slot as an item handle, or undefined. */
        private ItemStack menuStack(int index) {
            return inventoryPort().menuSlot(index).map(ItemStack::of).orElse(null);
        }

        /** The top input stack, or undefined.
         * @zh 上侧输入物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getTopInput() {
            return menuStack(0);
        }

        /** The bottom input stack, or undefined.
         * @zh 下侧输入物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getBottomInput() {
            return menuStack(1);
        }

        /** The disenchanted result stack, or undefined.
         * @zh 去魔结果物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getResult() {
            return menuStack(2);
        }
    }

    /**
     * A stonecutter screen: the input block and the selected recipe output.
     *
     * @zh 切石机界面：输入方块与所选配方产出。
     */
    @ApiClass(name = "StonecutterInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.StonecutterMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class StonecutterInventory extends Inventory {

        StonecutterInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** The input stack, or undefined.
         * @zh 输入物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getInput() {
            return inventoryPort().menuSlot(0).map(ItemStack::of).orElse(null);
        }

        /** The selected recipe index, or undefined without input.
         * @zh 所选配方序号；无输入为 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getSelectedRecipeIndex() {
            Object value = inventoryPort().containerState().get("selectedRecipeIndex");
            return value instanceof Integer i ? i : null;
        }

        /** How many recipes the visible set offers.
         * @zh 可见配方数量。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getRecipeCount() {
            Object value = inventoryPort().containerState().get("recipeCount");
            return value instanceof Integer i ? i : null;
        }
    }

    /**
     * A smithing table screen: template, base, addition, and result.
     *
     * @zh 锻造台界面：模板、基材、添加材料与产出。
     */
    @ApiClass(name = "SmithingInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.SmithingMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class SmithingInventory extends Inventory {

        SmithingInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** Reads one menu slot as an item handle, or undefined. */
        private ItemStack menuStack(int index) {
            return inventoryPort().menuSlot(index).map(ItemStack::of).orElse(null);
        }

        /** The smithing template stack, or undefined.
         * @zh 锻造模板物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getTemplate() {
            return menuStack(0);
        }

        /** The base stack, or undefined.
         * @zh 基材物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getBase() {
            return menuStack(1);
        }

        /** The addition stack, or undefined.
         * @zh 添加材料物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getAddition() {
            return menuStack(2);
        }

        /** The smithed result stack, or undefined.
         * @zh 锻造产出物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getResult() {
            return menuStack(3);
        }
    }

    /**
     * A cartography table screen: the map, the material, and the result.
     *
     * @zh 制图台界面：地图、材料与产出。
     */
    @ApiClass(name = "CartographyInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.CartographyTableMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class CartographyInventory extends Inventory {

        CartographyInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** Reads one menu slot as an item handle, or undefined. */
        private ItemStack menuStack(int index) {
            return inventoryPort().menuSlot(index).map(ItemStack::of).orElse(null);
        }

        /** The map stack, or undefined.
         * @zh 地图物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getMap() {
            return menuStack(0);
        }

        /** The material stack, or undefined.
         * @zh 材料物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getMaterial() {
            return menuStack(1);
        }

        /** The cartography result stack, or undefined.
         * @zh 制图产出物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getResult() {
            return menuStack(2);
        }
    }

    /**
     * A beacon screen: the payment slot, effect selection, and power level.
     *
     * @zh 信标界面：付费槽、效果选择与能级。
     */
    @ApiClass(name = "BeaconInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.BeaconMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class BeaconInventory extends Inventory {

        BeaconInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** The payment stack, or undefined.
         * @zh 付费物品；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getPayment() {
            return inventoryPort().menuSlot(0).map(ItemStack::of).orElse(null);
        }

        /** The beacon power level (1-4), or undefined.
         * @zh 信标能级（1-4）；未加载为 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getLevels() {
            Object value = inventoryPort().containerState().get("levels");
            return value instanceof Integer i ? i : null;
        }

        /** The primary effect id, or undefined.
         * @zh 主效果标识；未选择为 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getPrimaryEffect() {
            Object value = inventoryPort().containerState().get("primaryEffect");
            return value instanceof String v ? v : null;
        }

        /** The secondary effect id, or undefined.
         * @zh 副效果标识；未选择为 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getSecondaryEffect() {
            Object value = inventoryPort().containerState().get("secondaryEffect");
            return value instanceof String v ? v : null;
        }
    }

    /**
     * A brewing stand screen: the three bottles, ingredient, fuel, and the
     * live brew progress.
     *
     * @zh 酿造台界面：三药瓶、原料、燃料与实时酿造进度。
     */
    @ApiClass(name = "BrewingStandInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.BrewingStandMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class BrewingStandInventory extends Inventory {

        BrewingStandInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** Reads one menu slot as an item handle, or undefined. */
        private ItemStack menuStack(int index) {
            return inventoryPort().menuSlot(index).map(ItemStack::of).orElse(null);
        }

        /** The three bottle stacks (slots 0-2).
         * @zh 三个药瓶物品（槽 0-2）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        public List<ItemStack> getBottles() {
            List<ItemStack> out = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                out.add(menuStack(i));
            }
            return out;
        }

        /** The ingredient stack (slot 3), or undefined.
         * @zh 原料物品（槽 3）；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getIngredient() {
            return menuStack(3);
        }

        /** The blaze fuel stack (slot 4), or undefined.
         * @zh 烈焰燃料物品（槽 4）；为空时 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getFuel() {
            return menuStack(4);
        }

        /** The remaining fuel uses.
         * @zh 剩余燃料次数。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getFuelUses() {
            Object value = inventoryPort().containerState().get("fuel");
            return value instanceof Integer i ? i : null;
        }

        /** The total fuel uses a full blaze rod charges.
         * @zh 满燃料棒提供的总次数。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getTotalFuelUses() {
            Object value = inventoryPort().containerState().get("totalFuel");
            return value instanceof Integer i ? i : null;
        }

        /** The remaining brewing ticks.
         * @zh 剩余酿造 tick。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getBrewingTicks() {
            Object value = inventoryPort().containerState().get("brewingTicks");
            return value instanceof Integer i ? i : null;
        }

        /** The total brewing ticks per batch.
         * @zh 每批酿造总 tick。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getTotalBrewingTicks() {
            Object value = inventoryPort().containerState().get("totalBrewingTicks");
            return value instanceof Integer i ? i : null;
        }
    }

    /**
     * A villager trade screen: trader level and experience progress. Trade
     * offers ride the trade-offer data batch.
     *
     * @zh 村民交易界面：交易等级与经验进度。交易报价随交易数据批落地。
     */
    @ApiClass(name = "VillagerInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.MerchantMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class VillagerInventory extends Inventory {

        VillagerInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** The trader's current trade level (1-5).
         * @zh 交易等级（1-5）。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getTraderLevel() {
            Object value = inventoryPort().containerState().get("traderLevel");
            return value instanceof Integer i ? i : null;
        }

        /** The trader's accumulated experience.
         * @zh 交易者累计经验。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getTraderXp() {
            Object value = inventoryPort().containerState().get("traderXp");
            return value instanceof Integer i ? i : null;
        }

        /** The experience needed for the next trade level.
         * @zh 升到下一交易等级所需经验。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getFutureTraderXp() {
            Object value = inventoryPort().containerState().get("futureTraderXp");
            return value instanceof Integer i ? i : null;
        }

        /** The trade offers currently shown.
         * @zh 当前显示的交易报价。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public List<TradeOffer> getTrades() {
            Object value = inventoryPort().containerState().get("trades");
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            List<TradeOffer> trades = new java.util.ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> data) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) data;
                    trades.add(TradeOffer.of(typed));
                }
            }
            return trades;
        }
    }

    /**
     * One trade offer: its two costs, the result stack, and the use count.
     *
     * @zh 一条交易报价：两项成本、结果物品堆与使用计数。
     */
    @ApiClass(name = "TradeOffer", kind = ApiClassKind.DATA,
            wraps = "net.minecraft.world.item.trading.MerchantOffer")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class TradeOffer {

        final ItemStack baseCostA;
        final ItemStack costA;
        final ItemStack costB;
        final ItemStack result;
        final int uses;
        final int maxUses;
        final int demand;

        TradeOffer(ItemStack baseCostA, ItemStack costA, ItemStack costB,
                ItemStack result, int uses, int maxUses, int demand) {
            this.baseCostA = baseCostA;
            this.costA = costA;
            this.costB = costB;
            this.result = result;
            this.uses = uses;
            this.maxUses = maxUses;
            this.demand = demand;
        }

        /** Rebuilds one trade offer from its neutral port snapshot.
         * @zh 由中立端口快照重建一条交易报价。 */
        static TradeOffer of(Map<String, Object> data) {
            return new TradeOffer(
                    itemOrMissing(data.get("baseCostA")),
                    itemOrMissing(data.get("costA")),
                    itemOrMissing(data.get("costB")),
                    itemOrMissing(data.get("result")),
                    data.get("uses") instanceof Integer uses ? uses : 0,
                    data.get("maxUses") instanceof Integer max ? max : 0,
                    data.get("demand") instanceof Integer demand ? demand : 0);
        }

        private static ItemStack itemOrMissing(Object value) {
            return value instanceof ink.tuanzi.neomacros.port.model.ItemStackData data
                    ? ItemStack.of(data) : null;
        }

        /** The fixed first cost before price modifiers.
         * @zh 价格修饰前的固定第一成本。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getBaseCostA() {
            return baseCostA;
        }

        /** The live first cost after demand and reputation adjustments.
         * @zh 经需求与声望调整后的实时第一成本。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getCostA() {
            return costA;
        }

        /** The optional second cost; {@code undefined} when absent.
         * @zh 可选第二成本；缺省为 undefined。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getCostB() {
            return costB;
        }

        /** The stack the trader sells.
         * @zh 交易者售出的物品堆。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getResult() {
            return result;
        }

        /** How many times this offer has been traded.
         * @zh 该报价已被交易的次数。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getUses() {
            return uses;
        }

        /** How many trades this offer allows before locking.
         * @zh 该报价锁定前允许的交易次数。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getMaxUses() {
            return maxUses;
        }

        /** The current demand bonus affecting the price.
         * @zh 影响价格的当前需求加成。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getDemand() {
            return demand;
        }
    }

    /**
     * A mount inventory screen: the saddle and body armor slots over the
     * ordinary slot face.
     *
     * @zh 坐骑物品栏界面：在普通槽位面之上的鞍槽与马甲槽。
     */
    @ApiClass(name = "HorseInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.HorseInventoryMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class HorseInventory extends Inventory {

        HorseInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** The stack in the saddle slot.
         * @zh 鞍槽中的物品堆。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getSaddle() {
            return slot(0);
        }

        /** The stack in the body armor slot.
         * @zh 马甲槽中的物品堆。 */
        @ApiSince("0.2.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getArmor() {
            return slot(1);
        }
    }
    /**
     * Live inventory handle: slot reads re-query the inventory port on every
     * access; actions dispatch on the main thread and resolve once processed.
     *
     * @zh 物品栏句柄：槽位读取每次都重新查询物品栏端口；操作在主线程派发，处理完成后落定。
     */
    @ApiClass(name = "Inventory", kind = ApiClassKind.HANDLE)
    public static class Inventory implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        final String title;
        final String kind;
        final int size;

        Inventory(String title, String kind, int size) {
            this.title = title;
            this.kind = kind;
            this.size = size;
        }

        /** The inventory display title.
         * @zh 物品栏显示标题。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getTitle() {
            return title;
        }

        /** The inventory kind ({@code player}, {@code chest}, {@code crafting}, ...).
         * @zh 物品栏种类（{@code player}、{@code chest}、{@code crafting} 等）。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getKind() {
            return kind;
        }

        /** The inventory slot count.
         * @zh 物品栏槽位总数。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public int getSize() {
            return size;
        }

        /** Reads the stack at a slot, or undefined when out of range or empty.
         * @zh 读取指定槽位的物品；越界或空槽为 undefined。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public ItemStack slot(int index) {
            return inventoryPort().slot(index)
                    .map(ItemStack::of)
                    .orElse(null);
        }

        /** All non-empty stacks with their slot indices.
         * @zh 全部非空物品及其槽位号。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiType("Array<SlotItem>")
        public List<Map<String, Object>> getItems() {
            return inventoryPort().allItems().get().stream()
                    .map(entry -> {
                        Map<String, Object> view = ItemStack.of(entry.stack()).toJSON();
                        view.put("slot", entry.slot());
                        return view;
                    })
                    .toList();
        }

        /** Slot indices holding the given item id, in order.
         * @zh 持有指定物品 id 的槽位号列表（按序）。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        public List<Integer> findAll(String itemId) {
            String wanted = ItemStack.namespaced(itemId);
            List<Integer> hits = new java.util.ArrayList<>();
            for (var entry : inventoryPort().allItems().get()) {
                if (entry.stack().id().equals(wanted)) {
                    hits.add(entry.slot());
                }
            }
            return hits;
        }

        /** First slot holding the given item id, or undefined.
         * @zh 第一个持有指定物品 id 的槽位；无则 undefined。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public Integer findId(String itemId) {
            List<Integer> hits = findAll(itemId);
            return hits.isEmpty() ? null : hits.get(0);
        }

        /** Whether the inventory holds the given item id (namespace optional).
         * @zh 物品栏中是否存在指定 id 的物品（命名空间可省略）。 */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean contains(String itemId) {
            return !findAll(itemId).isEmpty();
        }

        /** Counts occurrences of one item id.
         * @zh 统计某物品 id 的数量。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public int countOf(String item) {
            return inventoryPort().countOf(item);
        }

        /** Clicks a slot (button 0 = left, 1 = right; shift = quick move).
         * @zh 点击槽位（button 0 = 左键，1 = 右键；shift = 快速移动）。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> click(int slot, int button, boolean shift) {
            return inventoryPort().click(slot, button, shift);
        }

        /** Quick-moves a slot to the other inventory.
         * @zh 将槽位物品快速移动到另一侧物品栏。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> quickMove(int slot) {
            return inventoryPort().quickMove(slot);
        }

        /** Drops the stack from a slot.
         * @zh 丢弃槽位中的物品（all = 全部）。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> drop(int slot, boolean all) {
            return inventoryPort().drop(slot, all);
        }

        /** Swaps a container slot with a hotbar slot (0..8).
         * @zh 将容器槽位与快捷栏槽位交换（0..8）。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> swapHotbar(int slot, int hotbarIndex) {
            return inventoryPort().swapHotbar(slot, hotbarIndex);
        }

        /** Closes the inventory screen.
         * @zh 关闭物品栏界面。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> close() {
            return inventoryPort().close();
        }
    }

    /**
     * One action button of the open dialog: the flattened click index, the
     * visible label, and the stable id scripts match against (the id equals
     * the index unless the dialog was opened with explicit ids).
     *
     * @zh 对话框的动作按钮：展平点击序号、可见标签与脚本比对的稳定 id（除非弹窗时显式给 id，id 即序号）。
     */
    @ApiClass(name = "DialogAction", kind = ApiClassKind.DATA)
    @ApiSince("0.3.0")
    @ApiMC(">=1.21.6")
    @ApiLoader("*")
    public record DialogAction(int index, String label, String id) {
    }

    /**
     * One declared input control of the open dialog: the form key, the
     * control type ({@code text}, {@code boolean}, {@code number},
     * {@code option}), and the declared initial value.
     *
     * @zh 对话框声明的输入控件：表单键、控件类型（{@code text}/{@code boolean}/{@code number}/{@code option}）与声明的初始值。
     */
    @ApiClass(name = "DialogInput", kind = ApiClassKind.DATA)
    @ApiSince("0.3.0")
    @ApiMC(">=1.21.6")
    @ApiLoader("*")
    public record DialogInput(String key, String type, String value) {
    }

    /**
     * Live dialog handle: reads resolve on every access, actions dispatch
     * on the main thread.
     *
     * @zh 对话框句柄：读取实时解析，动作在主线程派发。
     */
    @ApiClass(name = "DialogHandle", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.server.dialog.Dialog")
    public static final class DialogHandle {

        private final String kind;
        private final String title;
        private final boolean canCloseWithEscape;
        private final List<String> body;
        private final List<DialogAction> actions;
        private final List<DialogInput> inputs;

        DialogHandle(String kind, String title, boolean canCloseWithEscape,
                     List<String> body, List<DialogAction> actions,
                     List<DialogInput> inputs) {
            this.kind = kind;
            this.title = title;
            this.canCloseWithEscape = canCloseWithEscape;
            this.body = body;
            this.actions = actions;
            this.inputs = inputs;
        }

        /** Builds the handle for one dialog snapshot. */
        static DialogHandle of(ink.tuanzi.neomacros.port.DialogPort.DialogInfo info) {
            List<DialogAction> actions = new java.util.ArrayList<>();
            for (ink.tuanzi.neomacros.port.DialogPort.DialogActionInfo action : info.actions()) {
                actions.add(new DialogAction(action.index(), action.label(), action.id()));
            }
            List<DialogInput> inputs = new java.util.ArrayList<>();
            for (ink.tuanzi.neomacros.port.DialogPort.DialogInputInfo input : info.inputs()) {
                inputs.add(new DialogInput(input.key(), input.type(), input.value()));
            }
            return new DialogHandle(info.kind(), info.title(), info.canCloseWithEscape(),
                    List.copyOf(info.body()), List.copyOf(actions), List.copyOf(inputs));
        }

        /** The dialog kind ({@code notice}, {@code confirmation}, {@code multi_action}, ...).
         * @zh 对话框类别（{@code notice}、{@code confirmation}、{@code multi_action} 等）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public String getKind() {
            return kind;
        }

        /** The dialog title.
         * @zh 对话框标题。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public String getTitle() {
            return title;
        }

        /** Whether the dialog can be closed with Esc.
         * @zh 是否可按 Esc 关闭。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public boolean getCanCloseWithEscape() {
            return canCloseWithEscape;
        }

        /** The dialog body texts, in order.
         * @zh 对话框正文文本（按序）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public List<String> getBody() {
            return body;
        }

        /** The action buttons, in flattened click order.
         * @zh 动作按钮（按展平点击顺序）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public List<DialogAction> getActions() {
            return actions;
        }

        /** The declared input controls.
         * @zh 声明的输入控件。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public List<DialogInput> getInputs() {
            return inputs;
        }

        /**
         * Clicks one action button by its flattened index (script-shown
         * dialogs resolve their show() future with the action id).
         *
         * @zh 按展平序号点击动作按钮（脚本弹窗会以该动作 id 落定 showDialog 的 future）。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        public CompletableFuture<Void> clickAction(int index) {
            return dialogPort().clickAction(index);
        }

        /** Closes the dialog (Esc path; script dialogs resolve showDialog with null).
         * @zh 关闭对话框（Esc 路径；脚本弹窗以 null 落定 showDialog）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        public CompletableFuture<Void> close() {
            return dialogPort().close();
        }
    }

    /**
     * Base handle for any open screen: the MC class name, the kind label,
     * the title, and Esc-style close.
     *
     * @zh 任意打开屏幕的基础句柄：MC 类名、类别标签、标题与 Esc 式关闭。
     */
    @ApiClass(name = "Screen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.Screen")
    public static class Screen {

        final String mcClass;
        final String kind;
        final String title;

        Screen(String mcClass, String kind, String title) {
            this.mcClass = mcClass;
            this.kind = kind;
            this.title = title;
        }

        /** The Minecraft screen class name (e.g. {@code TitleScreen}).
         * @zh Minecraft 屏幕类名（如 {@code TitleScreen}）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getName() {
            return mcClass;
        }

        /** The screen kind label (e.g. {@code title}, {@code chest}).
         * @zh 屏幕类别标签（如 {@code title}、{@code chest}）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getKind() {
            return kind;
        }

        /** The screen title.
         * @zh 屏幕标题。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getTitle() {
            return title;
        }

        /** Closes the screen (the same path as pressing Esc).
         * @zh 关闭屏幕（与按 Esc 同路）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public void close() {
            lifecyclePort().closeScreen();
        }
    }

    /** The player inventory screen (the E-key backpack).
     * @zh 玩家背包界面（E 键打开的物品栏）。 */
    @ApiClass(name = "InventoryScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.InventoryScreen")
    public static final class InventoryScreen extends Screen {

        InventoryScreen(String mcClass, String title) {
            super(mcClass, "inventory", title);
        }

        /** The player inventory handle shown by this screen.
         * @zh 此界面展示的玩家背包句柄。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public PlayerInventory getContainer() {
            return new PlayerInventory();
        }
    }

    /** Any container screen (chest, furnace, villager, mount, ...).
     * @zh 任意容器界面（箱子、熔炉、村民、坐骑等）。 */
    @ApiClass(name = "ContainerScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen")
    public static final class ContainerScreen extends Screen {

        ContainerScreen(String mcClass, String title) {
            super(mcClass, "container", title);
        }

        /** The open container handle, or undefined outside a world.
         * @zh 打开的容器句柄；不在世界内为 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Inventory getContainer() {
            return inventoryPort().openedContainer()
                    .map(info -> openByKind(info.title(), info.kind(), info.size()))
                    .orElse(null);
        }
    }

    /** The chat screen with the text currently being typed.
     * @zh 聊天界面，可读当前正在输入的文本。 */
    @ApiClass(name = "ChatScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.ChatScreen")
    public static final class ChatScreen extends Screen {

        ChatScreen(String mcClass, String title) {
            super(mcClass, "chat", title);
        }

        /** The text currently typed into the chat input, or undefined.
         * @zh 聊天输入框中的文本；无输入时为 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getText() {
            return lifecyclePort().chatText().orElse(null);
        }
    }

    /** The sign editor with the front-face text.
     * @zh 告示牌编辑界面，可读正面文本。 */
    @ApiClass(name = "SignScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen")
    public static final class SignScreen extends Screen {

        SignScreen(String mcClass, String title) {
            super(mcClass, "sign", title);
        }

        /** The front-face sign text, line-joined.
         * @zh 告示牌正面文本（按行拼接）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getText() {
            return lifecyclePort().signText().orElse(null);
        }
    }

    /** The book editor with the page text under edit.
     * @zh 成书编辑界面，可读正在编辑的页文本。 */
    @ApiClass(name = "BookEditScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.BookEditScreen")
    public static final class BookEditScreen extends Screen {

        BookEditScreen(String mcClass, String title) {
            super(mcClass, "bookedit", title);
        }

        /** The text of the page under edit, or undefined.
         * @zh 正在编辑的页文本；无则 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getText() {
            return lifecyclePort().bookEditPage().orElse(null);
        }
    }

    /** The dialog screen (server dialogs and script-shown dialogs).
     * @zh 对话框界面（服务端对话框与脚本弹窗）。 */
    @ApiClass(name = "DialogScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.dialog.DialogScreen")
    public static final class DialogScreen extends Screen {

        DialogScreen(String mcClass, String title) {
            super(mcClass, "dialog", title);
        }

        /** The open dialog handle, or undefined when none.
         * @zh 打开的对话框句柄；无则 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC(">=1.21.6")
        @ApiReadOnly
        public DialogHandle getDialog() {
            return dialogPort().currentDialog().map(DialogHandle::of).orElse(null);
        }
    }

    /** The title screen.
     * @zh 主菜单界面。 */
    @ApiClass(name = "TitleScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.TitleScreen")
    public static final class TitleScreen extends Screen {

        TitleScreen(String mcClass, String title) {
            super(mcClass, "title", title);
        }
    }

    /** The pause screen.
     * @zh 暂停菜单界面。 */
    @ApiClass(name = "PauseScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.PauseScreen")
    public static final class PauseScreen extends Screen {

        PauseScreen(String mcClass, String title) {
            super(mcClass, "pause", title);
        }
    }

    /** The death screen.
     * @zh 死亡界面。 */
    @ApiClass(name = "DeathScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.DeathScreen")
    public static final class DeathScreen extends Screen {

        DeathScreen(String mcClass, String title) {
            super(mcClass, "death", title);
        }
    }

    /** The creative inventory screen.
     * @zh 创造模式物品栏界面。 */
    @ApiClass(name = "CreativeScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen")
    public static final class CreativeScreen extends Screen {

        CreativeScreen(String mcClass, String title) {
            super(mcClass, "creative", title);
        }
    }

    /** A loading or progress screen.
     * @zh 加载或进度界面。 */
    @ApiClass(name = "ProgressScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.ProgressScreen")
    public static final class ProgressScreen extends Screen {

        ProgressScreen(String mcClass, String title) {
            super(mcClass, "progress", title);
        }
    }

    /** The disconnect notice screen.
     * @zh 断开连接提示界面。 */
    @ApiClass(name = "DisconnectedScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.DisconnectedScreen")
    public static final class DisconnectedScreen extends Screen {

        DisconnectedScreen(String mcClass, String title) {
            super(mcClass, "disconnected", title);
        }
    }

    /** The end-poem (credits) screen.
     * @zh 终局诗（制作名单）界面。 */
    @ApiClass(name = "WinScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.WinScreen")
    public static final class WinScreen extends Screen {

        WinScreen(String mcClass, String title) {
            super(mcClass, "wingame", title);
        }
    }

    /** A yes/no confirmation screen.
     * @zh 是/否确认界面。 */
    @ApiClass(name = "ConfirmScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.ConfirmScreen")
    public static final class ConfirmScreen extends Screen {

        ConfirmScreen(String mcClass, String title) {
            super(mcClass, "confirm", title);
        }
    }

    /** The book view screen.
     * @zh 成书阅读界面。 */
    @ApiClass(name = "BookViewScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.BookViewScreen")
    public static final class BookViewScreen extends Screen {

        BookViewScreen(String mcClass, String title) {
            super(mcClass, "bookview", title);
        }
    }

    /** A command block editor screen.
     * @zh 命令方块编辑界面。 */
    @ApiClass(name = "CommandBlockScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen")
    public static final class CommandBlockScreen extends Screen {

        CommandBlockScreen(String mcClass, String title) {
            super(mcClass, "commandblock", title);
        }
    }

    /** The structure block editor screen.
     * @zh 结构方块编辑界面。 */
    @ApiClass(name = "StructureBlockScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen")
    public static final class StructureBlockScreen extends Screen {

        StructureBlockScreen(String mcClass, String title) {
            super(mcClass, "structureblock", title);
        }
    }

    /** The jigsaw block editor screen.
     * @zh 拼图方块编辑界面。 */
    @ApiClass(name = "JigsawScreen", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen")
    public static final class JigsawScreen extends Screen {

        JigsawScreen(String mcClass, String title) {
            super(mcClass, "jigsaw", title);
        }
    }

    /**
     * The local player's experience: the level, the progress into the
     * current level (0-1), and the total collected points.
     *
     * @zh 本地玩家经验：等级、当前等级内进度（0-1）与累计总点数。
     */
    @ApiClass(name = "XpInfo", kind = ApiClassKind.DATA)
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record XpInfo(int level, double progress, int total) {
    }

    /**
     * One non-empty inventory slot view: the stack's plain fields plus its
     * slot index.
     *
     * @zh 一个非空槽位视图：物品堆纯字段加槽位号。
     */
    @ApiClass(name = "SlotItem", kind = ApiClassKind.DATA)
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record SlotItem(
            String id,
            int count,
            String name,
            @ApiOptional Integer damage,
            @ApiOptional Integer maxDamage,
            @ApiOptional java.util.List<String> lore,
            @ApiOptional java.util.List<ink.tuanzi.neomacros.port.model.EnchantmentData> enchantments,
            int slot) {
    }

    /**
     * One script-shown dialog action button spec: the visible label and the
     * optional stable id (defaults to the flattened index).
     *
     * @zh 脚本弹窗动作按钮规格：可见标签与可选稳定 id（缺省为展平序号）。
     */
    @ApiClass(name = "DialogActionSpec", kind = ApiClassKind.DATA)
    @ApiSince("0.3.0")
    @ApiMC(">=1.21.6")
    @ApiLoader("*")
    public record DialogActionSpec(
            @ApiOptional String id,
            @ApiOptional String label) {
    }

    /**
     * One script-shown dialog config: the kind, the title, the body lines
     * (string or string array), the Esc behavior, the action buttons, and
     * the action column count.
     *
     * @zh 脚本弹窗配置：类别、标题、正文（字符串或字符串数组）、Esc 行为、动作按钮与动作列数。
     */
    @ApiClass(name = "DialogConfig", kind = ApiClassKind.DATA)
    @ApiSince("0.3.0")
    @ApiMC(">=1.21.6")
    @ApiLoader("*")
    public record DialogConfig(
            @ApiOptional String type,
            @ApiOptional String title,
            @ApiOptional @ApiType("string | Array<string>") Object body,
            @ApiOptional Boolean canCloseWithEscape,
            @ApiOptional java.util.List<DialogActionSpec> actions,
            @ApiOptional Integer columns) {
    }

    /**
     * The JSON form of one player snapshot ({@code Player.toJSON()}): the
     * entity fields plus the look direction, hunger, and air.
     *
     * @zh 玩家快照的 JSON 形式（{@code Player.toJSON()}）：实体字段加视线方向、饥饿与氧气。
     */
    @ApiClass(name = "PlayerJson", kind = ApiClassKind.DATA)
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record PlayerJson(String uuid, @ApiOptional String type, String name,
            ink.tuanzi.neomacros.core.api.entity.EntityApi.JsonPos pos,
            ink.tuanzi.neomacros.core.api.entity.EntityApi.JsonLook look,
            double health, Integer hunger, Integer air) {
    }
}
