package ink.tuanzi.neomacros.core.event.def.inventory;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the inventory close event, fired when an open container screen
 * closes.
 *
 * @zh 物品栏关闭事件载荷（打开的容器屏幕关闭时触发）。
 * @zh-param kind 关闭的容器类别
 * @zh-param title 关闭的容器标题
 */
@ApiSince("0.3.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "inventory:close", direction = ApiEvent.Direction.LOCAL)
public record InventoryCloseEvent(String kind, String title) {
}
