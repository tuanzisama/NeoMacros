package ink.tuanzi.neomacros.core.thread;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Context event loop semantics: FIFO order on a dedicated thread, delayed
 * scheduling, cancellation-by-close, and shutdown behavior.
 */
class ContextEventLoopTest {

    private ScriptThreadPool pool;
    private ContextEventLoop loop;

    @BeforeEach
    void setUp() {
        pool = new ScriptThreadPool(2);
        loop = pool.openLoop("test");
    }

    @AfterEach
    void tearDown() {
        loop.close();
        pool.close();
    }

    @Test
    void tasksRunFifoOnDedicatedThread() throws Exception {
        String loopThread = loop.threadName();
        assertEquals("neomacros-ctx-test", loopThread);

        List<Integer> order = new CopyOnWriteArrayList<>();
        AtomicReference<String> executingThread = new AtomicReference<>();
        for (int i = 0; i < 20; i++) {
            int value = i;
            loop.execute(() -> {
                order.add(value);
                executingThread.set(Thread.currentThread().getName());
            });
        }
        assertTrue(loop.awaitIdle(2000));
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19), order);
        assertEquals(loopThread, executingThread.get(), "tasks execute on the loop thread");
    }

    @Test
    void delayedTasksRunAfterTheirDelay() throws Exception {
        long start = System.currentTimeMillis();
        AtomicReference<Long> runAt = new AtomicReference<>();
        loop.schedule(() -> runAt.set(System.currentTimeMillis()), 150);

        long deadline = System.currentTimeMillis() + 2000;
        while (runAt.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(runAt.get() != null, "delayed task ran");
        assertTrue(runAt.get() - start >= 140, "task waited for the full delay");
    }

    @Test
    void tasksAfterCloseAreRejected() throws Exception {
        loop.close();
        assertFalse(loop.isRunning());
        assertThrows(IllegalStateException.class, () -> loop.execute(() -> {
        }));
    }

    @Test
    void closeIsIdempotent() {
        loop.close();
        loop.close();
    }

    @Test
    void externallySuppliedSchedulerDrivesDelays() throws Exception {
        var delayer = Executors.newSingleThreadScheduledExecutor();
        try {
            ContextEventLoop separate = new ContextEventLoop("second", delayer);
            try {
                AtomicReference<String> ran = new AtomicReference<>();
                separate.schedule(() -> ran.set("x"), 40);
                long deadline = System.currentTimeMillis() + 2000;
                while (ran.get() == null && System.currentTimeMillis() < deadline) {
                    Thread.sleep(5);
                }
                assertEquals("x", ran.get());
            } finally {
                separate.close();
            }
        } finally {
            delayer.shutdownNow();
        }
    }

    @Test
    void threadIdMatchesTheLoopThread() throws Exception {
        assertTrue(loop.threadId() > 0, "a running loop exposes its thread id");
        AtomicReference<Long> seen = new AtomicReference<>();
        loop.execute(() -> seen.set(Thread.currentThread().getId()));
        long deadline = System.currentTimeMillis() + 2000;
        while (seen.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(loop.threadId(), seen.get(), "the exposed id is the loop worker's id");
    }
}
