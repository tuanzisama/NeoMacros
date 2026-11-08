package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Handle of one event subscription; returned to scripts by
 * {@code events.on / once / onSync}. Unsubscribing through the handle removes
 * the subscription from dispatch; module dispose iterates its handles the same
 * way.
 *
 * @zh 一条事件订阅的句柄；由 {@code events.on / once / onSync} 返回给脚本。经句柄退订即从分发中移除；模块卸载按同一方式遍历其句柄。
 */
@ApiClass(name = "Subscription", kind = ApiClassKind.HANDLE)
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class Subscription {

    private final long id;
    private final String pattern;
    private final boolean wildcard;
    private final boolean sync;
    private final Executor executor;
    private final Consumer<Object> handler;
    private final SyncEventCallback syncCallback;
    private final Runnable removalHook;
    private volatile boolean active = true;

    Subscription(long id, String pattern, boolean wildcard, boolean sync, Executor executor,
                 Consumer<Object> handler, SyncEventCallback syncCallback, Runnable removalHook) {
        this.id = id;
        this.pattern = pattern;
        this.wildcard = wildcard;
        this.sync = sync;
        this.executor = executor;
        this.handler = handler;
        this.syncCallback = syncCallback;
        this.removalHook = removalHook;
    }

    /** Unsubscribes; idempotent.
     * @zh 退订；重复调用无副作用。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void unsubscribe() {
        deactivate();
    }

    /** Returns whether this subscription still receives events.
     * @zh 订阅是否仍在接收事件。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public boolean isActive() {
        return active;
    }

    /** Subscription pattern: exact event name or wildcard ({@code domain:*}, {@code *}).
     * @zh 订阅模式：精确事件名或通配（{@code domain:*}、{@code *}）。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String pattern() {
        return pattern;
    }

    /** Returns whether the pattern is a wildcard rather than an exact name.
     * @zh 模式是否为通配而非精确名。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public boolean isWildcard() {
        return wildcard;
    }

    /** Returns whether this is a synchronous-channel subscription.
     * @zh 是否为同步通道订阅。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public boolean isSync() {
        return sync;
    }

    long id() {
        return id;
    }

    Executor executor() {
        return executor;
    }

    Consumer<Object> handler() {
        return handler;
    }

    SyncEventCallback syncCallback() {
        return syncCallback;
    }

    /** Deactivates and notifies the owning bus for eager removal; idempotent. */
    void deactivate() {
        if (active) {
            active = false;
            removalHook.run();
        }
    }
}
