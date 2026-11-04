package ink.tuanzi.neomacros.fabric.web.pair;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Tokenless pairing requests awaiting the in-game decision. Submissions
 * arrive on HTTP threads and decisions land from the client thread, so the
 * table is a concurrent map with synchronized mutators. Requests expire
 * after a short window and the table is capped; both are enforced lazily
 * on access (the NonceStore idiom). Decided entries outlive their request
 * window so a late poll still reads the outcome.
 */
public final class PairingRequests {

    /** Request validity in milliseconds. */
    public static final long REQUEST_TTL_MILLIS = 60_000;

    private static final int MAX_ENTRIES = 64;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** One pairing request: the browser identity plus where it came from. */
    public record Request(String id, String label, String algo, Map<String, Object> pubJwk,
                          String origin, String source, long expiresAtMillis) {
    }

    /** The lifecycle state of a request. */
    public enum Outcome {
        /** Waiting for the in-game decision, inside the request window. */
        PENDING,
        /** Approved in game; the client id is frozen in. */
        APPROVED,
        /** Denied in game. */
        DENIED,
        /** Past its window without a decision (the poll maps this to expired). */
        EXPIRED
    }

    private static final class Entry {
        final Request request;
        Outcome outcome = Outcome.PENDING;
        String clientId;

        Entry(Request request) {
            this.request = request;
        }
    }

    private final LongSupplier nowMillis;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /** Creates the request table over one clock. */
    public PairingRequests(LongSupplier nowMillis) {
        this.nowMillis = nowMillis;
    }

    /**
     * Registers a new pending request; expired entries and the
     * oldest-over-cap entries are evicted first.
     */
    public synchronized Request submit(String label, String algo, Map<String, Object> pubJwk,
                                       String origin, String source) {
        sweep();
        String id = newId();
        Request request = new Request(id, label, algo, pubJwk, origin, source,
                nowMillis.getAsLong() + REQUEST_TTL_MILLIS);
        entries.put(id, new Entry(request));
        return request;
    }

    /** The live pending requests, submission order (oldest) first. */
    public synchronized List<Request> pending() {
        sweep();
        List<Request> out = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.outcome == Outcome.PENDING) {
                out.add(entry.request);
            }
        }
        return out;
    }

    /** Approves the request and freezes the client id; false when not decidable. */
    public synchronized boolean approve(String id, String clientId) {
        Entry entry = entries.get(id);
        if (entry == null || entry.outcome != Outcome.PENDING) {
            return false;
        }
        entry.outcome = Outcome.APPROVED;
        entry.clientId = clientId;
        return true;
    }

    /** Denies the request; false when not decidable. */
    public synchronized boolean deny(String id) {
        Entry entry = entries.get(id);
        if (entry == null || entry.outcome != Outcome.PENDING) {
            return false;
        }
        entry.outcome = Outcome.DENIED;
        return true;
    }

    /** The outcome of one request, or {@code null} for an unknown id. */
    public synchronized Outcome outcomeOf(String id) {
        Entry entry = entries.get(id);
        if (entry == null) {
            return null;
        }
        if (entry.outcome == Outcome.PENDING
                && nowMillis.getAsLong() >= entry.request.expiresAtMillis()) {
            entry.outcome = Outcome.EXPIRED;
        }
        return entry.outcome;
    }

    /** The frozen client id of an approved request, or {@code null}. */
    public synchronized String clientIdOf(String id) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.clientId;
    }

    /** One request by id, or {@code null} (prompt rendering). */
    public synchronized Request get(String id) {
        return entries.containsKey(id) ? entries.get(id).request : null;
    }

    /** Drops expired pending requests; decided entries are kept for late polls. */
    private void sweep() {
        long now = nowMillis.getAsLong();
        entries.values().removeIf(entry -> entry.outcome == Outcome.PENDING
                && now >= entry.request.expiresAtMillis());
        while (entries.size() > MAX_ENTRIES) {
            String oldest = entries.keySet().iterator().next();
            if (entries.remove(oldest) == null) {
                break;
            }
        }
    }

    /** Allocates a short random id that is not in use. */
    private String newId() {
        for (int attempt = 0; attempt < 64; attempt++) {
            byte[] raw = new byte[4];
            RANDOM.nextBytes(raw);
            String id = HexFormat.of().formatHex(raw);
            if (!entries.containsKey(id)) {
                return id;
            }
        }
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }
}
