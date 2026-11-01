package ink.tuanzi.neomacros.fabric.web.auth;

import java.util.Map;

/**
 * One paired web device: the browser-generated public key the service trusts
 * for NM1 signatures, persisted in {@code web-devices.json}.
 *
 * @param id         stable device identifier ({@code dev_} + hex)
 * @param label      display name (browser UA digest) shown in chat prompts
 * @param algo       signature algorithm: {@code ed25519} or {@code ecdsa-p256}
 * @param pubJwk     the public key as a JWK object
 * @param createdAt  unix seconds when the device was approved
 * @param lastSeenAt unix seconds of the last accepted signature
 */
public record DeviceRecord(String id, String label, String algo, Map<String, Object> pubJwk,
                           long createdAt, long lastSeenAt) {
}
