package ink.tuanzi.neomacros.core.event.def.entity;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the entity damage event; fires when a tracked entity takes
 * damage.
 *
 * @zh 实体受伤时触发。
 * @zh-param entity 实体快照
 * @zh-param amount 伤害数值
 */
@ApiSince("0.1.0")
@ApiEvent(name = "entity:damage")
public record EntityDamageEvent(ink.tuanzi.neomacros.port.model.EntitySnapshot entity, float amount) {
}
