package ink.tuanzi.neomacros.doc.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a record class as an event descriptor of the script event catalog.
 *
 * <p>Annotated records are registered in the event catalog under their
 * namespaced {@link #name()} (e.g. {@code player:join}). The flag
 * {@link #cancellable()} controls whether the event may be received through
 * the synchronous channel ({@code events.onSync}); it also drives the
 * documentation, which lists the sync channel only for cancellable events.
 * The invariant is {@code cancellable == true <=> direction() != LOCAL}.</p>
 *
 * <p>The record components form the event payload exactly as the script sees
 * it: point-in-time snapshot data, never live game references. Components
 * listed in {@link #mutable()} may be rewritten by synchronous subscribers;
 * their committed values travel back to the publisher.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ApiEvent {

    /** Namespaced event name used for subscribing and wildcard matching, e.g. {@code player:join}. */
    String name();

    /** Whether the event supports synchronous subscription and cancellation ({@code events.onSync}). */
    boolean cancellable() default false;

    /** The traffic direction of the interception point (client action, server display, or local state). */
    Direction direction() default Direction.LOCAL;

    /**
     * Payload component names a synchronous subscriber may rewrite on the
     * sync channel; committed values travel back to the publisher. Fields
     * outside this list are read-only on the mutable event face.
     */
    String[] mutable() default {};

    /**
     * The traffic direction of a script event: which side of the client link
     * the interception point sits on. The direction predicts what a
     * synchronous subscriber may do: {@code C2S} events intercept an upstream
     * action, {@code S2C} events intercept incoming display content, and
     * {@code LOCAL} events are client-derived observations that can never be
     * cancelled.
     *
     * @zh 脚本事件的流向：拦截点位于客户端链路的哪一侧。方向预示同步订阅者能做什么：{@code C2S} 拦截上行动作，{@code S2C} 拦截到达的显示内容，{@code LOCAL} 是客户端本地派生的观测，永不可取消。
     */
    enum Direction {

        /**
         * Client-to-server action interception: the client is about to act
         * (press a key, send chat, attack or interact). Cancelling suppresses
         * the action before it happens.
         *
         * @zh 客户端上行动作拦截：客户端即将做出动作（按键、发送聊天、攻击或交互）。取消即在该动作发生前抑制它。
         */
        C2S,

        /**
         * Server-to-client display interception: server-originated content is
         * about to land (chat, titles, action bar, sign editor). Cancelling
         * suppresses the display; whitelisted fields may be rewritten
         * locally.
         *
         * @zh 服务器下行显示拦截：服务器发来的内容即将落地（聊天、标题、动作栏、告示牌）。取消即抑制显示；白名单字段可在本地改写。
         */
        S2C,

        /**
         * Client-local state: changes the client derives or observes itself
         * (tick, vitals, world state, screen transitions). Synchronous
         * subscription is not available.
         *
         * @zh 客户端本地状态：客户端自行派生或感知的变化（tick、生命体征、世界状态、界面切换）。不可用同步订阅。
         */
        LOCAL
    }
}
