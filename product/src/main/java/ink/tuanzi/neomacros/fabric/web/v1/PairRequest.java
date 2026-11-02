package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import ink.tuanzi.neomacros.fabric.web.api.NmNullable;

import java.util.Map;

/**
 * A browser pairing request. Two mutually exclusive modes: the one-time
 * token from a deep link, or a six-character confirmation code the panel
 * displays for the player to verify in game. The public key travels as a
 * JWK object; the private key never leaves the browser.
 *
 * @param token the one-time pairing token (deep-link query parameter)
 * @param label device display name (UA digest) shown on the approve card
 * @param algo  signature algorithm: ed25519 (preferred) or ecdsa-p256
 * @param pubJwk the public key as a JWK object
 * @param code  the six-character (uppercase letters + digits) confirmation
 *              code displayed by the panel; the player verifies it in game
 */
public record PairRequest(
        @NmNullable @NmDesc("One-time pairing token from a deep link; either this or"
                + " code is required, not both") String token,
        String label,
        String algo,
        Map<String, Object> pubJwk,
        @NmNullable @NmDesc("Six-character confirmation code (uppercase letters and"
                + " digits) shown by the panel; the player verifies it against the"
                + " in-game prompt") String code) {
}
