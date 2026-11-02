package ink.tuanzi.neomacros.fabric.web.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.function.LongSupplier;

/**
 * The NM1 request authentication chain. For one request (method, raw path,
 * raw query, body bytes, Authorization header) it walks the checks in the
 * mandated order — device known, timestamp inside the ±30s window, nonce
 * unused, body hash matches, signature valid — and reports the first
 * failure. Signature failures feed the source's backoff record.
 */
public final class Nm1Verifier {

    /** The timestamp window in milliseconds. */
    public static final long WINDOW_MILLIS = 30_000;

    /** The verification outcome: a client id, or a failure reason. */
    public record Result(String clientId, String problem) {

        /** Whether the request is authenticated. */
        public boolean ok() {
            return problem == null;
        }
    }

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    private final DeviceRegistry devices;
    private final NonceStore nonces;
    private final LongSupplier nowMillis;

    /** Creates the verifier; {@code nowMillis} is injectable for tests. */
    public Nm1Verifier(DeviceRegistry devices, NonceStore nonces, LongSupplier nowMillis) {
        this.devices = devices;
        this.nonces = nonces;
        this.nowMillis = nowMillis;
    }

    /** SHA-256 of a body, base64url (the {@code bh} header value). */
    public static String bodyHash(byte[] body) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Verifies one request. {@code method}/{@code path}/{@code query} must be
     * the exact request-line values; {@code body} the actual body bytes.
     */
    public Result verify(String method, String path, String query,
                         byte[] body, String authorization) {
        Nm1Header header = Nm1Header.parse(authorization);
        if (header == null) {
            return new Result(null, "missing or malformed Authorization header");
        }
        DeviceRecord device = devices.get(header.clientId());
        if (device == null) {
            return new Result(null, "unknown device: " + header.clientId());
        }
        long now = nowMillis.getAsLong();
        if (Math.abs(now - header.ts() * 1000) > WINDOW_MILLIS) {
            return new Result(null, "timestamp outside the ±30s window");
        }
        if (!nonces.accept(header.clientId(), header.nonce())) {
            return new Result(null, "nonce already used");
        }
        String expectedHash = bodyHash(body);
        if (!expectedHash.equals(header.bh())) {
            return new Result(null, "body hash mismatch");
        }
        String signingString = Nm1Header.signingString(method, path, query,
                header.ts(), header.nonce(), header.bh());
        if (!signatureValid(device, signingString, header.sig())) {
            return new Result(null, "signature verification failed");
        }
        devices.touch(header.clientId());
        return new Result(header.clientId(), null);
    }

    private boolean signatureValid(DeviceRecord device, String signingString, String sig) {
        byte[] signature;
        try {
            signature = B64.decode(sig);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] message = signingString.getBytes(StandardCharsets.UTF_8);
        try {
            PublicKey key = Nm1Keys.publicKey(device.algo(), device.pubJwk());
            if ("ecdsa-p256".equals(device.algo())) {
                signature = Nm1Keys.derEcdsaSignature(signature, 32);
            }
            Signature verifier = Signature.getInstance(
                    "ecdsa-p256".equals(device.algo()) ? "SHA256withECDSA" : "Ed25519");
            verifier.initVerify(key);
            verifier.update(message);
            return verifier.verify(signature);
        } catch (RuntimeException | java.security.GeneralSecurityException e) {
            return false;
        }
    }
}
