package ink.tuanzi.neomacros.core.event;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Guards synchronous event dispatch against overruns: a callback that exceeds
 * the configured budget gets its subscription disabled and a violation
 * reported to the error consumer, while the stuck dispatch thread cannot be
 * interrupted safely and keeps running (JsMacros EventLockWatchdog lesson).
 */
public final class SyncWatchdog implements AutoCloseable {

    /** One overdue synchronous dispatch, reported exactly once. */
    public record Violation(String eventName, long elapsedMillis) {
    }

    /** Default synchronous dispatch budget in milliseconds. */
    public static final long DEFAULT_MAX_SYNC_MILLIS = 500;

    private final long maxSyncMillis;
    private final long pollIntervalMillis;
    private final Consumer<Violation> onViolation;
    private final Map<Long, ArmedDispatch> armed = new ConcurrentHashMap<>();
    private final Thread monitor;
    private volatile boolean closed;
    private volatile long sequence;

    /** Creates a watchdog with the default budget (500 ms). */
    public SyncWatchdog(Consumer<Violation> onViolation) {
        this(DEFAULT_MAX_SYNC_MILLIS, onViolation);
    }

    /** Creates a watchdog; the monitor is a daemon polling at a fraction of the budget. */
    public SyncWatchdog(long maxSyncMillis, Consumer<Violation> onViolation) {
        if (maxSyncMillis <= 0) {
            throw new IllegalArgumentException("maxSyncMillis must be positive");
        }
        this.maxSyncMillis = maxSyncMillis;
        this.pollIntervalMillis = Math.max(5, Math.min(50, maxSyncMillis / 4));
        this.onViolation = onViolation;
        this.monitor = new Thread(this::monitorLoop, "neomacros-sync-watchdog");
        this.monitor.setDaemon(true);
        this.monitor.start();
    }

    /** Runs the callback under the budget: overdue dispatches get the subscription disabled. */
    public void guard(Subscription subscription, String eventName, Runnable invoke) {
        guardArmed(subscription, eventName, invoke);
    }

    /** Guards a non-subscription synchronous section (e.g. a bridged main-thread task); overdue dispatches report only. */
    public void guard(String eventName, Runnable invoke) {
        guardArmed(null, eventName, invoke);
    }

    private void guardArmed(Subscription subscription, String eventName, Runnable invoke) {
        ArmedDispatch dispatch = new ArmedDispatch(subscription, eventName, System.nanoTime(),
                System.currentTimeMillis() + maxSyncMillis);
        long id = sequence++;
        armed.put(id, dispatch);
        try {
            invoke.run();
        } finally {
            armed.remove(id);
        }
    }

    private void monitorLoop() {
        while (!closed) {
            try {
                Thread.sleep(pollIntervalMillis);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.currentTimeMillis();
            Iterator<ArmedDispatch> it = armed.values().iterator();
            while (it.hasNext()) {
                ArmedDispatch dispatch = it.next();
                if (now >= dispatch.deadlineMillis() && !dispatch.violationReported) {
                    dispatch.violationReported = true;
                    if (dispatch.subscription() != null) {
                        dispatch.subscription().deactivate();
                    }
                    onViolation.accept(new Violation(dispatch.eventName(),
                            (System.nanoTime() - dispatch.startNanos()) / 1_000_000));
                }
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        monitor.interrupt();
    }

    private static final class ArmedDispatch {
        private final Subscription subscription;
        private final String eventName;
        private final long startNanos;
        private final long deadlineMillis;
        private volatile boolean violationReported;

        private ArmedDispatch(Subscription subscription, String eventName, long startNanos, long deadlineMillis) {
            this.subscription = subscription;
            this.eventName = eventName;
            this.startNanos = startNanos;
            this.deadlineMillis = deadlineMillis;
        }

        private Subscription subscription() {
            return subscription;
        }

        private String eventName() {
            return eventName;
        }

        private long startNanos() {
            return startNanos;
        }

        private long deadlineMillis() {
            return deadlineMillis;
        }
    }
}
