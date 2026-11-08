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

/** Manifest discovery, format priority, config block validation, category defaults. */
class ManifestParserTest {

    @Test
    void parsesYamlManifestWithConfigBlock(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.2.0
                author: tuanzi
                description: does things
                license: MIT
                homepage: https://example.com
                supportURL: https://example.com/support
                main: src/main.js
                permissions:
                  - fs
                  - net
                config:
                  keypress:
                    key_code: key.keyboard.j
                    on: keydown
                    modifiers: [ctrl, shift]
                  service:
                    lifecycle: always
                    autostart: false
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
        assertEquals(List.of("fs", "net"), manifest.permissions());

        MacroConfig.Keypress keypress = manifest.configOrEmpty().keypress();
        assertEquals("keydown", keypress.on());
        assertEquals("key.keyboard.j", keypress.keyCode());
        assertEquals(List.of("ctrl", "shift"), keypress.modifiers());
        assertEquals("keydown", keypress.onOrDefault());

        MacroConfig.Service service = manifest.configOrEmpty().service();
        assertEquals(ScriptMeta.Lifecycle.ALWAYS, service.lifecycle());
        assertEquals(Boolean.FALSE, service.autostart());
        assertEquals(ScriptMeta.Lifecycle.ALWAYS, manifest.lifecycleOr(ScriptCategory.KEYPRESS));
    }

