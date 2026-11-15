package ink.tuanzi.neomacros.core.module;

import ink.tuanzi.neomacros.core.api.PermissionApi;
import ink.tuanzi.neomacros.core.api.bindgen.LoadTracker;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionViewResolver;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionError;
import ink.tuanzi.neomacros.core.module.manifest.MacroConfig;
import ink.tuanzi.neomacros.core.module.manifest.MacroManifest;
import ink.tuanzi.neomacros.core.module.manifest.ScriptCategory;
import ink.tuanzi.neomacros.core.config.ConfigManager;
import ink.tuanzi.neomacros.core.config.CoreOptions;
import ink.tuanzi.neomacros.core.security.PermissionRequests;
import ink.tuanzi.neomacros.core.security.TrustManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code neo.permission} face: checkPermission reads the live view (a
 * list requires every capability), requestPermission rejects undeclared or
 * unknown capabilities, resolves immediately when already granted, and
 * otherwise surfaces one request whose decision grants through the trust
 * gate (persist + immediate flip) or settles false.
 */
class PermissionApiTest {

    @TempDir
    Path dir;

    private record Rig(PermissionApi api, PermissionRequests requests,
                       TrustManager trust, CoreOptions options) {
    }

    /** Builds the facade over a real trust gate; call {@code owner(...)} per scenario. */
    private Rig rig() {
        var options = new CoreOptions();
        var config = new ConfigManager(dir.resolve("options.json"), m -> { });
        config.register(options);
        config.load();
        var trust = new TrustManager(options);
        var requests = new PermissionRequests(trust);
        return new Rig(new PermissionApi(requests), requests, trust, options);
    }

    /** A loaded-module stand-in: manifest declarations, live view, content hash. */
    private LoadedModule owner(List<String> declared, PermissionView view, String hash) {
        MacroManifest manifest = new MacroManifest("perm-script", "1", "a", "", "", "", "", "",
                declared, MacroConfig.EMPTY);
        ScriptEntry entry = new ScriptEntry(ScriptCategory.SERVICES, Path.of("folder"),
                manifest, Path.of("folder", "index.js"));
        LoadedModule module = new LoadedModule(entry, entry.entryFile(), null);
        module.permissions(new PermissionViewResolver.ResolvedPermissions(view, hash));
        return module;
    }

    @Test
    void checkPermissionReadsTheLiveViewAndTheDirectory() {
        var rig = rig();
        LoadedModule granted = owner(List.of("fs", "net"),
                PermissionView.of(Set.of("fs", "net")), "hash-a");
        LoadedModule denied = owner(List.of("fs"),
                new PermissionView.GrantableView(Set.of()), "hash-b");

        LoadTracker.begin(granted);
        try {
            assertTrue(rig.api().checkPermission("fs"));
            assertTrue(rig.api().checkPermission(List.of("fs", "net")),
                    "a list requires every capability");
            assertFalse(rig.api().checkPermission("command"), "undeclared stays denied");
            assertFalse(rig.api().checkPermission(List.of("fs", "command")),
                    "one denied name sinks the whole list");
            assertFalse(rig.api().checkPermission("bogus"), "unknown names are not capabilities");
            assertFalse(rig.api().checkPermission(List.of("fs", "bogus")));
        } finally {
            LoadTracker.end();
        }

        LoadTracker.begin(denied);
        try {
            assertFalse(rig.api().checkPermission("fs"), "the ungranted view denies");
        } finally {
            LoadTracker.end();
        }

        // Outside a script context (the DevTools console) the gate runs on
        // an all-allowing view, so the face reports that.
        assertTrue(rig.api().checkPermission("fs"));
        assertFalse(rig.api().checkPermission("bogus"));
    }

    @Test
    void checkNormalizesNamesAndRejectsEmptyInput() {
        var rig = rig();
        LoadedModule granted = owner(List.of("fs"),
                PermissionView.of(Set.of("fs")), "hash-a");
        LoadTracker.begin(granted);
        try {
            assertTrue(rig.api().checkPermission(" FS "), "names normalize (trim + case)");
            assertTrue(rig.api().checkPermission(List.of("FS")), "list entries normalize too");
            var empty = assertThrows(IllegalArgumentException.class,
                    () -> rig.api().checkPermission(List.of()));
            assertTrue(empty.getMessage().contains("empty"));
            assertThrows(IllegalArgumentException.class, () -> rig.api().checkPermission("  "));
        } finally {
            LoadTracker.end();
        }
    }

    @Test
    void requestPermissionRejectsUndeclaredAndUnknown() {
        var rig = rig();
        LoadedModule owner = owner(List.of("fs"),
                new PermissionView.GrantableView(Set.of()), "hash-x");
        LoadTracker.begin(owner);
        try {
            // The face raises synchronously; the engine's async wrapper turns
            // that into the script-visible promise rejection.
            var undeclared = assertThrows(PermissionError.class,
                    () -> rig.api().requestPermission("net"));
            assertTrue(undeclared.getMessage()
                            .contains("is not declared in this script's meta.permissions"),
                    () -> "contract text: " + undeclared.getMessage());

            var inList = assertThrows(PermissionError.class,
                    () -> rig.api().requestPermission(List.of("fs", "net")));
            assertTrue(inList.getMessage().contains("'net'"),
                    () -> "the offending name is named: " + inList.getMessage());

            var unknown = assertThrows(PermissionError.class,
                    () -> rig.api().requestPermission("Bogus"));
            assertTrue(unknown.getMessage().contains("unknown permission"),
                    () -> "unknown name: " + unknown.getMessage());
        } finally {
            LoadTracker.end();
        }
    }

