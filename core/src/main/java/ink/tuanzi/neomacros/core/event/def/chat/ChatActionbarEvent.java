package ink.tuanzi.neomacros.core.event.def.chat;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ChatComponent;

/**
 * Payload of the action-bar display event; synchronous subscribers may cancel
 * the message ({@code true}) or return {@code {text: "..."}} to replace it.
 *
 * @zh 动作栏显示事件载荷；同步订阅返回 truthy 取消显示，或返回 {@code {text:"..."}} 替换文本。
 * @zh-param text 显示的文本
 * @zh-param source 来源：server（服务器动作栏包）或 local（客户端本地 overlay 提示）
 */
@ApiSince("0.4.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "chat:actionbar", cancellable = true)
public record ChatActionbarEvent(ChatComponent text, String source) {
}
