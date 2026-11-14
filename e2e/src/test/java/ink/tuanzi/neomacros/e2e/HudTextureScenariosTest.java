package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E-A scenarios for the G-038 texture registry (拍板⑥): a real script
 * through the real engine drives {@code neo.hud.texture} over the stub
 * render port — registration with a single blank upload, per-name
 * deduplication, adoption of a named {@code CustomImage} canvas,
 * {@code update(Image)} re-upload, explicit {@code dispose()} releasing
 * the GPU slot, and module-unload auto-reclaim (拍板③).
 */
class HudTextureScenariosTest {

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
    void textureRegistryLifecycleThroughTheStubRenderPort() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g038-hud-texture.js", String.join("\n",
                    "const results = {};",
                    "const badge = neo.hud.texture('badge', {width: 8, height: 8});",
                    // Dedupe without relying on guest wrapper identity (every
                    // crossing mints a fresh JS instance): the second call must
                    // keep the first registration's dimensions.
                    "const badgeAgain = neo.hud.texture('badge');",
                    "results['dedupe'] = neo.hud.textures.length === 1",
                    "    && badge.id === 'neomacros:image/badge'",
                    "    && badgeAgain.width === 8 && badgeAgain.height === 8",
                    "    && badgeAgain.disposed === false && badge.name === 'badge';",
                    "let badName = false;",
                    "try { neo.hud.texture('Bad Name'); } catch (e) { badName = true; }",
                    "results['badName'] = badName;",
                    // A named canvas self-registers; texture() adopts it with
                    // the canvas dimensions (no blank re-upload).
                    "const canvas = new neo.CustomImage(16, 8, 'canvas');",
                    "const adopted = neo.hud.texture('canvas');",
                    "results['adopt'] = neo.hud.textures.length === 2",
                    "    && adopted.id === 'neomacros:image/canvas'",
                    "    && adopted.width === 16 && adopted.height === 8;",
                    "adopted.update(canvas.image).then((tex) => {",
                    "  results['update'] = tex.width === 16 && tex.height === 8;",
                    "  badge.dispose();",
                    "  results['dispose'] = badge.disposed === true",
                    "      && neo.hud.textures.length === 1;",
                    "  neo.__test.report('g038-hud-texture',",
                    "      Object.values(results).every(Boolean), JSON.stringify(results));",
                    "});",
                    ""));

            e2e.loadScript(dir.resolve("scripts").resolve("g038-hud-texture.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("g038-hud-texture");
            assertNotNull(report, "hud texture report missing; scriptErrors="
                    + e2e.scriptErrors());
            assertTrue(report.pass(), "hud texture scenario failed: " + report.detail()
                    + " scriptErrors=" + e2e.scriptErrors());

            var port = e2e.adapter().renderPort();
            assertEquals(List.of("neomacros:image/badge", "neomacros:image/canvas"),
                    port.uploads().stream().map(u -> u.id()).distinct().toList(),
                    "both the blank placeholder and the canvas were uploaded");
            assertEquals(List.of("neomacros:image/canvas"), port.textureIds().get(),
                    "the disposed badge left the port; the canvas stays");
            assertEquals(1, port.uploads().stream()
                            .filter(u -> u.id().equals("neomacros:image/badge")).count(),
                    "the deduped name uploads its placeholder exactly once");
            assertEquals(List.of("neomacros:image/badge"), port.textureRemovals(),
                    "explicit dispose() releases the GPU slot");

            // 拍板③: unloading the module reclaims its remaining textures.
            e2e.modules().unload("g038-hud-texture");
            assertTrue(port.textureRemovals().contains("neomacros:image/canvas"),
                    "module unload auto-reclaims the canvas-registered texture");
            assertFalse(port.textureIds().get().contains("neomacros:image/canvas"),
                    "the reclaimed slot leaves the port");
        }
    }
}
