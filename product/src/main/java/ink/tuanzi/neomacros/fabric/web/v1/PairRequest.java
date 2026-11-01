package ink.tuanzi.neomacros.fabric.web.v1;

import java.util.Map;

/**
 * A browser pairing request against the one-time token from the deep link.
 * The public key travels as a JWK object; the private key never leaves the
 * browser.
 *
 * @param token  the one-time pairing token (deep-link fragment)
 * @param label  device display name (UA digest) shown on the approve card
 * @param algo   signature algorithm: ed25519 (preferred) or ecdsa-p256
 * @param pubJwk the public key as a JWK object
 */
public record PairRequest(String token, String label, String algo,
                          Map<String, Object> pubJwk) {
}
