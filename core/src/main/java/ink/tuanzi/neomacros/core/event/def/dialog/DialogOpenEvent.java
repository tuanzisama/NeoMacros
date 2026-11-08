package ink.tuanzi.neomacros.core.event.def.dialog;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the dialog open event, fired when the server shows a dialog or
 * a script opens one.
 *
 * @zh 对话框打开事件载荷（服务端弹出对话框或脚本打开时触发）。
 * @zh-param kind 对话框类别（notice/confirmation/multi_action/list/server_links）
 * @zh-param title 对话框标题
 * @zh-param canCloseWithEscape 是否可按 Esc 关闭
 * @zh-param actions 动作按钮列表
 */
@ApiSince("0.3.0")
@ApiMC(">=1.21.6")
@ApiLoader("*")
@ApiEvent(name = "dialog:open")
public record DialogOpenEvent(String kind, String title, boolean canCloseWithEscape,
                              java.util.List<ink.tuanzi.neomacros.port.DialogPort.DialogActionInfo> actions) {
}
