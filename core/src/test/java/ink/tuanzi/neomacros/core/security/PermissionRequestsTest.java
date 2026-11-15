package ink.tuanzi.neomacros.core.security;

import ink.tuanzi.neomacros.core.config.ConfigManager;
import ink.tuanzi.neomacros.core.config.CoreOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unified pending-decision table: first-load prompts and runtime
 * requests share one queue and one prompt slot, target resolution accepts
 * a request id or a script name, a grant is script-wide (siblings settle
 * with it), a deny remembers the refusal for the session, and an ignore
 * settles false and forgets.
 */
class PermissionRequestsTest {

    @TempDir
    Path dir;

    private record Rig(PermissionRequests requests, TrustManager trust) {
    }

    private Rig rig() {
        var options = new CoreOptions();
        var config = new ConfigManager(dir.resolve("options.json"), m -> { });
        config.register(options);
        config.load();
        var trust = new TrustManager(options);
        return new Rig(new PermissionRequests(trust), trust);
    }

    private static PermissionRequests.PermissionRequest request(String id, String script,
            String hash, String capability, List<String> declared) {
        return new PermissionRequests.PermissionRequest(id, script, hash, capability,
                declared, capability != null, "");
    }

    @Test
    void loadPromptsQueueBehindRuntimeRequestsAndSettleWithoutFutures() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);

        var future = rig.requests().submit(request("id-1", "alpha", "hash-a", "fs", List.of("fs")));
        rig.requests().submitLoad("beta", "hash-b", "beta description", List.of("net"));
        assertEquals(1, surfaced.size(), "one prompt shows at a time");
        assertEquals("id-1", surfaced.get(0).id());
        assertTrue(surfaced.get(0).awaited());

        assertEquals("alpha", rig.requests().respond("id-1", PermissionRequests.Decision.GRANT));
        assertTrue(future.join(), "the awaited request settles true");
        assertTrue(rig.trust().isTrusted("hash-a"), "an allow persists trust");

        assertEquals(2, surfaced.size(), "the load prompt follows the decision");
        assertEquals("beta", surfaced.get(1).scriptName());
        assertFalse(surfaced.get(1).awaited(), "a load prompt waits for nobody");
        assertEquals("beta description", surfaced.get(1).description(),
                "the manifest description rides on the request");

        assertEquals("beta", rig.requests().respond("beta", PermissionRequests.Decision.GRANT));
        assertTrue(rig.trust().isTrusted("hash-b"), "a prompt allow persists trust too");
        assertTrue(rig.requests().pending().isEmpty());
    }

    @Test
    void loadPromptDedupReplacesOnlyThePromptOfTheSameScript() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);

        rig.requests().submitLoad("alpha", "hash-a", "", List.of("fs", "net"));
        assertEquals(1, surfaced.size());
        var awaited = rig.requests().submit(
                request("id-2", "alpha", "hash-a", "fs", List.of("fs", "net")));

        // A repeated load of the same untrusted script replaces the pending
        // prompt; the awaited request stays exactly where it is.
        rig.requests().submitLoad("alpha", "hash-a", "", List.of("fs", "net"));
        assertEquals(2, surfaced.size(), "the replacement prompt surfaced");
        assertEquals(2, rig.requests().pending().size(), "one awaited entry + one prompt");
        assertEquals(1, rig.requests().pending().stream()
                        .filter(PermissionRequests.PermissionRequest::awaited).count(),
                "the awaited entry survived the dedup");

        assertEquals("alpha", rig.requests().respond("alpha", PermissionRequests.Decision.GRANT));
        assertTrue(awaited.join(), "an allow by name settles the queued sibling");
        assertTrue(rig.trust().isTrusted("hash-a"));
        assertTrue(rig.requests().pending().isEmpty(), "the prompt was swept too");
    }

    @Test
    void allowByIdGrantsScriptWideAndSettlesQueuedSiblings() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);

        var sibling = rig.requests().submit(
                request("id-1", "alpha", "hash-a", "fs", List.of("fs", "net")));
        var showing = rig.requests().submit(
                request("id-2", "alpha", "hash-a", "net", List.of("fs", "net")));
        rig.requests().submitLoad("beta", "hash-b", "", List.of("net"));
        assertEquals(1, surfaced.size());

        assertEquals("alpha", rig.requests().respond("id-2", PermissionRequests.Decision.GRANT));
        assertTrue(showing.join());
        assertTrue(sibling.join(), "the queued sibling settled by the same grant");
        assertTrue(rig.trust().isTrusted("hash-a"));
        assertEquals(1, rig.requests().pending().size(), "another script is untouched");
        assertEquals("beta", rig.requests().pending().get(0).scriptName());
    }

    @Test
    void denyByIdSettlesOnlyTheMatchedEntryAndRemembersTheRefusal() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);

        var denied = rig.requests().submit(
                request("id-1", "alpha", "hash-a", "fs", List.of("fs")));
        var sibling = rig.requests().submit(
                request("id-2", "alpha", "hash-a", "net", List.of("fs", "net")));

        assertEquals("alpha", rig.requests().respond("id-1", PermissionRequests.Decision.DENY));
        assertFalse(denied.join(), "a deny resolves false");
        assertFalse(sibling.isDone(), "a deny by id leaves the sibling pending");
        assertFalse(rig.trust().isTrusted("hash-a"), "a deny grants nothing");
        assertTrue(rig.trust().isSessionDenied("hash-a"), "the refusal is remembered");
        assertEquals(1, rig.requests().pending().size());
    }

    @Test
    void denyStopsFurtherPromptsForTheSession() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);

        var denied = rig.requests().submit(
                request("id-1", "alpha", "hash-a", "fs", List.of("fs")));
        assertEquals("alpha", rig.requests().respond("id-1", PermissionRequests.Decision.DENY));
        assertFalse(denied.join());
        assertTrue(rig.trust().isSessionDenied("hash-a"));

        // Runtime requests resolve false immediately; load prompts submit
        // nothing — the refusal holds until the game restarts.
        var again = rig.requests().submit(
                request("id-2", "alpha", "hash-a", "fs", List.of("fs")));
        assertFalse(again.join(), "a session-denied script resolves false without a prompt");
        rig.requests().submitLoad("alpha", "hash-a", "", List.of("fs"));
        assertEquals(1, surfaced.size(), "no further prompts surfaced");
        assertTrue(rig.requests().pending().isEmpty());
    }

    @Test
    void ignoreSettlesFalseAndRemembersNothing() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);

        var future = rig.requests().submit(
                request("id-1", "alpha", "hash-a", "fs", List.of("fs")));
        rig.requests().submitLoad("alpha", "hash-a", "", List.of("fs"));

        assertEquals("alpha", rig.requests().respond("alpha", PermissionRequests.Decision.IGNORE));
        assertFalse(future.join(), "an ignore resolves false");
        assertFalse(rig.trust().isTrusted("hash-a"), "an ignore grants nothing");
        assertFalse(rig.trust().isSessionDenied("hash-a"), "an ignore remembers nothing");
        assertTrue(rig.requests().pending().isEmpty());

        // The next ask comes back: nothing was held against the script.
        rig.requests().submitLoad("alpha", "hash-a", "", List.of("fs"));
        assertEquals(2, surfaced.size(), "the prompt returns after an ignore");
    }

    @Test
    void respondByNameSettlesEveryEntryOfTheScript() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);

        var future = rig.requests().submit(
                request("id-1", "alpha", "hash-a", "fs", List.of("fs")));
        rig.requests().submitLoad("alpha", "hash-a", "", List.of("fs"));
        rig.requests().submitLoad("beta", "hash-b", "", List.of("net"));

        assertEquals("alpha", rig.requests().respond("alpha", PermissionRequests.Decision.DENY));
        assertFalse(future.join(), "a deny by name settles the awaited request");
        assertFalse(rig.trust().isTrusted("hash-a"));
        assertEquals(1, rig.requests().pending().size(), "another script is untouched");

        assertNull(rig.requests().respond("gamma", PermissionRequests.Decision.GRANT),
                "an unknown target resolves nothing");
        assertNull(rig.requests().respond("id-1", PermissionRequests.Decision.GRANT),
                "a settled id resolves nothing");

        assertEquals("beta", rig.requests().respond("beta", PermissionRequests.Decision.GRANT));
        assertTrue(rig.trust().isTrusted("hash-b"));
        assertTrue(rig.requests().pending().isEmpty());
    }

    @Test
    void cancelForSettlesEveryEntryOfTheUnloadedScript() {
        var rig = rig();
        List<PermissionRequests.PermissionRequest> surfaced = new ArrayList<>();
        rig.requests().onRequest(surfaced::add);

        var future = rig.requests().submit(
                request("id-1", "alpha", "hash-a", "fs", List.of("fs")));
        rig.requests().submitLoad("alpha", "hash-a", "", List.of("fs"));
        rig.requests().submitLoad("beta", "hash-b", "", List.of("net"));

        rig.requests().cancelFor("alpha");
        assertFalse(future.join(), "unload settles the script's future as denied");
        assertFalse(rig.trust().isTrusted("hash-a"));
        assertEquals(1, rig.requests().pending().size(), "another script is untouched");
        assertNull(rig.requests().respond("id-1", PermissionRequests.Decision.GRANT),
                "cancelled ids are gone");
    }
}
