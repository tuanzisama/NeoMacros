package ink.tuanzi.neomacros.core.runtime;

import ink.tuanzi.neomacros.core.thread.ContextEventLoop;
import ink.tuanzi.neomacros.core.thread.MainThreadScheduler;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.core.thread.TickClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Async primitive semantics on the stub-free path: completion lands on the
 * context loop, timers and tick waits resolve, polling honors timeouts, and
 * runOnMain marshals results back to the loop.
 */
class AsyncPrimitivesTest {

    private ScriptThreadPool pool;
    private ContextEventLoop loop;
    private TickClock tickClock;
    private MainThreadScheduler main;
    private AsyncPrimitives async;

    @BeforeEach
    void setUp() {
        pool = new ScriptThreadPool(2);
        loop = pool.openLoop("async");
        tickClock = new TickClock();
        main = new MainThreadScheduler(null);
        async = new AsyncPrimitives(loop, pool, tickClock, main);
    }

    @AfterEach
    void tearDown() {
        loop.close();
        pool.close();
    }

    @Test
    void sleepCompletesOnLoopAfterDelay() throws Exception {
        long start = System.currentTimeMillis();
        CompletableFuture<Void> done = async.sleep(120);
        java.util.concurrent.CompletableFuture<String> completedOn = captureCompletionThread(done);
        done.get(2, TimeUnit.SECONDS);
        assertTrue(System.currentTimeMillis() - start >= 110);
        assertEquals(loop.threadName(), completedOn.get(2, TimeUnit.SECONDS));
    }

    @Test
    void ticksAndNextTickCompleteOnLoop() throws Exception {
        CompletableFuture<Void> nextTick = async.nextTick();
        java.util.concurrent.CompletableFuture<String> completedOn = captureCompletionThread(nextTick);
        tickClock.tick();
        nextTick.get(2, TimeUnit.SECONDS);
        assertEquals(loop.threadName(), completedOn.get(2, TimeUnit.SECONDS));
    }

    @Test
    void waitForPassesWhenConditionMet() throws Exception {
        CompletableFuture<Boolean> done = async.waitFor(unused -> true, 500);
        java.util.concurrent.CompletableFuture<String> completedOn = captureCompletionThread(done);
        assertTrue(done.get(2, TimeUnit.SECONDS));
        assertEquals(loop.threadName(), completedOn.get(2, TimeUnit.SECONDS));
    }

    @Test
    void waitForTimesOutExceptionally() {
        CompletableFuture<Boolean> done = async.waitFor(unused -> false, 120);
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> done.get(2, TimeUnit.SECONDS));
        assertInstanceOf(TimeoutException.class, failure.getCause());
    }

    @Test
    void waitForConditionExceptionPropagates() {
        CompletableFuture<Boolean> done = async.waitFor(unused -> {
            throw new IllegalStateException("poll exploded");
        }, 500);
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> done.get(2, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test
    void runOnMainMarshalsResultOntoLoop() throws Exception {
        CompletableFuture<Object> done = async.runOnMain(() -> "computed");
        java.util.concurrent.CompletableFuture<String> completedOn = captureCompletionThread(done);
        main.runQueuedTasks();
        assertEquals("computed", done.get(2, TimeUnit.SECONDS));
        assertEquals(loop.threadName(), completedOn.get(2, TimeUnit.SECONDS), "result relayed onto the loop");
    }

    @Test
    void runOnMainOnMainThreadRunsInline() throws Exception {
        main.bindMainThread(Thread.currentThread());
        CompletableFuture<Object> done = async.runOnMain(() -> "inline");
        assertEquals("inline", done.get(2, TimeUnit.SECONDS));
        assertEquals(0, main.activeBridgedTasks(), "no queue round-trip on the main thread");
    }

    @Test
    void runOnMainThreadFailureCompletesExceptionally() {
        CompletableFuture<Object> done = async.runOnMain(() -> {
            throw new IllegalStateException("main exploded");
        });
        main.runQueuedTasks();
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> done.get(2, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    @Test
    void blockingJoinFromMainThreadIsRejected() {
        main.bindMainThread(Thread.currentThread());
        CompletableFuture<Void> future = new CompletableFuture<>();
        assertThrows(IllegalThreadStateError.class, () -> async.blockingJoin(future, 100));
    }

    @Test
    void nestedRunOnMainInsideBridgedTaskIsRejected() throws Exception {
        CompletableFuture<Object> bridged = async.runOnMain(() -> {
            try {
                CompletableFuture<Object> nested = async.runOnMain(() -> "nested");
                return "returned:" + nested.get(1, TimeUnit.SECONDS);
            } catch (IllegalThreadStateError e) {
                return "rejected";
            } catch (Exception e) {
                return "other:" + e;
            }
        });
        main.runQueuedTasks();
        assertEquals("rejected", bridged.get(2, TimeUnit.SECONDS));
    }

    /** Captures the thread that completes the future; attach before completion. */
    private java.util.concurrent.CompletableFuture<String> captureCompletionThread(CompletableFuture<?> future) {
        java.util.concurrent.CompletableFuture<String> thread = new java.util.concurrent.CompletableFuture<>();
        future.whenComplete((value, error) -> thread.complete(Thread.currentThread().getName()));
        return thread;
    }
}
