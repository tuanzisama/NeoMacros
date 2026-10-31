package ink.tuanzi.neomacros.core.event;

/**
 * Callback of a synchronous subscription ({@code events.onSync}). It runs on
 * the dispatch thread (usually the game main thread) and must stay short: the
 * sync watchdog disables subscriptions that exceed the configured time budget.
 */
@FunctionalInterface
public interface SyncEventCallback {

    /** Handles the event payload; call {@code cancelFlag.cancel()} to cancel the event. */
    void invoke(Object payload, CancelFlag cancelFlag);
}
