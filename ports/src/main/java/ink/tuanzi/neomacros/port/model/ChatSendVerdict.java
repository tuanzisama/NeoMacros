package ink.tuanzi.neomacros.port.model;

/**
 * Combined verdict of the chat-send sinks: {@code canceled} suppresses the
 * outgoing message; a non-null {@code message} replaces the text that goes on
 * the wire.
 *
 * @zh 聊天发送的综合裁决：{@code canceled} 吞掉上行消息；{@code message} 非空时替换实际上行的文本。
 */
public record ChatSendVerdict(boolean canceled, String message) {

    /** The pass-through verdict: send the original text. */
    public static final ChatSendVerdict PASS = new ChatSendVerdict(false, null);

    /** The cancel verdict: swallow the outgoing message. */
    public static final ChatSendVerdict CANCEL = new ChatSendVerdict(true, null);
}
