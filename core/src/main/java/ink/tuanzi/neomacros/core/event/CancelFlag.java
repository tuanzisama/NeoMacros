package ink.tuanzi.neomacros.core.event;

/**
 * Cancellation state threaded through synchronous event dispatch; the script
 * surface of this object is {@code ev.cancel()} / the writable
 * {@code ev.cancelled} accessor. Semantics follow Bukkit: the flag stays
 * writable for the whole sync chain, and the last write wins — a later
 * callback may undo an earlier cancellation.
 */
public final class CancelFlag implements Runnable {

    private volatile boolean canceled;

    /** Marks the event as canceled. */
    public void cancel() {
        canceled = true;
    }

    /** Sets the cancellation state; a later callback may undo an earlier cancel. */
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    /** Runnable view used by the engine to expose cancel() to guest callbacks. */
    @Override
    public void run() {
        cancel();
    }

    /** Returns the current cancellation state. */
    public boolean isCanceled() {
        return canceled;
    }
}
