package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.StubAdapter;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E-A scenarios for the G-034B component faces: a real script through
 * the real engine drives {@code Draw2DImage}/{@code Draw2DItem} — subclass
 * upserts, the texture/region view (including the G-030 CustomImage
 * snapshot and identifier consumption), the stack and overlay faces, the
 * fluent chains and the disposed throw — over the stub render port, with
 * the paint primitives (tinted sprite, item icon, transform scopes), the
 * snapshot upload and the zero-port-traffic promise asserted on the fired
 * frame.
 */
class Draw2DImageItemScenariosTest {

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
                "author: " + "e2e",
                "config:",
                "  event:",
                "    on: [game:start]",
                ""));
        Files.writeString(folder.resolve("index.js"), source + "\nexport function startup() {}\n");
    }

    @Test
    void imageAndItemFacesThroughTheStubRenderPort() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g034b-image-item.js", String.join("\n",
                    "const results = {};",
                    "const layer = neo.hud.layer('imgitem');",
                    // Image: shape, texture/region state, tint, fluent set.
                    "const img = layer.image('i', {x: 10, y: 20, w: 32, h: 32,",
                    "    texture: 'neomacros:image/badge', color: '#80ff0000',",
                    "    region: {x: 4, y: 5, w: 6, h: 7}});",
                    "results['image-shape'] = typeof img.setImage === 'function'",
                    "    && typeof img.color === 'number'",
                    "    && typeof img.image === 'string';",
                    "results['image-state'] = img.image === 'neomacros:image/badge'",
                    "    && img.imageX === 4 && img.imageY === 5",
                    "    && img.regionWidth === 6 && img.regionHeight === 7",
                    "    && img.textureWidth === 256 && img.textureHeight === 256",
                    "    && img.width === 32 && img.height === 32",
                    "    && (img.color >>> 0) === 0x80FF0000 && img.alpha === 0x80;",
                    "img.setImage('a:b', 1, 2, 3, 4, 64, 64);",
                    "results['image-set'] = img.image === 'a:b'",
                    "    && img.imageX === 1 && img.regionWidth === 3",
                    "    && img.textureWidth === 64 && img.width === 32;",
                    // G-030 consumption: a CustomImage snapshot uploads once and
                    // the identifier face feeds the live-texture form.
                    "const badge = new neo.CustomImage(8, 4, 'g034b');",
                    "const snap = badge.image;",
                    "const bySnap = layer.image('snap', {x: 0, y: 0, w: 8, h: 8,",
                    "    image: snap});",
                    "results['image-snapshot'] =",
                    "    bySnap.image.indexOf('neomacros:image/hud/') === 0",
                    "    && bySnap.textureWidth === 8 && bySnap.textureHeight === 4;",
                    "const byId = layer.image('live', {x: 0, y: 0, w: 8, h: 8,",
                    "    texture: badge.identifier});",
                    "results['image-identifier'] =",
                    "    byId.image === 'neomacros:image/g034b';",
                    "layer.remove('snap');",
                    "layer.remove('live');",
                    // Item: shape, box/stack state, overlay faces, fluent set.
                    "const item = layer.item('it', 'minecraft:stone',",
                    "    {x: 5, y: 6, overlay: true});",
                    "results['item-shape'] = typeof item.setItem === 'function'",
                    "    && typeof item.shouldShowOverlay === 'function'",
                    "    && typeof item.setOverlayText === 'function'",
                    "    && typeof item.item === 'object';",
                    "results['item-state'] = item.item.id === 'minecraft:stone'",
                    "    && item.item.count === 1",
                    "    && item.width === 16 && item.scaledWidth === 16",
                    "    && item.x === 5 && item.y === 6",
                    "    && item.shouldShowOverlay() === true;",
                    "item.setItem('minecraft:diamond', 5).setOverlayText('x5');",
                    "results['item-set'] = item.item.id === 'minecraft:diamond'",
                    "    && item.item.count === 5 && item.overlayText === 'x5'",
                    "    && item.shouldShowOverlay() === true;",
                    "item.setScale(2);",
                    "results['item-scale'] = item.scaledWidth === 32;",
                    // Disposal: the component mutators throw, reads read undefined.
                    "const gone = layer.image('gone', {x: 0, y: 0, w: 4, h: 4});",
                    "layer.remove('gone');",
                    "let threw = false;",
                    "try { gone.setImage('a:b', 0, 0, 0, 0, 1, 1); }",
                    "  catch (e) { threw = true; }",
                    "results['disposed'] = threw && gone.image === undefined",
                    "    && gone.imageX === undefined && gone.color === undefined;",
                    "neo.__test.report('g034b-image-item',",
                    "    Object.values(results).every(Boolean), JSON.stringify(results));",
                    ""));

            e2e.loadScript(dir.resolve("scripts").resolve("g034b-image-item.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("g034b-image-item");
            assertNotNull(report, "component report missing; scriptErrors="
                    + e2e.scriptErrors());
            assertTrue(report.pass(), "component scenario failed: " + report.detail()
                    + " scriptErrors=" + e2e.scriptErrors());

            var port = e2e.adapter().renderPort();
            assertEquals(List.of("add:g034b-image-item:imgitem:0"), port.events(),
                    "element writes stay inside core: no port traffic at all");

            // The CustomImage constructor uploads its canvas, the snapshot
            // option uploads the decoded PNG once more under a
            // content-addressed id (G-030 → G-034B consumption).
            List<StubAdapter.StubRenderPort.TextureUpload> uploads = port.uploads();
            assertEquals(2, uploads.size(), "canvas upload + snapshot upload: " + uploads);
            assertEquals("neomacros:image/g034b", uploads.get(0).id());
            assertTrue(uploads.get(1).id().startsWith("neomacros:image/hud/"),
                    "content-addressed snapshot id: " + uploads.get(1).id());

            List<StubAdapter.StubRenderPort.Prim> frame = port.fireFrame(0);
            assertEquals(List.of(
                    "sprite:10,20,32,32,a:b@1,2,3,4,80ff0000",
                    "transform:5,6,0,2x2",
                    "item:5,6,minecraft:diamond,5,true,x5",
                    "transform-pop"),
                    frame.stream().map(Draw2DImageItemScenariosTest::describe).toList(),
                    "frame: the tinted image at its spot, the scaled item icon "
                            + "inside its transform scope, removed elements gone");
        }
    }

    /** One primitive as a readable {@code kind:...} line. */
    private static String describe(StubAdapter.StubRenderPort.Prim prim) {
        return switch (prim.kind()) {
            case "sprite" -> "sprite:" + prim.a() + "," + prim.b() + ","
                    + prim.c() + "," + prim.d() + "," + prim.text() + ","
                    + String.format("%08x", prim.color());
            case "item" -> "item:" + prim.a() + "," + prim.b() + ","
                    + prim.text();
            case "transform" -> "transform:" + prim.a() + "," + prim.b() + ","
                    + prim.c() + "," + num(prim.d() / 1000f) + "x"
                    + num(prim.color() / 1000f);
            case "transform-pop" -> "transform-pop";
            default -> prim.kind() + ":" + prim.a() + "," + prim.b() + ","
                    + prim.c() + "," + prim.d() + ","
                    + String.format("%08x", prim.color());
        };
    }

    /** Whole floats print as integers so assertions read naturally. */
    private static String num(float value) {
        return value == Math.floor(value) && !Float.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }
}
