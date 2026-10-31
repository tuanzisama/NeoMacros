package ink.tuanzi.neomacros.core.thread;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Script thread pool semantics: daemon workers, shared scheduler, managed
 * shutdown.
 */
class ScriptThreadPoolTest {

    @Test
    void workersAreDaemonThreads() throws Exception {
        try (ScriptThreadPool pool = new ScriptThreadPool(2)) {
            CompletableFuture<String> threadKind = new CompletableFuture<>();
            pool.workers().submit(() -> threadKind.complete(
                    Thread.currentThread().isDaemon() ? "daemon" : "platform"));
            assertEquals("daemon", threadKind.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void schedulerThreadIsDaemon() throws Exception {
        try (ScriptThreadPool pool = new ScriptThreadPool(1)) {
            CompletableFuture<String> threadKind = new CompletableFuture<>();
            pool.scheduler().submit(() -> threadKind.complete(
                    Thread.currentThread().isDaemon() ? "daemon" : "platform"));
            assertEquals("daemon", threadKind.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void openLoopCreatesIndependentContextThreads() throws Exception {
        try (ScriptThreadPool pool = new ScriptThreadPool(2)) {
            ContextEventLoop first = pool.openLoop("alpha");
            ContextEventLoop second = pool.openLoop("beta");
            try {
                assertEquals("neomacros-ctx-alpha", first.threadName());
                assertEquals("neomacros-ctx-beta", second.threadName());
                assertTrue(first.isRunning());
                assertTrue(second.isRunning());
            } finally {
                first.close();
                second.close();
            }
        }
    }

    @Test
    void closeStopsWorkers() throws Exception {
        ScriptThreadPool pool = new ScriptThreadPool(1);
        pool.close();
        assertTrue(pool.workers().isShutdown());
        assertTrue(pool.scheduler().isShutdown());
        assertTrue(pool.workers().awaitTermination(2, TimeUnit.SECONDS));
    }
}
