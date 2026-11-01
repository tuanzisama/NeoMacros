package ink.tuanzi.neomacros.fabric.web.auth;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.NamedParameterSpec;
import java.util.Base64;
import java.util.Map;

/**
 * Decodes a stored JWK public key into a JCA {@link PublicKey}. Supports the
 * two registered algorithms: Ed25519 ({@code kty=OKP}) and P-256 ECDSA
 * ({@code kty=EC}). Unusable material raises
 * {@code IllegalArgumentException}.
 */
public final class Nm1Keys {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    private Nm1Keys() {
    }

    /** Builds the public key for a device record. */
    public static PublicKey publicKey(String algo, Map<String, Object> jwk) {
        return switch (algo) {
            case "ed25519" -> ed25519(jwk);
            case "ecdsa-p256" -> p256(jwk);
            default -> throw new IllegalArgumentException("unsupported algorithm: " + algo);
        };
    }

    private static PublicKey ed25519(Map<String, Object> jwk) {
        require(jwk, "kty", "OKP");
        require(jwk, "crv", "Ed25519");
        // RFC 8037 wire form: 32 bytes, the y coordinate little-endian with
        // the high bit of the last byte carrying x's parity. The JCA wants
        // exactly that point (EdECPublicKeySpec, not the XDH spec class).
        byte[] raw = B64.decode(text(jwk, "x"));
        if (raw.length != 32) {
            throw new IllegalArgumentException("Ed25519 JWK x must be 32 bytes");
        }
        byte[] yBytes = raw.clone();
        boolean xOdd = (yBytes[31] & 0x80) != 0;
        yBytes[31] &= 0x7F;
        reverse(yBytes);
        BigInteger y = new BigInteger(1, yBytes);
        try {
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            var point = new java.security.spec.EdECPoint(xOdd, y);
            return factory.generatePublic(
                    new EdECPublicKeySpec(NamedParameterSpec.ED25519, point));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("bad Ed25519 JWK: " + e.getMessage(), e);
        }
    }

    private static PublicKey p256(Map<String, Object> jwk) {
        require(jwk, "kty", "EC");
        require(jwk, "crv", "P-256");
        byte[] x = B64.decode(text(jwk, "x"));
        byte[] y = B64.decode(text(jwk, "y"));
        ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);
            KeyFactory factory = KeyFactory.getInstance("EC");
            return factory.generatePublic(new ECPublicKeySpec(point, spec));
        } catch (NoSuchAlgorithmException | InvalidParameterSpecException
                | InvalidKeySpecException e) {
            throw new IllegalArgumentException("bad P-256 JWK: " + e.getMessage(), e);
        }
    }

    private static void require(Map<String, Object> jwk, String key, String expected) {
        if (!expected.equals(jwk.get(key))) {
            throw new IllegalArgumentException("JWK " + key + " must be " + expected);
        }
    }

    private static String text(Map<String, Object> jwk, String key) {
        Object value = jwk.get(key);
        if (!(value instanceof String s) || s.isEmpty()) {
            throw new IllegalArgumentException("JWK is missing '" + key + "'");
        }
        return s;
    }

    private static void reverse(byte[] data) {
        for (int i = 0, j = data.length - 1; i < j; i++, j--) {
            byte tmp = data[i];
            data[i] = data[j];
            data[j] = tmp;
        }
    }

    /**
     * Converts a WebCrypto-style raw ECDSA signature ({@code r||s}, fixed
     * width) into the DER encoding the JCA verifier expects.
     */
    public static byte[] derEcdsaSignature(byte[] raw, int componentWidth) {
        if (raw.length != componentWidth * 2) {
            throw new IllegalArgumentException("raw ECDSA signature must be "
                    + (componentWidth * 2) + " bytes, got " + raw.length);
        }
        byte[] r = half(raw, 0, componentWidth);
        byte[] s = half(raw, componentWidth, componentWidth);
        byte[] rDer = asn1Integer(r);
        byte[] sDer = asn1Integer(s);
        byte[] content = new byte[rDer.length + sDer.length + 4];
        content[0] = 0x02;
        content[1] = (byte) rDer.length;
        System.arraycopy(rDer, 0, content, 2, rDer.length);
        int offset = 2 + rDer.length;
        content[offset] = 0x02;
        content[offset + 1] = (byte) sDer.length;
        System.arraycopy(sDer, 0, content, offset + 2, sDer.length);
        byte[] out = new byte[content.length + 2];
        out[0] = 0x30;
        out[1] = (byte) content.length;
        System.arraycopy(content, 0, out, 2, content.length);
        return out;
    }

    private static byte[] half(byte[] source, int offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(source, offset, out, 0, length);
        return out;
    }

    /** Minimal ASN.1 INTEGER with a leading zero when the high bit is set. */
    private static byte[] asn1Integer(byte[] value) {
        int start = 0;
        while (start < value.length - 1 && value[start] == 0) {
            start++;
        }
        byte[] trimmed = half(value, start, value.length - start);
        boolean pad = (trimmed[0] & 0x80) != 0;
        byte[] out = new byte[trimmed.length + (pad ? 1 : 0)];
        System.arraycopy(trimmed, 0, out, pad ? 1 : 0, trimmed.length);
        return out;
    }
}
