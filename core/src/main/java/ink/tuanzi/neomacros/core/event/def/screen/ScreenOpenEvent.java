package ink.tuanzi.neomacros.core.event.def.screen;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the screen open event.
 *
 * @zh 屏幕打开事件载荷。
 * @zh-param title 屏幕标题
 * @zh-param kind 屏幕类型
 * @zh-param screen 屏幕 MC 类名
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "screen:open", direction = ApiEvent.Direction.LOCAL)
public record ScreenOpenEvent(String title, String kind, String screen) {
}
