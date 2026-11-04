package ink.tuanzi.neomacros.core.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * EventEmitter hub: asynchronous dispatch (callbacks queued onto their
 * subscription's executor) plus the synchronous cancellation channel for
 * cancellable events, wildcard pattern matching with a high-traffic ignore
 * list, and watchdog-guarded sync dispatch.
 *
 * <p>Publishing walks the exact-name subscriptions first, then the wildcard
 * subscriptions (unless the event is wildcard-ignored). Sync callbacks run
 * inline on the dispatch thread and their cancellation verdict is returned to
 * the caller (the adapter reads it when its game callback returns).</p>
 */
public final class EventBus {

    /** Outcome of one publish: whether any sync callback canceled the event, and dispatch count. */
    public record PublishResult(boolean canceled, int notified) {
    }

    /** High-traffic events excluded from wildcard dispatch by default (config-adjustable). */
    public static final Set<String> DEFAULT_WILDCARD_IGNORES =
            Set.of("tick", "sound:*", "packet:*", "world:blockUpdate", "entity:metadata");

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final EventCatalog catalog;
    private final Executor asyncExecutor;
    private final SyncWatchdog watchdog;
    private final Map<String, CopyOnWriteArrayList<Subscription>> exactSubscriptions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Subscription> wildcardSubscriptions = new CopyOnWriteArrayList<>();
    private volatile Set<String> wildcardIgnores = DEFAULT_WILDCARD_IGNORES;

    /** Creates a bus dispatching async callbacks onto {@code asyncExecutor}. */
    public EventBus(EventCatalog catalog, Executor asyncExecutor) {
        this(catalog, asyncExecutor,
                new SyncWatchdog(SyncWatchdog.DEFAULT_MAX_SYNC_MILLIS, violation -> {
                }));
    }

    /** Creates a bus with an explicit watchdog; pass {@code null} to disable sync guarding. */
    public EventBus(EventCatalog catalog, Executor asyncExecutor, SyncWatchdog watchdog) {
        this.catalog = catalog;
        this.asyncExecutor = asyncExecutor;
        this.watchdog = watchdog;
    }

    /** The catalog backing this bus (custom-event registration and checks). */
    public EventCatalog catalog() {
        return catalog;
    }

    /** Subscribes asynchronously with the bus-wide executor. Wildcards allowed. */
    public Subscription on(String name, Consumer<Object> handler) {
        return subscribe(name, null, name.contains("*"), false, handler, null);
    }

    /** Subscribes asynchronously with a dedicated executor (per-context event loop wiring). */
    public Subscription on(String name, Executor executor, Consumer<Object> handler) {
        return subscribe(name, executor, name.contains("*"), false, handler, null);
    }

    /** Subscribes to fire exactly once, then auto-unsubscribes. */
    public Subscription once(String name, Consumer<Object> handler) {
        java.util.concurrent.atomic.AtomicReference<Subscription> self =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean fired = new java.util.concurrent.atomic.AtomicBoolean();
        Subscription sub = subscribe(name, null, name.contains("*"), false, payload -> {
            if (fired.compareAndSet(false, true)) {
                Subscription active = self.get();
                if (active != null) {
                    active.deactivate();
                }
                handler.accept(payload);
            }
        }, null);
        self.set(sub);
        return sub;
    }

    /**
     * Subscribes on the synchronous cancellation channel; only cancellable
     * known events may be subscribed this way (the script surface raises a
     * TypeError for violations; the Java layer raises {@link IllegalStateException}).
     */
    public Subscription onSync(String name, SyncEventCallback callback) {
        if (name.contains("*")) {
            throw new IllegalArgumentException("synchronous subscriptions cannot use wildcards: " + name);
        }
        if (!catalog.isCancellable(name)) {
            throw new IllegalStateException(
                    "events.onSync requires a known cancellable event: " + name
                            + " (script surface: TypeError)");
        }
        return subscribe(name, null, false, true, null, callback);
    }

    /**
     * Completes with the next payload of the event (matching subscriptions
     * only; no wildcards). The future never completes unless the event fires;
     * use the filtered variant or external timeout for bounded waiting.
     */
    public CompletableFuture<Object> waitFor(String name) {
        return waitFor(name, payload -> true, Long.MAX_VALUE);
    }

    /**
     * Completes with the next payload passing {@code filter}, or completes
     * exceptionally after {@code timeoutMillis}. The filter runs on the
     * subscription's dispatch surface.
     */
    public CompletableFuture<Object> waitFor(String name, java.util.function.Predicate<Object> filter,
                                             long timeoutMillis) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        Subscription sub = subscribe(name, null, false, false, payload -> {
            if (future.isDone()) {
                return;
            }
            try {
                if (filter.test(payload)) {
                    future.complete(payload);
                }
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }, null);
        future.whenComplete((result, error) -> sub.deactivate());
        if (timeoutMillis != Long.MAX_VALUE) {
            future.orTimeout(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        return future;
    }

    /** Unsubscribes one subscription. */
    public void off(Subscription subscription) {
        subscription.unsubscribe();
    }

    /** Unsubscribes all exact subscriptions of the event name. */
    public void off(String name) {
        CopyOnWriteArrayList<Subscription> subs = exactSubscriptions.get(name);
        if (subs != null) {
            subs.forEach(Subscription::deactivate);
        }
    }

    /** Publishes an event; unknown names are allowed and treated as non-cancellable. */
    public PublishResult publish(String name, Object payload) {
        CancelFlag cancelFlag = new CancelFlag();
        int notified = 0;

        if (catalog.isCancellable(name)) {
            for (Subscription sub : snapshot(exactSubscriptions.get(name))) {
                if (sub.isActive() && sub.isSync()) {
                    notified++;
                    if (watchdog != null) {
                        watchdog.guard(sub, name, () -> sub.syncCallback().invoke(payload, cancelFlag));
                    } else {
                        sub.syncCallback().invoke(payload, cancelFlag);
                    }
                }
            }
        }

        notified += dispatchAsync(snapshot(exactSubscriptions.get(name)), name, payload);
        if (!isWildcardIgnored(name)) {
            notified += dispatchAsync(List.copyOf(wildcardSubscriptions), name, payload);
        }
        return new PublishResult(cancelFlag.isCanceled(), notified);
    }

    /** Replaces the wildcard ignore list (patterns: exact names or {@code domain:*}). */
    public void setWildcardIgnores(Collection<String> patterns) {
        this.wildcardIgnores = Set.copyOf(patterns);
    }

    /** Returns the active wildcard ignore list. */
    public Set<String> getWildcardIgnores() {
        return wildcardIgnores;
    }

    private int dispatchAsync(List<Subscription> candidates, String name, Object payload) {
        int notified = 0;
        for (Subscription sub : candidates) {
            if (!sub.isActive() || sub.isSync() || !matches(sub.pattern(), name)) {
                continue;
            }
            notified++;
            Executor executor = sub.executor();
            Consumer<Object> handler = sub.handler();
            executor.execute(() -> {
                if (sub.isActive()) {
                    handler.accept(payload);
                }
            });
        }
        return notified;
    }

    private Subscription subscribe(String name, Executor executor, boolean wildcard, boolean sync,
                                   Consumer<Object> handler, SyncEventCallback syncCallback) {
        java.util.concurrent.atomic.AtomicReference<Subscription> self = new java.util.concurrent.atomic.AtomicReference<>();
        Subscription sub = new Subscription(SEQUENCE.incrementAndGet(), name, wildcard, sync,
                executor != null ? executor : asyncExecutor, handler, syncCallback,
                () -> remove(self.get()));
        self.set(sub);
        if (wildcard) {
            wildcardSubscriptions.add(sub);
        } else {
            exactSubscriptions.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(sub);
        }
        return sub;
    }

    private void remove(Subscription sub) {
        if (sub.isWildcard()) {
            wildcardSubscriptions.remove(sub);
        } else {
            CopyOnWriteArrayList<Subscription> subs = exactSubscriptions.get(sub.pattern());
            if (subs != null) {
                subs.remove(sub);
            }
        }
    }

    private static List<Subscription> snapshot(CopyOnWriteArrayList<Subscription> subs) {
        return subs == null ? List.of() : List.copyOf(subs);
    }

    /** Wildcard matching: {@code *} matches everything, {@code domain:*} matches the domain. */
    static boolean matches(String pattern, String name) {
        if ("*".equals(pattern)) {
            return true;
        }
        if (pattern.endsWith(":*")) {
            return name.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(name);
    }

    private boolean isWildcardIgnored(String name) {
        for (String pattern : wildcardIgnores) {
            if (matches(pattern, name)) {
                return true;
            }
        }
        return false;
    }
}
