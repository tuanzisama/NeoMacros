package ink.tuanzi.neomacros.core.event.def.packet;

import ink.tuanzi.neomacros.core.api.types.PacketBuffer;
import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the outgoing-packet interception, fired at the connection's
 * single send funnel before the packet reaches the wire. Synchronous
 * subscribers may cancel the send (a cancelled packet never leaves the
 * client) or rewrite {@code buffer} in place; the adapter re-decodes the
 * edited bytes and fails open when they no longer decode.
 *
 * <p>The buffer write window closes as soon as the synchronous dispatch
 * returns: writes inside the callback are applied to the packet, writes
 * after it raise. Reading keeps working for the lifetime of the cell.</p>
 *
 * <p>{@code buffer} is {@code undefined} when the packet has no standalone
 * codec; a packet without a buffer can still be cancelled.</p>
 *
 * @zh 上行包拦截事件载荷，在包上线前于连接唯一发送漏斗触发。同步订阅可取消发送（取消后包不离开客户端）或原地改写 {@code buffer}；适配器会重新解码被改写的字节，解码失败时放行原包。同步派发一返回写窗口即关闭：回调内写入会作用于该包，之后写入抛错；读在整个单元生命周期内始终可用。包无独立编解码器时 {@code buffer} 为 undefined，但仍可被取消。
 * @zh-param id namespaced 包 id（如 {@code minecraft:keep_alive}）
 * @zh-param flow 包自身流向（{@code clientbound} / {@code serverbound}）
 * @zh-param protocol 连接当前协议阶段（{@code handshake} / {@code status} / {@code login} / {@code configuration} / {@code play}）
 * @zh-param buffer 包缓冲区（同步窗口内可写，事件结束后只读）；无独立编解码器时为 undefined
 */
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "packet:send", direction = ApiEvent.Direction.C2S,
        cancellable = true, mutable = { "buffer" })
public record PacketSendEvent(
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") String id,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") String flow,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") String protocol,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") PacketBuffer buffer) {
}
