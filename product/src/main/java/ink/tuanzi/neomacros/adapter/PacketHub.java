package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.core.config.CoreOptions;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.port.model.PacketRef;
import ink.tuanzi.neomacros.port.model.PacketSendVerdict;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Fan-out point for the two packet event sources on the client's network
 * connection. {@link ink.tuanzi.neomacros.adapter.mixin.ConnectionMixin}
 * calls in from {@code channelRead0} (netty thread) and from the private
 * {@code sendPacket} funnel (any thread).
 *
 * <p>Both paths run the G-002 §3 gates before any payload work: the
 * {@link CoreOptions#packetIgnore} suppression list (L2), then the
 * {@link EventBus#hasDispatchTarget} fast path, so an unsubscribed or
 * suppressed packet costs one cached id lookup plus a boolean. Receive
 * delivery hops onto the game main thread; send interception is only ever
 * fired from the main thread (the mixin defers off-thread sends first),
 * so the synchronous subscribers run inline with their verdict intact.</p>
 *
 * @zh 客户端网络连接上两个包事件源的扇出点。{@link ink.tuanzi.neomacros.adapter.mixin.ConnectionMixin} 从 {@code channelRead0}（netty 线程）与私有 {@code sendPacket} 漏斗（任意线程）调入。两条路径在任何载荷工作前先跑 G-002 §3 闸门：{@link CoreOptions#packetIgnore} 抑制名单（L2），然后 {@link EventBus#hasDispatchTarget} 快路径——无订阅或被抑制的包只花一次缓存 id 查找加一个布尔。收包投递跃迁到游戏主线程；发包拦截只会在主线程触发（mixin 先把非主线程发送推迟），因此同步订阅者内联执行、裁决完整可用。
 */
public final class PacketHub {

    private static volatile EventBus bus;
    private static volatile CoreOptions options;
    private static final List<Consumer<PacketRef>> RECEIVE_SINKS =
            new CopyOnWriteArrayList<>();
    private static final List<Function<PacketRef, PacketSendVerdict>> SEND_SINKS =
            new CopyOnWriteArrayList<>();
    /** Packet type to its namespaced id: zero-alloc after the first hit. */
    private static final Map<PacketType<?>, String> IDS = new ConcurrentHashMap<>();
    /**
     * The replacement verdict of the send currently being dispatched on
     * this thread. Thread-local because {@code sendPacket} can run on the
     * main thread and the netty thread at the same time: a shared slot
     * would let one call's head-clear erase another call's replacement
     * before its argument load reads it.
     */
    private static final ThreadLocal<PacketSendVerdict> PENDING = new ThreadLocal<>();

    private PacketHub() {
    }

    /** Binds the event bus behind the {@code hasDispatchTarget} fast path.
     * @zh 绑定快路径背后的事件总线。 */
    public static void bind(EventBus eventBus) {
        bus = eventBus;
    }

    /** Supplies the config holding the suppression list.
     * @zh 提供持有抑制名单的配置。 */
    public static void configure(CoreOptions coreOptions) {
        options = coreOptions;
    }

    /** Registers an incoming-packet observer (main-thread delivery).
     * @zh 注册下行包观测器（主线程投递）。 */
    public static void onReceive(Consumer<PacketRef> sink) {
        RECEIVE_SINKS.add(sink);
    }

    /** Registers an outgoing-packet interceptor (inline on the main thread).
     * @zh 注册上行包拦截器（主线程内联执行）。 */
    public static void onSend(Function<PacketRef, PacketSendVerdict> sink) {
        SEND_SINKS.add(sink);
    }

    /** Clears every sink (test isolation). */
    static void reset() {
        RECEIVE_SINKS.clear();
        SEND_SINKS.clear();
    }

    /**
     * Delivers one observed incoming packet: suppression list, fast path,
     * then a hop onto the main thread for the sinks. Called from
     * {@code channelRead0} on the netty thread.
     *
     * @zh 投递一个观测到的下行包：抑制名单、快路径，然后跃迁主线程执行各 sink。由 netty 线程上的 {@code channelRead0} 调用。
     */
    public static void fireReceive(Connection connection, Packet<?> packet) {
        if (RECEIVE_SINKS.isEmpty()) {
            return;
        }
        PacketType<?> type = packet.type();
        String id = idOf(type);
        if (ignored(id)) {
            return;
        }
        EventBus bound = bus;
        if (bound == null || !bound.hasDispatchTarget("packet:receive")) {
            return;
        }
        PacketRef ref = new PacketRef(id, type.flow().id(), protocolOf(connection), packet);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> {
            for (Consumer<PacketRef> sink : RECEIVE_SINKS) {
                sink.accept(ref);
            }
        });
    }

    /**
     * Whether one outgoing packet needs interception at all (sinks
     * registered, a live {@code packet:send} subscriber, not suppressed).
     * Cheap enough to ask from any thread before the mixin decides to
     * defer or fire.
     *
     * @zh 一个上行包是否根本需要拦截（已注册 sink、存在活跃 {@code packet:send} 订阅者、未被抑制）。足够廉价，可在 mixin 决定推迟或触发前从任意线程询问。
     */
    public static boolean interceptSend(Connection connection, Packet<?> packet) {
        if (SEND_SINKS.isEmpty()) {
            return false;
        }
        EventBus bound = bus;
        if (bound == null || !bound.hasDispatchTarget("packet:send")) {
            return false;
        }
        return !ignored(idOf(packet.type()));
    }

    /**
     * Fans one outgoing packet out to the send sinks and combines their
     * verdicts (cancel ORs, the last non-null replacement wins). Called on
     * the game main thread only.
     *
     * @zh 把一个上行包扇出给各 send sink 并合并裁决（取消取或、最后一个非空替换胜出）。只在游戏主线程调用。
     */
    public static PacketSendVerdict fireSend(Connection connection, Packet<?> packet) {
        PacketType<?> type = packet.type();
        PacketRef ref = new PacketRef(idOf(type), type.flow().id(),
                protocolOf(connection), packet);
        boolean canceled = false;
        Object replacement = null;
        for (Function<PacketRef, PacketSendVerdict> sink : SEND_SINKS) {
            PacketSendVerdict verdict = sink.apply(ref);
            if (verdict == null) {
                continue;
            }
            canceled |= verdict.canceled();
            if (verdict.replacement() != null) {
                replacement = verdict.replacement();
            }
        }
        if (replacement != null) {
            return new PacketSendVerdict(canceled, replacement);
        }
        return canceled ? new PacketSendVerdict(true, null) : PacketSendVerdict.PASS;
    }

    /**
     * Records (or with null clears) the replacement verdict for the send
     * in flight on this thread; the {@code sendPacket} argument loads read
     * it back through {@link #applyPending}.
     *
     * @zh 记录（null 时清除）本线程进行中发送的替换裁决；{@code sendPacket} 的参数加载经 {@link #applyPending} 读回。
     */
    public static void setPending(PacketSendVerdict verdict) {
        PENDING.set(verdict);
    }

    /**
     * Swaps in the pending replacement for one loaded {@code sendPacket}
     * argument, or returns the argument untouched when no replacement is
     * pending.
     *
     * @zh 为一个已加载的 {@code sendPacket} 参数换入待定替换；无待定替换时原样返回参数。
     */
    public static Packet<?> applyPending(Packet<?> packet) {
        PacketSendVerdict verdict = PENDING.get();
        if (verdict != null && verdict.replacement() != null) {
            @SuppressWarnings("unchecked")
            Packet<?> replacement = (Packet<?>) verdict.replacement();
            return replacement;
        }
        return packet;
    }

    /** The connection's current protocol phase id (empty without a listener).
     * @zh 连接当前协议阶段 id（无监听器时为空）。 */
    private static String protocolOf(Connection connection) {
        PacketListener listener = connection.getPacketListener();
        return listener != null ? listener.protocol().id() : "";
    }

    /** Whether the suppression list drops this packet id.
     * @zh 抑制名单是否丢弃该包 id。 */
    private static boolean ignored(String id) {
        CoreOptions current = options;
        java.util.List<String> ignore = current != null
                ? current.packetIgnore : CoreOptions.DEFAULT_PACKET_IGNORE;
        return CoreOptions.packetIgnored(ignore, id);
    }

    /** The namespaced id of one packet type, cached for zero-alloc reuse.
     * @zh 一个包类型的 namespaced id，缓存以零分配复用。 */
    private static String idOf(PacketType<?> type) {
        return IDS.computeIfAbsent(type, key -> key.id().toString());
    }
}
