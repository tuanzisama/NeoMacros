package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;

import java.util.Map;

/**
 * One tokenless pairing request body: who the browser is, signed with the
 * key it wants to register. Served to loopback browsers only; the Origin
 * header gates it and the in-game decision approves it.
 */
public record PairApplyRequest(
        @NmDesc("A human-readable browser label, e.g. Chrome@Windows") String label,
        @NmDesc("The signature algorithm: ed25519 or ecdsa-p256") String algo,
        @NmDesc("The browser's public key as JWK") Map<String, Object> pubJwk) {
}
