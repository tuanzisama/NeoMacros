package ink.tuanzi.neomacros.core.event.def.screen;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the screen change event, fired on every screen transition:
 * opens, closes, and direct switches between screens. The generic channel
 * for anything on screen (inventory, dialog, and menu screens included);
 * domain events add their own detail.
 *
 * @zh 屏幕变化事件载荷（任何屏幕切换都触发：打开、关闭与直接切换；背包、对话框与菜单界面同属此通道，领域事件另带细节）。
 * @zh-param phase 变化相位（open/switch/close）
 * @zh-param screen 屏幕 MC 类名（关闭为 null）
 * @zh-param kind 屏幕类别
 * @zh-param title 屏幕标题
 */
@ApiSince("0.3.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "screen:change", direction = ApiEvent.Direction.LOCAL)
public record ScreenChangeEvent(String phase, String screen, String kind, String title) {
}
