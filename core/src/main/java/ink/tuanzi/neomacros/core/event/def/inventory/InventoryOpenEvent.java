package ink.tuanzi.neomacros.core.event.def.inventory;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the inventory open event, fired when a container screen opens
 * (the player inventory screen included, kind {@code player}). Synchronous
 * subscribers may cancel it; a cancelled open closes the container right
 * away, so the server stays authoritative (the container may exist for at
 * most one client tick). {@code screen} is the MC class name of the opening
 * screen; it is absent when no screen snapshot is available.
 *
 * @zh 物品栏打开事件载荷（容器屏幕打开时触发，玩家背包为 {@code player}）。同步订阅可取消：取消后立即关闭该容器，服务端保持权威（容器最多存在一个客户端 tick）。{@code screen} 为打开中屏幕的 MC 类名；无屏幕快照时为 undefined。
 * @zh-param title 容器标题
 * @zh-param kind 容器类别
 * @zh-param size 槽位总数
 * @zh-param containerId 菜单 id
 * @zh-param screen 打开中屏幕的 MC 类名（如 {@code GenericContainerScreen}）；未知时为 undefined
 */
@ApiSince("0.3.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "inventory:open", direction = ApiEvent.Direction.LOCAL,
        cancellable = true)
public record InventoryOpenEvent(String title, String kind, int size, int containerId,
        String screen) {
}
