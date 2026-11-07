package ink.tuanzi.neomacros.core.runtime;

import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle of one pending or repeating timer created through the thread
 * module; cancelling stops the schedule (idempotent).
 * @zh thread 定时器句柄；cancel 停止调度（幂等）。
 */
@ApiSince("0.2.0")
@ApiLoader("*")
@ApiMC("*")
public final class TimerHandle {

    private final ScheduledFuture<?> scheduled;
    private final Runnable cancellation;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    TimerHandle(ScheduledFuture<?> scheduled, Runnable cancellation) {
        this.scheduled = scheduled;
        this.cancellation = cancellation;
    }

    /** Cancels the timer; later fires never run.
     * @zh 取消定时器；之后的触发不再执行。 */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            if (scheduled != null) {
                scheduled.cancel(false);
            }
            if (cancellation != null) {
                cancellation.run();
            }
        }
    }

    /** Whether the timer is still scheduled (or mid-fire).
     * @zh 定时器是否仍处于调度中。 */
    public boolean isActive() {
        return !cancelled.get()
                && (scheduled == null || !scheduled.isDone());
    }
}
