package ink.tuanzi.neomacros.core.event.def.packet;

import ink.tuanzi.neomacros.core.api.types.PacketBuffer;
import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the incoming-packet observation, published on the game main
 * thread before the packet is handled. The event is an observation only:
 * it is not cancellable and carries no raw packet object, so every
 * subscriber sees the same frozen face regardless of permissions.
 *
 * <p>{@code id} is the namespaced packet id, {@code flow} is the packet's
 * own direction ({@code clientbound} / {@code serverbound}) and
 * {@code protocol} is the connection's current phase. {@code buffer} is
 * {@code undefined} when the packet has no standalone codec (bundles and
 * other codec-less packets); the read-only cell stays readable after the
 * event returns.</p>
 *
 * @zh 下行包观测事件载荷，在包被处理前于游戏主线程发布。仅为观测：不可取消、不携带原始包对象，因此所有订阅者看到同一冻结载荷面。{@code id} 为 namespaced 包 id，{@code flow} 为包自身流向，{@code protocol} 为连接当前协议阶段；包无独立编解码器时 {@code buffer} 为 undefined（只读单元在事件返回后仍可读）。
 * @zh-param id namespaced 包 id（如 {@code minecraft:keep_alive}）
 * @zh-param flow 包自身流向（{@code clientbound} / {@code serverbound}）
 * @zh-param protocol 连接当前协议阶段（{@code handshake} / {@code status} / {@code login} / {@code configuration} / {@code play}）
 * @zh-param buffer 只读包缓冲区；无独立编解码器时为 undefined
 */
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "packet:receive", direction = ApiEvent.Direction.S2C, cancellable = false)
public record PacketReceiveEvent(
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") String id,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") String flow,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") String protocol,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") PacketBuffer buffer) {
}
