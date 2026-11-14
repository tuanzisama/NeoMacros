package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E-A scenarios for the CustomImage canvas: a real script through the
 * real engine constructing a canvas over the stub render port, exercising
 * pixel writes with boundary rejection, drawing, clipping, the PNG file
 * faces over the harness root, and the awaited {@code update()} upload
 * recorded by the stub.
 */
class CustomImageScenariosTest {

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
    void canvasFaceThroughTheStubRenderPort() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g030-custom-image.js", String.join("\n",
                    "const results = {};",
                    "const img = new neo.CustomImage(8, 4, 'badge');",
                    "results['name'] = img.name === 'badge';",
                    "results['identifier'] = img.identifier === 'neomacros:image/badge';",
                    "results['size'] = img.width === 8 && img.height === 4;",
                    "img.setPixel(0, 0, 0xFF112233);",
                    "results['pixel'] = (img.getPixel(0, 0) | 0) === (0xFF112233 | 0);",
                    "try { img.getPixel(8, 0); results['bounds'] = false; }",
                    "  catch (e) { results['bounds'] = true; }",
                    "try { img.setPixel(-1, 0, 0xFF000000); results['setBounds'] = false; }",
                    "  catch (e) { results['setBounds'] = true; }",
                    "img.setGraphicsColor(0x00FF00FF).fillRect(1, 1, 2, 2);",
                    "results['fill'] = (img.getPixel(2, 2) | 0) === (0xFFFF00FF | 0);",
                    "results['color'] = (img.graphicsColor | 0) === (0xFFFF00FF | 0);",
                    "const wide = img.getStringWidth('hello world');",
                    "const narrow = img.getStringWidth('hi');",
                    "results['stringWidth'] = typeof wide === 'number' && wide > narrow;",
                    "img.setClip(0, 0, 4, 4);",
                    "const clip = img.clipBounds;",
                    "results['clip'] = clip !== undefined && clip.x === 0 && clip.width === 4;",
                    "const snap = img.image;",
                    "results['image'] = snap !== undefined",
                    "    && snap.width === 8 && snap.height === 4;",
                    "img.saveImage('shots', 'badge');",
                    "const loaded = img.loadImage('shots/badge.png');",
                    "results['load'] = loaded !== undefined",
                    "    && loaded.width === 8 && loaded.height === 4;",
                    "results['loadMissing'] = img.loadImage('shots/absent.png') === undefined;",
                    "try { new neo.CustomImage(2, 2, 'BAD'); results['badName'] = false; }",
                    "  catch (e) { results['badName'] = true; }",
                    "img.update().then((self) => {",
                    "  results['update'] = self !== undefined",
                    "      && self.identifier === 'neomacros:image/badge';",
                    "  neo.__test.report('g030-custom-image',",
                    "      Object.values(results).every(Boolean), JSON.stringify(results));",
                    "});",
                    ""));

            e2e.loadScript(dir.resolve("scripts").resolve("g030-custom-image.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("g030-custom-image");
            assertNotNull(report, "custom image report missing");
            assertTrue(report.pass(), "custom image scenario failed: " + report.detail()
                    + " scriptErrors=" + e2e.scriptErrors());

            var uploads = e2e.adapter().renderPort().uploads();
            assertTrue(uploads.size() >= 2,
                    "construction and update() both upload, got " + uploads.size());
            var updated = uploads.get(uploads.size() - 1);
            assertEquals("neomacros:image/badge", updated.id());
            assertEquals(8, updated.width());
            assertEquals(4, updated.height());
            assertEquals(0xFF112233, updated.pixel(0, 0),
                    "the awaited upload carries the latest pixels");
            assertTrue(e2e.adapter().renderPort().textureIds().get()
                    .contains("neomacros:image/badge"));

            assertTrue(Files.exists(dir.resolve("image-root/shots/badge.png")),
                    "saveImage writes below the harness image root");
        }
    }
}
