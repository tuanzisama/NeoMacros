package ink.tuanzi.neomacros.core.event.def.dialog;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the dialog close event, fired when the open dialog closes.
 *
 * @zh 对话框关闭事件载荷（打开的对话框关闭时触发）。
 * @zh-param kind 关闭的对话框类别
 */
@ApiSince("0.3.0")
@ApiMC(">=1.21.6")
@ApiLoader("*")
@ApiEvent(name = "dialog:close")
public record DialogCloseEvent(String kind) {
}
