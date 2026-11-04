package ink.tuanzi.neomacros.core.event.def.movement;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the gliding toggle event; fires on the enter/leave edge of the state.
 *
 * @zh 鞘翅滑翔状态变化时触发。
 * @zh-param gliding 当前的滑翔状态
 */
@ApiSince("0.2.0")
@ApiEvent(name = "player:gliding")
public record PlayerGlidingEvent(boolean gliding) {
}
