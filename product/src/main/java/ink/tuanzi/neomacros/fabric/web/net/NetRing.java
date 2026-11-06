package ink.tuanzi.neomacros.fabric.web.net;

import ink.tuanzi.neomacros.core.net.NetTraceEvent;
import ink.tuanzi.neomacros.fabric.web.v1.NetworkEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * In-memory ring of recorded {@code neo.net} requests with a monotonically
 * increasing sequence, mirroring the {@code ConsoleRing} shape: web clients
 * replay incrementally with {@code GET /network?after=} and subscribe to the
 * live tail through the SSE {@code network} event; both read from here.
 * Full bodies up to the recorder's per-side cap ride along with each event
 * into a shared byte budget ({@link #MAX_RETAINED_BYTES}); oversize or
 * evicted content reads back null and the panel says so. Appends arrive on
 * the core net pipeline thread (one ordered thread off the HTTP completion
 * threads), so seq order matches settle order.
 */
public final class NetRing {

    /** Total byte budget shared by all retained request/response bodies. */
    public static final int MAX_RETAINED_BYTES = 32 * 1024 * 1024;

    private final int capacity;
    private final long maxRetainedBytes;
    private final ArrayDeque<NetworkEntry> entries;
    private final Map<Long, String> requestBodies = new LinkedHashMap<>();
    private final Map<Long, byte[]> responseBodies = new LinkedHashMap<>();
    private long retainedBytes;
    private long nextSeq;
    private Consumer<NetworkEntry> listener;

    /** Creates the ring with the given capacity and the default budget. */
    public NetRing(int capacity) {
        this(capacity, MAX_RETAINED_BYTES);
    }

    /**
     * Creates the ring with an explicit budget (tests exercise eviction on
     * byte-sized budgets).
     */
    public NetRing(int capacity, long maxRetainedBytes) {
        this.capacity = capacity;
        this.maxRetainedBytes = maxRetainedBytes;
        this.entries = new ArrayDeque<>(capacity);
    }

    /** Sets the live-tail listener (the SSE network feed). */
    public synchronized void addListener(Consumer<NetworkEntry> listener) {
        this.listener = listener;
    }

    /**
     * Converts one core net record into the payload record, appends it,
     * retains the event's bodies under the shared budget, and notifies the
     * live-tail listener.
     */
    public synchronized void append(NetTraceEvent event) {
        long seq = nextSeq + 1;
        String requestBody = event.requestBody();
        byte[] responseBody = event.responseBody();
        if (requestBody != null) {
            requestBodies.put(seq, requestBody);
            retainedBytes += requestBody.length() * 2L; // UTF-16-ish, generous
        }
        if (responseBody != null) {
            responseBodies.put(seq, responseBody);
            retainedBytes += responseBody.length;
        }
        evictOverflow();
        NetworkEntry entry = new NetworkEntry(seq, event.ts(), event.method(),
                event.url(), event.initiator(), event.initiatorFile(),
                event.requestHeaders(), event.requestBodyPreview(),
                requestBody != null, event.requestBodyTruncated(),
                event.status(), event.responseHeaders(),
                event.responseBodyPreview(), responseBody != null,
                event.responseBodyTruncated(), event.responseBinary(),
                event.size(), event.durationMs(), event.waitMs(), event.downloadMs(),
                event.error());
        entries.addLast(entry);
        nextSeq = seq;
        // entries dropped from the ring lose their bodies too (no orphans)
        while (entries.size() > capacity) {
            NetworkEntry dropped = entries.removeFirst();
            retainedBytes -= dropBody(dropped.seq());
        }
        if (listener != null) {
            listener.accept(entry);
        }
    }

    /**
     * Evicts oldest-seq bodies until the retained set fits the budget again;
     * the panel learns of this through the body endpoint's 410.
     */
    private void evictOverflow() {
        while (retainedBytes > maxRetainedBytes
                && (!requestBodies.isEmpty() || !responseBodies.isEmpty())) {
            // insertion order = seq order; evict whichever side holds the
            // older entry first
            Iterator<Map.Entry<Long, String>> requestKeys =
                    requestBodies.entrySet().iterator();
            Iterator<Map.Entry<Long, byte[]>> responseKeys =
                    responseBodies.entrySet().iterator();
            Map.Entry<Long, String> oldestRequest =
                    requestKeys.hasNext() ? requestKeys.next() : null;
            Map.Entry<Long, byte[]> oldestResponse =
                    responseKeys.hasNext() ? responseKeys.next() : null;
            if (oldestResponse == null
                    || (oldestRequest != null
                    && oldestRequest.getKey() < oldestResponse.getKey())) {
                requestKeys.remove();
                retainedBytes -= oldestRequest.getValue().length() * 2L;
            } else {
                responseKeys.remove();
                retainedBytes -= oldestResponse.getValue().length;
            }
        }
    }

    /** The byte charge of one seq's bodies (0 when none are retained). */
    private long dropBody(long seq) {
        long freed = 0;
        String request = requestBodies.remove(seq);
        if (request != null) {
            freed += request.length() * 2L;
        }
        byte[] response = responseBodies.remove(seq);
        if (response != null) {
            freed += response.length;
        }
        return freed;
    }

    /** The retained request body for {@code seq}, or null when evicted/absent. */
    public synchronized String requestBody(long seq) {
        return requestBodies.get(seq);
    }

    /** The retained response body for {@code seq}, or null when evicted/absent. */
    public synchronized byte[] responseBody(long seq) {
        return responseBodies.get(seq);
    }

    /** The entry with the given seq, or null when not in the ring. */
    public synchronized NetworkEntry entry(long seq) {
        for (NetworkEntry entry : entries) {
            if (entry.seq() == seq) {
                return entry;
            }
        }
        return null;
    }

    /** Whether the seq is live in the ring (entry present). */
    public synchronized boolean contains(long seq) {
        return entry(seq) != null;
    }

    /** The entries with a sequence greater than {@code after}, oldest first. */
    public synchronized List<NetworkEntry> after(long after) {
        List<NetworkEntry> out = new ArrayList<>();
        for (NetworkEntry entry : entries) {
            if (entry.seq() > after) {
                out.add(entry);
            }
        }
        return out;
    }

    /** The newest sequence number (0 for an empty ring). */
    public synchronized long lastSeq() {
        return nextSeq;
    }
}
