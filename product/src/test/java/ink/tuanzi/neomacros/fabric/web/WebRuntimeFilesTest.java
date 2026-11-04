package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.config.ConfigManager;
import ink.tuanzi.neomacros.core.config.CoreOptions;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.module.manifest.MacroManifest;
import ink.tuanzi.neomacros.core.module.manifest.ManifestParser;
import ink.tuanzi.neomacros.fabric.web.auth.Nm1Verifier;
import ink.tuanzi.neomacros.fabric.web.http.ApiError;
import ink.tuanzi.neomacros.fabric.web.sse.PanelEvents;
import ink.tuanzi.neomacros.fabric.web.sse.SseHub;
import ink.tuanzi.neomacros.fabric.web.v1.FilesResponse;
import ink.tuanzi.neomacros.fabric.web.v1.MacroView;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The file editor API over a real macros root: the sandbox gates (the
 * traversal, drive, UNC, ADS, and symlink matrix), ETag concurrency, the
 * write whitelist, scaffold creation, deletion cleanup, and the SSE
 * announcements. The module manager runs with a disabled autoload filter
 * over a null runtime: discovery and watch scans never reach the engine,
 * so no game context is needed.
 */
class WebRuntimeFilesTest {

    @TempDir
    Path temp;

    private Path root;
    private ModuleManager modules;
    private CoreOptions options;
    private ConfigManager config;
    private PanelEvents events;
    private ByteArrayOutputStream frames;

    @BeforeEach
    void setUp() throws IOException {
        root = temp.resolve("macros");
        Files.createDirectories(root.resolve("event"));
        Files.createDirectories(root.resolve("keypress"));
        Files.createDirectories(root.resolve("services"));
        modules = new ModuleManager(null, new BindingRegistry("26.3"),
                PermissionView.ALLOW_ALL, root, message -> { });
        modules.withAutoloadFilter(name -> false);
        events = new PanelEvents();
        SseHub hub = new SseHub(System::currentTimeMillis);
        frames = new ByteArrayOutputStream();
        hub.open(frames, "test");
        events.attach(hub);
        options = new CoreOptions();
        config = new ConfigManager(temp.resolve("config.json"), message -> { });
        config.register(options);
        config.load();
    }

    private WebRuntime backend() {
        WebRuntime runtime = new WebRuntime(
                modules, options, config, root, null, events, "0.0.0-test", "26.3",
                new ink.tuanzi.neomacros.core.task.TaskRegistry());
        modules.onFolderChange(runtime::folderChanged);
        return runtime;
    }

    /** Writes one discovered macro folder with a minimal manifest. */
    private static void writeMacro(Path root, String category, String name)
            throws IOException {
        Path folder = root.resolve(category).resolve(name);
        Files.createDirectories(folder);
        String config = switch (category) {
            case "keypress" -> "config:\n  keypress: {}\n";
            case "event" -> "config:\n  event:\n    on: [game:start]\n";
            default -> "";
        };
        Files.writeString(folder.resolve("neomacro.yml"),
                "name: \"" + name + "\"\nversion: \"1.0.0\"\nauthor: \"tester\"\n" + config);
        Files.writeString(folder.resolve("index.js"), "// initial\n");
    }

    /** Asserts the call fails with the given ApiError status. */
    private static ApiError expect(int status, Runnable call) {
        try {
            call.run();
        } catch (ApiError error) {
            assertEquals(status, error.status());
            return error;
        }
        throw new AssertionError("expected ApiError " + status);
    }

    // ---- sandbox ------------------------------------------------------------

    @Test
    void theTraversalMatrixIsRejectedBeforeTouchingDisk() throws IOException {
        Files.writeString(temp.resolve("web-devices.json"), "device credentials");
        WebRuntime backend = backend();
        String[] matrix = {
                "../escape.js",                      // parent escape
                "a/../../escape.js",                 // chained parent escape
                "/etc/passwd",                       // absolute (empty first segment)
                "C:/Windows/win.ini",                // drive letter
                "\\\\server\\share\\secret.js",      // UNC
                "..\\..\\escape.js",                 // backslash traversal
                "index.js:$DATA",                    // alternate data stream
                "index.js.",                         // trailing dot (Windows trimming)
                "index.js ",                         // trailing space
                "a\u0000.js",                        // NUL byte
                "../web-devices.json"                // the sibling credential file
        };
        for (String path : matrix) {
            expect(403, () -> backend.readFile(path));
            expect(403, () -> backend.writeFile(path, "x".getBytes(StandardCharsets.UTF_8), null));
            expect(403, () -> backend.deleteFile(path));
            expect(403, () -> backend.makeDirectory(path));
            expect(403, () -> backend.moveFile(path, "event/auto-welcome/x.js"));
            expect(403, () -> backend.moveFile("event/auto-welcome/index.js", path));
        }
        assertFalse(Files.exists(temp.resolve("escape.js")));
        assertFalse(Files.exists(root.resolve("escape.js")));
    }

