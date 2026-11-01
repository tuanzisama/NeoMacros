package ink.tuanzi.neomacros.fabric.web.api;

/** The authentication styles a route can require. */
public enum NmAuth {

    /** Per-request NM1 signature in the Authorization header. */
    NM1,

    /** No authentication (non-sensitive endpoints; hardening still applies). */
    NONE,

    /** A one-time SSE ticket query parameter instead of a signature. */
    TICKET,
}
