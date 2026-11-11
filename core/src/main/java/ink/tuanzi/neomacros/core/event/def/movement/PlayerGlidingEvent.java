package ink.tuanzi.neomacros.core.event.def.movement;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the gliding toggle event; fires on the enter/leave edge of the state.
 *
 * @zh 鞘翅滑翔状态变化时触发。
 * @zh-param gliding 当前的滑翔状态
 */
@ApiSince("0.2.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:gliding", direction = ApiEvent.Direction.LOCAL)
public record PlayerGlidingEvent(boolean gliding) {
}