    @Test
    void aSymlinkInsideTheMacrosRootCannotReadOutward() throws IOException {
        Path outside = temp.resolve("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("secret.txt"), "secret");
        writeMacro(root, "event", "auto-welcome");
        Path link = root.resolve("event").resolve("auto-welcome").resolve("link.js");
        boolean created = false;
        try {
            Files.createSymbolicLink(link, outside.resolve("secret.txt"));
            Files.createSymbolicLink(
                    root.resolve("event").resolve("auto-welcome").resolve("linkdir"),
                    outside);
            created = true;
        } catch (IOException | UnsupportedOperationException e) {
            // no symlink privilege on this host; the gate is untestable here
        }
        Assumptions.assumeTrue(created, "symbolic links are unavailable");
        WebRuntime backend = backend();
        expect(403, () -> backend.readFile("event/auto-welcome/link.js"));
        expect(403, () -> backend.writeFile("event/auto-welcome/link.js",
                "x".getBytes(StandardCharsets.UTF_8), null));
        expect(403, () -> backend.deleteFile("event/auto-welcome/link.js"));
        FilesResponse listed = backend.listFiles("event/auto-welcome");
        assertTrue(listed.files().stream().noneMatch(file -> file.contains("link")),
                "an outward symlink must not be listed");
        assertTrue(listed.dirs().stream().noneMatch(dir -> dir.contains("link")),
                "an outward symlink directory must not be listed");
        assertEquals("secret", Files.readString(outside.resolve("secret.txt")));
    }

    // ---- read ----------------------------------------------------------------

    @Test
    void readsServeContentEtagAnd404() throws IOException {
        writeMacro(root, "event", "auto-welcome");
        byte[] content = "chat.log('hello');".getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve("event/auto-welcome/index.js"), content);
        WebRuntime backend = backend();
        FileContent file = backend.readFile("event/auto-welcome/index.js");
        assertEquals("chat.log('hello');",
                new String(file.bytes(), StandardCharsets.UTF_8));
        assertEquals(Nm1Verifier.bodyHash(content), file.etag());
        expect(404, () -> backend.readFile("event/auto-welcome/missing.js"));
        expect(404, () -> backend.readFile("event/auto-welcome"));
    }

    @Test
    void oversizedReadsAreRefused() throws IOException {
        WebRuntime backend = backend();
        Path big = root.resolve("event").resolve("huge.txt");
        Files.write(big, new byte[4 * 1024 * 1024 + 1]);
        expect(413, () -> backend.readFile("event/huge.txt"));
    }

    // ---- listing --------------------------------------------------------------

    @Test
    void listingServesFolderRelativePosixPathsManifestFirst() throws IOException {
        writeMacro(root, "event", "auto-welcome");
        Path folder = root.resolve("event/auto-welcome");
        Files.createDirectories(folder.resolve("lib/nested"));
        Files.createDirectories(folder.resolve("zz-empty"));
        Files.writeString(folder.resolve("lib/util.mjs"), "export {};\n");
        Files.writeString(folder.resolve("lib/nested/deep.js"), "// deep\n");
        Files.writeString(folder.resolve("zebra.txt"), "z\n");
        Files.writeString(folder.resolve("alpha.md"), "# a\n");
        WebRuntime backend = backend();
        FilesResponse files = backend.listFiles("event/auto-welcome");
        assertEquals(List.of("neomacro.yml", "alpha.md", "index.js",
                "lib/nested/deep.js", "lib/util.mjs", "zebra.txt"), files.files());
        // Empty directories are listed too (the panel's mkdir needs them).
        assertEquals(List.of("lib", "lib/nested", "zz-empty"), files.dirs());

        // Bare names resolve like every other macro endpoint.
        assertEquals(files.files(), backend.listFiles("auto-welcome").files());
        expect(404, () -> backend.listFiles("event/unknown"));
    }

