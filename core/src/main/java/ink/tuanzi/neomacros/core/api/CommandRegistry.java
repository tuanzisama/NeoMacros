package ink.tuanzi.neomacros.core.api;

import java.util.List;
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

    /**
     * The command names currently in the client-visible command tree. The
     * default answer is empty: an implementation without a live dispatcher
     * (stubs, pre-connection) lists nothing, matching the game's behaviour
     * before a session opens.
     */
    default List<String> names() {
        return List.of();
    }

    /**
     * Completes one partial command line against the live tree and returns
     * the suggestion texts. The default answer is empty for the same reason
     * as {@link #names()}.
     */
    default CompletableFuture<List<String>> suggestions(String commandPart) {
        return CompletableFuture.completedFuture(List.of());
    }
}
