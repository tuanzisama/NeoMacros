package ink.tuanzi.neomacros.core.event;

/**
 * Callback of a synchronous subscription ({@code events.onSync}). It runs on
 * the dispatch thread (usually the game main thread) and must stay short: the
 * sync watchdog disables subscriptions that exceed the configured time budget.
 */
@FunctionalInterface
public interface SyncEventCallback {

    /**
     * Handles one sync delivery. Call {@code cancelFlag.cancel()} /
     * {@code setCanceled(boolean)} to drive the cancellation state (last
     * write wins); script deliveries commit field rewrites through
     * {@code envelope.hooks()} — the bus merges them onto the event state so
     * later sync callbacks observe them, and the accumulation lands on
     * {@code PublishResult.verdict()}.
     */
    void invoke(Object payload, CancelFlag cancelFlag);
}
