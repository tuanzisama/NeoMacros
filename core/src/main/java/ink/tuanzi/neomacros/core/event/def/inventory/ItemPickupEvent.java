package ink.tuanzi.neomacros.core.event.def.inventory;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ItemStackData;

/**
 * Payload of the item pickup event, fired while the client processes a
 * pickup packet and before the inventory is updated. {@code item} is a
 * snapshot whose count is the picked-up amount; mutating it does not change
 * the dropped stack or the inventory.
 *
 * @zh 物品拾取事件载荷（客户端处理拾取包期间、背包更新之前触发）。{@code item} 是数量=本次拾取量的快照；修改它不影响掉落物或背包。
 * @zh-param item 拾取到的物品快照（数量为本次拾取量）
 * @zh-param amount 本次拾取数量
 */
@ApiSince("0.6.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "item:pickup", direction = ApiEvent.Direction.LOCAL)
public record ItemPickupEvent(ItemStackData item, int amount) {
}
