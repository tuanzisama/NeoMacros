package ink.tuanzi.neomacros.core.module.manifest;

import ink.tuanzi.neomacros.core.module.ScriptMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manifest discovery, format priority, validation, and category defaults. */
class ManifestParserTest {

    @Test
    void parsesYamlManifest(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.2.0
                author: tuanzi
                description: does things
                license: MIT
                homepage: https://example.com
                supportURL: https://example.com/support
                main: src/main.js
                key: key.keyboard.j
                lifecycle: always
                permissions:
                  - fs
                  - net
                """);

        MacroManifest manifest = ManifestParser.parse(dir, m -> { });

        assertEquals("demo", manifest.name());
        assertEquals("1.2.0", manifest.version());
        assertEquals("tuanzi", manifest.author());
        assertEquals("does things", manifest.description());
        assertEquals("MIT", manifest.license());
        assertEquals("https://example.com", manifest.homepage());
        assertEquals("https://example.com/support", manifest.supportURL());
        assertEquals("src/main.js", manifest.main());
        assertEquals("key.keyboard.j", manifest.key());
        assertEquals(ScriptMeta.Lifecycle.ALWAYS, manifest.lifecycle());
        assertEquals(List.of("fs", "net"), manifest.permissions());
    }

    @Test
    void parsesJsonAndJson5Manifests(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.json"), """
                {"name": "j", "version": "1.0.0", "author": "a"}
                """);
        MacroManifest json = ManifestParser.parse(dir, m -> { });
        assertEquals("j", json.name());

        Files.delete(dir.resolve("neomacro.json"));
        Files.writeString(dir.resolve("neomacro.json5"), """
                {
                    name: 'j5',     // identity
                    version: '1.0.0',
                    author: 'a',
                }
                """);
        MacroManifest json5 = ManifestParser.parse(dir, m -> { });
        assertEquals("j5", json5.name());
    }

    @Test
    void priorityIsYamlThenJsonThenJson5(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"),
                "name: from-yaml\nversion: 1.0.0\nauthor: a\n");
        Files.writeString(dir.resolve("neomacro.json"),
                "{\"name\": \"from-json\", \"version\": \"1.0.0\", \"author\": \"a\"}");
        Files.writeString(dir.resolve("neomacro.json5"),
                "{ name: 'from-json5', version: '1.0.0', author: 'a' }");
        List<String> warnings = new ArrayList<>();

        MacroManifest manifest = ManifestParser.parse(dir, warnings::add);

        assertEquals("from-yaml", manifest.name());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("multiple manifests"));
        assertTrue(warnings.get(0).contains("neomacro.yml"));
    }

    @Test
    void missingManifestThrows(@TempDir Path dir) {
        ManifestParseException e = assertThrows(ManifestParseException.class,
                () -> ManifestParser.parse(dir, m -> { }));
        assertTrue(e.getMessage().contains("no neomacro"));
    }

    @Test
    void requiredFieldsAreEnforced(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"), "name: only-name\n");
        ManifestParseException e = assertThrows(ManifestParseException.class,
                () -> ManifestParser.parse(dir, m -> { }));
        assertTrue(e.getMessage().contains("'version'"));
        assertTrue(e.getMessage().contains("'author'"));
    }

    @Test
    void nameRejectsPathCharacters(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"),
                "name: 'bad/name'\nversion: 1.0.0\nauthor: a\n");
        assertThrows(ManifestParseException.class, () -> ManifestParser.parse(dir, m -> { }));
    }

    @Test
    void unknownFieldsWarnButDoNotReject(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"),
                "name: x\nversion: 1.0.0\nauthor: a\ntypoFiled: oops\n");
        List<String> warnings = new ArrayList<>();

        MacroManifest manifest = ManifestParser.parse(dir, warnings::add);

        assertEquals("x", manifest.name());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("typoFiled"));
    }

    @Test
    void invalidSyntaxIsReported(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.json"), "{not json");
        ManifestParseException e = assertThrows(ManifestParseException.class,
                () -> ManifestParser.parse(dir, m -> { }));
        assertTrue(e.getMessage().contains("invalid JSON"));
    }

    @Test
    void categoryDefaultsResolveThroughLifecycleOr(@TempDir Path dir) {
        MacroManifest without = new MacroManifest("n", "1", "a", "", "", "", "", "", "",
                null, List.of(), List.of(), null);
        assertEquals(ScriptMeta.Lifecycle.ALWAYS, without.lifecycleOr(ScriptCategory.SERVICES));
        assertEquals(ScriptMeta.Lifecycle.WORLD, without.lifecycleOr(ScriptCategory.EVENT));
        assertEquals(ScriptMeta.Lifecycle.WORLD, without.lifecycleOr(ScriptCategory.KEYPRESS));

        MacroManifest with = new MacroManifest("n", "1", "a", "", "", "", "", "", "",
                ScriptMeta.Lifecycle.MANUAL, List.of(), List.of(), null);
        assertEquals(ScriptMeta.Lifecycle.MANUAL, with.lifecycleOr(ScriptCategory.SERVICES));
    }

    @Test
    void eventsAndAutostartAreRecognized(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.0.0
                author: tuanzi
                events:
                  - ChatReceive
                  - PlayerConnect
                autostart: false
                """);
        List<String> warnings = new ArrayList<>();
        MacroManifest manifest = ManifestParser.parse(dir, warnings::add);

        assertEquals(List.of("ChatReceive", "PlayerConnect"), manifest.events());
        assertEquals(Boolean.FALSE, manifest.autostart());
        // Both are known fields: no unknown-field warnings.
        assertTrue(warnings.isEmpty(), String.valueOf(warnings));

        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.0.0
                author: tuanzi
                autostart: true
                """);
        assertEquals(Boolean.TRUE, ManifestParser.parse(dir, m -> { }).autostart());
    }

    @Test
    void aTypedAutostartRejectsTheManifest(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.0.0
                author: tuanzi
                autostart: maybe
                """);
        ManifestParseException e = assertThrows(ManifestParseException.class,
                () -> ManifestParser.parse(dir, m -> { }));
        assertTrue(e.getMessage().contains("autostart"), e.getMessage());
    }

    @Test
    void anEventsScalarRejectsTheManifest(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.0.0
                author: tuanzi
                events: ChatReceive
                """);
        ManifestParseException e = assertThrows(ManifestParseException.class,
                () -> ManifestParser.parse(dir, m -> { }));
        assertTrue(e.getMessage().contains("events"), e.getMessage());
    }

    @Test
    void categoryDirectoryNamesRoundTrip() {
        assertEquals("keypress", ScriptCategory.KEYPRESS.dirName());
        assertEquals("event", ScriptCategory.EVENT.dirName());
        assertEquals("services", ScriptCategory.SERVICES.dirName());
        assertEquals(ScriptCategory.SERVICES, ScriptCategory.fromDirName("services"));
        assertNull(ScriptCategory.fromDirName("nope"));
    }
}
