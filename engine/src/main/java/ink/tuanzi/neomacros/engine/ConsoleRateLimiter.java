package ink.tuanzi.neomacros.engine;

/**
 * Per-session token bucket guarding the SSE channel from log floods (a
 * script console-logging in a tick loop would otherwise saturate every
 * panel client). Bursts up to 50 emissions, refills at 25 per second.
 * Suppressed emissions surface as synthetic WARN notices instead of being
 * lost silently: when an allowed emission follows a drop episode the
 * notice rides along immediately, and a one-shot flood still notices every
 * {@value #NOTICE_BATCH} suppressed emissions mid-episode.
 */
final class ConsoleRateLimiter {

    private static final int BURST = 50;
    private static final double REFILL_PER_NANO = 25.0 / 1_000_000_000.0;

    /** Suppressed emissions per synthetic notice episode. */
    static final int NOTICE_BATCH = 50;

    private long lastNanos = System.nanoTime();
    private double tokens = BURST;
    private long suppressed;
    private long sinceNotice;

    /** One acquisition outcome: allowed, plus any pending suppression count. */
    record Decision(boolean allowed, long suppressedCount) {
    }

    private final Decision allowed = new Decision(true, 0);
    private final Decision dropped = new Decision(false, 0);

    /** Acquires one emission slot; the decision carries the notice count. */
    synchronized Decision acquire() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            if (sinceNotice > 0) {
                long notice = sinceNotice;
                sinceNotice = 0;
                return new Decision(true, notice);
            }
            return allowed;
        }
        suppressed++;
        sinceNotice++;
        if (sinceNotice >= NOTICE_BATCH) {
            long notice = sinceNotice;
            sinceNotice = 0;
            return new Decision(false, notice);
        }
        return dropped;
    }

    private void refill() {
        long now = System.nanoTime();
        tokens = Math.min(BURST, tokens + (now - lastNanos) * REFILL_PER_NANO);
        lastNanos = now;
    }
}
