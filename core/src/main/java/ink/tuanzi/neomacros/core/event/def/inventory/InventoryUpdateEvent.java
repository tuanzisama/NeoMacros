package ink.tuanzi.neomacros.core.event.def.inventory;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ItemStackData;

/**
 * Payload of the inventory update event, fired on every server slot sync:
 * single-slot updates ({@code mode} {@code slot}) with the previous and
 * incoming stacks, full menu resyncs ({@code mode} {@code all}) without
 * stack data, and cursor-stack syncs ({@code mode} {@code held}). Menu id 0
 * covers the player inventory while no container screen is open, so item
 * pickup and crafting surface here too. Fired before the menu applies the
 * incoming stack, so synchronous readers still see the previous state.
 *
 * @zh 物品栏更新事件载荷（服务端槽位同步触发：单槽为 {@code slot} 并带新旧物品，全量重同步为 {@code all}（无物品数据），光标堆同步为 {@code held}；菜单 id 0 覆盖玩家背包，拾取与合成同样触发）。事件在菜单写入新物品之前触发，同步读取仍是旧状态。
 * @zh-param mode 更新模式（slot/all/held）
 * @zh-param containerId 菜单 id
 * @zh-param kind 容器类别（背包为 player）
 * @zh-param slot 变化槽位号（全量同步与光标堆为 -1）
 * @zh-param oldStack 变化前的物品；全量同步与空槽为 undefined
 * @zh-param newStack 变化后的物品；全量同步与空槽为 undefined
 */
@ApiSince("0.3.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "inventory:update", direction = ApiEvent.Direction.LOCAL)
public record InventoryUpdateEvent(String mode, int containerId, String kind, int slot,
        ItemStackData oldStack, ItemStackData newStack) {
}
