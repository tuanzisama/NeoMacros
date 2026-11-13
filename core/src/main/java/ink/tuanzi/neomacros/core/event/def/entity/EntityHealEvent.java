package ink.tuanzi.neomacros.core.event.def.entity;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the entity heal event; {@code health} is the entity's health
 * after the gain.
 *
 * @zh 实体恢复时触发。{@code health} 为恢复后的生命值。
 * @zh-param entity 实体快照
 * @zh-param amount 恢复数值
 * @zh-param health 恢复后的生命值
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "entity:heal", direction = ApiEvent.Direction.LOCAL)
public record EntityHealEvent(ink.tuanzi.neomacros.port.model.EntitySnapshot entity,
        float amount,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") float health) {
}