    @Test
    void parsesJsonManifest(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.json"), """
                {"name": "j", "version": "1.0.0", "author": "a"}
                """);
        MacroManifest json = ManifestParser.parse(dir, m -> { });
        assertEquals("j", json.name());
        assertEquals(MacroConfig.EMPTY, json.configOrEmpty());
    }

    @Test
    void priorityIsYamlThenJson(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"),
                "name: from-yaml\nversion: 1.0.0\nauthor: a\n");
        Files.writeString(dir.resolve("neomacro.json"),
                "{\"name\": \"from-json\", \"version\": \"1.0.0\", \"author\": \"a\"}");
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
    void categoryDefaultsResolveThroughLifecycleOr() {
        MacroManifest without = new MacroManifest("n", "1", "a", "", "", "", "", "",
                List.of(), MacroConfig.EMPTY);
        assertEquals(ScriptMeta.Lifecycle.ALWAYS, without.lifecycleOr(ScriptCategory.SERVICES));
        assertEquals(ScriptMeta.Lifecycle.WORLD, without.lifecycleOr(ScriptCategory.EVENT));
        assertEquals(ScriptMeta.Lifecycle.WORLD, without.lifecycleOr(ScriptCategory.KEYPRESS));

        MacroConfig.Service manual = new MacroConfig.Service(ScriptMeta.Lifecycle.MANUAL, null);
        MacroManifest with = new MacroManifest("n", "1", "a", "", "", "", "", "",
                List.of(), new MacroConfig(null, null, manual));
        assertEquals(ScriptMeta.Lifecycle.MANUAL, with.lifecycleOr(ScriptCategory.SERVICES));
    }

    @Test
    void eventOnAcceptsScalarAndList(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.0.0
                author: tuanzi
                config:
                  event:
                    on: player:join
                """);
        MacroManifest scalar = ManifestParser.parse(dir, m -> { });
        assertEquals(List.of("player:join"), scalar.configOrEmpty().event().on());

        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.0.0
                author: tuanzi
                config:
                  event:
                    on: [chat:receive, world:join]
                """);
        MacroManifest list = ManifestParser.parse(dir, m -> { });
        assertEquals(List.of("chat:receive", "world:join"), list.configOrEmpty().event().on());
    }

    @Test
    void keypressDefaultsAreApplied(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.0.0
                author: tuanzi
                config:
                  keypress: {}
                """);
        MacroConfig.Keypress keypress = ManifestParser.parse(dir, m -> { }).configOrEmpty().keypress();
        assertEquals("keypress", keypress.onOrDefault());
        assertEquals("", keypress.keyCodeOrEmpty());
        assertEquals(List.of(), keypress.modifiersOrEmpty());
    }

    @Test
    void legacyTopLevelBehaviorFieldsRejectWithMigrationHint(@TempDir Path dir) throws IOException {
        for (String legacy : List.of("key: key.keyboard.j", "lifecycle: always",
                "autostart: false", "on: keydown", "modifiers: [ctrl]",
                "events: [chat:receive]")) {
            Files.writeString(dir.resolve("neomacro.yml"),
                    "name: demo\nversion: 1.0.0\nauthor: a\n" + legacy + "\n");
            ManifestParseException e = assertThrows(ManifestParseException.class,
                    () -> ManifestParser.parse(dir, m -> { }),
                    "legacy field should reject: " + legacy);
            assertTrue(e.getMessage().contains("migration guide"), e.getMessage());
        }
    }

    @Test
    void legacyKeypressKeyFieldsInsideConfigReject(@TempDir Path dir) throws IOException {
        for (String legacy : List.of("key: key.keyboard.j", "default-key: key.keyboard.j")) {
            Files.writeString(dir.resolve("neomacro.yml"), """
                    name: demo
                    version: 1.0.0
                    author: tuanzi
                    config:
                      keypress:
                        %s
                    """.formatted(legacy));
            ManifestParseException e = assertThrows(ManifestParseException.class,
                    () -> ManifestParser.parse(dir, m -> { }),
                    "legacy field should reject: " + legacy);
            assertTrue(e.getMessage().contains("key_code"), e.getMessage());
        }
    }

    @Test
    void unknownTriggerEdgeRejects(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.0.0
                author: tuanzi
                config:
                  keypress:
                    on: keyleft
                """);
        ManifestParseException e = assertThrows(ManifestParseException.class,
                () -> ManifestParser.parse(dir, m -> { }));
        assertTrue(e.getMessage().contains("config.keypress.on"), e.getMessage());
    }

    @Test
    void unknownModifierRejects(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.0.0
                author: tuanzi
                config:
                  keypress:
                    modifiers: [ctrl, hyper]
                """);
        ManifestParseException e = assertThrows(ManifestParseException.class,
                () -> ManifestParser.parse(dir, m -> { }));
        assertTrue(e.getMessage().contains("modifiers"), e.getMessage());
    }

    @Test
    void typedAutostartRejects(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.0.0
                author: tuanzi
                config:
                  service:
                    autostart: maybe
                """);
        ManifestParseException e = assertThrows(ManifestParseException.class,
                () -> ManifestParser.parse(dir, m -> { }));
        assertTrue(e.getMessage().contains("autostart"), e.getMessage());
    }

    @Test
    void configMustBeAMapping(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"),
                "name: demo\nversion: 1.0.0\nauthor: a\nconfig: nope\n");
        ManifestParseException e = assertThrows(ManifestParseException.class,
                () -> ManifestParser.parse(dir, m -> { }));
        assertTrue(e.getMessage().contains("'config' must be a mapping"), e.getMessage());
    }

    @Test
    void validateForRequiresTheCategorySection(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"),
                "name: demo\nversion: 1.0.0\nauthor: a\n");
        MacroManifest identity = ManifestParser.parse(dir, m -> { });

        ManifestParseException keypress = assertThrows(ManifestParseException.class,
                () -> identity.validateFor(ScriptCategory.KEYPRESS, m -> { }));
        assertTrue(keypress.getMessage().contains("config.keypress"));
        ManifestParseException event = assertThrows(ManifestParseException.class,
                () -> identity.validateFor(ScriptCategory.EVENT, m -> { }));
        assertTrue(event.getMessage().contains("config.event"));
        // Services may omit config.service entirely (category defaults apply).
        identity.validateFor(ScriptCategory.SERVICES, m -> { });
    }

    @Test
    void validateForWarnsOnForeignSections(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("neomacro.yml"), """
                name: demo
                version: 1.0.0
                author: tuanzi
                config:
                  keypress:
                    on: keydown
                  service:
                    lifecycle: always
                """);
        MacroManifest manifest = ManifestParser.parse(dir, m -> { });
        // A keypress macro moved to services keeps its old section; it is
        // warned about, not rejected. config.service is the section of the
        // target category and produces no warning.
        List<String> warnings = new ArrayList<>();
        manifest.validateFor(ScriptCategory.SERVICES, warnings::add);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("config.keypress"));
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
