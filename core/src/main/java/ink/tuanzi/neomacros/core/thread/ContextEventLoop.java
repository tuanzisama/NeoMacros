package ink.tuanzi.neomacros.core.thread;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Dedicated event loop of one script context: FIFO task queue drained by the
 * context's own thread. Immediate submissions queue directly; delayed
 * submissions are parked on the shared scheduler and enqueue when due,
 * preserving FIFO order among immediate tasks.
 */
public final class ContextEventLoop implements Executor, AutoCloseable {

    private final String name;
    private final ScheduledExecutorService delayer;
    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final Consumer<Throwable> onError;
    private volatile Thread worker;
    private volatile boolean closed;

    /** Creates and starts the loop thread named {@code neomacros-ctx-<name>}. */
    public ContextEventLoop(String name, ScheduledExecutorService delayer) {
        this(name, delayer, null);
    }

    /**
     * Creates and starts the loop thread; task failures are handed to
     * {@code onError} (nullable) instead of killing the loop.
     */
    public ContextEventLoop(String name, ScheduledExecutorService delayer, Consumer<Throwable> onError) {
        this.name = name;
        this.delayer = delayer;
        this.onError = onError != null ? onError : t -> { };
        this.worker = new Thread(this::drainForever, "neomacros-ctx-" + name);
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** Returns whether the calling thread is this loop's worker thread. */
    public boolean isOnLoop() {
        return Thread.currentThread() == worker;
    }

    /** Enqueues the task for FIFO execution on the loop thread. */
    @Override
    public void execute(Runnable task) {
        if (closed) {
            throw new IllegalStateException("context loop is closed: " + name);
        }
        queue.add(task);
    }

    /** Enqueues the task to run on the loop thread after {@code delayMillis}. */
    public void schedule(Runnable task, long delayMillis) {
        if (closed) {
            throw new IllegalStateException("context loop is closed: " + name);
        }
        delayer.schedule(() -> {
            if (!closed) {
                execute(task);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /** Waits until the queue is drained (test and shutdown support). */
    public boolean awaitIdle(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!queue.isEmpty()) {
            if (System.currentTimeMillis() > deadline) {
                return false;
            }
            Thread.sleep(2);
        }
        return true;
    }

    /** Returns whether the loop thread is alive. */
    public boolean isRunning() {
        return worker != null && worker.isAlive();
    }

    /** Returns the loop thread name. */
    public String threadName() {
        return worker == null ? "" : worker.getName();
    }

    /**
     * The loop thread id; 0 before the thread exists. All guest (GraalJS)
     * execution of the owning context happens on this thread, so its
     * per-thread allocation counter is the per-script memory signal for the
     * web memory sampler.
     */
    public long threadId() {
        Thread current = worker;
        return current == null ? 0 : current.getId();
    }

    /** Stops accepting tasks, waits for the loop thread to exit; pending tasks are dropped. */
    @Override
    public void close() {
        closed = true;
        Thread current = worker;
        if (current != null) {
            current.interrupt();
            try {
                current.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void drainForever() {
        while (!closed) {
            try {
                Runnable task = queue.poll(50, TimeUnit.MILLISECONDS);
                if (task != null) {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        // A failing task (script error, binding crash) is reported
                        // and dropped; the loop itself must survive it.
                        try {
                            onError.accept(t);
                        } catch (RuntimeException ignored) {
                            // Error reporting must never take the loop down.
                        }
                    }
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
