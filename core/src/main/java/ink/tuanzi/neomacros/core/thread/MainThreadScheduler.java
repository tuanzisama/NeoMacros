package ink.tuanzi.neomacros.core.thread;

import ink.tuanzi.neomacros.core.event.SyncWatchdog;
import ink.tuanzi.neomacros.core.runtime.IllegalThreadStateError;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridge to the game main thread: tasks are enqueued by any thread and drained
 * by the main thread (the adapter hooks the drain into its tick path). Each
 * bridged task is optionally watchdog-guarded; the join-depth counter feeds
 * deadlock protection (waiting on main from within a bridged task, or blocking
 * waits issued from main, raise IllegalThreadStateError).
 */
public final class MainThreadScheduler {

    private static final String GUARDED_EVENT = "runOnMain";

    private final Queue<BridgedTask> queue = new ConcurrentLinkedQueue<>();
    private final SyncWatchdog watchdog;
    private final ThreadLocal<Integer> joinDepth = ThreadLocal.withInitial(() -> 0);
    private final AtomicInteger joinCount = new AtomicInteger();
    private volatile Thread mainThread;

    /** Creates the scheduler; {@code watchdog} may be null to skip budget guarding. */
    public MainThreadScheduler(SyncWatchdog watchdog) {
        this.watchdog = watchdog;
    }

    /** Registers the game main thread; called once at bootstrap. */
    public void bindMainThread(Thread mainThread) {
        this.mainThread = mainThread;
    }

    /** Returns whether the current thread is the registered game main thread. */
    public boolean isMainThread() {
        return Thread.currentThread() == mainThread;
    }

    /** Returns whether the current thread runs inside a bridged main-thread task. */
    public boolean isOnBridgedTask() {
        return joinDepth.get() > 0;
    }

    /** Submits a task to run on the game main thread. */
    public CompletableFuture<Object> submit(Callable<?> task) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        queue.add(new BridgedTask(task, future));
        return future;
    }

    /**
     * Drains queued tasks on the game main thread; called by the adapter's
     * tick hook (or by tests simulating the main thread).
     */
    public void runQueuedTasks() {
        BridgedTask polled;
        while ((polled = queue.poll()) != null) {
            BridgedTask task = polled;
            Runnable guarded = task;
            if (watchdog != null) {
                guarded = () -> watchdog.guard(GUARDED_EVENT, () -> task.run());
            }
            joinDepth.set(joinDepth.get() + 1);
            joinCount.incrementAndGet();
            try {
                guarded.run();
            } finally {
                joinDepth.set(joinDepth.get() - 1);
                joinCount.decrementAndGet();
            }
        }
    }

    /** Returns the number of tasks currently executing (or nested) on bridged paths. */
    public int activeBridgedTasks() {
        return joinCount.get();
    }

    /** Asserts the current thread may block on foreign completion (deadlock protection). */
    public void checkBlockingAllowed() {
        if (isMainThread()) {
            throw new IllegalThreadStateError(
                    "blocking wait on the game main thread would deadlock the task queue");
        }
        if (isOnBridgedTask()) {
            throw new IllegalThreadStateError(
                    "blocking wait inside a bridged main-thread task would deadlock the drain loop");
        }
    }

    private static final class BridgedTask implements Runnable {
        private final Callable<?> task;
        private final CompletableFuture<Object> future;

        private BridgedTask(Callable<?> task, CompletableFuture<Object> future) {
            this.task = task;
            this.future = future;
        }

        @Override
        public void run() {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
    }
}
