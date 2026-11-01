package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.fabric.web.auth.DeviceRegistry;
import ink.tuanzi.neomacros.fabric.web.auth.Nm1Verifier;
import ink.tuanzi.neomacros.fabric.web.auth.NonceStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The NM1 chain against known-good self-signed requests and a table of
 * forgeries: wrong signature, stale timestamp, reused nonce, tampered body,
 * unknown device, and malformed headers.
 */
class Nm1VerifierTest {

    @TempDir
    Path temp;

    private final AtomicLong now = new AtomicLong(1_700_000_000_000L);

    private Nm1Verifier verifier(DeviceRegistry devices) {
        return new Nm1Verifier(devices, new NonceStore(60_000), now::get);
    }

    @Test
    void acceptsASelfSignedRequest() {
        TestKeys.Device device = TestKeys.ed25519("dev_aa");
        DeviceRegistry registry = new DeviceRegistry(temp.resolve("d.json"), m -> { });
        String registeredId = registry.register("browser", device.algo, device.pubJwk).id();
        device = device.withId(registeredId);
        String clientId = registry.all().get(0).id();
        Nm1Verifier verifier = verifier(registry);

        byte[] body = "{\"hello\":true}".getBytes(StandardCharsets.UTF_8);
        String authorization = device.authorize("POST", "/api/v1/state", "", body,
                now.get() / 1000, "AAAAAAAAAAAAAAAAAAAAAA");
        Nm1Verifier.Result result = verifier.verify("POST", "/api/v1/state", "",
                body, authorization);
        assertTrue(result.ok());
        assertEquals(clientId, result.clientId());
    }

    @Test
    void rejectsAForgedSignature() {
        TestKeys.Device device = TestKeys.ed25519("dev_bb");
        TestKeys.Device other = TestKeys.ed25519("dev_cc");
        DeviceRegistry registry = new DeviceRegistry(temp.resolve("d.json"), m -> { });
        String registeredId = registry.register("browser", device.algo, device.pubJwk).id();
        device = device.withId(registeredId);
        Nm1Verifier verifier = verifier(registry);

        byte[] body = new byte[0];
        String authorization = other.authorize("GET", "/api/v1/state", "", body,
                now.get() / 1000, "AAAAAAAAAAAAAAAAAAAAAA");
        // Header claims the registered device; the signature belongs to another key.
        String tampered = authorization.replaceFirst("id=[^,]+", "id="
                + registry.all().get(0).id());
        assertFalse(verifier.verify("GET", "/api/v1/state", "", body, tampered).ok());
    }

    @Test
    void enforcesTheTimestampWindow() {
        TestKeys.Device device = TestKeys.ed25519("dev_dd");
        DeviceRegistry registry = new DeviceRegistry(temp.resolve("d.json"), m -> { });
        String registeredId = registry.register("browser", device.algo, device.pubJwk).id();
        device = device.withId(registeredId);
        Nm1Verifier verifier = verifier(registry);
        byte[] body = new byte[0];

        long inside = (now.get() - 29_000) / 1000;
        assertTrue(verifier.verify("GET", "/api/v1/state", "", body,
                device.authorize("GET", "/api/v1/state", "", body, inside, "bQ==")).ok());

        long outside = (now.get() - 31_000) / 1000;
        assertFalse(verifier.verify("GET", "/api/v1/state", "", body,
                device.authorize("GET", "/api/v1/state", "", body, outside, "bg==")).ok());

        long future = (now.get() + 31_000) / 1000;
        assertFalse(verifier.verify("GET", "/api/v1/state", "", body,
                device.authorize("GET", "/api/v1/state", "", body, future, "bw==")).ok());
    }

    @Test
    void rejectsANonceReuse() {
        TestKeys.Device device = TestKeys.ed25519("dev_ee");
        DeviceRegistry registry = new DeviceRegistry(temp.resolve("d.json"), m -> { });
        String registeredId = registry.register("browser", device.algo, device.pubJwk).id();
        device = device.withId(registeredId);
        Nm1Verifier verifier = verifier(registry);
        byte[] body = new byte[0];
        String authorization = device.authorize("GET", "/api/v1/state", "", body,
                now.get() / 1000, "cmVwbGF5LW1lLW9uY2U");
        assertTrue(verifier.verify("GET", "/api/v1/state", "", body, authorization).ok());
        assertFalse(verifier.verify("GET", "/api/v1/state", "", body, authorization).ok());
    }

    @Test
    void rejectsABodyHashMismatch() {
        TestKeys.Device device = TestKeys.ed25519("dev_ff");
        DeviceRegistry registry = new DeviceRegistry(temp.resolve("d.json"), m -> { });
        String registeredId = registry.register("browser", device.algo, device.pubJwk).id();
        device = device.withId(registeredId);
        Nm1Verifier verifier = verifier(registry);

        byte[] body = "{\"v\":1}".getBytes(StandardCharsets.UTF_8);
        String authorization = device.authorize("GET", "/api/v1/state", "", body,
                now.get() / 1000, "Ym9keS1oYXNoLXRlc3Q");
        byte[] different = "{\"v\":2}".getBytes(StandardCharsets.UTF_8);
        assertFalse(verifier.verify("GET", "/api/v1/state", "", different, authorization).ok());
    }

    @Test
    void rejectsAnUnknownDeviceAndMalformedHeaders() {
        DeviceRegistry registry = new DeviceRegistry(temp.resolve("d.json"), m -> { });
        Nm1Verifier verifier = verifier(registry);
        byte[] body = new byte[0];

        assertFalse(verifier.verify("GET", "/api/v1/state", "", body, null).ok());
        assertFalse(verifier.verify("GET", "/api/v1/state", "", body, "Bearer x").ok());
        assertFalse(verifier.verify("GET", "/api/v1/state", "", body,
                "NeoMacros1 id=dev_404, ts=1, nonce=a, bh=b").ok());
        assertFalse(verifier.verify("GET", "/api/v1/state", "", body,
                "NeoMacros1 id=dev_404, ts=notanumber, nonce=a, bh=b, sig=c").ok());
    }

    @Test
    void verifiesP256WithRawWebCryptoSignatures() {
        TestKeys.Device device = TestKeys.p256("dev_10");
        DeviceRegistry registry = new DeviceRegistry(temp.resolve("d.json"), m -> { });
        String registeredId = registry.register("browser", device.algo, device.pubJwk).id();
        device = device.withId(registeredId);
        Nm1Verifier verifier = verifier(registry);
        byte[] body = new byte[0];
        String authorization = device.authorize("GET", "/api/v1/state", "", body,
                now.get() / 1000, "cDI1Ni1yYXctc2lnLXRlc3Q");
        assertTrue(verifier.verify("GET", "/api/v1/state", "", body, authorization).ok());
    }
}
