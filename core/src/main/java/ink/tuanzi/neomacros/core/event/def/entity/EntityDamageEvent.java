package ink.tuanzi.neomacros.core.event.def.entity;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the entity damage event; fires when a tracked entity takes
 * damage. {@code health} is the entity's health after the loss.
 *
 * @zh 实体受伤时触发。{@code health} 为受伤后的生命值。
 * @zh-param entity 实体快照
 * @zh-param amount 伤害数值
 * @zh-param health 受伤后的生命值
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "entity:damage", direction = ApiEvent.Direction.LOCAL)
public record EntityDamageEvent(ink.tuanzi.neomacros.port.model.EntitySnapshot entity,
        float amount,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") float health) {
}
