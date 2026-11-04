package ink.tuanzi.neomacros.fabric.web.http;

import ink.tuanzi.neomacros.fabric.web.auth.Nm1Verifier;

/**
 * The NM1 authentication step of the request pipeline: requests that need
 * a signature are verified, the exempt endpoints pass through.
 */
public final class AuthFilter {

    /** Paths served without an NM1 signature (hardening still applies). */
    private static final java.util.Set<String> EXEMPT = java.util.Set.of(
            "GET /api/v1/meta",
            "POST /api/v1/pair",
            "POST /api/v1/pair-request",
            "GET /api-docs/openapi.json");

    private final Nm1Verifier verifier;

    /** Creates the filter over the verifier. */
    public AuthFilter(Nm1Verifier verifier) {
        this.verifier = verifier;
    }

    /** Whether this verb+path may skip the NM1 signature. */
    public static boolean exempt(String method, String path) {
        if (EXEMPT.contains(method + " " + path)) {
            return true;
        }
        // The tokenless pairing poll is unauthenticated by design (the
        // browser holds no credentials yet); the random request id is the
        // capability, and the decision itself happens in game.
        return method.equals("GET") && path.startsWith("/api/v1/pair-request/");
    }

    /**
     * Verifies a signed request. Returns the authenticated client id; the
     * thrown {@link ApiError} carries the reason. The SSE stream endpoint
     * authenticates by ticket instead and never reaches this filter.
     */
    public String authenticate(String method, String path, String query,
                               byte[] body, String authorization) {
        Nm1Verifier.Result result = verifier.verify(method, path, query, body, authorization);
        if (!result.ok()) {
            throw ApiError.unauthorized(result.problem());
        }
        return result.clientId();
    }
}
