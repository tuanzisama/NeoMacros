package ink.tuanzi.neomacros.core.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Daemon worker pool backing the runtime: supplementary work threads and the
 * shared scheduler that parks delayed tasks before they enter their context
 * loop. Script threads never block the game; this pool never blocks the game
 * either (all threads are daemons).
 */
public final class ScriptThreadPool implements AutoCloseable {

    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler;

    /** Creates the pool with fixed-size daemon workers and a shared scheduler. */
    public ScriptThreadPool(int workerCount) {
        this.workers = Executors.newFixedThreadPool(workerCount, runnable -> {
            Thread thread = new Thread(runnable, "neomacros-pool-" + java.util.UUID.randomUUID());
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "neomacros-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Returns the worker pool for supplementary (non-context) work. */
    public ExecutorService workers() {
        return workers;
    }

    /** Returns the shared scheduler parking delayed tasks for context loops. */
    public ScheduledExecutorService scheduler() {
        return scheduler;
    }

    /** Opens a context event loop backed by this pool's scheduler. */
    public ContextEventLoop openLoop(String contextName) {
        return openLoop(contextName, null);
    }

    /** Opens a context event loop whose task failures go to {@code onError}. */
    public ContextEventLoop openLoop(String contextName, java.util.function.Consumer<Throwable> onError) {
        return new ContextEventLoop(contextName, scheduler, onError);
    }

    /** Shuts the pool down: running work finishes, queued work is dropped. */
    @Override
    public void close() {
        scheduler.shutdownNow();
        workers.shutdownNow();
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
