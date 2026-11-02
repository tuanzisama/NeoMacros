package ink.tuanzi.neomacros.fabric.web.v1;

import java.util.Map;

/**
 * A browser pairing request against the one-time token from the deep link
 * (single use, one token pairs one device, three-minute lifetime). The
 * public key travels as a JWK object; the private key never leaves the
 * browser.
 *
 * @param token  the one-time pairing token (deep-link query parameter)
 * @param label  device display name (UA digest) shown in the device list
 * @param algo   signature algorithm: ed25519 (preferred) or ecdsa-p256
 * @param pubJwk the public key as a JWK object
 */
public record PairRequest(String token, String label, String algo,
                          Map<String, Object> pubJwk) {
}
