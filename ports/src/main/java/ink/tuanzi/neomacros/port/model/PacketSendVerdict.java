package ink.tuanzi.neomacros.port.model;

/**
 * The outcome of one outgoing-packet interception: whether the packet must be
 * dropped, and the raw replacement packet when a subscriber swapped the
 * buffer (null keeps the original packet).
 *
 * @param canceled    whether the packet must not reach the wire
 * @param replacement raw replacement packet, or null to keep the original
 * @zh 一次上行包拦截的裁决：是否丢包，以及订阅者替换缓冲区后的原始替换包（null 表示保留原包）。
 */
public record PacketSendVerdict(boolean canceled, Object replacement) {

    /** The pass-through verdict: the original packet goes on the wire unchanged. */
    public static final PacketSendVerdict PASS = new PacketSendVerdict(false, null);
}
