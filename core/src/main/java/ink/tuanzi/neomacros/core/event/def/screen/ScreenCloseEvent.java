package ink.tuanzi.neomacros.core.event.def.screen;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the screen close event.
 *
 * @zh 屏幕关闭事件载荷。
 * @zh-param title 关闭的屏幕标题
 * @zh-param screen 关闭的屏幕 MC 类名
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "screen:close", direction = ApiEvent.Direction.LOCAL)
public record ScreenCloseEvent(String title, String screen) {
}