    @Test
    void requestPermissionResolvesImmediatelyWhenAllowed() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);
        LoadedModule owner = owner(List.of("fs", "net"),
                PermissionView.of(Set.of("fs", "net")), "hash-ok");
        LoadTracker.begin(owner);
        try {
            assertTrue(rig.api().requestPermission("fs").join(),
                    "the granted capability resolves true without a prompt");
            assertTrue(rig.api().requestPermission(List.of("fs", "net")).join(),
                    "an all-granted list resolves true without a prompt");
        } finally {
            LoadTracker.end();
        }
        assertTrue(surfaced.isEmpty(), "no UI request for already-granted capabilities");
    }

    @Test
    void requestPermissionAllowPersistsTrustAndFlipsTheView() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);
        // The production wiring registers the live view through viewFor at
        // load time; mirror it here so the grant reaches the running view.
        var view = rig.trust().viewFor("hash-grant", List.of("fs", "net"));
        LoadedModule owner = owner(List.of("fs", "net"), view, "hash-grant");
        LoadTracker.begin(owner);
        var future = rig.api().requestPermission(List.of("fs", "net"));
        LoadTracker.end();

        assertEquals(1, surfaced.size(), "the ungranted request surfaces once");
        var request = surfaced.get(0);
        assertEquals("perm-script", request.scriptName());
        assertEquals(List.of("fs", "net"), request.capabilities(),
                "the requested list rides on the request");
        assertEquals(List.of("fs", "net"), request.declared());
        assertFalse(future.isDone(), "the future waits for the player");

        assertEquals("perm-script", rig.requests().respond(request.id(),
                PermissionRequests.Decision.GRANT));
        assertTrue(future.join(), "an allow resolves true");
        assertTrue(view.allows("fs"), "the running view flipped immediately");
        assertTrue(view.allows("net"), "the declared set rides with the trust decision");
        assertTrue(rig.trust().isTrusted("hash-grant"), "the grant persists");
        assertTrue(rig.options().getTrust().containsKey("hash-grant"));
    }

    @Test
    void requestPermissionDenySettlesFalseWithoutGrant() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);
        var view = rig.trust().viewFor("hash-deny", List.of("fs"));
        LoadedModule owner = owner(List.of("fs"), view, "hash-deny");
        LoadTracker.begin(owner);
        var future = rig.api().requestPermission("fs");
        LoadTracker.end();

        String id = surfaced.get(0).id();
        assertEquals("perm-script", rig.requests().respond(id, PermissionRequests.Decision.DENY));
        assertFalse(future.join(), "a deny resolves false");
        assertFalse(view.allows("fs"), "a deny grants nothing");
        assertFalse(rig.trust().isTrusted("hash-deny"), "a deny persists nothing");
        assertNull(rig.requests().respond(id, PermissionRequests.Decision.DENY),
                "the id is consumed");
    }

    @Test
    void unloadCancelsPendingRequests() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);
        LoadedModule owner = owner(List.of("fs"),
                new PermissionView.GrantableView(Set.of()), "hash-cancel");
        LoadTracker.begin(owner);
        var future = rig.api().requestPermission("fs");
        LoadTracker.end();

        rig.requests().cancelFor("perm-script");
        assertFalse(future.join(), "unload settles the script's future as denied");
        assertTrue(rig.requests().pending().isEmpty(), "the queue is clear");
        assertNull(rig.requests().respond(surfaced.get(0).id(),
                PermissionRequests.Decision.GRANT), "a cancelled id is gone");
    }

    @Test
    void queuedSiblingSettlesWithTheScriptWideGrant() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);
        // The production wiring registers the live view through viewFor at
        // load time; mirror it here so the grant reaches the running view.
        var view = rig.trust().viewFor("hash-queue", List.of("fs", "net"));
        LoadedModule owner = owner(List.of("fs", "net"), view, "hash-queue");
        LoadTracker.begin(owner);
        var first = rig.api().requestPermission("fs");
        var second = rig.api().requestPermission("net");
        LoadTracker.end();

        assertEquals(1, surfaced.size(), "only one prompt shows at a time");
        assertEquals("fs", surfaced.get(0).capabilities().get(0));
        assertFalse(second.isDone(), "the queued request waits");

        // An allow grants script-wide: the queued sibling settles with the
        // same decision instead of surfacing its own prompt.
        assertEquals("perm-script", rig.requests().respond(surfaced.get(0).id(),
                PermissionRequests.Decision.GRANT));
        assertTrue(first.join());
        assertTrue(second.join(), "the queued sibling settled by the same grant");
        assertEquals(1, surfaced.size(), "no second prompt was needed");
        assertTrue(view.allows("net"), "the declared set rode with the grant");
    }

    @Test
    void requestPermissionResolvesFalseWithoutPromptAfterSessionDenial() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);
        rig.trust().denySession("hash-x");
        LoadedModule owner = owner(List.of("fs"),
                new PermissionView.GrantableView(Set.of()), "hash-x");
        LoadTracker.begin(owner);
        try {
            assertFalse(rig.api().requestPermission("fs").join(),
                    "a session-denied script resolves false immediately");
        } finally {
            LoadTracker.end();
        }
        assertTrue(surfaced.isEmpty(), "no prompt surfaced for a session-denied script");
    }
}
