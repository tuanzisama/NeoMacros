package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;

/**
 * One paired web device as the panel sees it: identity and bookkeeping
 * only — the public key material never leaves the registry file.
 */
public record DeviceView(
        @NmDesc("The device id (dev_ + hex)") String id,
        @NmDesc("The display label (browser UA digest)") String label,
        @NmDesc("The signature algorithm: ed25519 or ecdsa-p256") String algo,
        @NmDesc("Unix seconds of the pairing approval") long createdAt,
        @NmDesc("Unix seconds of the last accepted signature") long lastSeenAt) {
}
