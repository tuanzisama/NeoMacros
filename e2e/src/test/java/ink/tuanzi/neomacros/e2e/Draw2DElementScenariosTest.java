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
 * E2E-A scenarios for the G-032B Draw2DElement base face: a real script
 * through the real engine drives the fluent element setters over the stub
 * render port — fluent chaining (拍板②), geometry getters (raw / bounds /
 * scaled / parent), back references, percentage round-trip, visibility,
 * the disposed-handle throw, and element-level z reordering that must stay
 * inside core (no port traffic).
 */
class Draw2DElementScenariosTest {

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
    void elementFaceThroughTheStubRenderPort() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g032b-element.js", String.join("\n",
                    "const results = {};",
                    "const layer = neo.hud.layer('elem');",
                    "const el = layer.text('t', 'hi', {x: 1, y: 2});",
                    // Fluent setters return the element itself (拍板②: the
                    // handle is its own builder).
                    "el.setX(10).setY(20).setPos(30, 40).setZIndex(2);",
                    "results['fluent'] = el.x === 30 && el.y === 40 && el.zIndex === 2;",
                    "el.setWidth(100).setHeight(50).setScale(2).setRotation(90)",
                    "    .setRotateCenter(true);",
                    "results['state'] = el.width === 100 && el.height === 50",
                    "    && el.scale === 2 && el.rotation === 90",
                    "    && el.rotateCenter === true && el.visible === true;",
                    // getDraw2D() lands as .layer; v1 parent box is the layer.
                    "results['refs'] = el.layer !== undefined && el.layer.id === 'elem'",
                    "    && el.parent !== undefined && el.parent.id === 'elem'",
                    "    && el.id === 't' && el.disposed === false;",
                    // Bounds and scaled geometry: 100x50 at scale 2 from (30, 40).
                    "results['bounds'] = el.bounds.x === 30 && el.bounds.y === 40",
                    "    && el.bounds.width === 200 && el.bounds.height === 100;",
                    "results['scaled'] = el.scaledWidth === 200",
                    "    && el.scaledHeight === 100 && el.scaledLeft === 30",
                    "    && el.scaledTop === 40 && el.parentWidth === 640",
                    "    && el.parentHeight === 360;",
                    // Percentage coordinates round-trip as strings and resolve.
                    "el.setX('50%');",
                    "results['percent'] = el.x === '50%' && el.bounds.x === 320;",
                    "el.setX(30);",
                    "el.setVisible(false);",
                    "results['hidden'] = el.visible === false;",
                    "el.setVisible(true);",
                    // A second element at z0 observes the element z from Java.
                    "layer.text('t2', 'other', {x: 0, y: 0});",
                    // Disposal: reads become undefined, setters throw.
                    "const gone = layer.text('gone', 'x', {});",
                    "layer.remove('gone');",
                    "let threw = false;",
                    "try { gone.setX(1); } catch (e) { threw = true; }",
                    "results['disposed'] = threw && gone.disposed === true",
                    "    && gone.x === undefined && gone.id === undefined;",
                    "neo.__test.report('g032b-element',",
                    "    Object.values(results).every(Boolean), JSON.stringify(results));",
                    ""));

            e2e.loadScript(dir.resolve("scripts").resolve("g032b-element.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("g032b-element");
            assertNotNull(report, "element report missing; scriptErrors="
                    + e2e.scriptErrors());
            assertTrue(report.pass(), "element scenario failed: " + report.detail()
                    + " scriptErrors=" + e2e.scriptErrors());

            var port = e2e.adapter().renderPort();
            String owner = "g032b-element";
            assertEquals(List.of("add:" + owner + ":elem:0"), port.events(),
                    "element z changes stay inside core: no port traffic at all");

            List<StubAdapter.StubRenderPort.Prim> frame = port.fireFrame(0);
            assertEquals(List.of(
                    "text:0,0,other,ffffffff",
                    "text:30,40,hi,ffffffff"),
                    frame.stream().map(Draw2DElementScenariosTest::describe).toList(),
                    "frame order: the z0 element first, the fluent-moved z2 "
                            + "text at its new position, the removed one gone");
        }
    }

    /** One primitive as a readable {@code kind:a,b,c,d,color,text} line. */
    private static String describe(StubAdapter.StubRenderPort.Prim prim) {
        return prim.kind() + ":" + prim.a() + "," + prim.b() + ","
                + (prim.kind().equals("text") ? prim.text() + "," : "")
                + (prim.kind().equals("rect") ? prim.c() + "," + prim.d() + "," : "")
                + String.format("%08x", prim.color());
    }
}
