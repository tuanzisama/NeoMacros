package ink.tuanzi.neomacros.fabric.web.auth;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-source-address exponential backoff after signature failures: the
 * n-th consecutive failure silences the source for
 * {@code min(1s · 2^(n-1), 4s)}. A request arriving inside the window is
 * rejected without being processed; a successful authentication resets the
 * source's strike count. Only failed signatures feed the tracker — client
 * mistakes must not lock out the (necessarily shared) local source.
 */
public final class BackoffTracker {

    private static final long BASE_MILLIS = 1000;
    private static final long MAX_MILLIS = 4_000;
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

    /** Seconds until the source's window opens (at least 1 when blocked). */
    public long retryAfterSeconds(String source) {
        Strikes strikes = sources.get(source);
        if (strikes == null) {
            return 0;
        }
        long remaining = strikes.until - System.currentTimeMillis();
        return remaining <= 0 ? 0 : Math.max(1, remaining / 1000 + (remaining % 1000 == 0 ? 0 : 1));
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
