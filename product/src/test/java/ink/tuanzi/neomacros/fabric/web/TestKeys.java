package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.fabric.web.auth.Nm1Header;
import ink.tuanzi.neomacros.fabric.web.auth.Nm1Verifier;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test-side cryptography for the NM1 integration tests: generates key
 * pairs, renders their public halves as JWK maps (the registry's stored
 * shape), and signs requests exactly like the browser would.
 */
public final class TestKeys {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private TestKeys() {
    }

    /** A device the tests control: key pair plus its JWK rendering. */
    public static final class Device {

        public final String id;
        public final String algo;
        public final KeyPair pair;
        public final Map<String, Object> pubJwk;

        private Device(String id, String algo, KeyPair pair, Map<String, Object> pubJwk) {
            this.id = id;
            this.algo = algo;
            this.pair = pair;
            this.pubJwk = pubJwk;
        }

        /** The same device re-keyed to the id the registry allocated. */
        public Device withId(String newId) {
            return new Device(newId, algo, pair, pubJwk);
        }

        /** The Authorization header value for one signed request. */
        public String authorize(String method, String path, String query, byte[] body,
                                long ts, String nonce) {
            String bh = Nm1Verifier.bodyHash(body);
            String signing = Nm1Header.signingString(method, path, query, ts, nonce, bh);
            byte[] sig = sign(signing.getBytes(StandardCharsets.UTF_8));
            return "NeoMacros1 id=" + id + ", ts=" + ts + ", nonce=" + nonce
                    + ", bh=" + bh + ", sig=" + B64.encodeToString(sig);
        }

        private byte[] sign(byte[] message) {
            try {
                Signature signer = Signature.getInstance(
                        "ecdsa-p256".equals(algo) ? "SHA256withECDSA" : "Ed25519");
                signer.initSign(pair.getPrivate());
                signer.update(message);
                byte[] der = signer.sign();
                if ("ecdsa-p256".equals(algo)) {
                    return rawEcdsa(der);
                }
                return der;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Generates an Ed25519 device with a JWK public key. */
    public static Device ed25519(String id) {
        try {
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            var keyFactory = KeyFactory.getInstance("Ed25519");
            var spec = keyFactory.getKeySpec(pair.getPublic(),
                    java.security.spec.EdECPublicKeySpec.class);
            BigInteger y = spec.getPoint().getY();
            byte[] little = new byte[32];
            byte[] big = fixed(y, 32);
            for (int i = 0; i < 32; i++) {
                little[i] = big[31 - i];
            }
            if (spec.getPoint().isXOdd()) {
                little[31] |= (byte) 0x80;
            }
            Map<String, Object> jwk = new LinkedHashMap<>();
            jwk.put("kty", "OKP");
            jwk.put("crv", "Ed25519");
            jwk.put("x", B64.encodeToString(little));
            return new Device(id, "ed25519", pair, jwk);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Generates a P-256 ECDSA device with a JWK public key. */
    public static Device p256(String id) {
        try {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            var keyFactory = java.security.KeyFactory.getInstance("EC");
            var spec = keyFactory.getKeySpec(pair.getPublic(), ECPublicKeySpec.class);
            ECPoint point = spec.getW();
            Map<String, Object> jwk = new LinkedHashMap<>();
            jwk.put("kty", "EC");
            jwk.put("crv", "P-256");
            jwk.put("x", B64.encodeToString(fixed(point.getAffineX(), 32)));
            jwk.put("y", B64.encodeToString(fixed(point.getAffineY(), 32)));
            return new Device(id, "ecdsa-p256", pair, jwk);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Converts a DER ECDSA signature back to the WebCrypto raw r||s form. */
    static byte[] rawEcdsa(byte[] der) {
        // Parse SEQ { INTEGER r, INTEGER s } (the tests only see this shape).
        int offset = 2; // skip 0x30 + length
        int rLen = der[offset + 1];
        byte[] r = new byte[32];
        byte[] rSrc = java.util.Arrays.copyOfRange(der, offset + 2, offset + 2 + rLen);
        copyRightAligned(r, rSrc);
        int sOffset = offset + 2 + rLen;
        int sLen = der[sOffset + 1];
        byte[] s = new byte[32];
        byte[] sSrc = java.util.Arrays.copyOfRange(der, sOffset + 2, sOffset + 2 + sLen);
        copyRightAligned(s, sSrc);
        byte[] out = new byte[64];
        System.arraycopy(r, 0, out, 0, 32);
        System.arraycopy(s, 0, out, 32, 32);
        return out;
    }

    private static void copyRightAligned(byte[] target, byte[] source) {
        // DER INTEGERs may carry a leading sign byte; strip it first.
        int start = 0;
        while (start < source.length - 1 && source[start] == 0) {
            start++;
        }
        byte[] trimmed = java.util.Arrays.copyOfRange(source, start, source.length);
        int pad = target.length - trimmed.length;
        if (pad < 0) {
            throw new IllegalArgumentException("component longer than the fixed width");
        }
        java.util.Arrays.fill(target, 0, pad, (byte) 0);
        System.arraycopy(trimmed, 0, target, pad, trimmed.length);
    }

    private static byte[] dropSign(byte[] value) {
        if (value[0] == 0) {
            byte[] out = new byte[value.length - 1];
            System.arraycopy(value, 1, out, 0, out.length);
            return out;
        }
        return value;
    }

    private static byte[] fixed(BigInteger value, int width) {
        byte[] out = new byte[width];
        byte[] src = dropSign(value.toByteArray());
        System.arraycopy(src, 0, out, width - src.length, src.length);
        return out;
    }

    /** b64url of the SHA-256 (helper for tampered-header tests). */
    public static String sha256B64(String text) {
        try {
            return B64.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
