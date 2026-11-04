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
 * The {@code neo.player} facade: the local player plus the currently open
 * container screen. {@code self} is a live handle reading current state
 * through the player port; outside a world it reads back as {@code undefined}.
 *
 * @zh {@code neo.player} 门面：本地玩家与当前打开的容器屏幕。{@code self} 是经玩家端口回读当前状态的活句柄；不在世界内时回读为 undefined。
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
     * The currently open container screen as an {@code Inventory} handle, or
     * undefined when no container screen is open.
     *
     * @zh 当前打开的容器屏幕句柄；无屏幕时为 undefined。
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public Inventory getOpened() {
        return inventoryPort().openedContainer()
                .map(info -> new Inventory(info.title(), info.kind(), info.size()))
                .orElse(null);
    }

    /** The player port behind the facade (single adapter access point). */
    private static ink.tuanzi.neomacros.port.PlayerPort adapter() {
        return ScriptApiEnvironment.adapter().player();
    }

    /** The inventory port behind the facade (container screen access). */
    private static ink.tuanzi.neomacros.port.InventoryPort inventoryPort() {
        return ScriptApiEnvironment.adapter().inventory();
    }

    /** The latest local player snapshot, or empty when not in a world. */
    static Optional<PlayerSnapshot> current() {
        return adapter().self();
    }

    /**
     * Live player handle: the entity read face comes through the entity port
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
        public Map<String, Object> getXp() {
            if (!isSelf()) {
                return null;
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("level", adapter().expLevel());
            view.put("progress", (double) adapter().expProgress());
            view.put("total", adapter().expTotal());
            return view;
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
     * The action surface of the local player: attack, interact, swing, and
     * drop dispatch through the game on the main thread and resolve once
     * processed.
     *
     * @zh 本地玩家动作面：attack/interact/swing/drop 经游戏主线程派发，处理完成后落定。
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
     * session (the same handle {@code player.opened} returns while the
     * inventory screen is open).
     *
     * @zh 本地玩家自己的物品栏：恒可用的物品栏会话（物品栏界面打开时 {@code player.opened} 返回的就是它）。
     */
    @ApiClass(name = "PlayerInventory", kind = ApiClassKind.HANDLE)
    public static final class PlayerInventory extends Inventory {

        PlayerInventory() {
            super("Inventory", "player", 0);
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

        private final String title;
        private final String kind;
        private final int size;

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
}
