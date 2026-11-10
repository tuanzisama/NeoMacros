package ink.tuanzi.neomacros.core.event.def.chat;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ChatComponent;

/**
 * Payload of the title display event (main title or subtitle); synchronous
 * subscribers may cancel the display ({@code true}) or return
 * {@code {text: "..."}} to replace the text.
 *
 * @zh 标题显示事件载荷（大标题或副标题）；同步订阅返回 truthy 取消显示，或返回 {@code {text:"..."}} 替换文本。
 * @zh-param kind 显示类型：title（大标题）或 subtitle（副标题）
 * @zh-param text 显示的文本
 */
@ApiSince("0.4.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "chat:title", cancellable = true)
public record ChatTitleEvent(String kind, ChatComponent text) {
}
