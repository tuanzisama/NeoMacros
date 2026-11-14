package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Options scenarios across the root, video, chat/control/accessibility and
 * music/skin groups: a real user-format script reads the canned option face,
 * writes several options through promises, reads each value back, and observes
 * a refused write reject against the stub.
 */
class OptionsRoundtripScenariosTest {

    @TempDir
    Path dir;

    /** Writes one script folder (manifest + index.js) below the event category. */
    private static void fixture(Path scriptsDir, String name, String source) throws Exception {
        String stem = name.endsWith(".js") ? name.substring(0, name.length() - 3) : name;
        Path folder = scriptsDir.resolve("event").resolve(stem);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("neomacro.yml"), String.join("\n",
                "name: " + stem,
                "version: 1.0.0",
                "author: e2e",
                "config:",
                "  event:",
                "    on: [game:start]",
                ""));
        Files.writeString(folder.resolve("index.js"), source + "\nexport function startup() {}\n");
    }

    @Test
    void writeThenReadBackAcrossTheOptionFace() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            var stub = e2e.adapter().clientPort();

            fixture(dir.resolve("scripts"), "options.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  try {",
                    "    const opts = neo.client.options;",
                    "    const checks = {",
                    "      rootFov: opts.fov === 70,",
                    "      legacyGamma: opts.gamma === 0.5,",
                    "      legacyGuiScale: opts.guiScale === 2,",
                    "      legacyRenderDistance: opts.renderDistance === 12,",
                    "      videoRender: opts.video.renderDistance === 12,",
                    "      videoGraphics: opts.video.graphicsMode === 'fancy',",
                    "      videoVsync: opts.video.enableVsync === true,",
                    "      language: opts.language === 'en_us',",
                    "      packs: Array.isArray(opts.resourcePacks)",
                    "          && opts.resourcePacks[0] === 'vanilla',",
                    "      groups: opts.skin !== undefined && opts.music !== undefined",
                    "          && opts.control !== undefined && opts.chat !== undefined",
                    "          && opts.accessibility !== undefined,",
                    "      parent: opts.video.parent.fov === opts.fov,",
                    "      savePromise: typeof opts.saveOptions().then === 'function',",
                    "    };",
                    "    opts.setFov(75).then(() => {",
                    "      checks.fovRoundTrip = opts.fov === 75;",
                    "      return opts.video.setRenderDistance(6);",
                    "    }).then(() => {",
                    "      checks.videoRoundTrip = opts.video.renderDistance === 6;",
                    "      return opts.setLanguage('en_gb');",
                    "    }).then(() => {",
                    "      checks.languageRoundTrip = opts.language === 'en_gb';",
                    "      return opts.setCameraMode(9).then(() => false, () => true);",
                    "    }).then((rejected) => {",
                    "      checks.rejectOutOfRange = rejected;",
                    "      const pass = Object.values(checks).every(Boolean);",
                    "      const failed = Object.keys(checks).filter((k) => !checks[k]);",
                    "      neo.__test.report('options', pass,",
                    "          'failed=' + failed.join(','));",
                    "    }, (e) => neo.__test.report('options', false, 'write: ' + e));",
                    "  } catch (err) {",
                    "    neo.__test.report('options', false, 'sync-error: ' + err);",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("options.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("options");
            assertTrue(report != null && report.pass(),
                    "report was: " + report + " reports=" + e2e.bridge().all()
                            + " errors=" + e2e.scriptErrors());
            assertEquals(List.of("save", "option:fov", "option:renderDistance",
                    "language:en_gb"), stub.actions(),
                    "action log was: " + stub.actions());
        }
    }

    @Test
    void chatControlAndAccessibilityGroupsWriteThenReadBack() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            var stub = e2e.adapter().clientPort();

            fixture(dir.resolve("scripts"), "option-groups.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  try {",
                    "    const opts = neo.client.options;",
                    "    const chat = opts.chat;",
                    "    const control = opts.control;",
                    "    const a11y = opts.accessibility;",
                    "    const checks = {",
                    "      chatHandle: typeof chat.setChatOpacity === 'function'",
                    "          && chat.chatVisibility === 'full'",
                    "          && chat.narrator === 'off',",
                    "      controlHandle: typeof control.setAutoJump === 'function'",
                    "          && control.sensitivity === 0.5",
                    "          && control.touchscreen === false,",
                    "      a11yHandle: typeof a11y.setShowSubtitles === 'function'",
                    "          && a11y.showSubtitles === false",
                    "          && a11y.hideLightningFlash === false,",
                    "      parent: chat.parent.fov === opts.fov",
                    "          && control.parent.fov === opts.fov",
                    "          && a11y.parent.fov === opts.fov,",
                    "      keys: Array.isArray(control.keys)",
                    "          && control.keys[0] === 'key.forward',",
                    "      categories: Array.isArray(control.categories)",
                    "          && control.categories[0] === 'key.categories.movement',",
                    "      binds: control.keyBindsByCategory['key.categories.movement']",
                    "          .Forward === 'W',",
                    "      bindsForCategory:",
                    "          control.getKeyBindsByCategory('key.categories.movement')",
                    "              .Back === 'S',",
                    "      bindsMissing:",
                    "          control.getKeyBindsByCategory('nope') === undefined,",
                    "    };",
                    "    chat.setChatOpacity(0.5).then(() => {",
                    "      checks.chatRoundTrip = chat.chatOpacity === 0.5;",
                    "      return chat.setChatVisibility('hidden');",
                    "    }).then(() => {",
                    "      checks.visibilityRoundTrip = chat.chatVisibility === 'hidden';",
                    "      return control.setAutoJump(true);",
                    "    }).then(() => {",
                    "      checks.controlRoundTrip = control.autoJump === true;",
                    "      return a11y.setShowSubtitles(true);",
                    "    }).then(() => {",
                    "      checks.a11yRoundTrip = a11y.showSubtitles === true;",
                    "      return a11y.setDistortionEffect(0.25);",
                    "    }).then(() => {",
                    "      checks.distortionRoundTrip = a11y.distortionEffect === 0.25;",
                    "      const pass = Object.values(checks).every(Boolean);",
                    "      const failed = Object.keys(checks).filter((k) => !checks[k]);",
                    "      neo.__test.report('option-groups', pass,",
                    "          'failed=' + failed.join(','));",
                    "    }, (e) => neo.__test.report('option-groups', false,",
                    "        'write: ' + e));",
                    "  } catch (err) {",
                    "    neo.__test.report('option-groups', false, 'sync-error: ' + err);",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("option-groups.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("option-groups");
            assertTrue(report != null && report.pass(),
                    "report was: " + report + " reports=" + e2e.bridge().all()
                            + " errors=" + e2e.scriptErrors());
            assertEquals(List.of("option:chatOpacity", "option:chatVisibility",
                    "option:autoJump", "option:showSubtitles", "option:screenEffectScale"),
                    stub.actions(), "action log was: " + stub.actions());
        }
    }

    @Test
    void musicAndSkinGroupsWriteThenReadBack() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            var stub = e2e.adapter().clientPort();

            fixture(dir.resolve("scripts"), "option-music-skin.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  try {",
                    "    const opts = neo.client.options;",
                    "    const music = opts.music;",
                    "    const skin = opts.skin;",
                    "    const checks = {",
                    "      musicHandle: typeof music.setMasterVolume === 'function'",
                    "          && typeof music.setSoundDevice === 'function'",
                    "          && music.masterVolume === 1.0",
                    "          && music.soundDevice === 'speakers',",
                    "      volumes: music.volumes.music === 0.5",
                    "          && music.volumes.record === 0.75",
                    "          && music.volumes.ui === 0.25",
                    "          && Object.keys(music.volumes).length === 11,",
                    "      devices: Array.isArray(music.audioDevices)",
                    "          && music.audioDevices[0] === ''",
                    "          && music.audioDevices[1] === 'speakers',",
                    "      skinHandle: typeof skin.setCape === 'function'",
                    "          && typeof skin.setMainHand === 'function'",
                    "          && skin.cape === true",
                    "          && skin.mainHand === 'right'",
                    "          && skin.rightHanded === true",
                    "          && skin.leftHanded === false,",
                    "      parent: music.parent.fov === opts.fov",
                    "          && skin.parent.fov === opts.fov,",
                    "    };",
                    "    music.setMasterVolume(0.75).then(() => {",
                    "      checks.volumeRoundTrip = music.masterVolume === 0.75",
                    "          && music.volumes.master === 0.75;",
                    "      return music.setSoundDevice('headphones');",
                    "    }).then(() => {",
                    "      checks.deviceRoundTrip = music.soundDevice === 'headphones';",
                    "      return music.setShowSubtitles(true);",
                    "    }).then(() => {",
                    "      checks.subtitlesRoundTrip = music.showSubtitles === true;",
                    "      return skin.setCape(false);",
                    "    }).then(() => {",
                    "      checks.capeRoundTrip = skin.cape === false;",
                    "      return skin.setMainHand('left');",
                    "    }).then(() => {",
                    "      checks.handRoundTrip = skin.mainHand === 'left'",
                    "          && skin.leftHanded === true;",
                    "      return music.setMasterVolume(1.5).then(() => false, () => true);",
                    "    }).then((rejected) => {",
                    "      checks.rejectOutOfRange = rejected;",
                    "      const pass = Object.values(checks).every(Boolean);",
                    "      const failed = Object.keys(checks).filter((k) => !checks[k]);",
                    "      neo.__test.report('music-skin', pass,",
                    "          'failed=' + failed.join(','));",
                    "    }, (e) => neo.__test.report('music-skin', false, 'write: ' + e));",
                    "  } catch (err) {",
                    "    neo.__test.report('music-skin', false, 'sync-error: ' + err);",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("option-music-skin.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("music-skin");
            assertTrue(report != null && report.pass(),
                    "report was: " + report + " reports=" + e2e.bridge().all()
                            + " errors=" + e2e.scriptErrors());
            assertEquals(List.of("option:masterVolume", "option:soundDevice",
                    "option:showSubtitles", "option:cape", "option:mainHand"),
                    stub.actions(), "action log was: " + stub.actions());
        }
    }
}
