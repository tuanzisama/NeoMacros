package ink.tuanzi.neomacros.core.api.inventory;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@code neo.inventory} facade: container queries and item movement.
 * The {@code opened} property is a live Container handle for the currently
 * open container screen; actions dispatch on the main thread.
 *
 * @zh {@code neo.inventory} 门面：容器查询与物品移动。{@code opened} 属性是当前打开容器屏幕的活句柄；操作在主线程派发。
 */
@ApiModule(name = "inventory", category = "世界")
@ApiSince("0.1.0")
public final class InventoryApi {

    /**
     * The currently open container screen handle, or undefined when no
     * container screen is open.
     *
     * @zh 当前打开的容器屏幕句柄；无屏幕时为 undefined。
     */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public Container getOpened() {
        return adapter().openedContainer()
                .map(info -> new Container(info.title(), info.kind(), info.size()))
                .orElse(null);
    }

    private static ink.tuanzi.neomacros.port.InventoryPort adapter() {
        return ScriptApiEnvironment.adapter().inventory();
    }

    /**
     * Live container handle: slot reads re-query the inventory port on every
     * access; actions (click/quickMove/drop/close) dispatch on the main
     * thread and resolve once processed.
     *
     * @zh 容器句柄：槽位读取每次都重新查询物品栏端口；click/quickMove/drop/close 操作在主线程派发，处理完成后落定。
     */
    @ApiClass(name = "Container", kind = ApiClassKind.HANDLE)
    public static class Container implements ScriptHandle {

        private final String title;
        private final String kind;
        private final int size;

        Container(String title, String kind, int size) {
            this.title = title;
            this.kind = kind;
            this.size = size;
        }

        /** The container display title.
         * @zh 容器显示标题。 */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public String getTitle() {
            return title;
        }

        /** The container kind ({@code player}, {@code chest}, {@code crafting}, ...).
         * @zh 容器种类（{@code player}、{@code chest}、{@code crafting} 等）。 */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public String getKind() {
            return kind;
        }

        /** The container slot count.
         * @zh 容器槽位总数。 */
        @ApiSince("0.1.0")
        @ApiReadOnly
        public int getSize() {
            return size;
        }

        /** Reads the stack at a slot, or undefined when out of range or empty.
         * @zh 读取指定槽位的物品；越界或空槽为 undefined。 */
        @ApiSince("0.1.0")
        public Map<String, Object> slot(int index) {
            return adapter().slot(index)
                    .map(InventoryApi::stackView)
                    .orElse(null);
        }

        /** All non-empty stacks with their slot indices.
         * @zh 全部非空物品及其槽位号。 */
        @ApiSince("0.1.0")
        public List<Map<String, Object>> getItems() {
            return adapter().allItems().get().stream()
                    .map(entry -> {
                        var view = stackView(entry.stack());
                        view.put("slot", entry.slot());
                        return view;
                    })
                    .toList();
        }

        /** Counts occurrences of one item id.
         * @zh 统计某物品 ID 的数量。 */
        @ApiSince("0.1.0")
        public int countOf(String item) {
            return adapter().countOf(item);
        }

        /** Clicks a slot (button 0 = left, 1 = right; shift = quick move).
         * @zh 点击槽位（button 0 = 左键，1 = 右键；shift = 快速移动）。 */
        @ApiSince("0.1.0")
        public java.util.concurrent.CompletableFuture<Void> click(int slot,
                int button, boolean shift) {
            return adapter().click(slot, button, shift);
        }

        /** Quick-moves a slot to the other inventory.
         * @zh 将槽位物品快速移动到另一侧物品栏。 */
        @ApiSince("0.1.0")
        public java.util.concurrent.CompletableFuture<Void> quickMove(int slot) {
            return adapter().quickMove(slot);
        }

        /** Drops the stack from a slot.
         * @zh 丢弃槽位中的物品（all = 全部）。 */
        @ApiSince("0.1.0")
        public java.util.concurrent.CompletableFuture<Void> drop(int slot, boolean all) {
            return adapter().drop(slot, all);
        }

        /** Swaps a container slot with a hotbar slot.
         * @zh 将容器槽位与快捷栏槽位交换。 */
        @ApiSince("0.1.0")
        public java.util.concurrent.CompletableFuture<Void> swapHotbar(int slot, int hotbarIndex) {
            return adapter().swapHotbar(slot, hotbarIndex);
        }

        /** Closes the container screen.
         * @zh 关闭容器屏幕。 */
        @ApiSince("0.1.0")
        public java.util.concurrent.CompletableFuture<Void> close() {
            return adapter().close();
        }
    }

    /** Renders an ItemStackData as a plain script object shape. */
    static Map<String, Object> stackView(ink.tuanzi.neomacros.port.model.ItemStackData data) {
        var view = new LinkedHashMap<String, Object>();
        view.put("id", data.id());
        view.put("count", data.count());
        view.put("name", data.name());
        if (data.maxDamage() > 0) {
            view.put("damage", data.damage());
            view.put("maxDamage", data.maxDamage());
        }
        if (!data.lore().isEmpty()) {
            view.put("lore", data.lore());
        }
        if (!data.enchantments().isEmpty()) {
            view.put("enchantments", data.enchantments().stream()
                    .map(e -> Map.of("id", e.id(), "level", e.level()))
                    .toList());
        }
        return view;
    }
}
