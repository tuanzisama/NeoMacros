package ink.tuanzi.neomacros.core.event.def.inventory;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the inventory open event, fired when a container screen opens
 * (the player inventory screen included, kind {@code player}).
 *
 * @zh 物品栏打开事件载荷（容器屏幕打开时触发，玩家背包为 {@code player}）。
 * @zh-param title 容器标题
 * @zh-param kind 容器类别
 * @zh-param size 槽位总数
 * @zh-param containerId 菜单 id
 */
@ApiSince("0.3.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "inventory:open", direction = ApiEvent.Direction.LOCAL)
public record InventoryOpenEvent(String title, String kind, int size, int containerId) {
}
