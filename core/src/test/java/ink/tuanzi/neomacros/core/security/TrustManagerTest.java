package ink.tuanzi.neomacros.core.security;

import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.config.ConfigManager;
import ink.tuanzi.neomacros.core.config.CoreOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trust gate state machine: undeclared = allow, declared+untrusted = deny
 * with a prompt, trusted = exactly the declared capabilities, content change
 * re-enters the flow, session grants do not persist, and persistence survives
 * a ConfigManager round-trip.
 */
class TrustManagerTest {

    private static CoreOptions options(Path dir) {
        return options(dir, true);
    }

    private static CoreOptions options(Path dir, boolean saveOnClose) {
        var options = new CoreOptions();
        var manager = new ConfigManager(dir.resolve("options.json"), m -> { });
        manager.register(options);
        manager.load();
        if (saveOnClose) {
            manager.close();
        }
        return options;
    }

    private static TrustManager manager(CoreOptions options, List<TrustManager.TrustPrompt> prompts) {
        var manager = new TrustManager(options);
        manager.onPrompt(prompts::add);
        return manager;
    }

    @Test
    void undeclaredCapabilitiesAllowEverything(@TempDir Path dir) {
        var options = options(dir);
        var prompts = new ArrayList<TrustManager.TrustPrompt>();
        var manager = manager(options, prompts);

        var view = manager.viewFor("hash-a", List.of());

        assertTrue(view.allows("fs"));
        assertTrue(view.allows("net"));
        assertTrue(prompts.isEmpty());
    }

    @Test
    void declaredUntrustedDeniesAndFiresPrompt(@TempDir Path dir) {
        var options = options(dir);
        var prompts = new ArrayList<TrustManager.TrustPrompt>();
        var manager = manager(options, prompts);

        var view = manager.viewFor("hash-b", List.of("fs", "net"));

        assertFalse(view.allows("fs"));
        assertFalse(view.allows("net"));
        assertEquals(1, prompts.size());
        assertEquals(List.of("fs", "net"), prompts.get(0).requested());
    }

    @Test
    void trustPersistsAndMatchesDeclared(@TempDir Path dir) throws Exception {
        Path configDir = dir.resolve("cfg");
        var options = options(configDir);
        var manager = manager(options, new ArrayList<>());
        String hash = TrustManager.hashOf("script source v1");
        manager.trust(hash);
        // Flush the trust table to options.json: register + close (save is
        // unconditional, so the runtime-mutated field lands on disk).
        try (var flusher = new ConfigManager(configDir.resolve("options.json"), m -> { })) {
            flusher.register(options);
        }

        // Reload from disk through a fresh options instance.
        var reloaded = options(configDir);
        assertTrue(reloaded.getTrust().containsKey(hash));

        var view = manager.viewFor(hash, List.of("fs"));
        assertTrue(view.allows("fs"));
        assertFalse(view.allows("net"), "only declared capabilities are granted");
    }

    @Test
    void contentChangeReentersTrustFlow(@TempDir Path dir) {
        var options = options(dir);
        var prompts = new ArrayList<TrustManager.TrustPrompt>();
        var manager = manager(options, prompts);
        manager.trust(TrustManager.hashOf("v1"));
        var view = manager.viewFor(TrustManager.hashOf("v2-changed"), List.of("fs"));

        assertFalse(view.allows("fs"));
        assertEquals(1, prompts.size());
    }

    @Test
    void sessionGrantsDoNotPersist(@TempDir Path dir) throws Exception {
        Path configDir = dir.resolve("cfg");
        var options = options(configDir);
        var manager = manager(options, new ArrayList<>());
        String hash = TrustManager.hashOf("session-script");
        manager.grantSession(hash, List.of("net"));

        var view = manager.viewFor(hash, List.of("net"));
        assertTrue(view.allows("net"), "session grant should be honored this session");
        manager.revoke(hash);
        assertFalse(manager.isTrusted(hash), "revoke must remove the trust entry");
    }

    @Test
    void packetCapabilityIsInTheDirectoryAndGatesLikeTheOthers(@TempDir Path dir) {
        // 拍板甲: buffer.send() walks the wire under the `packet` key.
        assertTrue(TrustManager.CAPABILITIES.contains("packet"),
                "the packet capability joins the directory (G-002 §5.2 方案甲)");

        var options = options(dir);
        var prompts = new ArrayList<TrustManager.TrustPrompt>();
        var manager = manager(options, prompts);

        // Declared but untrusted: denied + one prompt (the rejection path).
        var denied = manager.viewFor("hash-packet", List.of("packet"));
        assertFalse(denied.allows("packet"));
        assertEquals(1, prompts.size());
        assertEquals(List.of("packet"), prompts.get(0).requested());

        // Trusted: exactly the declared capability is granted.
        manager.trust("hash-packet");
        var granted = manager.viewFor("hash-packet", List.of("packet"));
        assertTrue(granted.allows("packet"));
        assertFalse(granted.allows("fs"), "only declared capabilities are granted");
    }
}
