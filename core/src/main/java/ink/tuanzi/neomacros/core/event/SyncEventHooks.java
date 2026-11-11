package ink.tuanzi.neomacros.core.event;

/**
 * Mutation sink of one synchronous delivery. The engine hands this to the
 * mutable event face ({@code __neoWrapMutableEvent}): field writes and
 * cancellation changes commit through it, the bus merges the writes back
 * onto the event state after each callback (sequential visibility), and the
 * accumulated values surface as {@code PublishResult.verdict()}. MONITOR
 * deliveries never reach the sink — the engine drops those writes guest-side.
 */
public interface SyncEventHooks {

    /** Commits one rewritten payload field (value already neutralized). */
    void write(String key, Object value);

    /** Marks the event canceled. */
    void cancel();

    /** Current cancellation state. */
    boolean isCancelled();

    /** Sets the cancellation state (last write wins). */
    void setCancelled(boolean cancelled);
}
