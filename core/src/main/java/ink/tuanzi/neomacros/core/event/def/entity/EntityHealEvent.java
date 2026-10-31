package ink.tuanzi.neomacros.core.event.def.entity;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the entity heal event.
 *
 * @zh 实体恢复时触发。
 * @zh-param entity 实体快照
 * @zh-param amount 恢复数值
 */
@ApiSince("0.1.0")
@ApiEvent(name = "entity:heal")
public record EntityHealEvent(ink.tuanzi.neomacros.port.model.EntitySnapshot entity, float amount) {
}
