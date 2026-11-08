package ink.tuanzi.neomacros.fabric;

import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * The one background thread every script module mutation runs on: command
 * reloads, lifecycle phase loads, watch scans, and the web panel's
 * macro/file actions. Loading a module blocks its caller until the script
 * compiles and settles on its context loop, so running those calls here
 * keeps the game main thread responsive; state that the main thread reads
 * concurrently lives in concurrent structures inside {@code ModuleManager}.
 * Mutations serialize on this single thread, so no two module operations
 * ever interleave.
 */
public final class ModuleWorker {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "neomacros-modules");
        thread.setDaemon(true);
        return thread;
    });

    private ModuleWorker() {
    }

    /** The worker as a plain executor (event wiring, watch cadence, fire-and-forget). */
    public static void execute(Runnable task) {
        WORKER.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                // A failing module operation must not kill the worker; the
                // per-call failure paths already report through their sinks.
                LoggerFactory.getLogger(ModuleWorker.class)
                        .warn("neomacros module operation failed", t);
            }
        });
    }

    /** Runs the task on the worker and waits for its result (callers are never the main thread). */
    public static <T> T submit(Supplier<T> task) throws InterruptedException, ExecutionException,
            TimeoutException {
        return WORKER.submit(task::get).get(5, TimeUnit.SECONDS);
    }
}
