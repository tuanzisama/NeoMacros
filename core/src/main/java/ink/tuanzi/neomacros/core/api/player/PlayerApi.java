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
import ink.tuanzi.neomacros.port.model.AdvancementData;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.RayHit;

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

    /** Reads one integer from the container state channel, or {@code null}. */
    private static Integer stateInt(String key) {
        Object value = inventoryPort().containerState().get(key);
        return value instanceof Integer i ? i : null;
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

    /** Normalizes the script hand name onto the port contract ({@code main}/{@code off}). */
    static String normalizedHand(String hand) {
        if (hand == null || "main".equals(hand)) {
            return "main";
        }
        if ("off".equals(hand)) {
            return "off";
        }
        throw new IllegalArgumentException("hand must be 'main' or 'off': " + hand);
    }

    /** Normalizes the script face name, defaulting to up; unknown faces throw. */
    static String normalizedFace(String face) {
        if (face == null || "up".equals(face)) {
            return "up";
        }
        if ("down".equals(face) || "north".equals(face) || "south".equals(face)
                || "east".equals(face) || "west".equals(face)) {
            return face;
        }
        throw new IllegalArgumentException("unknown face: " + face);
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
            case "loom" -> new LoomInventory(title, kind, size);
            case "creative" -> new CreativeInventory(title, kind, size);
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

        /** JSON string; the local player renders through the player view.
         * @zh JSON 字符串；本地玩家经玩家视图输出。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public String toJSON() {
            Optional<PlayerSnapshot> self = local();
            var view = self.map(SnapshotViews::player)
                    .orElseGet(() -> SnapshotViews.entity(current().orElse(null)));
            return view == null ? null
                    : ink.tuanzi.neomacros.core.util.JsonLite.write(view);
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
         * The client-side statistics handle: typed per-category queries over
         * the live stat counters. {@code null} without a world.
         *
         * @zh 客户端统计句柄：活统计计数器上的分类查询。无世界时为 null。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Statistics getStatistics() {
            return current().map(s -> new Statistics()).orElse(null);
        }

        /**
         * The block interaction range in blocks (survival base 4.5; the
         * creative modifier raises it to 5.0; attribute modifiers apply).
         *
         * @zh 方块交互距离（格）：生存基线 4.5；创造修饰提升到 5.0；属性修饰符生效。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public double getBlockInteractionRange() {
            return adapter().blockInteractionRange();
        }

        /**
         * The entity interaction range in blocks (survival base 3.0; the
         * creative modifier raises it to 3.5; attribute modifiers apply).
         *
         * @zh 实体交互距离（格）：生存基线 3.0；创造修饰提升到 3.5；属性修饰符生效。
         */
        @ApiSince("0.2.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public double getEntityInteractionRange() {
            return adapter().entityInteractionRange();
        }

        /**
         * The crosshair pick: the first block or entity inside the
         * interaction ranges, re-read on every access. {@code null} without
         * a world.
         *
         * @zh 准星选取：交互距离内的第一个方块或实体，每次访问实时回读。无世界时为 null。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Target getTarget() {
            return current().map(s -> new Target()).orElse(null);
        }

        /**
         * The interaction override handle: the crosshair-target, block-break
         * and held-use overrides plus the crosshair-driven use/break actions
         * that read them (JsMacros {@code Player.interactions()} parity);
         * {@code null} without a world.
         *
         * @zh 交互覆盖句柄：准星目标、方块破坏与按住使用三类覆盖，以及读取这些覆盖的准星驱动使用/破坏动作（对齐 JsMacros {@code Player.interactions()}）；无世界时为 null。
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Interactions getInteractions() {
            // Qualified: the bare name would resolve to the inherited
            // entity-lookup current(), not the local-player snapshot.
            return PlayerApi.current().map(s -> new Interactions()).orElse(null);
        }

        /**
         * The client-known advancements: tree queries plus per-advancement
         * progress. {@code null} without a server connection.
         *
         * @zh 客户端已知的进度：树查询加逐条进度。无服务器连接时为 null。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Advancements getAdvancements() {
            return adapter().advancements().isEmpty() ? null : new Advancements();
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

        /**
         * Uses (right-clicks) the entity behind the target handle with the
         * named hand ({@code main} or {@code off}; default main).
         *
         * @zh 与目标句柄背后的实体交互（右键），可用手为 {@code main}/{@code off}（默认 main）。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> interactEntity(EntityApi.Entity target,
                @ApiOptional String hand) {
            EntitySnapshot targetData = target == null ? null : target.snapshot();
            if (targetData == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("target entity is not loaded"));
            }
            return adapter().useEntity(targetData, normalizedHand(hand));
        }

        /**
         * Uses (right-clicks) the held item in the air with the named hand
         * ({@code main} or {@code off}; default main).
         *
         * @zh 在空中对手持物品执行使用（右键），可用手为 {@code main}/{@code off}（默认 main）。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> interactItem(@ApiOptional String hand) {
            return adapter().useItem(normalizedHand(hand));
        }

        /**
         * Uses (right-clicks) the block behind the position handle on the
         * named face ({@code up}/{@code down}/{@code north}/{@code south}/
         * {@code east}/{@code west}; default up) with the named hand
         * ({@code main} or {@code off}; default main). Unknown faces throw.
         *
         * @zh 与坐标句柄背后的方块在指定面上交互（右键），面可选 {@code up}/{@code down}/{@code north}/{@code south}/{@code east}/{@code west}（默认 up），手可选 {@code main}/{@code off}（默认 main）；未知面抛错。
         */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> interactBlock(
                ink.tuanzi.neomacros.core.api.types.BlockPos pos,
                @ApiOptional String face, @ApiOptional String hand) {
            var hit = new BlockInteraction("minecraft:use", new Pos3d(
                    pos.getIntX(), pos.getIntY(), pos.getIntZ()), normalizedFace(face));
            return adapter().useBlock(hit, normalizedHand(hand));
        }

        /**
         * Starts breaking the block at the position on the named face
         * (defaults to up): swings and begins the destroy path; instant-break
         * blocks complete at once, harder blocks start the mining progress
         * that the game advances while the attack key stays held. Unknown
         * faces throw.
         *
         * @zh 开始破坏坐标处方块的指定面（默认 up）：挥手并进入破坏流程；瞬挖方块立即完成，更硬的方块开始挖掘进度（需要按住攻击键推进）；未知面抛错。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> attackBlock(
                ink.tuanzi.neomacros.core.api.types.BlockPos pos,
                @ApiOptional String face) {
            var hit = new BlockInteraction("minecraft:attack", new Pos3d(
                    pos.getIntX(), pos.getIntY(), pos.getIntZ()), normalizedFace(face));
            return adapter().attackBlock(hit);
        }

        /** Releases the ongoing block breaking (the same path as releasing the attack key).
         * @zh 结束正在进行的方块破坏（与松开攻击键同路）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> stopBreaking() {
            return adapter().stopBreaking();
        }

        /** Swings the main hand (offhand = the off hand).
         * @zh 挥动手（offhand = 副手）。 */
        @ApiSince("0.1.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> swingHand(boolean offhand) {
            return adapter().swingHand(normalizedHand(offhand ? "off" : "main"));
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
     * Live statistics handle: every read re-queries the client stat
     * counters. Values are keyed by stat type and entry; only non-zero
     * entries appear in the aggregate views.
     *
     * @zh 活统计句柄：每次读取都重新查询客户端统计计数器。数值按统计类型与条目键控；聚合视图只含非零条目。
     */
    @ApiClass(name = "Statistics", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.stats.StatsCounter")
    @ApiSince("0.3.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class Statistics {

        /** Reads the live non-zero stat map ({@code statTypeId:valueId -> value}).
         * @zh 读取实时非零统计映射（{@code 类型id:条目id -> 数值}）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        @ApiType("Record<string, number>")
        public Map<String, Integer> getSnapshot() {
            return adapter().statistics();
        }

        /**
         * Every non-zero stat as a typed entry ({@code category} is the stat
         * type id, {@code key} the entry id).
         *
         * @zh 每条非零统计的类型化条目（{@code category} 为统计类型 id，{@code key} 为条目 id）。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        @ApiType("Array<StatisticsEntry>")
        public List<StatisticsEntry> getEntries() {
            List<StatisticsEntry> out = new java.util.ArrayList<>();
            for (Map.Entry<String, Integer> entry : adapter().statistics().entrySet()) {
                int split = entry.getKey().indexOf(':', entry.getKey().indexOf(':') + 1);
                if (split < 0) {
                    out.add(new StatisticsEntry(entry.getKey(), "", entry.getValue()));
                } else {
                    out.add(new StatisticsEntry(entry.getKey().substring(0, split),
                            entry.getKey().substring(split + 1), entry.getValue()));
                }
            }
            return out;
        }

        /**
         * The value of one stat: {@code category} is the stat type id
         * ({@code minecraft:mined}, {@code minecraft:custom}, ...), {@code
         * key} the entry id. Unknown entries read as 0.
         *
         * @zh 单条统计值：{@code category} 为统计类型 id（{@code minecraft:mined}、{@code minecraft:custom} 等），{@code key} 为条目 id；未知条目读作 0。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public int value(String category, String key) {
            Integer count = adapter().statistics().get(category + ":" + key);
            return count != null ? count : 0;
        }

        /** How many of the block the player has mined.
         * @zh 玩家已挖掘该方块的数量。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public int mined(String blockId) {
            return value("minecraft:mined", ItemStack.namespaced(blockId));
        }

        /** How many of the item the player has crafted.
         * @zh 玩家已合成该物品的数量。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public int crafted(String itemId) {
            return value("minecraft:crafted", ItemStack.namespaced(itemId));
        }

        /** How many times the player has used the item.
         * @zh 玩家已使用该物品的次数。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public int used(String itemId) {
            return value("minecraft:used", ItemStack.namespaced(itemId));
        }

        /** How many of the item the player has broken (depleted).
         * @zh 玩家已用坏该物品的数量。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public int broken(String itemId) {
            return value("minecraft:broken", ItemStack.namespaced(itemId));
        }

        /** How many of the item the player has picked up.
         * @zh 玩家已拾取该物品的数量。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public int pickedUp(String itemId) {
            return value("minecraft:picked_up", ItemStack.namespaced(itemId));
        }

        /** How many of the item the player has dropped.
         * @zh 玩家已丢弃该物品的数量。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public int dropped(String itemId) {
            return value("minecraft:dropped", ItemStack.namespaced(itemId));
        }

        /** How many of the entity the player has killed.
         * @zh 玩家已击杀该实体的数量。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public int killed(String entityId) {
            return value("minecraft:killed", ItemStack.namespaced(entityId));
        }

        /** How many times the entity kind has killed the player.
         * @zh 该实体击杀玩家的次数。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public int killedBy(String entityId) {
            return value("minecraft:killed_by", ItemStack.namespaced(entityId));
        }

        /**
         * The custom stat value ({@code minecraft:play_time},
         * {@code minecraft:walk_one_cm}, ...).
         *
         * @zh 自定义统计值（{@code minecraft:play_time}、{@code minecraft:walk_one_cm} 等）。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public int custom(String statId) {
            return value("minecraft:custom", ItemStack.namespaced(statId));
        }
    }

    /**
     * Live advancements handle: tree queries plus per-advancement progress,
     * re-read on every access. Requires a server connection (singleplayer
     * integrated server included).
     *
     * @zh 活进度句柄：树查询加逐条进度，每次访问实时回读。需要服务器连接（含单机内置服务端）。
     */
    @ApiClass(name = "Advancements", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.multiplayer.ClientAdvancements")
    @ApiSince("0.3.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class Advancements {

        /** The ids of every client-known advancement, in registry order.
         * @zh 客户端已知的全部进度 id（按注册序）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public List<String> getIds() {
            List<String> out = new java.util.ArrayList<>();
            for (AdvancementData data : adapter().advancements()) {
                out.add(data.id());
            }
            return out;
        }

        /** The ids of root advancements (those without a parent).
         * @zh 根进度 id（无父节点的进度）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public List<String> getRoots() {
            List<String> out = new java.util.ArrayList<>();
            for (AdvancementData data : adapter().advancements()) {
                if (data.parentId() == null) {
                    out.add(data.id());
                }
            }
            return out;
        }

        /** The ids of fully completed advancements.
         * @zh 已全部完成的进度 id。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public List<String> getCompleted() {
            List<String> out = new java.util.ArrayList<>();
            for (AdvancementData data : adapter().advancements()) {
                var progress = adapter().advancementProgress(data.id()).orElse(null);
                if (progress != null && progress.done()) {
                    out.add(data.id());
                }
            }
            return out;
        }

        /** The ids of started but incomplete advancements.
         * @zh 已开始但未完成的进度 id。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public List<String> getStarted() {
            List<String> out = new java.util.ArrayList<>();
            for (AdvancementData data : adapter().advancements()) {
                var progress = adapter().advancementProgress(data.id()).orElse(null);
                if (progress != null && !progress.done() && !progress.obtained().isEmpty()) {
                    out.add(data.id());
                }
            }
            return out;
        }

        /**
         * The children (direct sub-advancements) of one advancement id, in
         * registry order; unknown ids return an empty list.
         *
         * @zh 指定进度的直接子进度 id（按注册序）；未知 id 返回空列表。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public List<String> children(String id) {
            List<String> out = new java.util.ArrayList<>();
            for (AdvancementData data : adapter().advancements()) {
                if (id.equals(data.parentId())) {
                    out.add(data.id());
                }
            }
            return out;
        }

        /**
         * The live advancement handle for one id, or {@code undefined} when
         * unknown.
         *
         * @zh 指定 id 的活进度句柄；未知时为 undefined。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public Advancement get(String id) {
            for (AdvancementData data : adapter().advancements()) {
                if (data.id().equals(id)) {
                    return new Advancement(id);
                }
            }
            return null;
        }
    }

    /**
     * Live advancement handle: the display data and progress of one
     * advancement, re-read on every access.
     *
     * @zh 活进度条目句柄：单个进度的展示数据与进度，每次访问实时回读。
     */
    @ApiClass(name = "Advancement", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.advancements.AdvancementHolder")
    @ApiSince("0.3.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class Advancement {

        private final String id;

        Advancement(String id) {
            this.id = id;
        }

        /** Reads the live display data, or null when the id is no longer known. */
        private AdvancementData data() {
            for (AdvancementData data : adapter().advancements()) {
                if (data.id().equals(id)) {
                    return data;
                }
            }
            return null;
        }

        /** The namespaced advancement id.
         * @zh 进度的命名空间 id。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getId() {
            return id;
        }

        /** The parent advancement id, or {@code undefined} on roots.
         * @zh 父进度 id；根进度为 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getParentId() {
            AdvancementData data = data();
            return data != null ? data.parentId() : null;
        }

        /** The display title plain text.
         * @zh 展示标题纯文本。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getTitle() {
            AdvancementData data = data();
            return data != null ? data.title() : null;
        }

        /** The display description plain text.
         * @zh 展示描述纯文本。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getDescription() {
            AdvancementData data = data();
            return data != null ? data.description() : null;
        }

        /**
         * The display frame ({@code task}, {@code goal}, {@code challenge}).
         *
         * @zh 展示框样式（{@code task}/{@code goal}/{@code challenge}）。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getFrame() {
            AdvancementData data = data();
            return data != null ? data.frame() : null;
        }

        /** Whether the advancement is hidden until completed.
         * @zh 进度是否在完成前隐藏。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public boolean isHidden() {
            AdvancementData data = data();
            return data != null && data.hidden();
        }

        /** The live progress, or {@code undefined} without server progress data.
         * @zh 实时进度；无服务器进度数据时为 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public AdvancementProgress getProgress() {
            return adapter().advancementProgress(id)
                    .map(p -> new AdvancementProgress(p.done(), p.obtained(), p.remaining()))
                    .orElse(null);
        }
    }

    /**
     * Live crosshair pick handle: what the player is currently aiming at,
     * capped by the block and entity interaction ranges; re-read on every
     * access.
     *
     * @zh 活准星选取句柄：玩家当前指向的目标，上限为方块与实体交互距离；每次访问实时回读。
     */
    @ApiClass(name = "Target", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.phys.HitResult")
    @ApiSince("0.3.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class Target
            implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        /** Reads the live crosshair pick, or empty on a miss or without a world. */
        private Optional<RayHit> pick() {
            return adapter().target();
        }

        /** The aimed block handle, or {@code undefined} on entity hits or misses.
         * @zh 指向的方块句柄；指向实体或未命中时为 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public ink.tuanzi.neomacros.core.api.types.Block getBlock() {
            return pick()
                    .filter(hit -> hit.block() != null)
                    .map(hit -> new ink.tuanzi.neomacros.core.api.types.Block(
                            new ink.tuanzi.neomacros.core.api.types.BlockPos(
                                    hit.block().pos().x(), hit.block().pos().y(),
                                    hit.block().pos().z())))
                    .orElse(null);
        }

        /** The aimed entity handle, or {@code undefined} on block hits or misses.
         * @zh 指向的实体句柄；指向方块或未命中时为 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public EntityApi.Entity getEntity() {
            return pick()
                    .filter(hit -> hit.entity() != null)
                    .map(hit -> new EntityApi.Entity(hit.entity().uuid()))
                    .orElse(null);
        }

        /**
         * The aimed block face name ({@code up}, {@code north}, ...), or
         * {@code undefined} on entity hits or misses.
         *
         * @zh 指向的方块面名（{@code up}、{@code north} 等）；指向实体或未命中时为 undefined。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public String getFace() {
            return pick()
                    .map(hit -> hit.block() != null ? hit.block().face() : null)
                    .orElse(null);
        }

        /** The exact hit position, or {@code undefined} on a miss.
         * @zh 精确命中位置；未命中时为 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public ink.tuanzi.neomacros.core.api.types.Pos3D getPos() {
            return pick()
                    .map(hit -> new ink.tuanzi.neomacros.core.api.types.Pos3D(
                            hit.pos().x(), hit.pos().y(), hit.pos().z()))
                    .orElse(null);
        }

        /** The eye-to-hit distance in blocks ({@code 0} on a miss).
         * @zh 眼睛到命中点的距离（格）；未命中时为 0。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public double getDistance() {
            return pick().map(RayHit::distance).orElse(0.0);
        }

        /** Whether the crosshair is on anything at all.
         * @zh 准星是否指向了任何目标。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public boolean isHit() {
            return pick()
                    .map(hit -> hit.block() != null || hit.entity() != null)
                    .orElse(false);
        }
    }

    /**
     * Interaction override handle: the client-side crosshair-target override,
     * the block-break outcome override and the held-use override that
     * JsMacros groups behind {@code Player.getInteractionManager()} and
     * {@code InteractionProxy}.
     *
     * <p><b>Override semantics.</b> These three overrides are shared
     * client-side state for the whole game, not per-player state, and they
     * never grant server reach:</p>
     * <ul>
     *   <li><b>Target</b> — once stored, {@code player.target} (and every
     *       crosshair-driven action on this handle, {@code interact()} and
     *       {@code breakBlock()}) reads the override instead of the real
     *       pick until {@link #clearTarget()} or a failing check removes it.
     *       The vanilla crosshair itself is left alone, so vanilla code
     *       keeps acting on what the player actually aims at.</li>
     *   <li><b>Break</b> — arming tracks one block position and settles the
     *       pending {@link #breakBlock} promise with a
     *       {@link BreakBlockResult}; a reason code reports why it ended.
     *       Client-side outcomes only, never server confirmation.</li>
     *   <li><b>Interact</b> — while held, the client re-issues the use
     *       action every few ticks until stopped, the use key is pressed or
     *       the world unloads.</li>
     * </ul>
     *
     * <p>{@link #reset()} restores all three to their defaults, which is what
     * the game itself does on world join and disconnect.</p>
     *
     * @zh 交互覆盖句柄：JsMacros 归在 {@code Player.getInteractionManager()}
     * 与 {@code InteractionProxy} 下的客户端准星目标覆盖、方块破坏结果覆盖与
     * 按住使用覆盖。三者都是全客户端共享状态（非按玩家），且绝不授予服务端
     * 触达：目标覆盖只改写 {@code player.target} 与本句柄上准星驱动动作的读取
     * 结果，原版准星不受影响；破坏覆盖只结算客户端结果，不代表服务端确认；
     * {@link #reset()} 恢复三者默认值（等同游戏在进世界/断开时的重置）。
     */
    @ApiClass(name = "Interactions", kind = ApiClassKind.HANDLE)
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class Interactions
            implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        Interactions() {
        }

        /**
         * Stores a block target override at the block center on the named
         * face ({@code up}/{@code down}/{@code north}/{@code south}/
         * {@code east}/{@code west}; defaults to {@code down}, the JsMacros
         * default). Unknown faces throw.
         *
         * @zh 在指定面（默认 {@code down}，沿 JsMacros 默认）上把方块中心存为目标覆盖；未知面抛错。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> setTarget(
                ink.tuanzi.neomacros.core.api.types.BlockPos pos,
                @ApiOptional String face) {
            if (pos == null) {
                return adapter().clearTargetOverride();
            }
            return adapter().setTargetOverride(pos.getIntX(), pos.getIntY(), pos.getIntZ(),
                    overrideFace(face));
        }

        /**
         * Stores an entity target override; the entity handle must be loaded
         * (the same contract as {@link LocalPlayer#attack}).
         *
         * @zh 把实体句柄存为目标覆盖；实体句柄必须已加载（与 {@link LocalPlayer#attack} 同契约）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> setTargetEntity(EntityApi.Entity entity) {
            if (entity == null) {
                return adapter().clearTargetOverride();
            }
            EntitySnapshot snapshot = entity.snapshot();
            if (snapshot == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("target entity is not loaded"));
            }
            return adapter().setTargetEntityOverride(snapshot.uuid());
        }

        /**
         * Stores a fixed miss override: the pick reports nothing at all
         * until the override is cleared. A stored miss passes the range
         * check.
         *
         * @zh 存入固定未命中覆盖：在覆盖被清除前准星一律读作未命中；未命中恒过距离检查。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> setTargetMissed() {
            return adapter().setTargetMissedOverride();
        }

        /**
         * Clears the target override so {@code player.target} reads the real
         * crosshair pick again. Clearing can settle a pending break with
         * {@code TARGET_LOST}.
         *
         * @zh 清除目标覆盖，{@code player.target} 重新读取真实准星；清除可能以 {@code TARGET_LOST} 结算挂起的破坏。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> clearTarget() {
            return adapter().clearTargetOverride();
        }

        /**
         * Whether a target override is stored, including a stored miss.
         * Reading does not revalidate it.
         *
         * @zh 是否存有目标覆盖（含未命中覆盖）；读取不会重新校验。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public boolean isTargetOverride() {
            return adapter().hasTargetOverride();
        }

        /**
         * Configures the reach check: {@code enabled} runs it while the
         * override is read (defaults true), {@code autoClear} drops an
         * out-of-range override instead of rendering it as a miss
         * (defaults true).
         *
         * @zh 配置距离检查：{@code enabled} 在读取覆盖时执行（默认 true），{@code autoClear} 为 true 时超距直接清除覆盖、否则渲染为未命中（默认 true）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> setRangeCheck(boolean enabled, boolean autoClear) {
            return adapter().setTargetRangeCheck(enabled, autoClear);
        }

        /**
         * Configures the air check: {@code enabled} rejects an air block
         * target (defaults false), {@code autoClear} drops it instead of
         * rendering it as a miss (defaults false).
         *
         * @zh 配置空气检查：{@code enabled} 拒绝空气方块目标（默认 false），{@code autoClear} 为 true 时清除、否则渲染为未命中（默认 false）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> setAirCheck(boolean enabled, boolean autoClear) {
            return adapter().setTargetAirCheck(enabled, autoClear);
        }

        /**
         * Configures the empty-shape check: {@code enabled} rejects a block
         * whose collision shape is empty (defaults true, air excluded),
         * {@code autoClear} drops it instead of rendering it as a miss
         * (defaults false).
         *
         * @zh 配置空形状检查：{@code enabled} 拒绝碰撞形状为空的方块（默认 true，空气除外），{@code autoClear} 为 true 时清除、否则渲染为未命中（默认 false）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> setShapeCheck(boolean enabled, boolean autoClear) {
            return adapter().setTargetShapeCheck(enabled, autoClear);
        }

        /**
         * Restores the three checks to their defaults (range on and clearing,
         * air off and retaining, shape on and rendering a miss). The stored
         * target is neither cleared nor immediately revalidated.
         *
         * @zh 把三项检查恢复默认（距离开且清除、空气关且保留、形状开且渲染为未命中）；不清除也不立即重验已存目标。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> resetChecks() {
            return adapter().resetTargetChecks();
        }

        /**
         * Whether the stored target override passes the client interaction
         * reach at the interpolation fraction (0 when omitted). False with
         * no override; a stored miss always passes. This is a query: it
         * never clears the override.
         *
         * @zh 已存目标覆盖在指定插值分数（缺省 0）下是否落在客户端交互距离内；无覆盖为 false，未命中恒过；只查询不清除。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public boolean isTargetInRange(@ApiOptional Double tickDelta) {
            return adapter().targetInRange((float) (tickDelta == null ? 0.0 : tickDelta));
        }

        /**
         * Breaks the block at the position, or the block the pick currently
         * reports when the position is omitted, and resolves with the
         * outcome: {@code SUCCESS} carries the broken position, every other
         * reason ({@code CANCELLED} {@code RESET} {@code NO_TARGET}
         * {@code TARGET_LOST} {@code TARGET_CHANGE} {@code IS_AIR}
         * {@code NOT_BREAKING}) reports why it ended and carries no
         * position. Instant-break blocks resolve at once; harder blocks
         * advance through the game's own destroy path while this call holds
         * the break override. The outcome is client-side only.
         *
         * @zh 破坏坐标处方块（省略坐标时破坏当前准星指向的方块）并以结果落定：{@code SUCCESS} 携带被破坏位置，其余理由码（{@code CANCELLED}/{@code RESET}/{@code NO_TARGET}/{@code TARGET_LOST}/{@code TARGET_CHANGE}/{@code IS_AIR}/{@code NOT_BREAKING}）只说明结束原因、不带位置。瞬挖方块立即落定；更硬的方块在本调用持有破坏覆盖期间沿游戏自身的破坏流程推进。结果仅为客户端侧。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<BreakBlockResult>")
        public CompletableFuture<BreakBlockResult> breakBlock(
                @ApiOptional ink.tuanzi.neomacros.core.api.types.BlockPos pos) {
            CompletableFuture<ink.tuanzi.neomacros.port.model.BreakBlockResult> future =
                    pos == null ? adapter().breakBlock()
                            : adapter().breakBlockAt(pos.getIntX(), pos.getIntY(), pos.getIntZ());
            return future.thenApply(PlayerApi::toBreakResult);
        }

        /**
         * Whether the break override is armed and still valid. The client
         * validates it every tick, so reading never has to end it.
         *
         * @zh 破坏覆盖是否已武装且仍然有效；客户端每 tick 校验，读取无需结束它。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public boolean isBreakOverride() {
            return adapter().hasBreakBlockOverride();
        }

        /**
         * Arms or disarms the break override. Disabling reports {@code reason}
         * (a pending break settles with it and no position), stops
         * client-side breaking while the attack key is up, and resets the
         * tracked position; enabling only arms it and starts no break action.
         *
         * @zh 武装或解除破坏覆盖：解除时以 {@code reason} 上报（挂起的破坏以该理由且无位置结算）、攻击键未按下时停止客户端破坏并重置跟踪位置；武装只置位、不发起任何破坏动作。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> setBreakOverride(boolean value,
                @ApiOptional String reason) {
            return adapter().setBreakOverride(value, reason);
        }

        /**
         * Cancels the active break override, settling a pending break with
         * {@code CANCELLED}.
         *
         * @zh 取消活动的破坏覆盖，挂起的破坏以 {@code CANCELLED} 结算。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> cancelBreak() {
            return adapter().cancelBreakBlock();
        }

        /**
         * Reports a client-side destruction call result to the active break
         * override: {@code broken} true ends it and settles a pending break
         * with {@code SUCCESS} at the position; false leaves it unchanged.
         * The game's own break path already feeds this, so scripts normally
         * never call it.
         *
         * @zh 向活动的破坏覆盖上报一次客户端破坏调用结果：{@code broken} 为 true 时结束覆盖并以该位置的 {@code SUCCESS} 结算挂起破坏，为 false 时不改动。游戏自身的破坏路径已自动上报，脚本通常无需调用。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> onBreakBlock(
                ink.tuanzi.neomacros.core.api.types.BlockPos pos, boolean broken) {
            if (pos == null) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("pos is required"));
            }
            return adapter().reportBreakBlock(pos.getIntX(), pos.getIntY(), pos.getIntZ(), broken);
        }

        /**
         * Queues a one-shot outcome callback for the next break-override
         * settlement and optionally arms the break override: {@code breaking}
         * true resets the tracked position and starts no break action, false
         * leaves the override unchanged. The callback receives the outcome as
         * a scalar tuple {@code (reason, x, y, z)} — the position coordinates
         * are {@code null} unless the reason is {@code SUCCESS} (the
         * handle-path guest-function contract passes neutral scalars; use
         * {@link #breakBlock} promises for the typed result record).
         * Callbacks run once on the game main thread when an outcome
         * settles, then the queue clears; an exception in one callback does
         * not stop the others (JsMacros {@code Break.addCallback} parity).
         *
         * @zh 排入一个一次性破坏结果回调（下一次覆盖结算时触发一次），并可选择同时武装破坏覆盖：{@code breaking} 为 true 时重置跟踪位置、不发起任何破坏动作，为 false 时不改动覆盖。回调以标量元组 {@code (reason, x, y, z)} 收到结果——理由非 {@code SUCCESS} 时坐标为 null（句柄路径的 guest 函数契约只传中性标量；带类型的结果记录用 {@link #breakBlock} 的 Promise）。回调在游戏主线程结算时执行一次随后清空队列；单个回调抛错不影响其余回调（对齐 JsMacros {@code Break.addCallback}）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public void addBreakCallback(
                @ApiType("(reason: string, x: number | null, y: number | null, z: number | null) => void")
                java.util.function.Consumer<Object[]> callback, boolean breaking) {
            adapter().addBreakCallback(
                    callback == null ? null
                            : result -> {
                                var pos = result == null ? null : result.pos();
                                callback.accept(new Object[]{
                                        result == null ? null : result.reason(),
                                        pos == null ? null : pos.x(),
                                        pos == null ? null : pos.y(),
                                        pos == null ? null : pos.z()});
                            },
                    breaking);
        }

        /**
         * Uses (right-clicks) whatever the pick currently reports — the
         * target override when one is set, otherwise the real crosshair:
         * a block face, an entity, or the held item in the air. The named
         * hand defaults to {@code main}.
         *
         * @zh 对当前准星（存有目标覆盖时即该覆盖，否则真实准星）执行使用（右键）：方块面、实体或手持物品对空气；手默认 {@code main}。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> interact(@ApiOptional String hand) {
            String normalized = normalizedHand(hand);
            var hit = adapter().target().orElse(null);
            if (hit == null) {
                return adapter().useItem(normalized);
            }
            if (hit.block() != null) {
                return adapter().useBlock(hit.block(), normalized);
            }
            if (hit.entity() != null) {
                return adapter().useEntity(hit.entity(), normalized);
            }
            return adapter().useItem(normalized);
        }

        /**
         * Starts ({@code holding} true) or stops the continuous held-use
         * override: while held the client re-issues the use action every few
         * ticks until stopped, the use key is pressed or the world unloads.
         * {@code awaitFirstClick} is accepted for JsMacros parity; this
         * face always settles once the override has been applied, never on
         * a server round trip.
         *
         * @zh 启动（{@code holding} 为 true）或停止按住使用覆盖：保持期间客户端每隔数 tick 重发使用动作，直到停止、按下使用键或离开世界。{@code awaitFirstClick} 为 JsMacros 对齐保留参数；本面恒在覆盖应用后落定，绝不等待服务端往返。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> holdInteract(boolean holding,
                @ApiOptional Boolean awaitFirstClick) {
            return adapter().setHoldInteract(holding);
        }

        /**
         * Holds the use override for up to {@code ticks} unpaused ticks and
         * then releases it ({@code stopOnPause} defaults true: paused ticks
         * do not count, the hold stops as soon as the game pauses). Resolves
         * with the ticks that did not elapse — {@code 0} for a full run,
         * nonzero when the hold stopped early.
         *
         * @zh 保持按住使用覆盖至多 {@code ticks} 个未暂停 tick 后释放（{@code stopOnPause} 默认 true：暂停 tick 不计数，游戏一暂停即停止）；以未走完的 tick 数落定——完整走完为 0，提前停止为非 0。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<number>")
        public CompletableFuture<Integer> holdInteractFor(int ticks,
                @ApiOptional Boolean stopOnPause) {
            return adapter().holdInteractFor(ticks,
                    stopOnPause == null || stopOnPause);
        }

        /**
         * Whether the held-use override is currently active (the flag only;
         * it does not query the physical use key or the item being used).
         *
         * @zh 按住使用覆盖当前是否活动（只是旗标，不查询物理使用键或正在使用的物品）。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public boolean isInteractOverride() {
            return adapter().hasInteractOverride();
        }

        /**
         * Restores every override to its default: the three target checks,
         * the target override, the break override (a pending break settles
         * with {@code RESET}) and the held-use override. The game runs the
         * same reset on world join and disconnect.
         *
         * @zh 把全部覆盖恢复默认：三项目标检查、目标覆盖、破坏覆盖（挂起的破坏以 {@code RESET} 结算）与按住使用覆盖。游戏在进世界与断开时执行同一重置。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiType("Promise<void>")
        public CompletableFuture<Void> reset() {
            return adapter().resetTargetChecks()
                    .thenCompose(ignored -> adapter().clearTargetOverride())
                    .thenCompose(ignored -> adapter().setBreakOverride(false, "RESET"))
                    .thenCompose(ignored -> adapter().setHoldInteract(false));
        }
    }

    /**
     * Outcome of a client-side block-break attempt: a diagnostic reason code
     * and the position it applies to ({@code null} unless the reason is
     * {@code SUCCESS}).
     *
     * @zh 客户块破坏尝试的结果：诊断理由码与其适用位置（理由非 {@code SUCCESS} 时位置为 null）。
     */
    @ApiClass(name = "BreakBlockResult", kind = ApiClassKind.DATA)
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record BreakBlockResult(String reason,
            ink.tuanzi.neomacros.core.api.types.BlockPos pos) {
    }

    /** Normalizes the override face name, defaulting to down (JsMacros parity). */
    static String overrideFace(String face) {
        return normalizedFace(face == null ? "down" : face);
    }

    /** Lifts a port break outcome onto the script-facing record. */
    private static BreakBlockResult toBreakResult(
            ink.tuanzi.neomacros.port.model.BreakBlockResult result) {
        if (result == null) {
            return null;
        }
        return new BreakBlockResult(result.reason(),
                result.pos() == null ? null
                        : new ink.tuanzi.neomacros.core.api.types.BlockPos(
                                result.pos().x(), result.pos().y(), result.pos().z()));
    }

    /**
     * The local player's own inventory: the always-available inventory
     * session (what {@code player.inventory} resolves to while the inventory
     * screen is open and when no container screen is open). Equipment and
     * offhand reads use the player inventory order (36 boots ... 39 helmet,
     * 40 offhand); the craft-grid reads address the player menu itself.
     *
     * @zh 本地玩家自己的物品栏：恒可用的物品栏会话（物品栏界面打开或未开容器时 {@code player.inventory} 解析到的句柄）。装备与副手按玩家物品栏序号读取（36 靴子 ... 39 头盔、40 副手）；合成格读取直接面向玩家菜单。
     */
    @ApiClass(name = "PlayerInventory", kind = ApiClassKind.HANDLE)
    public static final class PlayerInventory extends RecipeInventory {

        PlayerInventory() {
            super("Inventory", "player", 0);
        }

        /** The player menu reports a fixed 2x2 craft grid.
         * @zh 玩家菜单的合成格恒为 2x2。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        @Override
        public int getCraftingWidth() {
            return 2;
        }

        /** The player menu reports a fixed 2x2 craft grid.
         * @zh 玩家菜单的合成格恒为 2x2。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        @Override
        public int getCraftingHeight() {
            return 2;
        }

        /** Craft cells read the player menu itself, whatever screen is open.
         * @zh 合成格直接读取玩家菜单，与当前打开的界面无关。 */
        @Override
        protected ItemStack craftCell(int index) {
            return inventoryPort().playerMenuSlot(index).map(ItemStack::of).orElse(null);
        }

        /**
         * Whether a player inventory slot is the hotbar or the offhand
         * (player inventory order: 0-8 hotbar, 40 offhand).
         *
         * @zh 判断玩家物品栏槽位是否为快捷栏或副手（玩家物品栏序号：0-8 快捷栏、40 副手）。
         * @zh-param slot 玩家物品栏槽位号
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public boolean isInHotbar(int slot) {
            return (slot >= 0 && slot <= 8) || slot == 40;
        }

        /** The offhand stack, or {@code undefined} when empty.
         * @zh 副手物品；为空时 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getOffhand() {
            return slot(40);
        }

        /** The helmet stack, or {@code undefined} when empty.
         * @zh 头盔物品；为空时 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getHelmet() {
            return slot(39);
        }

        /** The chestplate stack, or {@code undefined} when empty.
         * @zh 胸甲物品；为空时 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getChestplate() {
            return slot(38);
        }

        /** The leggings stack, or {@code undefined} when empty.
         * @zh 护腿物品；为空时 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getLeggings() {
            return slot(37);
        }

        /** The boots stack, or {@code undefined} when empty.
         * @zh 靴子物品；为空时 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getBoots() {
            return slot(36);
        }
    }

    /**
     * A furnace-family container screen: input/fuel/output slots, the live
     * burn and cook state read from the open menu, and the recipe-book face
     * inherited from {@link RecipeInventory}. The furnace input grid is the
     * single smelting slot, so {@code input(x, z)} reads it regardless of
     * the coordinates.
     *
     * @zh 熔炉族容器界面：输入/燃料/输出槽位、打开菜单的实时燃烧与冶炼状态，以及继承自 {@link RecipeInventory} 的配方书面。熔炉输入格为单个冶炼槽，故 {@code input(x, z)} 无视坐标读取该槽。
     */
    @ApiClass(name = "FurnaceInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.AbstractFurnaceMenu")
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class FurnaceInventory extends RecipeInventory {

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
        public ItemStack getSmeltedItem() {
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

        /** The furnace input grid is the single smelting slot.
         * @zh 熔炉输入格即单个冶炼槽。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        @Override
        public int getCraftingWidth() {
            return 1;
        }

        /** The furnace input grid is one slot tall.
         * @zh 熔炉输入格高度为 1。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        @Override
        public int getCraftingHeight() {
            return 1;
        }

        /** The furnace input slot count, excluding fuel and result.
         * @zh 熔炉输入槽数量，不含燃料与产出。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        @Override
        public int getCraftingSlotCount() {
            return 1;
        }

        /**
         * The smelting input, ignoring the coordinates; the furnace has one
         * input slot.
         *
         * @zh 冶炼输入物品，忽略坐标参数；熔炉只有一个输入槽。
         * @zh-param x 列号（忽略）
         * @zh-param z 行号（忽略）
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @Override
        public ItemStack getInput(int x, int z) {
            return menuStack(0);
        }

        /**
         * Whether the stack burns as furnace fuel, or {@code undefined}
         * without an open furnace-family menu.
         *
         * @zh 该物品能否作为熔炉燃料；未打开熔炉族菜单时为 undefined。
         * @zh-param stack 待检查的物品
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public Boolean isFuel(ItemStack stack) {
            return stack == null ? null : inventoryPort().isFuel(stack.toData());
        }

        /**
         * Whether the open furnace menu accepts the stack as a smelting
         * input, or {@code undefined} without one.
         *
         * @zh 打开的熔炉菜单是否接受该物品作为冶炼输入；未打开时为 undefined。
         * @zh-param stack 待检查的物品
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public Boolean isSmeltable(ItemStack stack) {
            return stack == null ? null : inventoryPort().isSmeltable(stack.toData());
        }

        /**
         * The level's fuel values as item id to burn ticks; an empty map
         * when the level does not expose them.
         *
         * @zh 当前世界的燃料表（物品 id → 燃烧 tick）；无法取得时为空对象。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Map<String, Integer> getFuelValues() {
            return inventoryPort().fuelValues();
        }

        /** The remaining fuel burn time in ticks, or undefined.
         * @zh 剩余燃料燃烧 tick；无则为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getFuelTime() {
            return stateInt("fuelTime");
        }

        /** The total fuel burn time in ticks, or undefined.
         * @zh 燃料总燃烧 tick；无则为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getFuelTimeTotal() {
            return stateInt("fuelTimeTotal");
        }

        /** The smelting progress in ticks, or undefined.
         * @zh 已进行的冶炼 tick；无则为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getSmeltingTime() {
            return stateInt("smeltingTime");
        }

        /** The total smelting time in ticks, or undefined.
         * @zh 冶炼总 tick；无则为 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getSmeltingTimeTotal() {
            return stateInt("smeltingTimeTotal");
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
     * The recipe-book family face shared by the crafting-capable screens:
     * grid dimensions, the input grid, and the recipe-book visibility. Grid
     * reads answer for the menu open right now, so a stale handle reports
     * whatever menu is currently open.
     *
     * @zh 配方书家族的共享面：合成格尺寸、输入格与配方书可见性。格子读取回答当前打开的菜单，过期句柄读到的是此刻打开的菜单。
     */
    @ApiClass(name = "RecipeInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.RecipeBookMenu")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static class RecipeInventory extends Inventory {

        RecipeInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /** Reads one input-grid cell by menu index, or {@code undefined}. */
        protected ItemStack craftCell(int index) {
            return inventoryPort().menuSlot(index).map(ItemStack::of).orElse(null);
        }

        /** The open recipe menu's grid width, or {@code 0} without one.
         * @zh 打开的配方菜单的合成格宽度；无则为 0。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public int getCraftingWidth() {
            return inventoryPort().craftingWidth();
        }

        /** The open recipe menu's grid height, or {@code 0} without one.
         * @zh 打开的配方菜单的合成格高度；无则为 0。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public int getCraftingHeight() {
            return inventoryPort().craftingHeight();
        }

        /** The grid cell count (width x height), or {@code 0} without an
         * open recipe menu.
         * @zh 合成格总数（宽 x 高）；无打开的配方菜单时为 0。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public int getCraftingSlotCount() {
            return getCraftingWidth() * getCraftingHeight();
        }

        /** The input grid cell count (width x height), or {@code 0}.
         * @zh 输入格总数（宽 x 高）；无则为 0。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public int getInputSize() {
            return getCraftingWidth() * getCraftingHeight();
        }

        /**
         * One input-grid cell by column ({@code x}) and row ({@code z}), or
         * {@code undefined} outside the grid or when empty.
         *
         * @zh 按列（x）与行（z）读取一个输入格；越界或为空时为 undefined。
         * @zh-param x 列号（0 起）
         * @zh-param z 行号（0 起）
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public ItemStack getInput(int x, int z) {
            int width = getCraftingWidth();
            int height = getCraftingHeight();
            if (width <= 0 || height <= 0 || x < 0 || x >= width || z < 0 || z >= height) {
                return null;
            }
            return craftCell(1 + x + z * width);
        }

        /**
         * The whole input grid indexed as {@code [x][z]}; empty cells read as
         * {@code undefined}. A menu without a grid yields an empty grid.
         *
         * @zh 整个输入格，按 {@code [x][z]} 索引；空格为 undefined。菜单没有格子时返回空网格。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        @ApiType("ItemStack[][]")
        public List<List<ItemStack>> getInput() {
            int width = getCraftingWidth();
            int height = getCraftingHeight();
            List<List<ItemStack>> out = new java.util.ArrayList<>();
            for (int x = 0; x < width; x++) {
                List<ItemStack> column = new java.util.ArrayList<>();
                for (int z = 0; z < height; z++) {
                    column.add(getInput(x, z));
                }
                out.add(column);
            }
            return out;
        }

        /**
         * Whether the open recipe-book screen shows its recipe book, or
         * {@code undefined} without one.
         *
         * @zh 打开的配方书界面是否展开配方书；无则为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isRecipeBookOpened() {
            return inventoryPort().recipeBookOpen();
        }

        /**
         * Shows or hides the recipe book of the open recipe-book screen; a
         * no-op without one or when the state already matches.
         *
         * @zh 展开或收起打开的配方书；无配方书界面或状态已一致时为无操作。
         * @zh-param open 是否展开
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> setRecipeBook(boolean open) {
            return inventoryPort().setRecipeBookOpen(open);
        }

        /**
         * Toggles the recipe book of the open recipe-book screen; a no-op
         * without one.
         *
         * @zh 切换打开的配方书；无配方书界面时为无操作。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> toggleRecipeBook() {
            Boolean open = inventoryPort().recipeBookOpen();
            return inventoryPort().setRecipeBookOpen(open == null || !open);
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
    public static final class CraftingInventory extends RecipeInventory {

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

        /**
         * The text currently in the anvil rename box, or {@code undefined}
         * without an anvil screen. This is box text, not a guarantee that
         * the anvil produces a result.
         *
         * @zh 铁砧重命名框中的当前文本；未打开铁砧界面时为 undefined。这是输入框文本，不代表铁砧一定有产出。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public String getItemName() {
            return inventoryPort().anvilItemName().orElse(null);
        }

        /**
         * Writes the anvil rename box, re-running the menu's result and
         * cost computation; a no-op without an anvil screen. The rename is
         * applied by taking the result.
         *
         * @zh 写入铁砧重命名框，同步重算菜单的产出与费用；未打开铁砧界面时为无操作。改名在取出产出时生效。
         * @zh-param name 新的名称文本
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> setItemName(String name) {
            return inventoryPort().setAnvilItemName(name == null ? "" : name);
        }

        /** The fixed anvil cost threshold of 40 levels.
         * @zh 铁砧费用的固定上限 40 级。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public int getCostLimit() {
            return 40;
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
        public List<Integer> getCosts() {
            Object value = inventoryPort().containerState().get("costs");
            if (!(value instanceof int[] array)) {
                return List.of();
            }
            List<Integer> out = new java.util.ArrayList<>(array.length);
            for (int cost : array) {
                out.add(cost);
            }
            return out;
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

        /**
         * The three offered enchantments as {@code {id, level}} entries in
         * option order; an option the table does not offer reads as
         * {@code undefined}.
         *
         * @zh 三档可选附魔，按 {@code {id, level}} 形态、按选项顺序返回；附魔台未提供的选项为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public List<ink.tuanzi.neomacros.port.model.EnchantmentData> getEnchantments() {
            Object value = inventoryPort().containerState().get("enchantments");
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            List<ink.tuanzi.neomacros.port.model.EnchantmentData> out = new java.util.ArrayList<>();
            for (Object entry : list) {
                out.add(entry instanceof ink.tuanzi.neomacros.port.model.EnchantmentData data
                        ? data : null);
            }
            return out;
        }

        /**
         * The registry id of each offered enchantment, in option order; an
         * option the table does not offer reads as {@code undefined}.
         *
         * @zh 各档可选附魔的注册 id，按选项顺序返回；附魔台未提供的选项为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public List<String> getEnchantmentIds() {
            List<ink.tuanzi.neomacros.port.model.EnchantmentData> enchants = getEnchantments();
            List<String> out = new java.util.ArrayList<>(3);
            for (int i = 0; i < 3; i++) {
                out.add(i < enchants.size() && enchants.get(i) != null
                        ? enchants.get(i).id() : null);
            }
            return out;
        }

        /**
         * Asks the table to buy one of its three options (0-2); resolves
         * {@code false} when the local menu check refuses the index, and
         * {@code false} without an open enchanting table.
         *
         * @zh 请求附魔台购买三档之一（0-2）；本地菜单校验拒绝该序号时落定为 false，未打开附魔台时同样为 false。
         * @zh-param index 选项序号（0-2）
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Boolean> doEnchant(int index) {
            return inventoryPort().doEnchant(index);
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

        /**
         * The lower bound of the experience a grindstone run would award
         * for the two inputs; the real award is random between this value
         * and twice it minus one. Reads as {@code undefined} without an open
         * grindstone.
         *
         * @zh 两个输入槽去魔可获得的经验下界；实际奖励在该值到两倍减一之间随机。未打开砂轮时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer xpEstimate() {
            return stateInt("simulatedXp");
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

        /**
         * Selects one of the currently visible recipes by its zero-based
         * index; an out-of-range index is a no-op, as is a call without an
         * open stonecutter.
         *
         * @zh 按从 0 起的序号选择当前可见配方之一；序号越界或未打开切石机时为无操作。
         * @zh-param idx 配方序号
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> selectRecipe(int idx) {
            return inventoryPort().selectStonecutterRecipe(idx);
        }

        /**
         * The displayed result of each currently visible recipe, in
         * selection-index order; empty without an open stonecutter.
         *
         * @zh 当前每个可见配方的展示产出，按选择序号排列；未打开切石机时为空列表。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public List<ItemStack> getRecipes() {
            Object value = inventoryPort().containerState().get("recipes");
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            List<ItemStack> out = new java.util.ArrayList<>();
            for (Object entry : list) {
                out.add(entry instanceof ink.tuanzi.neomacros.port.model.ItemStackData data
                        ? ItemStack.of(data) : null);
            }
            return out;
        }

        /**
         * Whether the menu has an input item, or {@code undefined} without
         * an open stonecutter.
         *
         * @zh 菜单是否放有输入物品；未打开切石机时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public Boolean canCraft() {
            var info = inventoryPort().openedContainer().orElse(null);
            if (info == null || !"stonecutter".equals(info.kind())) {
                return null;
            }
            Object value = inventoryPort().containerState().get("hasInput");
            return value instanceof Boolean b ? b : null;
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

        /**
         * Highlights a primary effect reachable at the current beacon level;
         * the selection stays on the screen until {@link #applyEffects()}
         * sends it. Resolves {@code false} when the id matches nothing.
         *
         * @zh 高亮当前信标能级可达的主效果；选择停留在界面上，直到 {@link #applyEffects()} 发送。id 无匹配时落定为 false。
         * @zh-param id 效果标识（如 {@code minecraft:haste}）
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Boolean> selectPrimary(String id) {
            return inventoryPort().selectBeaconPrimaryEffect(id);
        }

        /**
         * Highlights a secondary effect reachable at the current beacon
         * level (level 3 or above); the selection stays on the screen until
         * {@link #applyEffects()} sends it. Resolves {@code false} when the
         * id matches nothing.
         *
         * @zh 高亮当前信标能级可达的副效果（需能级 3 以上）；选择停留在界面上，直到 {@link #applyEffects()} 发送。id 无匹配时落定为 false。
         * @zh-param id 效果标识（如 {@code minecraft:resistance}）
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Boolean> selectSecondary(String id) {
            return inventoryPort().selectBeaconSecondaryEffect(id);
        }

        /**
         * Sends the selected effects to the server and closes the screen;
         * resolves {@code false} when the payment slot is empty or no beacon
         * screen is open.
         *
         * @zh 将所选效果发送给服务端并关闭界面；付费槽为空或未打开信标界面时落定为 false。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Boolean> applyEffects() {
            return inventoryPort().applyBeaconEffects();
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

        /**
         * Whether the stack holds a potion type used as the result of a
         * brewing recipe, or {@code undefined} when brewing recipes are
         * unavailable.
         *
         * @zh 该物品是否为酿造配方的产出药水类型；无法取得酿造配方时为 undefined。
         * @zh-param potion 待检查的药水物品
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public Boolean isBrewablePotion(ItemStack potion) {
            return potion == null ? null
                    : inventoryPort().isBrewablePotion(potion.toData());
        }

        /**
         * Whether the stack is a valid brewing ingredient, or {@code
         * undefined} when brewing recipes are unavailable.
         *
         * @zh 该物品是否为合法的酿造原料；无法取得酿造配方时为 undefined。
         * @zh-param ingredient 待检查的原料
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public Boolean isValidIngredient(ItemStack ingredient) {
            return ingredient == null ? null
                    : inventoryPort().isValidIngredient(ingredient.toData());
        }

        /**
         * Whether the potion and the ingredient can be brewed together, or
         * {@code undefined} when brewing recipes are unavailable.
         *
         * @zh 该药水与原料能否一起酿造；无法取得酿造配方时为 undefined。
         * @zh-param potion 待酿造的药水
         * @zh-param ingredient 酿造原料
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public Boolean isValidRecipe(ItemStack potion, ItemStack ingredient) {
            if (potion == null || ingredient == null) {
                return null;
            }
            return inventoryPort().isValidRecipe(potion.toData(), ingredient.toData());
        }

        /**
         * Whether the stand could brew right now: a valid ingredient with at
         * least one potion slot that matches it. Fuel and brew progress are
         * not considered. {@code undefined} without an open brewing stand or
         * when brewing recipes are unavailable.
         *
         * @zh 酿造台此刻能否酿造：原料合法且至少一个药水槽有匹配配方。不考虑燃料与酿造进度。未打开酿造台或无法取得配方时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public Boolean canBrewCurrentInput() {
            if (!brewingOpen()) {
                return null;
            }
            ItemStack ingredient = getIngredient();
            if (ingredient == null) {
                return false;
            }
            Boolean ingredientOk = isValidIngredient(ingredient);
            if (ingredientOk == null) {
                return null;
            }
            if (!ingredientOk) {
                return false;
            }
            for (ItemStack potion : getBottles()) {
                if (potion == null) {
                    continue;
                }
                Boolean match = isValidRecipe(potion, ingredient);
                if (match == null) {
                    return null;
                }
                if (match) {
                    return true;
                }
            }
            return false;
        }

        /** Whether a brewing stand menu is open behind this handle. */
        private boolean brewingOpen() {
            var info = inventoryPort().openedContainer().orElse(null);
            return info != null && "brewing".equals(info.kind());
        }

        /**
         * The stack the potion becomes when the ingredient is brewed into
         * it; a potion with no matching mix reads back unchanged, and
         * {@code undefined} when brewing recipes are unavailable.
         *
         * @zh 药水与原料酿造后的产物；无匹配配方时原样返回，无法取得酿造配方时为 undefined。
         * @zh-param potion 待酿造的药水
         * @zh-param ingredient 酿造原料
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public ItemStack previewPotion(ItemStack potion, ItemStack ingredient) {
            if (potion == null || ingredient == null) {
                return null;
            }
            return inventoryPort().previewPotion(potion.toData(), ingredient.toData())
                    .map(ItemStack::of)
                    .orElse(null);
        }

        /**
         * The three potion slots after brewing with the current ingredient,
         * in slot order; empty without an open brewing stand. Slots that
         * cannot change (empty, or no ingredient loaded) read back as they
         * are.
         *
         * @zh 当前原料酿造后三个药水槽的结果，按槽序；未打开酿造台时为空列表。无法变化的槽（空槽或未放原料）原样返回。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public List<ItemStack> previewPotions() {
            if (!brewingOpen()) {
                return List.of();
            }
            ItemStack ingredient = getIngredient();
            List<ItemStack> out = new java.util.ArrayList<>();
            for (ItemStack potion : getBottles()) {
                out.add(potion == null || ingredient == null
                        ? potion : previewPotion(potion, ingredient));
            }
            return out;
        }
    }

    /**
     * A loom screen: the banner pattern offer over the ordinary slot face.
     * The offer depends on the pattern slot item; the banner and dye do not
     * change it.
     *
     * @zh 织机界面：在普通槽位面之上的旗帜图案供给面。供给取决于图案槽中的物品；旗帜与染料不影响供给。
     */
    @ApiClass(name = "LoomInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.world.inventory.LoomMenu")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class LoomInventory extends Inventory {

        LoomInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /**
         * The banner pattern registry ids the loom offers for the current
         * pattern-slot item, in selection order; empty without an open loom.
         *
         * @zh 织机针对当前图案槽物品提供的旗帜图案注册 id（按选择顺序）；未开织机时为空列表。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public List<String> getPatterns() {
            return inventoryPort().loomPatterns();
        }

        /**
         * Applies the offered pattern with the given registry id and asks the
         * server to apply it; resolves {@code false} when the id is not
         * offered or the pattern list is not displayed.
         *
         * @zh 应用指定注册 id 的图案并向服务端请求应用；id 不在供给内或图案列表未显示时落定为 false。
         * @zh-param id 图案注册 id（如 {@code minecraft:base}）
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Boolean> selectPatternId(String id) {
            if (id == null) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            int index = getPatterns().indexOf(id);
            return index < 0
                    ? CompletableFuture.completedFuture(Boolean.FALSE)
                    : inventoryPort().loomSelectPattern(index);
        }

        /**
         * Applies the offered pattern at its zero-based position and asks the
         * server to apply it; resolves {@code false} when the index is out of
         * range or the pattern list is not displayed.
         *
         * @zh 应用指定序号的图案并向服务端请求应用；越界或图案列表未显示时落定为 false。
         * @zh-param index 图案在供给列表中的零基序号
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Boolean> selectPattern(int index) {
            return inventoryPort().loomSelectPattern(index);
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

        /**
         * Selects a trade by its zero-based position in {@link #getTrades()},
         * highlighting it and requesting it from the server; an out-of-range
         * index or a closed trader screen is a no-op.
         *
         * @zh 按 {@link #getTrades()} 中的零基序号选择交易（高亮并向服务端请求）；越界或未开交易界面时无操作。
         * @zh-param index 交易报价序号
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> selectTrade(int index) {
            return inventoryPort().selectVillagerTrade(index);
        }

        /**
         * Whether the trader offers a restock; {@code undefined} without an
         * open trader screen.
         *
         * @zh 交易者是否可补货；未开交易界面时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean canRestock() {
            Object value = inventoryPort().containerState().get("canRestock");
            return value instanceof Boolean b ? b : null;
        }

        /**
         * Whether the trader screen shows its level progress bar;
         * {@code undefined} without an open trader screen.
         *
         * @zh 交易界面是否显示等级进度条；未开交易界面时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isLeveled() {
            Object value = inventoryPort().containerState().get("leveled");
            return value instanceof Boolean b ? b : null;
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

        /**
         * Whether the mount can use its saddle slot (false for untamed, baby,
         * or dead mounts); it does not report whether a saddle is equipped.
         * {@code undefined} without an open mount screen.
         *
         * @zh 坐骑是否可用鞍槽（未驯服/幼年/死亡为 false）；不表示当前是否装备鞍。未开坐骑界面时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean canBeSaddled() {
            Object value = inventoryPort().containerState().get("canBeSaddled");
            return value instanceof Boolean b ? b : null;
        }

        /**
         * Whether a saddle is currently equipped; {@code undefined} without
         * an open mount screen.
         *
         * @zh 当前是否装备了鞍；未开坐骑界面时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean isSaddled() {
            Object value = inventoryPort().containerState().get("isSaddled");
            return value instanceof Boolean b ? b : null;
        }

        /**
         * Whether the mount can use the body-armor slot; it does not report
         * whether armor is equipped. {@code undefined} without an open mount
         * screen.
         *
         * @zh 坐骑是否可用马甲槽；不表示当前是否装备马甲。未开坐骑界面时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasArmorSlot() {
            Object value = inventoryPort().containerState().get("hasArmorSlot");
            return value instanceof Boolean b ? b : null;
        }

        /**
         * Whether the mount carries a chest; {@code undefined} without an
         * open mount screen.
         *
         * @zh 坐骑是否装备了箱子；未开坐骑界面时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Boolean hasChest() {
            Object value = inventoryPort().containerState().get("hasChest");
            return value instanceof Boolean b ? b : null;
        }

        /**
         * The number of carried slots (15 for a chested mount, otherwise 0);
         * {@code undefined} without an open mount screen.
         *
         * @zh 随身携带槽位数（装备箱子的坐骑为 15，否则为 0）；未开坐骑界面时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public Integer getInventorySize() {
            Object value = inventoryPort().containerState().get("inventorySize");
            return value instanceof Integer i ? i : null;
        }

        /**
         * The carried slots in menu order, including empty slots as
         * {@code null} entries; an empty list when the mount has no chest.
         *
         * @zh 按菜单序读取随身携带槽位，空槽以 null 占位；坐骑无箱子时为空列表。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public List<ItemStack> getHorseInventory() {
            Integer size = getInventorySize();
            if (size == null || size <= 0) {
                return List.of();
            }
            List<ItemStack> out = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                out.add(inventoryPort().menuSlot(2 + i)
                        .map(ItemStack::of)
                        .orElse(null));
            }
            return out;
        }

        /**
         * The mount this screen belongs to, as a species-specific entity
         * handle; {@code undefined} without an open mount screen.
         *
         * @zh 该界面对应的坐骑（物种子类实体句柄）；未开坐骑界面时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public ink.tuanzi.neomacros.core.api.entity.EntityApi.Entity getHorse() {
            Object value = inventoryPort().containerState().get("horse");
            if (!(value instanceof String uuid)) {
                return null;
            }
            return wrapEntity(java.util.UUID.fromString(uuid));
        }

        /**
         * Wraps one entity UUID as the kind-specific handle, mirroring the
         * entity package factory (players, dropped stacks, registered
         * species, living entities, everything else).
         */
        private static ink.tuanzi.neomacros.core.api.entity.EntityApi.Entity wrapEntity(
                java.util.UUID uuid) {
            var snapshot = ScriptApiEnvironment.adapter().entity().byUuid(uuid).orElse(null);
            if (snapshot == null) {
                return null;
            }
            if (snapshot.type().equals("minecraft:player")) {
                return new Player(uuid);
            }
            if (snapshot.type().equals("minecraft:item")) {
                return new ink.tuanzi.neomacros.core.api.entity.EntityApi.ItemEntity(uuid);
            }
            var species = ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.registry()
                    .get(snapshot.type());
            if (species != null) {
                return species.apply(uuid);
            }
            if (snapshot.living()) {
                return new ink.tuanzi.neomacros.core.api.entity.EntityApi.LivingEntity(uuid);
            }
            return new ink.tuanzi.neomacros.core.api.entity.EntityApi.Entity(uuid);
        }
    }

    /**
     * The creative inventory screen: the item-list tabs, search, scrolling,
     * saved hotbars, and the cursor stack over the player inventory face.
     * Equipment and hotbar reads answer from the player's own inventory,
     * the same slots {@link PlayerInventory} exposes.
     *
     * @zh 创造物品栏界面：物品列表页签、搜索、滚动、保存的快捷栏与光标堆，叠加在玩家背包面之上。装备与快捷栏读取取自玩家自己的物品栏，与 {@link PlayerInventory} 暴露的槽位一致。
     */
    @ApiClass(name = "CreativeInventory", kind = ApiClassKind.HANDLE,
            wraps = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen")
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public static final class CreativeInventory extends Inventory {

        CreativeInventory(String title, String kind, int size) {
            super(title, kind, size);
        }

        /**
         * The item stacks the creative list currently holds, in display
         * order (empty slots skipped); empty without an open creative screen.
         *
         * @zh 创造物品列表当前持有的物品堆（按显示顺序，空槽不入列）；未开创造界面时为空列表。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public List<ItemStack> getShownItems() {
            List<ItemStack> out = new java.util.ArrayList<>();
            for (ink.tuanzi.neomacros.port.model.ItemStackData data
                    : inventoryPort().creativeShownItems()) {
                out.add(ItemStack.of(data));
            }
            return out;
        }

        /**
         * The localized display name of every creative tab, in display
         * order; empty without an open creative screen. These names are not
         * tab ids and are the values matched by {@link #selectTab(String)}.
         *
         * @zh 全部创造页签的本地化显示名（按显示顺序）；未开创造界面时为空列表。这些名称不是页签 id，而是 {@link #selectTab(String)} 的匹配值。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public List<String> getTabNames() {
            List<String> out = new java.util.ArrayList<>();
            for (ink.tuanzi.neomacros.port.InventoryPort.CreativeTab tab
                    : inventoryPort().creativeTabs()) {
                out.add(tab.name());
            }
            return out;
        }

        /**
         * Every creative tab's display name as a text object, in the same
         * order as {@link #getTabNames()}; empty without an open creative
         * screen.
         *
         * @zh 全部创造页签的显示名文本对象（顺序与 {@link #getTabNames()} 一致）；未开创造界面时为空列表。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public List<ink.tuanzi.neomacros.core.api.types.TextComponent> getTabTexts() {
            List<ink.tuanzi.neomacros.core.api.types.TextComponent> out = new java.util.ArrayList<>();
            for (String name : getTabNames()) {
                out.add(ink.tuanzi.neomacros.core.api.types.TextComponent.of(name));
            }
            return out;
        }

        /**
         * The item stacks stored in a saved-hotbar slot, in hotbar order;
         * an empty list for an out-of-range index or an unset slot.
         *
         * @zh 保存的快捷栏槽位中的物品堆（按快捷栏顺序）；序号越界或未保存时为空列表。
         * @zh-param index 保存的快捷栏序号（0-8）
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public List<ItemStack> getSavedHotbar(int index) {
            List<ItemStack> out = new java.util.ArrayList<>();
            for (ink.tuanzi.neomacros.port.model.ItemStackData data
                    : inventoryPort().creativeSavedHotbar(index)) {
                out.add(ItemStack.of(data));
            }
            return out;
        }

        /**
         * Whether a player inventory slot is the hotbar or the offhand
         * (player inventory order: 0-8 hotbar, 40 offhand).
         *
         * @zh 判断玩家物品栏槽位是否为快捷栏或副手（玩家物品栏序号：0-8 快捷栏、40 副手）。
         * @zh-param slot 玩家物品栏槽位号
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public boolean isInHotbar(int slot) {
            return (slot >= 0 && slot <= 8) || slot == 40;
        }

        /** The offhand stack, or {@code undefined} when empty.
         * @zh 副手物品；为空时 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getOffhand() {
            return slot(40);
        }

        /** The helmet stack, or {@code undefined} when empty.
         * @zh 头盔物品；为空时 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getHelmet() {
            return slot(39);
        }

        /** The chestplate stack, or {@code undefined} when empty.
         * @zh 胸甲物品；为空时 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getChestplate() {
            return slot(38);
        }

        /** The leggings stack, or {@code undefined} when empty.
         * @zh 护腿物品；为空时 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getLeggings() {
            return slot(37);
        }

        /** The boots stack, or {@code undefined} when empty.
         * @zh 靴子物品；为空时 undefined。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        @ApiReadOnly
        public ItemStack getBoots() {
            return slot(36);
        }

        /**
         * Scrolls the creative item list by a relative amount; the resulting
         * position is clamped to {@code 0..1}. A no-op on a screen without a
         * scrollbar.
         *
         * @zh 按相对量滚动创造物品列表，结果位置夹取到 {@code 0..1}；无滚动条的界面为无操作。
         * @zh-param amount 相对当前滚动位置的偏移量
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> scroll(double amount) {
            return inventoryPort().creativeScrollBy(amount);
        }

        /**
         * Sets the creative item list's scroll position (clamped to
         * {@code 0..1}); a no-op on a screen without a scrollbar.
         *
         * @zh 设置创造物品列表的滚动位置（夹取到 {@code 0..1}）；无滚动条的界面为无操作。
         * @zh-param position 目标滚动位置
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> scrollTo(double position) {
            return inventoryPort().creativeScrollTo(position);
        }

        /**
         * Replaces the search box text and refreshes the results; does
         * nothing unless the search tab is selected.
         *
         * @zh 替换搜索框文本并刷新结果；未选中搜索页签时无操作。
         * @zh-param text 搜索文本
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> search(String text) {
            return inventoryPort().creativeSearch(text == null ? "" : text);
        }

        /** Selects the search tab; a no-op when no creative screen is open.
         * @zh 选中搜索页签；未开创造界面时无操作。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> selectSearch() {
            return selectTabByType("SEARCH");
        }

        /** Selects the inventory tab; a no-op when no creative screen is open.
         * @zh 选中物品栏页签；未开创造界面时无操作。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> selectInventory() {
            return selectTabByType("INVENTORY");
        }

        /** Selects the hotbar tab; a no-op when no creative screen is open.
         * @zh 选中快捷栏页签；未开创造界面时无操作。 */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> selectHotbar() {
            return selectTabByType("HOTBAR");
        }

        /**
         * Selects the tab whose display name exactly matches the argument;
         * resolves {@code false} when no tab has that name.
         *
         * @zh 选中显示名与参数完全一致的页签；无同名页签时落定为 false。
         * @zh-param tabName 页签显示名（见 {@link #getTabNames()}）
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Boolean> selectTab(String tabName) {
            if (tabName == null) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            int index = getTabNames().indexOf(tabName);
            if (index < 0) {
                return CompletableFuture.completedFuture(Boolean.FALSE);
            }
            return inventoryPort().creativeSelectTab(index)
                    .thenApply(v -> Boolean.TRUE);
        }

        /** Selects the first tab of the given type, or does nothing. */
        private CompletableFuture<Void> selectTabByType(String type) {
            List<ink.tuanzi.neomacros.port.InventoryPort.CreativeTab> tabs =
                    inventoryPort().creativeTabs();
            for (int i = 0; i < tabs.size(); i++) {
                if (type.equals(tabs.get(i).type())) {
                    return inventoryPort().creativeSelectTab(i);
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        /**
         * Clears the stack carried on the cursor. Client-side only until the
         * cursor is used; a no-op without an open creative screen.
         *
         * @zh 清空光标上的物品堆；在光标被使用前仅客户端生效，未开创造界面时无操作。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> destroyHeldItem() {
            return inventoryPort().creativeDestroyHeld();
        }

        /**
         * Clears every player inventory slot by sending an empty stack for
         * each slot through the creative item-add operation.
         *
         * <p><b>Risk:</b> the requests are only accepted while the server
         * allows creative-mode slot edits; on a survival server they are
         * rejected and the inventory stays unchanged.
         *
         * @zh 通过创造物品添加操作为每个槽位发送空堆，清空全部玩家物品栏槽位。
         * <p><b>风险：</b>仅当服务端允许创造模式槽位修改时才会被接受；生存服上会被拒绝，物品栏保持不变。
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> destroyAllItems() {
            return inventoryPort().creativeDestroyAll();
        }

        /**
         * Sets the stack carried on the cursor; a null stack clears it.
         *
         * @zh 设置光标上的物品堆；传 null 即清空。
         * @zh-param stack 要放到光标上的物品
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> setCursorStack(ItemStack stack) {
            return inventoryPort().creativeSetCursor(stack == null ? null : stack.toData());
        }

        /**
         * Saves the current hotbar into a saved-hotbar slot; a no-op for an
         * out-of-range index or without an open creative screen.
         *
         * @zh 将当前快捷栏保存到保存的快捷栏槽位；序号越界或未开创造界面时无操作。
         * @zh-param index 保存的快捷栏序号（0-8）
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> saveHotbar(int index) {
            return inventoryPort().creativeSaveHotbar(index);
        }

        /**
         * Restores a saved-hotbar slot onto the current hotbar; a no-op for
         * an out-of-range index or without an open creative screen.
         *
         * @zh 将保存的快捷栏槽位恢复到当前快捷栏；序号越界或未开创造界面时无操作。
         * @zh-param index 保存的快捷栏序号（0-8）
         */
        @ApiSince("0.5.0")
        @ApiMC("*")
        @ApiLoader("*")
        public CompletableFuture<Void> restoreHotbar(int index) {
            return inventoryPort().creativeRestoreHotbar(index);
        }
    }

    /**
     * Live inventory handle: slot reads re-query the inventory port on every
     * access; actions dispatch on the main thread and resolve once processed.
     *
     * <p>Two slot index spaces stay distinct: {@link #slot(int)},
     * {@link #findAll(String)} and the equipment getters take the player
     * inventory order (0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand),
     * while {@link #regionOf(int)}, {@link #getSlotPos(int)} and
     * {@link #getSlotUnderMouse()} take whole-screen menu indices
     * (container rows first, player rows last).</p>
     *
     * @zh 物品栏句柄：槽位读取每次都重新查询物品栏端口；操作在主线程派发，处理完成后落定。两套槽位序号并存：{@code slot/findAll} 与装备读取用玩家物品栏序号（0-8 快捷栏、9-35 主背包、36-39 护甲、40 副手）；{@code regionOf/slotPos/slotUnderMouse} 用整屏菜单序号（容器行在前、玩家行在后）。
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

        /** All non-empty stacks keyed by their slot index; every value is a
         * full {@code ItemStack} handle, the same type {@code slot()} returns.
         * @zh 全部非空物品，按槽位号键控；每个值都是完整的 {@code ItemStack} 句柄，与 {@code slot()} 返回值同类型。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public Map<Integer, ItemStack> getItems() {
            Map<Integer, ItemStack> out = new java.util.LinkedHashMap<>();
            for (var entry : inventoryPort().allItems().get()) {
                out.put(entry.slot(), ItemStack.of(entry.stack()));
            }
            return out;
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

        /** Total item count per item id across the inventory.
         * @zh 物品栏内按物品 id 聚合的总数量。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Map<String, Integer> getItemCount() {
            Map<String, Integer> out = new LinkedHashMap<>();
            for (var entry : inventoryPort().allItems().get()) {
                out.merge(entry.stack().id(), entry.stack().count(), Integer::sum);
            }
            return out;
        }

        /** The stack held on the mouse cursor over the open menu, or undefined.
         * @zh 光标抓取中的物品堆；无则 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public ItemStack getCarried() {
            return inventoryPort().carriedItem()
                    .map(ItemStack::of)
                    .orElse(null);
        }

        /** The selected hotbar slot (0-8), or undefined.
         * @zh 当前选中的快捷栏槽位（0-8）；无则 undefined。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getSelectedHotbarSlot() {
            return inventoryPort().selectedHotbarSlot();
        }

        /** First empty hotbar slot (0-8), or undefined when full. Reads the
         * player's own inventory, no GUI needed.
         * @zh 第一个空的快捷栏槽位（0-8）；全满为 undefined。读取玩家自身物品栏，无需打开界面。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer findFreeHotbarSlot() {
            return findFreeSlotIn(0, 9);
        }

        /** First empty main-inventory slot (9-35), or undefined when full.
         * Reads the player's own inventory, no GUI needed.
         * @zh 主背包（9-35）第一个空槽；全满为 undefined。读取玩家自身物品栏，无需打开界面。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer findFreeInventorySlot() {
            return findFreeSlotIn(9, 36);
        }

        /** First absent slot index in {@code [from, to)} over the live item map. */
        private Integer findFreeSlotIn(int from, int to) {
            java.util.Set<Integer> filled = new java.util.HashSet<>();
            for (var entry : inventoryPort().allItems().get()) {
                filled.add(entry.slot());
            }
            for (int i = from; i < to; i++) {
                if (!filled.contains(i)) {
                    return i;
                }
            }
            return null;
        }

        /** Clicks a slot (button 0 = left, 1 = right, 2 = middle-click clone
         * in creative; shift = quick move).
         * @zh 点击槽位（button 0 = 左键，1 = 右键，2 = 中键克隆（创造）；shift = 快速移动）。 */
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

        /** Swaps a container slot with a hotbar slot (0..8, or 40 for the
         * offhand).
         * @zh 将容器槽位与快捷栏槽位交换（0..8，或 40 = 副手）。 */
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

        /** Selects the active hotbar slot (0-8).
         * @zh 将快捷栏选中位切到指定槽（0-8）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> setSelectedHotbarSlot(int index) {
            return inventoryPort().setSelectedHotbarSlot(index);
        }

        /** Picks the slot up, then gathers every matching stack onto the
         * cursor (the double-click collect semantics).
         * @zh 拿起槽位物品，再把所有可合并堆聚拢到光标（双击聚拢语义）。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> grabAll(int slot) {
            return inventoryPort().grabAll(slot);
        }

        /**
         * Places the cursor stack split across two empty slots: one item
         * into the first, the rest into the second. Both slots must be empty
         * and distinct; the server arbitrates violations.
         *
         * @zh 将光标上的堆拆放到两个空槽：第一槽放 1 个，其余放第二槽。两槽需为空且不同；违例由服务端裁决。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> split(int slot1, int slot2) {
            return inventoryPort().split(slot1, slot2);
        }

        /**
         * Drag-distributes the cursor stack over the given menu slots
         * (button 0 = even split, other = greedy fill).
         *
         * @zh 将光标堆拖拽分发到给定槽位（button 0 = 均分，其他 = 贪心填满）。
         * @zh-param slots 菜单槽位号数组
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> dragClick(
                @ApiType("Array<number>") Object slots, int button) {
            return inventoryPort().dragClick(slotList(slots), button);
        }

        /**
         * Quick-moves every slot that can merge with the given slot's stack
         * (same backing container). Returns the moved item count.
         *
         * @zh 快速移动所有可与指定槽位物品合并的槽（同一背后容器）。返回移动的物品总数。
         */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Integer> quickAll(int slot,
                @ApiOptional Integer button) {
            return inventoryPort().quickAll(slot, button != null ? button : 0);
        }

        /** Drops the cursor-held stack (if any) and closes the open screen.
         * @zh 丢弃光标上的堆（如有）并关闭当前界面。 */
        @ApiSince("0.3.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> closeAndDrop() {
            return inventoryPort().closeAndDrop();
        }

        /** Coerces a guest slot array into a list of ints (numbers only). */
        private static List<Integer> slotList(Object slots) {
            if (!(slots instanceof List<?> list)) {
                throw new IllegalArgumentException("slots must be a number array");
            }
            List<Integer> out = new java.util.ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Number number)) {
                    throw new IllegalArgumentException("slots must be a number array");
                }
                out.add(number.intValue());
            }
            return out;
        }

        /**
         * Opens this inventory's screen on the client: the survival inventory
         * for the player handle, while container handles need their own
         * screen to still be open (the server owns container openings).
         *
         * @zh 在客户端打开此物品栏界面：玩家句柄打开生存背包；容器句柄要求其界面仍处于打开状态（容器的开启动作归服务端）。
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public CompletableFuture<Void> open() {
            return inventoryPort().open(kind);
        }

        /**
         * The menu slot index under the mouse, or {@code undefined} without
         * a container screen or when the pointer sits over no slot.
         *
         * @zh 鼠标下的菜单槽位号；未开容器界面或未悬停任何槽位时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getSlotUnderMouse() {
            return inventoryPort().slotUnderMouse().orElse(null);
        }

        /**
         * The named region one menu slot belongs to (whole-screen slot
         * order), or {@code undefined} for an index outside the open menu.
         *
         * @zh 一个菜单槽位所属的逻辑区名（整屏槽位序号）；越界时为 undefined。
         * @zh-param slot 菜单槽位号
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public String regionOf(int slot) {
            return inventoryPort().regionOf(slot).orElse(null);
        }

        /**
         * The screen-space top-left corner of one menu slot (whole-screen
         * slot order), or {@code undefined} without a container screen or
         * for an out-of-range index.
         *
         * @zh 一个菜单槽位的屏幕左上角坐标（整屏槽位序号）；未开容器界面或越界时为 undefined。
         * @zh-param slot 菜单槽位号
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        public ink.tuanzi.neomacros.core.api.types.Pos2D getSlotPos(int slot) {
            return inventoryPort().slotPos(slot)
                    .map(pos -> new ink.tuanzi.neomacros.core.api.types.Pos2D(pos.x(), pos.y()))
                    .orElse(null);
        }

        /**
         * The network sync id of the open menu ({@code 0} for the player's
         * own menu), or {@code undefined} outside a world.
         *
         * @zh 打开菜单的网络同步 id（玩家自己的菜单为 0）；不在世界内时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        public Integer getContainerId() {
            return inventoryPort().openedContainer()
                    .map(info -> info.containerId())
                    .orElse(null);
        }

        /**
         * The open container menu as a raw host object behind this handle's
         * {@code wraps} target; {@code undefined} outside a world. Requires
         * the {@code java} permission.
         *
         * @zh 句柄 {@code wraps} 背后的原始打开菜单宿主对象（需 java 权限）；不在世界内时为 undefined。
         */
        @ApiSince("0.5.0")
        @ApiLoader("*")
        @ApiMC("*")
        @ApiReadOnly
        @ApiPermission("java")
        @ApiType("JavaObject")
        public Object getRaw() {
            return inventoryPort().rawContainer().orElse(null);
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
     * One non-zero statistic entry: the stat type id ({@code category},
     * e.g. {@code minecraft:mined}), the entry id ({@code key}), and the
     * value.
     *
     * @zh 一条非零统计：统计类型 id（{@code category}，如 {@code minecraft:mined}）、条目 id（{@code key}）与数值。
     */
    @ApiClass(name = "StatisticsEntry", kind = ApiClassKind.DATA)
    @ApiSince("0.3.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record StatisticsEntry(String category, String key, int value) {
    }

    /**
     * The client-known progress of one advancement: whether it is done,
     * which criteria are obtained, and which are still open.
     *
     * @zh 一个进度的客户端已知进度：是否完成、已获得的判据与尚未获得的判据。
     */
    @ApiClass(name = "AdvancementProgress", kind = ApiClassKind.DATA)
    @ApiSince("0.3.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record AdvancementProgress(boolean done,
            java.util.List<String> obtained,
            java.util.List<String> remaining) {
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
}
