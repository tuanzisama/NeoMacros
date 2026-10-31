package ink.tuanzi.neomacros.core.event;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Handle of one event subscription; returned to scripts by
 * {@code events.on / once / onSync}. Unsubscribing through the handle removes
 * the subscription from dispatch; module dispose iterates its handles the same
 * way.
 */
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

    /** Unsubscribes; idempotent. */
    public void unsubscribe() {
        deactivate();
    }

    /** Returns whether this subscription still receives events. */
    public boolean isActive() {
        return active;
    }

    /** Subscription pattern: exact event name or wildcard ({@code domain:*}, {@code *}). */
    public String pattern() {
        return pattern;
    }

    /** Returns whether the pattern is a wildcard rather than an exact name. */
    public boolean isWildcard() {
        return wildcard;
    }

    /** Returns whether this is a synchronous-channel subscription. */
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
