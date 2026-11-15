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
 * {@link CoreOptions#getTrust()} (config.json); an untrusted script with
 * declared capabilities runs under a denying view until trusted, and a
 * script whose content changed re-enters the trust flow (its old hash no
 * longer matches).
 */
public final class TrustManager {

    /** Capability directory. New entries extend this set and the docs. */
    public static final Set<String> CAPABILITIES =
            Set.of("fs", "net", "command", "java", "packet");

    private final CoreOptions options;
    private final List<Consumer<TrustPrompt>> promptListeners = new CopyOnWriteArrayList<>();
    private final Map<String, List<String>> sessionGrants = new LinkedHashMap<>();
    /** Live views of currently loaded declared scripts, keyed by content hash. */
    private final Map<String, PermissionView.GrantableView> liveViews =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Content hashes the player denied this session (no more prompts until restart). */
    private final Set<String> sessionDenials =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

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
     * fires a prompt and runs under a denying (but grantable) view until a
     * decision lands — unless the player denied this hash this session, in
     * which case it runs denied silently; no declaration means an
     * all-allowing view for the base surface. Every declared view is a live
     * {@link PermissionView.GrantableView} registered per hash, so a runtime
     * grant flips the running script's members without a reload. The script
     * name rides on the prompt so the prompt UI can label it. Two scripts
     * sharing one content hash share the live view; the last load wins the
     * registration.
     */
    public PermissionView viewFor(String scriptName, String contentHash, List<String> declared) {
        List<String> effective = declared == null ? List.of() : declared;
        if (effective.isEmpty()) {
            return PermissionView.ALLOW_ALL;
        }
        boolean granted = isTrusted(contentHash) || sessionGrants.containsKey(contentHash);
        var view = new PermissionView.GrantableView(
                granted ? new java.util.HashSet<>(effective) : java.util.Set.of());
        liveViews.put(contentHash, view);
        if (!granted && !sessionDenials.contains(contentHash)) {
            var prompt = new TrustPrompt(scriptName, contentHash, List.copyOf(effective));
            for (Consumer<TrustPrompt> listener : promptListeners) {
                listener.accept(prompt);
            }
        }
        return view;
    }

    /** Hash-only variant for callers without a script name. */
    public PermissionView viewFor(String contentHash, List<String> declared) {
        return viewFor(null, contentHash, declared);
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

    /**
     * Persists trust for one content hash and flips its live view, so the
     * running script's gated members allow the capabilities immediately
     * (no reload). The granted set is the script's declared capability set.
     */
    public void trustLive(String contentHash, Set<String> capabilities) {
        trust(contentHash);
        PermissionView.GrantableView live = liveViews.get(contentHash);
        if (live != null) {
            live.grant(capabilities);
        }
    }

    /** Grants capabilities for this session only (not persisted). */
    public void grantSession(String contentHash, List<String> capabilities) {
        sessionGrants.put(contentHash, new ArrayList<>(capabilities));
    }

    /**
     * Denies one content hash for this session: no prompts fire for it and
     * runtime requests resolve false without surfacing, until the game
     * restarts. Nothing is persisted (the player is asked again next
     * session).
     */
    public void denySession(String contentHash) {
        sessionDenials.add(contentHash);
    }

    /** Returns whether the player denied this content hash this session. */
    public boolean isSessionDenied(String contentHash) {
        return sessionDenials.contains(contentHash);
    }

    /** Revokes a persisted trust entry (revoke action or management UI). */
    public void revoke(String contentHash) {
        Map<String, List<String>> trust = new LinkedHashMap<>(options.getTrust());
        trust.remove(contentHash);
        options.setTrust(trust);
    }

    /**
     * Revokes every grant for one content hash: the persisted entry, any
     * session grant, any session denial, and the running script's live view
     * flips back to denying its declared set (gated calls throw again
     * immediately, no reload). The player is asked again on the next load or
     * runtime request.
     */
    public void revokeAll(String contentHash) {
        revoke(contentHash);
        sessionGrants.remove(contentHash);
        sessionDenials.remove(contentHash);
        PermissionView.GrantableView live = liveViews.get(contentHash);
        if (live != null) {
            live.grant(java.util.Set.of());
        }
    }

    /** Trusted content hashes (diagnostics and the management panel). */
    public Set<String> trustedHashes() {
        return options.getTrust().keySet();
    }
}
