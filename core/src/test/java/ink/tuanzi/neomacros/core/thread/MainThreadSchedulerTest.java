package ink.tuanzi.neomacros.core.thread;

import ink.tuanzi.neomacros.core.event.SyncWatchdog;
import ink.tuanzi.neomacros.core.runtime.IllegalThreadStateError;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Main-thread bridge semantics: enqueue/drain, join-depth tracking, deadlock
 * protection, and watchdog arming of bridged tasks.
 */
class MainThreadSchedulerTest {

    @Test
    void tasksRunWhenDrained() throws Exception {
        MainThreadScheduler main = new MainThreadScheduler(null);
        CompletableFuture<Object> done = main.submit(() -> "value");
        assertFalse(done.isDone(), "queued until the main thread drains");
        main.runQueuedTasks();
        assertEquals("value", done.get(2, TimeUnit.SECONDS));
    }

    @Test
    void taskExceptionsCompleteExceptionally() throws Exception {
        MainThreadScheduler main = new MainThreadScheduler(null);
        CompletableFuture<Object> done = main.submit(() -> {
            throw new IllegalStateException("boom");
        });
        main.runQueuedTasks();
        assertTrue(done.isCompletedExceptionally());
    }

    @Test
    void tasksExecuteOnDrainingThread() throws Exception {
        MainThreadScheduler main = new MainThreadScheduler(null);
        List<String> threads = new CopyOnWriteArrayList<>();
        main.submit(() -> threads.add(Thread.currentThread().getName()));
        main.runQueuedTasks();
        assertEquals(List.of(Thread.currentThread().getName()), threads);
    }

    @Test
    void blockingOnMainThreadIsRejected() {
        MainThreadScheduler main = new MainThreadScheduler(null);
        main.bindMainThread(Thread.currentThread());
        assertTrue(main.isMainThread());
        assertThrows(IllegalThreadStateError.class, main::checkBlockingAllowed);
    }

    @Test
    void blockingInsideBridgedTaskIsRejected() {
        MainThreadScheduler main = new MainThreadScheduler(null);
        CompletableFuture<Object> probe = main.submit(() -> {
            assertThrows(IllegalThreadStateError.class, main::checkBlockingAllowed);
            assertTrue(main.isOnBridgedTask());
            return null;
        });
        main.runQueuedTasks();
        assertTrue(probe.isDone());
    }

    @Test
    void bridgedTasksCompleteOutOfJoinState() throws Exception {
        MainThreadScheduler main = new MainThreadScheduler(null);
        CompletableFuture<Object> probe = main.submit(() -> main.activeBridgedTasks());
        main.runQueuedTasks();
        assertEquals(1, probe.get(2, TimeUnit.SECONDS), "join depth seen from inside the task");
        assertEquals(0, main.activeBridgedTasks());
    }

    @Test
    void slowBridgedTaskReportsWatchdogViolation() throws Exception {
        List<SyncWatchdog.Violation> violations = new CopyOnWriteArrayList<>();
        try (SyncWatchdog watchdog = new SyncWatchdog(60, violations::add)) {
            MainThreadScheduler main = new MainThreadScheduler(watchdog);
            CompletableFuture<Object> slow = main.submit(() -> {
                Thread.sleep(250);
                return null;
            });
            main.runQueuedTasks();
            assertTrue(slow.isDone(), "the stuck task still runs to completion");
            assertEquals(1, violations.size());
            assertEquals("runOnMain", violations.get(0).eventName());
        }
    }

    @Test
    void unboundSchedulerRejectsMainThreadClaims() {
        MainThreadScheduler main = new MainThreadScheduler(null);
        assertFalse(main.isMainThread());
        assertDoesNotThrow(main::checkBlockingAllowed);
    }
}
