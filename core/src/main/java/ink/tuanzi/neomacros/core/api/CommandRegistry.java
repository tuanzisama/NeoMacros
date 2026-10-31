package ink.tuanzi.neomacros.core.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Internal registration surface implemented by the command subsystem (the
 * adapter's brigadier bridge). Kept as an interface so tests can stub the
 * dispatcher and the fabric layer owns the vanilla wiring.
 */
public interface CommandRegistry {

    /** Registers one spec and returns the opaque handle. */
    Object register(Map<String, Object> spec);

    /** Unregisters by command name. */
    void unregister(String name);

    /** Executes one command line through the vanilla dispatcher. */
    CompletableFuture<Boolean> execute(String commandLine);
}
