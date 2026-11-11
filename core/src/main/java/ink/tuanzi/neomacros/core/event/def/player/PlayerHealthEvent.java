package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the local player health change event.
 *
 * @zh 本地玩家生命值变化事件载荷。
 * @zh-param health 变化后的生命值
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:health", direction = ApiEvent.Direction.LOCAL)
public record PlayerHealthEvent(double health) {
}
