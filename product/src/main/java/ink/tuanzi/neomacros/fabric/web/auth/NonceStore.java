package ink.tuanzi.neomacros.fabric.web.auth;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single-use replay guard for NM1 nonces: a bounded least-recently-used map
 * with a time-to-live. A nonce is accepted exactly once per device; entries
 * leave by either the LRU bound ({@value CAPACITY}) or the TTL, whichever
 * comes first. The TTL is twice the timestamp window, so any in-window
 * replay is always still remembered.
 */
public final class NonceStore {

    private static final int CAPACITY = 4096;

    private final long ttlMillis;
    private final java.util.function.LongSupplier clock;
    private final Map<String, Long> seen = new LinkedHashMap<>(16, 0.75f, true);

    /** Creates the store with the given entry lifetime on the wall clock. */
    public NonceStore(long ttlMillis) {
        this(ttlMillis, System::currentTimeMillis);
    }

    /** Creates the store with an explicit clock (tests shrink the TTL). */
    public NonceStore(long ttlMillis, java.util.function.LongSupplier clock) {
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    /**
     * Records a nonce; returns false when it was already used inside the
     * TTL. Expired entries and the least-recently-used overflow are dropped.
     * A rejected replay does not refresh the entry's lifetime.
     */
    public synchronized boolean accept(String clientId, String nonce) {
        long now = clock.getAsLong();
        evictExpired(now);
        String key = clientId + "\n" + nonce;
        if (seen.containsKey(key)) {
            return false;
        }
        seen.put(key, now);
        while (seen.size() > CAPACITY) {
            Iterator<String> oldest = seen.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }

    private void evictExpired(long now) {
        Iterator<Map.Entry<String, Long>> entries = seen.entrySet().iterator();
        while (entries.hasNext()) {
            if (now - entries.next().getValue() > ttlMillis) {
                entries.remove();
            } else {
                break;
            }
        }
    }
}
