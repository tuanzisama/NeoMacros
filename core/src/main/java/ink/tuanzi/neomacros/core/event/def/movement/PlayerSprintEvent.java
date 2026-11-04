package ink.tuanzi.neomacros.core.event.def.movement;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the sprinting toggle event; fires on the enter/leave edge of the state.
 *
 * @zh 疾跑状态变化时触发。
 * @zh-param sprinting 当前的疾跑状态
 */
@ApiSince("0.2.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:sprint")
public record PlayerSprintEvent(boolean sprinting) {
}
