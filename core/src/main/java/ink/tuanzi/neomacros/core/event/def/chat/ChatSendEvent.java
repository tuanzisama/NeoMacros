package ink.tuanzi.neomacros.core.event.def.chat;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the outgoing chat message event; synchronous subscribers may
 * cancel the send.
 *
 * @zh 发出聊天消息事件载荷；同步订阅可取消发送。
 * @zh-param message 待发送的消息文本
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "chat:send", cancellable = true)
public record ChatSendEvent(String message) {
}
