package ink.tuanzi.neomacros.core.runtime;

import ink.tuanzi.neomacros.core.thread.ContextEventLoop;
import ink.tuanzi.neomacros.core.thread.MainThreadScheduler;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.core.thread.TickClock;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Java side of the script async primitives. Every returned future completes
 * on the context event loop, so the engine's promise bridge can settle JS
 * promises there without extra marshaling (03 §3 thread model).
 */
public final class AsyncPrimitives {

    private static final long DEFAULT_WAIT_INTERVAL_MILLIS = 25;

    private final ContextEventLoop loop;
    private final ScriptThreadPool pool;
    private final TickClock tickClock;
    private final MainThreadScheduler mainThread;

    /** Binds the primitives to one context's loop and the runtime services. */
    public AsyncPrimitives(ContextEventLoop loop, ScriptThreadPool pool,
                           TickClock tickClock, MainThreadScheduler mainThread) {
        this.loop = loop;
        this.pool = pool;
        this.tickClock = tickClock;
        this.mainThread = mainThread;
    }

    /** Timer primitive: completes on the loop after {@code millis} (nanosecond scheduling upstream). */
    public CompletableFuture<Void> sleep(long millis) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        loop.schedule(() -> future.complete(null), millis);
        return future;
    }

    /** Completes on the loop after the next client tick boundary. */
    public CompletableFuture<Void> nextTick() {
        return tickClock.nextTick(loop);
    }

    /** Completes on the loop after {@code ticks} client tick boundaries. */
    public CompletableFuture<Void> ticks(int ticks) {
        return tickClock.ticks(loop, ticks);
    }

    /**
     * Polls {@code condition} on pool threads every {@code intervalMillis}
     * until it passes or {@code timeoutMillis} elapses (then exceptionally
     * completes with a TimeoutException); completion lands on the loop.
     */
    public CompletableFuture<Boolean> waitFor(Predicate<Void> condition,
                                              long intervalMillis, long timeoutMillis) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        long deadline = System.currentTimeMillis() + timeoutMillis;
        schedulePoll(condition, intervalMillis, deadline, future);
        return future;
    }

    /** Polls with the default interval (25 ms). */
    public CompletableFuture<Boolean> waitFor(Predicate<Void> condition, long timeoutMillis) {
        return waitFor(condition, DEFAULT_WAIT_INTERVAL_MILLIS, timeoutMillis);
    }

    private void schedulePoll(Predicate<Void> condition, long intervalMillis, long deadline,
                              CompletableFuture<Boolean> future) {
        pool.scheduler().schedule(() -> {
            if (future.isDone()) {
                return;
            }
            boolean passed;
            try {
                passed = condition.test(null);
            } catch (Throwable t) {
                loop.execute(() -> future.completeExceptionally(t));
                return;
            }
            if (passed) {
                loop.execute(() -> future.complete(true));
            } else if (System.currentTimeMillis() >= deadline) {
                loop.execute(() -> future.completeExceptionally(
                        new TimeoutException("waitFor condition not met within the timeout")));
            } else {
                schedulePoll(condition, intervalMillis, deadline, future);
            }
        }, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Runs {@code task} on the game main thread and completes on the loop.
     * Calls made on the main thread run inline; calls from inside a bridged
     * main-thread task would deadlock the drain loop and are rejected.
     */
    public CompletableFuture<Object> runOnMain(Callable<?> task) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        if (mainThread.isMainThread()) {
            // Inline path: no queue round-trip, no wait, no deadlock.
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
            return future;
        }
        if (mainThread.isOnBridgedTask()) {
            throw new IllegalThreadStateError(
                    "runOnMain from inside a bridged main-thread task would deadlock the drain loop");
        }
        mainThread.submit(task).whenComplete((value, error) ->
                loop.execute(() -> {
                    if (error != null) {
                        future.completeExceptionally(error);
                    } else {
                        future.complete(value);
                    }
                }));
        return future;
    }

    /**
     * Blocks the calling thread until {@code future} settles; forbidden on the
     * game main thread and inside bridged tasks (deadlock protection, 03 §3
     * rule 5).
     */
    public <T> T blockingJoin(CompletableFuture<T> future, long timeoutMillis) throws Exception {
        mainThread.checkBlockingAllowed();
        return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** The context loop every completion funnels through. */
    public ContextEventLoop loop() {
        return loop;
    }
}
