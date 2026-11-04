package ink.tuanzi.neomacros.core.event.def.screen;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the screen close event.
 *
 * @zh 屏幕关闭事件载荷。
 * @zh-param title 关闭的屏幕标题
 */
@ApiSince("0.1.0")
@ApiEvent(name = "screen:close")
public record ScreenCloseEvent(String title) {
}
