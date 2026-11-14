package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.PacketHub;
import ink.tuanzi.neomacros.port.model.PacketSendVerdict;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The two packet event hooks on the client's network connection (G-002
 * §7.2, four-node javap-verified targets; the task card's
 * {@code ClientPacketListenerMixin}落点 is superseded per §1偏离声明):
 *
 * <ul>
 *   <li>{@code channelRead0} HEAD (netty thread): the receive source —
 *       {@link PacketHub} runs the suppression list and the
 *       {@code hasDispatchTarget} fast path, then hops the delivery onto
 *       the game main thread. Receive is observation-only, so the hook
 *       never cancels.</li>
 *   <li>private {@code sendPacket} HEAD: the single send funnel every
 *       public {@code send} overload reaches. Off-thread sends are
 *       deferred onto the main thread (cancel + re-send, G-002 §7.3 甲)
 *       so the synchronous {@code packet:send} subscribers always run
 *       inline there; on the main thread the sinks fire and either cancel
 *       the packet or stage a replacement. The two
 *       {@code @ModifyVariable} argument loads (both branches of the
 *       funnel: in-event-loop and lambda-capture, present on all four
 *       nodes) swap in the staged replacement.</li>
 * </ul>
 *
 * @zh 客户端网络连接上的两个包事件钩子（G-002 §7.2，四节点 javap 已证目标；任务卡的 {@code ClientPacketListenerMixin} 落点按 §1 偏离声明由本文件取代）。{@code channelRead0} HEAD（netty 线程）为收包源：{@link PacketHub} 跑抑制名单与 {@code hasDispatchTarget} 快路径，再把投递跃迁到游戏主线程；收包纯观测、永不取消。私有 {@code sendPacket} HEAD 是所有公有 {@code send} 重载到达的唯一漏斗：非主线程发送先推迟到主线程（取消 + 重发，G-002 §7.3 甲），使同步 {@code packet:send} 订阅者总在主线程内联执行；主线程上各 sink 触发后或取消该包、或暂存替换。两处 {@code @ModifyVariable} 参数加载（漏斗两个分支：事件循环内与 lambda 捕获，四节点均在）换入暂存的替换。
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"))
    private void neomacros$onReceive(ChannelHandlerContext ctx, Packet<?> packet,
            CallbackInfo ci) {
        PacketHub.fireReceive((Connection) (Object) this, packet);
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void neomacros$onSend(Packet<?> packet, ChannelFutureListener listener,
            boolean flush, CallbackInfo ci) {
        PacketHub.setPending(null);
        Connection connection = (Connection) (Object) this;
        if (!PacketHub.interceptSend(connection, packet)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && !minecraft.isSameThread()) {
            // G-002 §7.3 甲: never block the network thread waiting for a
            // verdict — re-send the whole packet from the main-thread pump,
            // where the sinks fire inline (and the replacement loads below
            // apply).
            ci.cancel();
            minecraft.execute(() -> connection.send(packet, listener, flush));
            return;
        }
        PacketSendVerdict verdict = PacketHub.fireSend(connection, packet);
        if (verdict.canceled()) {
            ci.cancel();
            return;
        }
        if (verdict.replacement() != null) {
            PacketHub.setPending(verdict);
        }
    }

    // The funnel loads its packet argument twice (in-event-loop call and
    // lambda capture). Swap the replacement at both loads; with the
    // deferral above, only the lambda-capture load runs under active
    // interception, but covering both keeps every path consistent.
    @ModifyVariable(method = "sendPacket", at = @At("LOAD"), argsOnly = true, ordinal = 0)
    private Packet<?> neomacros$replaceSendFirst(Packet<?> packet) {
        return PacketHub.applyPending(packet);
    }

    @ModifyVariable(method = "sendPacket", at = @At("LOAD"), argsOnly = true, ordinal = 1)
    private Packet<?> neomacros$replaceSendSecond(Packet<?> packet) {
        return PacketHub.applyPending(packet);
    }
}
