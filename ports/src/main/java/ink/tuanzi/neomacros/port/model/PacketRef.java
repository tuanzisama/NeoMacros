package ink.tuanzi.neomacros.port.model;

/**
 * One packet observed at the client's network connection, in neutral shape:
 * its namespaced packet id, the packet's own flow, the connection's current
 * protocol phase, and the raw packet object the adapter's codec layer needs.
 *
 * <p>The raw {@code packet} carrier never crosses into script payloads; it is
 * the adapter-side handle used for encoding, decoding and sending.</p>
 *
 * @param id      namespaced packet id, e.g. {@code minecraft:keep_alive}
 * @param flow    {@code clientbound} or {@code serverbound}
 * @param protocol connection phase id: {@code handshake}, {@code status},
 *                 {@code login}, {@code configuration} or {@code play}
 * @param packet  raw host packet object (adapter scope only)
 * @zh 客户端网络连接处观测到的一个包（中立形态）：namespaced 包 id、包自身流向、连接当前协议阶段，以及编解码层所需的原始包对象。{@code packet} 原始载体不进入脚本载荷。
 */
public record PacketRef(String id, String flow, String protocol, Object packet) {
}
