package ink.tuanzi.neomacros.core.event;

/**
 * Cancellation flag threaded through synchronous event dispatch; the script
 * surface of this object is {@code ev.cancel()} / {@code ev.isCanceled()}.
 */
public final class CancelFlag implements Runnable {

    private volatile boolean canceled;

    /** Marks the event as canceled. */
    public void cancel() {
        canceled = true;
    }

    /** Runnable view used by the engine to expose cancel() to guest callbacks. */
    @Override
    public void run() {
        cancel();
    }

    /** Returns whether any synchronous callback canceled the event. */
    public boolean isCanceled() {
        return canceled;
    }
}
