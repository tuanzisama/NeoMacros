package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.fabric.web.http.ApiError;

import java.util.function.Supplier;

/**
 * Runs a task on the game main thread and blocks for the result; the
 * implementation maps a timeout to {@link ApiError#gameThreadTimeout()}.
 * Tests substitute a direct executor.
 */
public interface GameRunner {

    /** The main-thread task timeout in milliseconds. */
    long TIMEOUT_MILLIS = 5_000;

    /** Runs the task on the game main thread and returns its result. */
    <T> T call(Supplier<T> task);
}
