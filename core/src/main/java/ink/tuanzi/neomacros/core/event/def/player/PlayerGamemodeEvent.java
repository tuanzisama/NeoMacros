package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the gamemode change event.
 *
 * @zh 游戏模式变化事件载荷。
 * @zh-param previous 先前模式（survival/creative/adventure/spectator）
 * @zh-param current 当前模式
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:gamemode")
public record PlayerGamemodeEvent(String previous, String current) {
}