    @Test
    void listingAScaffoldServesManifestAndEntryOnly() throws IOException {
        writeMacro(root, "event", "bare");
        WebRuntime backend = backend();
        // Directory entries never appear; just the two scaffold files.
        assertEquals(List.of("neomacro.yml", "index.js"),
                backend.listFiles("event/bare").files());
    }

    // ---- write ---------------------------------------------------------------

    @Test
    void writesRoundTripThroughIfMatch() throws IOException {
        writeMacro(root, "event", "auto-welcome");
        WebRuntime backend = backend();
        byte[] first = "v1".getBytes(StandardCharsets.UTF_8);
        assertNotNull(backend.writeFile("event/auto-welcome/index.js", first, null));
        String etag = backend.readFile("event/auto-welcome/index.js").etag();

        // A matching If-Match (even quoted) overwrites.
        assertNullError(backend.writeFile("event/auto-welcome/index.js",
                "v2".getBytes(StandardCharsets.UTF_8), "\"" + etag + "\""));
        assertEquals("v2", Files.readString(root.resolve("event/auto-welcome/index.js")));

        // The stale etag conflicts and carries the current text.
        FileWriteResult conflict = backend.writeFile("event/auto-welcome/index.js",
                "v3".getBytes(StandardCharsets.UTF_8), etag);
        assertTrue(conflict.conflict());
        assertEquals("v2", conflict.conflictContent());
        assertEquals(Nm1Verifier.bodyHash("v2".getBytes(StandardCharsets.UTF_8)),
                conflict.currentEtag());
        assertEquals("v2", Files.readString(root.resolve("event/auto-welcome/index.js")));

        // An If-Match against a missing file conflicts with empty content.
        FileWriteResult missing = backend.writeFile("event/auto-welcome/new.js",
                "x".getBytes(StandardCharsets.UTF_8), "stale");
        assertTrue(missing.conflict());
        assertEquals("", missing.conflictContent());

        // No If-Match creates the file (with parent directories).
        assertNullError(backend.writeFile("event/auto-welcome/deep/nested/notes.md",
                "notes".getBytes(StandardCharsets.UTF_8), null));
        assertEquals("notes",
                Files.readString(root.resolve("event/auto-welcome/deep/nested/notes.md")));
    }

    @Test
    void writesOutsideTheTextWhitelistAreRefused() throws IOException {
        WebRuntime backend = backend();
        expect(422, () -> backend.writeFile("event/evil.exe",
                "x".getBytes(StandardCharsets.UTF_8), null));
        expect(422, () -> backend.writeFile("event/Makefile",
                "x".getBytes(StandardCharsets.UTF_8), null));
        // Upper-case extensions pass the (case-insensitive) whitelist.
        assertNullError(backend.writeFile("event/UPPER.TXT",
                "x".getBytes(StandardCharsets.UTF_8), null));
        assertFalse(Files.exists(root.resolve("event/evil.exe")));
    }

    @Test
    void webWritesAnnounceThemselvesAsWebNotDisk() throws IOException {
        writeMacro(root, "event", "auto-welcome");
        WebRuntime backend = backend();
        assertNullError(backend.writeFile("event/auto-welcome/index.js",
                "v2".getBytes(StandardCharsets.UTF_8), null));
        String announced = frames.toString(StandardCharsets.UTF_8);
        assertTrue(announced.contains("event: file:changed"), announced);
        assertTrue(announced.contains("\"path\":\"event/auto-welcome/index.js\",\"by\":\"web\""),
                announced);
        assertTrue(announced.contains("event: macro:state"), announced);
        assertFalse(announced.contains("\"by\":\"disk\""), announced);
    }

