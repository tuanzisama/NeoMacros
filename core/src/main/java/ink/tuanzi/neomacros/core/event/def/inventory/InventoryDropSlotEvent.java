package ink.tuanzi.neomacros.core.event.def.inventory;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the slot drop event, raised before the drop is applied so
 * synchronous subscribers can cancel it. Covers both drop paths: the menu
 * THROW click ({@code slot} is the menu slot, {@code -999} for a click
 * outside the window) and the hotbar Q key ({@code slot} is
 * {@code 36 + hotbar index}, kind {@code player}).
 *
 * @zh 槽位丢弃事件载荷（在丢弃生效前触发，同步订阅可取消）。覆盖两条丢弃路径：菜单 THROW 点击（{@code slot} 为菜单槽位，窗口外为 -999）与热栏 Q 键（{@code slot} 为 {@code 36 + 热栏序号}，kind 为 player）。
 * @zh-param kind 容器类别
 * @zh-param slot 丢弃源槽位号（-999 表示点击窗口外）
 * @zh-param all 是否丢弃整组
 */
@ApiSince("0.6.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "inventory:dropSlot", direction = ApiEvent.Direction.C2S,
        cancellable = true)
public record InventoryDropSlotEvent(String kind, int slot, boolean all) {
}
