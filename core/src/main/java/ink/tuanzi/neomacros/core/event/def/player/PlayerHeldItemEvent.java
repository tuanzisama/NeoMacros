package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ItemStackData;

/**
 * Payload of the held item change event.
 *
 * @zh 手持物品变化事件载荷。
 * @zh-param previous 先前手持物品快照（可为 null）
 * @zh-param current 当前手持物品快照（可为 null）
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:heldItem", direction = ApiEvent.Direction.LOCAL)
public record PlayerHeldItemEvent(ItemStackData previous, ItemStackData current) {
}
