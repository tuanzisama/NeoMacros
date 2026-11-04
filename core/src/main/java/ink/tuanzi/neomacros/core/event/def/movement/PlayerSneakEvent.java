package ink.tuanzi.neomacros.core.event.def.movement;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the sneaking toggle event; fires on the enter/leave edge of the state.
 *
 * @zh 潜行状态变化时触发。
 * @zh-param sneaking 当前的潜行状态
 */
@ApiSince("0.2.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:sneak")
public record PlayerSneakEvent(boolean sneaking) {
}
