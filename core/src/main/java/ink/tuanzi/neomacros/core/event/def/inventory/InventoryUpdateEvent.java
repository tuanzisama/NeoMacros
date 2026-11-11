package ink.tuanzi.neomacros.core.event.def.inventory;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the inventory update event, fired on every server slot sync:
 * single-slot updates ({@code mode} {@code slot}) and full menu resyncs
 * ({@code mode} {@code all}). Menu id 0 covers the player inventory while no
 * container screen is open, so item pickup and crafting surface here too.
 *
 * @zh 物品栏更新事件载荷（服务端槽位同步触发：单槽为 {@code slot}，全量重同步为 {@code all}；菜单 id 0 覆盖玩家背包，拾取与合成同样触发）。
 * @zh-param mode 更新模式（slot/all）
 * @zh-param containerId 菜单 id
 * @zh-param kind 容器类别（背包为 player）
 * @zh-param slot 变化槽位号（全量同步为 -1）
 */
@ApiSince("0.3.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "inventory:update", direction = ApiEvent.Direction.LOCAL)
public record InventoryUpdateEvent(String mode, int containerId, String kind, int slot) {
}
