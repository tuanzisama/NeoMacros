package ink.tuanzi.neomacros.core.thread;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Source of client ticks. The adapter advances the clock once per client tick
 * (production) or a test advances it manually; tick waits resolve their
 * continuations onto the given context loop.
 */
public final class TickClock {

    private final AtomicLong currentTick = new AtomicLong();
    private final Map<Long, Queue<Runnable>> waiters = new ConcurrentHashMap<>();

    /** Returns the current tick count of the session. */
    public long currentTick() {
        return currentTick.get();
    }

    /** Advances one tick and fires the continuations due at this boundary. */
    public void tick() {
        long now = currentTick.incrementAndGet();
        Queue<Runnable> due = waiters.remove(now);
        if (due != null) {
            Runnable waiter;
            while ((waiter = due.poll()) != null) {
                waiter.run();
            }
        }
    }

    /**
     * Registers a continuation running once {@code ticks} boundaries have
     * passed; the continuation itself is invoked on the clock thread (the game
     * main thread in production) and must hand work to the context loop.
     */
    public Runnable atTick(long dueTick, Runnable continuation) {
        if (dueTick <= currentTick.get()) {
            continuation.run();
            return () -> {
            };
        }
        Queue<Runnable> due = waiters.computeIfAbsent(dueTick, k -> new ConcurrentLinkedQueue<>());
        due.add(continuation);
        return () -> due.remove(continuation);
    }

    /** Completes after the next tick boundary, with the completion running on {@code loop}. */
    public CompletableFuture<Void> nextTick(ContextEventLoop loop) {
        return ticks(loop, 1);
    }

    /** Completes after {@code ticks} boundaries, with the completion running on {@code loop}. */
    public CompletableFuture<Void> ticks(ContextEventLoop loop, int ticks) {
        if (ticks <= 0) {
            CompletableFuture<Void> immediate = new CompletableFuture<>();
            loop.execute(() -> immediate.complete(null));
            return immediate;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        atTick(currentTick.get() + ticks,
                () -> loop.execute(() -> future.complete(null)));
        return future;
    }
}
