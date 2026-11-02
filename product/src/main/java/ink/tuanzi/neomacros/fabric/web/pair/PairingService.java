package ink.tuanzi.neomacros.fabric.web.pair;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.LongSupplier;

/**
 * The pairing token store: one live token at a time (minted by the chat
 * link, delivered to the browser through the deep link). A token is the
 * pairing credential itself — single use, one token pairs exactly one
 * device, and it expires after three minutes; minting a fresh token
 * invalidates the previous one.
 */
public final class PairingService {

    /** Token validity in milliseconds. */
    public static final long TOKEN_TTL_MILLIS = 180_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LongSupplier nowMillis;
    private final long tokenTtlMillis;

    private String token;
    private long tokenExpiresAt;

    /** Creates the service with the production token lifetime. */
    public PairingService(LongSupplier nowMillis) {
        this(nowMillis, TOKEN_TTL_MILLIS);
    }

    /** Creates the service with an explicit token lifetime (tests shrink it). */
    public PairingService(LongSupplier nowMillis, long tokenTtlMillis) {
        this.nowMillis = nowMillis;
        this.tokenTtlMillis = tokenTtlMillis;
    }

    /**
     * Mints a fresh pairing token, invalidating the previous one, and
     * returns it (base64url, 32 bytes of entropy).
     */
    public synchronized String newToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokenExpiresAt = nowMillis.getAsLong() + tokenTtlMillis;
        return token;
    }

    /** Whether the given token is the live one and still inside its lifetime. */
    public synchronized boolean validate(String candidate) {
        return token != null && token.equals(candidate)
                && nowMillis.getAsLong() < tokenExpiresAt;
    }

    /** Burns the live token (single use) after a successful pairing. */
    public synchronized void consume(String candidate) {
        if (validate(candidate)) {
            token = null;
        }
    }
}
