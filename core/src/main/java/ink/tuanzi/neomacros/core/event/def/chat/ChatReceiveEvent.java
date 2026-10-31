package ink.tuanzi.neomacros.core.event.def.chat;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ChatComponent;

/**
 * Payload of the incoming chat message event; carries the message as a
 * neutral chat component.
 *
 * @zh 收到聊天消息事件载荷；消息以中立聊天组件承载。
 * @zh-param message 显示的消息内容
 */
@ApiSince("0.1.0")
@ApiEvent(name = "chat:receive")
public record ChatReceiveEvent(ChatComponent message) {
}
