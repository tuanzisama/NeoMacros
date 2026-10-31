package ink.tuanzi.neomacros.adapter;

import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Marshals world-touching queries onto the game main thread and waits for
 * the answer, so context loop threads observe completion ordering. Timeouts
 * surface as empty results rather than crashing the calling loop.
 */
public final class MainThread {

    private MainThread() {
    }

    /** Runs the task on the main thread and waits for its result. */
    public static <T> T run(Supplier<T> task) {
        CompletableFuture<T> done = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                done.complete(task.get());
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        try {
            return done.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
            return null;
        }
    }

    /** Runs the task on the main thread and waits for completion. */
    public static void run(Runnable task) {
        run(() -> {
            task.run();
            return null;
        });
    }
}
