package ink.tuanzi.neomacros.fabric.web.auth;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-source-address exponential backoff after authentication failures:
 * the n-th consecutive failure silences the source for
 * {@code min(1s · 2^(n-1), 16s)}. A request arriving inside the window is
 * rejected without being processed; a successful authentication resets the
 * source's strike count.
 */
public final class BackoffTracker {

    private static final long BASE_MILLIS = 1000;
    private static final long MAX_MILLIS = 16_000;
    private static final int MAX_STRIKES = 30;

    private static final class Strikes {
        int count;
        long until;
    }

    private final Map<String, Strikes> sources = new ConcurrentHashMap<>();

    /**
     * Returns true when the source is currently inside a backoff window and
     * must be rejected without further processing.
     */
    public boolean blocked(String source) {
        Strikes strikes = sources.get(source);
        return strikes != null && System.currentTimeMillis() < strikes.until;
    }

    /** Records one authentication failure for the source. */
    public void fail(String source) {
        Strikes strikes = sources.computeIfAbsent(source, key -> new Strikes());
        synchronized (strikes) {
            if (System.currentTimeMillis() >= strikes.until) {
                strikes.count = 0;
            }
            if (strikes.count < MAX_STRIKES) {
                strikes.count++;
            }
            long delay = Math.min(BASE_MILLIS << (strikes.count - 1), MAX_MILLIS);
            strikes.until = System.currentTimeMillis() + delay;
        }
    }

    /** Clears the source's record after a successful authentication. */
    public void success(String source) {
        sources.remove(source);
    }

    /** Drops expired records so idle sources do not accumulate. */
    public void prune() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Strikes>> it = sources.entrySet().iterator();
                it.hasNext(); ) {
            if (now >= it.next().getValue().until) {
                it.remove();
            }
        }
    }
}
