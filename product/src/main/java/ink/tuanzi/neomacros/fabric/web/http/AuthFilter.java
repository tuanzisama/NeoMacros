package ink.tuanzi.neomacros.fabric.web.http;

import ink.tuanzi.neomacros.fabric.web.auth.BackoffTracker;
import ink.tuanzi.neomacros.fabric.web.auth.Nm1Verifier;

/**
 * The NM1 authentication step of the request pipeline: requests that need a
 * signature are verified (failures feed the source's backoff record), the
 * exempt endpoints pass through, and sources inside a backoff window are
 * rejected before any processing.
 */
public final class AuthFilter {

    /** Paths served without an NM1 signature (hardening still applies). */
    private static final java.util.Set<String> EXEMPT = java.util.Set.of(
            "GET /api/v1/meta",
            "POST /api/v1/pair",
            "GET /api-docs/openapi.json");

    private final Nm1Verifier verifier;
    private final BackoffTracker backoff;

    /** Creates the filter over the verifier and its backoff tracker. */
    public AuthFilter(Nm1Verifier verifier, BackoffTracker backoff) {
        this.verifier = verifier;
        this.backoff = backoff;
    }

    /** Whether this verb+path may skip the NM1 signature. */
    public static boolean exempt(String method, String path) {
        return EXEMPT.contains(method + " " + path);
    }

    /** Whether the source is currently inside a backoff window. */
    public boolean blocked(String source) {
        return backoff.blocked(source);
    }

    /**
     * Verifies a signed request. Returns the authenticated client id; the
     * thrown {@link ApiError} carries the reason. The SSE stream endpoint
     * authenticates by ticket instead and never reaches this filter.
     */
    public String authenticate(String source, String method, String path, String query,
                               byte[] body, String authorization) {
        Nm1Verifier.Result result = verifier.verify(method, path, query, body, authorization);
        if (!result.ok()) {
            backoff.fail(source);
            throw ApiError.unauthorized(result.problem());
        }
        backoff.success(source);
        return result.clientId();
    }
}
