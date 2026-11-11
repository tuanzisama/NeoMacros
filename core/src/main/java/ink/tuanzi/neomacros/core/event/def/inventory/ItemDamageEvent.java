package ink.tuanzi.neomacros.core.event.def.inventory;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ItemStackData;

/**
 * Payload of the item durability change event: the same item in one slot
 * kept its identity but changed durability (mining, combat, armor wear).
 * {@code damage} is the current durability value, not the delta. Only the
 * durability axis is reported here; other stack changes keep flowing through
 * the held-item and armor events.
 *
 * @zh 物品耐久变化事件载荷：同一槽位的同一物品身份不变、仅耐久变化（挖掘、战斗、护甲磨损）。{@code damage} 为当前耐久值而非增量。这里只报告耐久这一维；其它物品差异仍走主手/护甲变更事件。
 * @zh-param slot 槽位标识（main/offhand/head/chest/legs/feet）
 * @zh-param item 变化后的物品
 * @zh-param damage 当前耐久值
 */
@ApiSince("0.6.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "item:damage", direction = ApiEvent.Direction.LOCAL)
public record ItemDamageEvent(String slot, ItemStackData item, int damage) {
}
