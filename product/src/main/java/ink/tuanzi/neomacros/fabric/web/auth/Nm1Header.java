package ink.tuanzi.neomacros.fabric.web.auth;

/**
 * A parsed NM1 Authorization header:
 * {@code NeoMacros1 id=…, ts=…, nonce=…, bh=…, sig=…}.
 *
 * @param clientId the paired device id
 * @param ts       the unix-seconds timestamp the client signed at
 * @param nonce    the base64url 16-byte single-use value
 * @param bh       the base64url SHA-256 of the request body
 * @param sig      the base64url signature over the NM1 signing string
 */
public record Nm1Header(String clientId, long ts, String nonce, String bh, String sig) {

    /** The authentication scheme prefix. */
    public static final String SCHEME = "NeoMacros1";

    /** Parses a raw header value; null when the header is absent or malformed. */
    public static Nm1Header parse(String header) {
        if (header == null || !header.startsWith(SCHEME)) {
            return null;
        }
        String rest = header.substring(SCHEME.length()).strip();
        if (rest.isEmpty()) {
            return null;
        }
        String clientId = null;
        long ts = Long.MIN_VALUE;
        String nonce = null;
        String bh = null;
        String sig = null;
        for (String part : rest.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                return null;
            }
            String key = part.substring(0, eq).strip();
            String value = part.substring(eq + 1).strip();
            switch (key) {
                case "id" -> clientId = value;
                case "ts" -> {
                    try {
                        ts = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                case "nonce" -> nonce = value;
                case "bh" -> bh = value;
                case "sig" -> sig = value;
                default -> {
                    return null;
                }
            }
        }
        if (clientId == null || ts == Long.MIN_VALUE
                || nonce == null || bh == null || sig == null
                || clientId.isEmpty() || nonce.isEmpty() || bh.isEmpty() || sig.isEmpty()) {
            return null;
        }
        return new Nm1Header(clientId, ts, nonce, bh, sig);
    }

    /**
     * Builds the NM1 signing string:
     * {@code NM1\nMETHOD\npath?query\nts\nnonce\nbh} (the query may be empty;
     * the question mark is always present).
     */
    public static String signingString(String method, String path, String query,
                                       long ts, String nonce, String bh) {
        return "NM1\n" + method + "\n" + path + "?" + query + "\n"
                + ts + "\n" + nonce + "\n" + bh;
    }
}
