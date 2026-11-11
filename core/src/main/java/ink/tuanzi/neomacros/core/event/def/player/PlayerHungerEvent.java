package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the local player hunger change event.
 *
 * @zh 本地玩家饥饿值变化事件载荷。
 * @zh-param hunger 变化后的饥饿值
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:hunger", direction = ApiEvent.Direction.LOCAL)
public record PlayerHungerEvent(int hunger) {
}