    @Test
    void diskChangesAreAnnouncedAsDisk() throws IOException {
        writeMacro(root, "event", "auto-welcome");
        backend();
        modules.checkForChanges();
        frames.reset();
        Path file = root.resolve("event/auto-welcome/index.js");
        Files.writeString(file, "// changed outside");
        // Same-millisecond writes would compare equal; move the stamp.
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(60)));
        modules.checkForChanges();
        String announced = frames.toString(StandardCharsets.UTF_8);
        assertTrue(announced.contains("\"by\":\"disk\""), announced);
        assertTrue(announced.contains("\"path\":\"event/auto-welcome/index.js\""), announced);
        // The structural signal rides along so the panel re-syncs its trees.
        assertTrue(announced.contains("event: macros:changed"), announced);
    }

    @Test
    void anEmptyDirectoryShowsUpInTheWatchAndAnnouncesStructure() throws IOException {
        writeMacro(root, "event", "auto-welcome");
        WebRuntime backend = backend();
        modules.checkForChanges();
        frames.reset();
        // A freshly created empty folder is fingerprinted too, so its
        // creation (and later deletion) announces the structural change.
        Files.createDirectories(root.resolve("event/auto-welcome/lib"));
        Files.setLastModifiedTime(root.resolve("event/auto-welcome/lib"),
                FileTime.from(Instant.now().plusSeconds(60)));
        modules.checkForChanges();
        String announced = frames.toString(StandardCharsets.UTF_8);
        assertTrue(announced.contains("event: macros:changed"), announced);
    }

    @Test
    void aVanishedMacroFolderAnnouncesTheStructuralChange() throws IOException {
        writeMacro(root, "event", "doomed");
        backend();
        modules.checkForChanges();
        frames.reset();
        // Deleted outside the panel (even while not loaded): the removal
        // listener announces so the panel drops the ghost entry.
        deleteRecursively(root.resolve("event/doomed"));
        modules.checkForChanges();
        String announced = frames.toString(StandardCharsets.UTF_8);
        assertTrue(announced.contains("event: macros:changed"), announced);
    }

    @Test
    void panelCausedScansDoNotEchoDiskEvents() throws IOException {
        writeMacro(root, "event", "auto-welcome");
        WebRuntime backend = backend();
        modules.checkForChanges();
        frames.reset();
        // A panel write announces by=web and suppresses the disk echo
        // (including the structural signal) of the follow-up scan.
        backend.writeFile("event/auto-welcome/index.js",
                "// from panel".getBytes(StandardCharsets.UTF_8), null);
        String announced = frames.toString(StandardCharsets.UTF_8);
        assertTrue(announced.contains("\"by\":\"web\""), announced);
        assertFalse(announced.contains("\"by\":\"disk\""), announced);
        assertFalse(announced.contains("event: macros:changed"), announced);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var files = Files.walk(path)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    // ---- tree mutations -------------------------------------------------------

    @Test
    void deleteRemovesSubtreesButProtectsManifestsAndMacroFolders() throws IOException {
        writeMacro(root, "event", "doomed");
        Path folder = root.resolve("event/doomed");
        Files.createDirectories(folder.resolve("lib/nested"));
        Files.writeString(folder.resolve("lib/old.js"), "// old\n");
        Files.writeString(folder.resolve("lib/nested/deeper.js"), "// deeper\n");
        WebRuntime backend = backend();
        frames.reset();

        backend.deleteFile("event/doomed/lib");
        assertFalse(Files.exists(folder.resolve("lib")));

        // The manifest, the macro folder itself, category folders, and any
        // stray outside macro folders are all protected.
        expect(403, () -> backend.deleteFile("event/doomed/neomacro.yml"));
        expect(403, () -> backend.deleteFile("event/doomed"));
        expect(403, () -> backend.deleteFile("event"));
        Files.writeString(root.resolve("event/stray.txt"), "stray");
        expect(403, () -> backend.deleteFile("event/stray.txt"));
        expect(404, () -> backend.deleteFile("event/doomed/missing.js"));

        // The deletion announces itself as a web write, never a disk echo.
        String announced = frames.toString(StandardCharsets.UTF_8);
        assertTrue(announced.contains("\"path\":\"event/doomed/lib\",\"by\":\"web\""),
                announced);
        assertFalse(announced.contains("\"by\":\"disk\""), announced);
    }

    @Test
    void moveRenamesFilesAndDirsInsideOneMacroOnly() throws IOException {
        writeMacro(root, "event", "mover");
        writeMacro(root, "event", "other");
        Path folder = root.resolve("event/mover");
        Files.createDirectories(folder.resolve("lib"));
        Files.writeString(folder.resolve("lib/util.mjs"), "export {};\n");
        WebRuntime backend = backend();
        frames.reset();

        // A file rename passes the whitelist and auto-creates parents.
        backend.moveFile("event/mover/lib/util.mjs", "event/mover/deep/renamed.mjs");
        assertTrue(Files.exists(folder.resolve("deep/renamed.mjs")));

        // A directory rename skips the whitelist entirely.
        backend.moveFile("event/mover/deep", "event/mover/deep.exe");
        assertTrue(Files.isDirectory(folder.resolve("deep.exe")));

        String announced = frames.toString(StandardCharsets.UTF_8);
        assertTrue(announced.contains(
                "\"path\":\"event/mover/deep/renamed.mjs\",\"by\":\"web\""), announced);
        assertFalse(announced.contains("\"by\":\"disk\""), announced);

        // Protections: manifests, macro folders, cross-macro, stray targets.
        expect(403, () -> backend.moveFile("event/mover/neomacro.yml", "event/mover/x.yml"));
        expect(403, () -> backend.moveFile("event/mover/neomacro.yml", "event/other/x.yml"));
        expect(403, () -> backend.moveFile("event/mover/index.js", "event/other/index.js"));
        expect(403, () -> backend.moveFile("event/mover/index.js", "event/stray.js"));
        expect(403, () -> backend.moveFile("event/mover", "event/mover2"));

        // Missing source, existing target, self-nesting, whitelist, whitespace.
        expect(404, () -> backend.moveFile("event/mover/missing.js", "event/mover/x.js"));
        expect(409, () -> backend.moveFile("event/mover/index.js", "event/mover/neomacro.yml"));
        expect(409, () -> backend.moveFile("event/mover/index.js", "event/mover/index.js"));
        expect(422, () -> backend.moveFile("event/mover/index.js", "event/mover/evil.exe"));
        expect(422, () -> backend.moveFile("event/mover/index.js", "event/mover/bad name.js"));
        expect(422, () -> backend.moveFile("event/mover/deep.exe", "event/mover/deep.exe/inner"));
    }

    @Test
    void mkdirCreatesRecursivelyInsideMacrosOnly() throws IOException {
        writeMacro(root, "event", "maker");
        WebRuntime backend = backend();
        backend.makeDirectory("event/maker/a/b/c");
        assertTrue(Files.isDirectory(root.resolve("event/maker/a/b/c")));
        assertTrue(backend.listFiles("event/maker").dirs()
                .containsAll(List.of("a", "a/b", "a/b/c")));

        expect(409, () -> backend.makeDirectory("event/maker/a"));
        expect(403, () -> backend.makeDirectory("event/maker-adjacent"));
        expect(403, () -> backend.makeDirectory("event"));
        expect(422, () -> backend.makeDirectory("event/maker/bad name"));
    }

    // ---- create --------------------------------------------------------------

    @Test
    void createMacroScaffoldsAParsableMacro() throws IOException {
        WebRuntime backend = backend();
        MacroView view = backend.createMacro("event", "shop-reporter", "Counts shop visits");
        assertEquals("event/shop-reporter", view.id());
        assertEquals("0.0.1", view.version());
        assertEquals("world", view.lifecycle().name());
        Path folder = root.resolve("event/shop-reporter");
        MacroManifest manifest = ManifestParser.parse(folder, message -> { });
        assertEquals("shop-reporter", manifest.name());
        assertEquals("0.0.1", manifest.version());
        assertEquals("your-name", manifest.author());
        assertEquals("Counts shop visits", manifest.description());
        assertTrue(Files.readString(folder.resolve("index.js")).contains("shop-reporter"));

        // Cross-category duplicates, on-disk collisions, and the name rule.
        expect(409, () -> backend.createMacro("keypress", "shop-reporter", null));
        Files.createDirectories(root.resolve("event/ghost"));
        expect(409, () -> backend.createMacro("event", "ghost", null));
        expect(422, () -> backend.createMacro("event", "bad name", null));
        expect(422, () -> backend.createMacro("event", "a/b", null));
        expect(422, () -> backend.createMacro("nope", "valid-name", null));
        expect(422, () -> backend.createMacro("event", null, null));
    }

    @Test
    void aNameThatYamlWouldCoerceStaysAString() throws IOException {
        WebRuntime backend = backend();
        backend.createMacro("keypress", "on", null);
        MacroManifest manifest = ManifestParser.parse(
                root.resolve("keypress/on"), message -> { });
        assertEquals("on", manifest.name());
    }

    // ---- delete --------------------------------------------------------------

    @Test
    void deleteMacroRemovesFolderAndPersistedState() throws IOException {
        writeMacro(root, "event", "doomed");
        WebRuntime backend = backend();
        options.autoload = new java.util.LinkedHashMap<>(Map.of("doomed", false));
        options.keybinds = new java.util.LinkedHashMap<>(Map.of("doomed", "key.keyboard.j"));
        config.save();
        backend.deleteMacro("event/doomed");
        assertFalse(Files.exists(root.resolve("event/doomed")));
        assertFalse(options.autoload.containsKey("doomed"));
        assertFalse(options.keybinds.containsKey("doomed"));
        assertFalse(Files.readString(temp.resolve("config.json")).contains("doomed"));
        expect(404, () -> backend.deleteMacro("event/doomed"));
        expect(404, () -> backend.deleteMacro("never-existed"));

        // Bare names resolve too.
        writeMacro(root, "event", "doomed2");
        backend.deleteMacro("doomed2");
        assertFalse(Files.exists(root.resolve("event/doomed2")));
    }

    // ---- macro actions -------------------------------------------------------

    @Test
    void macroActionsAcceptCategoryIdsAndBareNames() throws IOException {
        writeMacro(root, "event", "toggler");
        WebRuntime backend = backend();
        // A stop on a never-loaded macro is a conflict, not a 404: the id
        // form category/name resolves.
        expect(409, () -> backend.macroAction("event/toggler", "stop"));
        MacroView enabled = backend.macroAction("event/toggler", "enable");
        assertTrue(enabled.enabled());
        assertTrue(options.autoload.get("toggler"));
        MacroView disabled = backend.macroAction("toggler", "disable");
        assertFalse(disabled.enabled());
        assertFalse(options.autoload.get("toggler"));
        expect(404, () -> backend.macroAction("event/unknown", "enable"));
    }

    // ---- reveal ---------------------------------------------------------------

    @Test
    void revealValidatesBeforeTheSandbox() throws IOException {
        writeMacro(root, "event", "auto-welcome");
        WebRuntime backend = backend();
        // 422 shapes come first: backslash, dot-dot, empty segments, whitespace.
        expect(422, () -> backend.reveal("event\\auto-welcome"));
        expect(422, () -> backend.reveal("../escape"));
        expect(422, () -> backend.reveal("event//auto-welcome"));
        expect(422, () -> backend.reveal("event/auto welcome"));
        // A trailing slash is an empty segment too: it must not silently
        // normalize to the bare directory name.
        expect(422, () -> backend.reveal("event/"));
        // The sandbox gates still apply (the drive letter is not a 422 shape).
        expect(403, () -> backend.reveal("C:/Windows"));
        expect(422, () -> backend.reveal("../web-devices.json"));
        // Existing targets validate through (the spawn itself is exercised
        // on a live machine only — tests must not open Explorer windows).
        expect(404, () -> backend.reveal("event/auto-welcome/missing.js"));
        expect(404, () -> backend.reveal("event/never-existed"));
    }

    // ---- macro move -----------------------------------------------------------

    @Test
    void moveMacroRelocatesTheFolderAndKeepsTheManifest() throws IOException {
        writeMacro(root, "keypress", "shop-reporter");
        WebRuntime backend = backend();
        MacroView moved = backend.moveMacro("keypress/shop-reporter", "services");
        assertEquals("services/shop-reporter", moved.id());
        assertTrue(Files.isDirectory(root.resolve("services/shop-reporter")));
        assertFalse(Files.exists(root.resolve("keypress/shop-reporter")));
        MacroManifest manifest = ManifestParser.parse(
                root.resolve("services/shop-reporter"), message -> { });
        assertEquals("shop-reporter", manifest.name());
        // Same category is an idempotent no-op.
        MacroView again = backend.moveMacro("services/shop-reporter", "services");
        assertEquals("services/shop-reporter", again.id());
        // Error shapes: bad category, unknown macro, name collision.
        expect(422, () -> backend.moveMacro("services/shop-reporter", "nope"));
        expect(404, () -> backend.moveMacro("services/unknown", "event"));
        Files.createDirectories(root.resolve("event/shop-reporter"));
        expect(409, () -> backend.moveMacro("services/shop-reporter", "event"));
    }

    // ---- state ----------------------------------------------------------------

    @Test
    void snapshotCarriesTheAbsoluteMacrosRoot() throws IOException {
        writeMacro(root, "event", "auto-welcome");
        WebRuntime backend = backend();
        var snapshot = backend.snapshot();
        assertEquals(root.toRealPath().toString(), snapshot.macrosRoot());
        assertEquals(1, snapshot.macros().size());
    }

    @Test
    void folderChangeSuppressionLeavesNoDiskEcho() {
        WebRuntime backend = backend();
        assertDoesNotThrow(() -> backend.folderChanged(
                root.resolve("event").resolve("auto-welcome").resolve("nonexistent"),
                java.util.List.of("index.js")));
    }

    private static void assertNullError(FileWriteResult result) {
        assertNotNull(result);
        assertFalse(result.conflict());
    }
}
