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

    /**
     * Outcome of one publish: the current cancellation state (last sync
     * write wins — a callback may undo an earlier cancel), the dispatch
     * count, and the accumulated mutations committed by the sync callbacks
     * ({@code null} when nothing was rewritten; later priority overrides
     * earlier fields).
     */
    public record PublishResult(boolean canceled, int notified, Object verdict) {
    }

    /** High-traffic events excluded from wildcard dispatch by default (config-adjustable). */
    public static final Set<String> DEFAULT_WILDCARD_IGNORES =
            Set.of("tick", "sound:*", "packet:*", "world:blockUpdate", "entity:metadata",
                    "inventory:update", "inventory:click", "inventory:dropSlot",
                    "item:damage");

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
        return subscribe(name, null, name.contains("*"), false, EventPriority.NORMAL, false, handler, null);
    }

    /** Subscribes asynchronously with a dedicated executor (per-context event loop wiring). */
    public Subscription on(String name, Executor executor, Consumer<Object> handler) {
        return subscribe(name, executor, name.contains("*"), false, EventPriority.NORMAL, false, handler, null);
    }

    /** Subscribes to fire exactly once, then auto-unsubscribes. */
    public Subscription once(String name, Consumer<Object> handler) {
        java.util.concurrent.atomic.AtomicReference<Subscription> self =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean fired = new java.util.concurrent.atomic.AtomicBoolean();
        Subscription sub = subscribe(name, null, name.contains("*"), false, EventPriority.NORMAL, false, payload -> {
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
     * Subscribes on the synchronous cancellation channel with default
     * priority ({@link EventPriority#NORMAL}); only cancellable known events
     * may be subscribed this way (the script surface raises a TypeError for
     * violations; the Java layer raises {@link IllegalStateException}).
     */
    public Subscription onSync(String name, SyncEventCallback callback) {
        return onSync(name, EventPriority.NORMAL, false, callback);
    }

    /**
     * Subscribes on the synchronous channel with an explicit priority and
     * cancelled-dispatch policy. Handlers run LOWEST first, HIGHEST last;
     * the last write — mutations and cancellation alike — wins, and
     * {@code ignoreCancelled} skips this callback once the event is already
     * cancelled.
     */
    public Subscription onSync(String name, EventPriority priority, boolean ignoreCancelled,
                               SyncEventCallback callback) {
        if (name.contains("*")) {
            throw new IllegalArgumentException("synchronous subscriptions cannot use wildcards: " + name);
        }
        EventDescriptor descriptor = catalog.descriptor(name).orElse(null);
        if (descriptor == null || !descriptor.cancellable()) {
            throw new IllegalStateException(
                    "events.onSync requires a known cancellable event: " + name
                            + " (script surface: TypeError)");
        }
        return subscribe(name, null, false, true, priority, ignoreCancelled, null, callback);
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
        Subscription sub = subscribe(name, null, false, false, EventPriority.NORMAL, false, payload -> {
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

    /**
     * Publishes an event; unknown names are allowed and treated as
     * non-cancellable local events.
     *
     * <p>Sync callbacks run in priority order (LOWEST first, HIGHEST last,
     * ties by registration order) and share one state snapshot: every
     * committed mutation applies back onto the current payload, so later
     * priorities observe and override earlier writes (sequential visibility,
     * Bukkit-style last write wins). The accumulated mutations surface as the
     * structured verdict, and the async dispatch observes the final state.</p>
     */
    public PublishResult publish(String name, Object payload) {
        CancelFlag cancelFlag = new CancelFlag();
        long timestamp = System.currentTimeMillis();
        int notified = 0;
        Object verdict = null;
        EventDescriptor descriptor = catalog.descriptor(name).orElse(null);
        boolean cancellable = descriptor != null && descriptor.cancellable();

        Object current = payload;
        if (cancellable) {
            List<Subscription> syncSubs = snapshot(exactSubscriptions.get(name)).stream()
                    .filter(sub -> sub.isActive() && sub.isSync())
                    .sorted(java.util.Comparator.comparingInt(
                                    (Subscription sub) -> sub.priority().slot())
                            .thenComparingLong(Subscription::id))
                    .toList();
            if (!syncSubs.isEmpty()) {
                Object snap = Payloads.snapshot(payload);
                Map<String, Object> state = snap instanceof Map<?, ?> map
                        ? new java.util.LinkedHashMap<>((Map<String, Object>) map)
                        : wrappedState(snap);
                Map<String, Object> committed = new java.util.LinkedHashMap<>();
                for (Subscription sub : syncSubs) {
                    if (!sub.isActive()
                            || (cancelFlag.isCanceled() && sub.ignoresCancelled())) {
                        continue;
                    }
                    notified++;
                    // Every dispatched payload pairs with its concrete name so
                    // the engine boundary can attach the event class prototype;
                    // the hooks sink commits this delivery's mutations, which
                    // merge back onto the shared state right after the callback.
                    Map<String, Object> writes = new java.util.LinkedHashMap<>();
                    SyncEventHooks hooks = new SyncEventHooks() {
                        @Override
                        public void write(String key, Object value) {
                            writes.put(key, value);
                        }

                        @Override
                        public void cancel() {
                            cancelFlag.cancel();
                        }

                        @Override
                        public boolean isCancelled() {
                            return cancelFlag.isCanceled();
                        }

                        @Override
                        public void setCancelled(boolean cancelled) {
                            cancelFlag.setCanceled(cancelled);
                        }
                    };
                    EventPayloadEnvelope envelope = new EventPayloadEnvelope(name, true,
                            descriptor.direction(), timestamp, state, true,
                            descriptor.mutable(), sub.priority() == EventPriority.MONITOR,
                            hooks);
                    if (watchdog != null) {
                        watchdog.guard(sub, name, () ->
                                sub.syncCallback().invoke(envelope, cancelFlag));
                    } else {
                        sub.syncCallback().invoke(envelope, cancelFlag);
                    }
                    for (Map.Entry<String, Object> entry : writes.entrySet()) {
                        committed.put(entry.getKey(), entry.getValue());
                        state.put(entry.getKey(), entry.getValue());
                    }
                }
                if (!committed.isEmpty()) {
                    verdict = committed;
                    current = state;
                }
            }
        }

        notified += dispatchAsync(snapshot(exactSubscriptions.get(name)), name, current,
                descriptor, timestamp);
        if (!isWildcardIgnored(name)) {
            notified += dispatchAsync(List.copyOf(wildcardSubscriptions), name, current,
                    descriptor, timestamp);
        }
        return new PublishResult(cancelFlag.isCanceled(), notified, verdict);
    }

    /** Replaces the wildcard ignore list (patterns: exact names or {@code domain:*}). */
    public void setWildcardIgnores(Collection<String> patterns) {
        this.wildcardIgnores = Set.copyOf(patterns);
    }

    /** Returns the active wildcard ignore list. */
    public Set<String> getWildcardIgnores() {
        return wildcardIgnores;
    }

    private int dispatchAsync(List<Subscription> candidates, String name, Object payload,
                              EventDescriptor descriptor, long timestamp) {
        EventPayloadEnvelope envelope = new EventPayloadEnvelope(name,
                descriptor != null && descriptor.cancellable(),
                descriptor != null ? descriptor.direction()
                        : ink.tuanzi.neomacros.doc.api.ApiEvent.Direction.LOCAL,
                timestamp, payload);
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
                    handler.accept(envelope);
                }
            });
        }
        return notified;
    }

    private Subscription subscribe(String name, Executor executor, boolean wildcard, boolean sync,
                                   EventPriority priority, boolean ignoreCancelled,
                                   Consumer<Object> handler, SyncEventCallback syncCallback) {
        java.util.concurrent.atomic.AtomicReference<Subscription> self = new java.util.concurrent.atomic.AtomicReference<>();
        Subscription sub = new Subscription(SEQUENCE.incrementAndGet(), name, wildcard, sync,
                priority, ignoreCancelled, executor != null ? executor : asyncExecutor,
                handler, syncCallback, () -> remove(self.get()));
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

    /** Wraps a non-map sync payload (bare scalars in tests) into the shared state shape. */
    private static Map<String, Object> wrappedState(Object snap) {
        Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put("value", snap);
        return state;
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
