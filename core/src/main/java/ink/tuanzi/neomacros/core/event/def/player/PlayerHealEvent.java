package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the local player heal event; carries the regained amount and
 * the resulting health.
 *
 * @zh 本地玩家治疗事件载荷；携带恢复量与治疗后的生命值。
 * @zh-param amount 恢复的生命值
 * @zh-param health 治疗后的生命值
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:heal", direction = ApiEvent.Direction.LOCAL)
public record PlayerHealEvent(double amount, double health) {
}
