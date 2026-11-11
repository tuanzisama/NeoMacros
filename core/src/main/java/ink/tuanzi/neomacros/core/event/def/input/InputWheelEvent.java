package ink.tuanzi.neomacros.core.event.def.input;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the mouse wheel event; positive amounts scroll up.
 *
 * @zh 鼠标滚轮事件载荷；正值表示向上滚动。
 * @zh-param amount 滚动偏移量
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "input:wheel", direction = ApiEvent.Direction.LOCAL)
public record InputWheelEvent(double amount) {
}
