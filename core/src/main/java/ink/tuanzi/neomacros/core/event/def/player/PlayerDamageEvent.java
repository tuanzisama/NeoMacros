package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the local player damage event; carries the lost amount and the
 * resulting health.
 *
 * @zh 本地玩家受伤事件载荷；携带损失量与受伤后的生命值。
 * @zh-param amount 损失的生命值
 * @zh-param health 受伤后的生命值
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:damage")
public record PlayerDamageEvent(double amount, double health) {
}
