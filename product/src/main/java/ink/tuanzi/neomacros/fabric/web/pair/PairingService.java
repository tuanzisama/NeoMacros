package ink.tuanzi.neomacros.fabric.web.pair;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/**
 * Device pairing state: one live pairing token at a time (created by the
 * chat commands, delivered to the browser through the deep-link fragment)
 * and one pending pairing request at a time (a browser POST that waits for
 * the in-game approve/deny decision). A new request replaces a pending one,
 * releasing the previous waiter as a timeout; once a decision lands the
 * token is dead.
 */
public final class PairingService {

    /** Token validity in milliseconds. */
    public static final long TOKEN_TTL_MILLIS = 120_000;

    /** How long a pending request waits for the in-game decision. */
    public static final long DECISION_TIMEOUT_MILLIS = 120_000;

    /** The decision the long poll resolves with. */
    public enum Decision { APPROVED, DENIED, TIMED_OUT, REPLACED }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LongSupplier nowMillis;
    private final long tokenTtlMillis;
    private final long decisionTimeoutMillis;

    private String token;
    private long tokenExpiresAt;
    private boolean tokenDead;

    private PendingRequest pending;

    /** A browser pairing request waiting for the in-game decision. */
    public record PendingRequest(String label, String algo,
                                 java.util.Map<String, Object> pubJwk,
                                 String code,
                                 CompletableFuture<Decision> resolution) {
    }

    /** Creates the service with the production timeouts. */
    public PairingService(LongSupplier nowMillis) {
        this(nowMillis, TOKEN_TTL_MILLIS, DECISION_TIMEOUT_MILLIS);
    }

    /** Creates the service with explicit timeouts (tests shrink them). */
    public PairingService(LongSupplier nowMillis, long tokenTtlMillis,
                          long decisionTimeoutMillis) {
        this.nowMillis = nowMillis;
        this.tokenTtlMillis = tokenTtlMillis;
        this.decisionTimeoutMillis = decisionTimeoutMillis;
    }

    /** The configured decision wait (the long poll's own bound). */
    public long decisionTimeoutMillis() {
        return decisionTimeoutMillis;
    }

    /**
     * Mints a fresh pairing token, replacing any previous one, and returns
     * it (base64url, 32 bytes of entropy).
     */
    public synchronized String newToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokenExpiresAt = nowMillis.getAsLong() + tokenTtlMillis;
        tokenDead = false;
        return token;
    }

    /** The live token, or null when none exists or it expired. */
    public synchronized String currentToken() {
        if (token == null || tokenDead || nowMillis.getAsLong() >= tokenExpiresAt) {
            return null;
        }
        return token;
    }

    /**
     * Submits a browser pairing request against a token. Returns null when
     * the token is missing, expired, or already consumed; otherwise the
     * pending request whose {@code resolution} future completes with the
     * in-game decision (or {@link Decision#TIMED_OUT}).
     */
    public synchronized PendingRequest submit(String token, String label, String algo,
                                              java.util.Map<String, Object> pubJwk) {
        if (token == null || tokenDead
                || !token.equals(this.token)
                || nowMillis.getAsLong() >= tokenExpiresAt) {
            return null;
        }
        return enqueue(label, algo, pubJwk, null);
    }

    /**
     * Submits a token-less pairing request carrying the panel's six-char
     * confirmation code (the player verifies it against the panel before
     * approving). No token check applies; the single pending slot and the
     * decision timeout work the same.
     */
    public synchronized PendingRequest submitWithCode(String code, String label,
                                                      String algo,
                                                      java.util.Map<String, Object> pubJwk) {
        return enqueue(label, algo, pubJwk, code);
    }

    private synchronized PendingRequest enqueue(String label, String algo,
                                                java.util.Map<String, Object> pubJwk,
                                                String code) {
        CompletableFuture<Decision> resolution = new CompletableFuture<>();
        resolution.completeOnTimeout(Decision.TIMED_OUT, decisionTimeoutMillis,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        PendingRequest request = new PendingRequest(label, algo, pubJwk, code, resolution);
        PendingRequest previous = pending;
        pending = request;
        if (previous != null) {
            previous.resolution().complete(Decision.REPLACED);
        }
        return request;
    }

    /**
     * Resolves the pending request with the player's decision. Returns false
     * when nothing is pending (or the waiter already timed out and left).
     */
    public synchronized boolean resolve(boolean approved) {
        PendingRequest request = pending;
        if (request == null) {
            return false;
        }
        pending = null;
        tokenDead = true;
        return request.resolution().complete(approved ? Decision.APPROVED : Decision.DENIED);
    }

    /** Whether a pairing request is currently waiting for a decision. */
    public synchronized boolean hasPending() {
        return pending != null;
    }

    /** The pending request's label, or null. */
    public synchronized String pendingLabel() {
        return pending == null ? null : pending.label();
    }
}
