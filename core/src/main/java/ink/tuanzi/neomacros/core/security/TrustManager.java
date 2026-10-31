package ink.tuanzi.neomacros.core.security;

import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.config.CoreOptions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The declarative trust gate: a script's {@code meta.permissions} become a
 * {@link PermissionView} only after the player trusts the exact script
 * content (keyed by SHA-256). Decisions persist through
 * {@link CoreOptions#getTrust()} (options.json); an untrusted script with
 * declared capabilities runs under a denying view until trusted, and a
 * script whose content changed re-enters the trust flow (its old hash no
 * longer matches).
 */
public final class TrustManager {

    /** Capability directory. New entries extend this set and the docs. */
    public static final Set<String> CAPABILITIES = Set.of("fs", "net", "command", "java");

    private final CoreOptions options;
    private final List<Consumer<TrustPrompt>> promptListeners = new CopyOnWriteArrayList<>();
    private final Map<String, List<String>> sessionGrants = new LinkedHashMap<>();

    /** One pending first-load trust question. */
    public record TrustPrompt(String scriptName, String contentHash, List<String> requested) {
    }

    /** Creates the manager over the persisted trust table. */
    public TrustManager(CoreOptions options) {
        this.options = options;
    }

    /** Registers a listener that surfaces trust prompts to the player. */
    public void onPrompt(Consumer<TrustPrompt> listener) {
        promptListeners.add(listener);
    }

    /** SHA-256 of the script source, hex-encoded (the trust table key). */
    public static String hashOf(String source) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Resolves the view for one script load: trusted content gets exactly its
     * declared capabilities; untrusted content with declared capabilities
     * fires a prompt and runs under a denying view until a decision lands;
     * no declaration means an all-allowing view for the base surface.
     */
    public PermissionView viewFor(String contentHash, List<String> declared) {
        List<String> effective = declared == null ? List.of() : declared;
        if (effective.isEmpty()) {
            return PermissionView.ALLOW_ALL;
        }
        if (isTrusted(contentHash) || sessionGrants.containsKey(contentHash)) {
            return PermissionView.of(new java.util.HashSet<>(effective));
        }
        var prompt = new TrustPrompt(null, contentHash, List.copyOf(effective));
        for (Consumer<TrustPrompt> listener : promptListeners) {
            listener.accept(prompt);
        }
        return PermissionView.DENY_ALL;
    }

    /** Returns whether this exact content hash has been trusted. */
    public boolean isTrusted(String contentHash) {
        return options.getTrust().containsKey(contentHash);
    }

    /** Persists a trust decision for one content hash. */
    public void trust(String contentHash) {
        Map<String, List<String>> trust = new LinkedHashMap<>(options.getTrust());
        trust.put(contentHash, List.of());
        options.setTrust(trust);
    }

    /** Grants capabilities for this session only (not persisted). */
    public void grantSession(String contentHash, List<String> capabilities) {
        sessionGrants.put(contentHash, new ArrayList<>(capabilities));
    }

    /** Revokes a persisted trust entry (revoke action or management UI). */
    public void revoke(String contentHash) {
        Map<String, List<String>> trust = new LinkedHashMap<>(options.getTrust());
        trust.remove(contentHash);
        options.setTrust(trust);
    }

    /** Trusted content hashes (diagnostics and the management panel). */
    public Set<String> trustedHashes() {
        return options.getTrust().keySet();
    }

    /** Fills the script name into a prompt before surfacing it. */
    public TrustPrompt named(TrustPrompt prompt, String scriptName) {
        return new TrustPrompt(scriptName, prompt.contentHash(), prompt.requested());
    }
}
