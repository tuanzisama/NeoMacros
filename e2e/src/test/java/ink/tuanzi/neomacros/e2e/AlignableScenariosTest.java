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
 * E2E-A scenarios for the G-034C alignment face: a real script through the
 * real engine drives parent-relative and sibling-relative alignment, the
 * single-axis moves, the derived scaled edges, the malformed-token throw
 * and the disposed-handle throw — all over the stub render port, with the
 * element writes proven to stay inside core (no port traffic).
 */
class AlignableScenariosTest {

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
    void alignmentFaceThroughTheStubRenderPort() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g034c-align.js", String.join("\n",
                    "const results = {};",
                    "const layer = neo.hud.layer('align');",
                    "const panel = layer.rect('panel', {x: 100, y: 40, w: 200, h: 100});",
                    "const label = layer.rect('label', {x: 0, y: 0, w: 40, h: 20});",
                    // Parent-relative: center and bottom inside the 640x360 viewport.
                    "label.alignHorizontally('center');",
                    "results['parent-center'] = label.x === 300 && label.y === 0;",
                    "label.alignVertically('bottom');",
                    "results['parent-bottom'] = label.y === 340 && label.x === 300;",
                    // Sibling-relative: the label's left edge on the panel's right edge.",
                    "label.alignHorizontally(panel, 'leftOnRight');",
                    "results['sibling'] = label.x === panel.scaledLeft + panel.scaledWidth",
                    "    && label.x === 300;",
                    // Both axes in one call; middle is a center synonym on the vertical axis.",
                    "label.align(panel, 'center', 'middle');",
                    "results['both'] = label.x === 180 && label.y === 80;",
                    // Single-axis moves keep the other coordinate in its raw form.",
                    "label.moveToX(10);",
                    "results['moveto-x'] = label.x === 10 && label.y === 80;",
                    "label.moveToY(15);",
                    "results['moveto-y'] = label.x === 10 && label.y === 15;",
                    // Derived scaled edges read the resolved box.",
                    "results['edges'] = label.scaledRight === 50 && label.scaledBottom === 35;",
                    // Unsupported parent tokens do nothing; malformed siblings throw.",
                    "label.alignHorizontally('diagonal');",
                    "results['noop'] = label.x === 10;",
                    "let malformed = false;",
                    "try { label.alignHorizontally(panel, 'leftOn'); } catch (e) { malformed = true; }",
                    "results['malformed'] = malformed;",
                    // Disposal: the align face throws and derived edges read undefined.",
                    "const gone = layer.text('gone', 'x', {});",
                    "layer.remove('gone');",
                    "let threw = false;",
                    "try { gone.alignHorizontally('left'); } catch (e) { threw = true; }",
                    "results['disposed'] = threw && gone.scaledRight === undefined;",
                    "neo.__test.report('g034c-align',",
                    "    Object.values(results).every(Boolean), JSON.stringify(results));",
                    ""));

            e2e.loadScript(dir.resolve("scripts").resolve("g034c-align.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("g034c-align");
            assertNotNull(report, "align report missing; scriptErrors="
                    + e2e.scriptErrors());
            assertTrue(report.pass(), "align scenario failed: " + report.detail()
                    + " scriptErrors=" + e2e.scriptErrors());

            var port = e2e.adapter().renderPort();
            assertEquals(List.of("add:g034c-align:align:0"), port.events(),
                    "alignment writes stay inside core: no port traffic at all");

            List<StubAdapter.StubRenderPort.Prim> frame = port.fireFrame(0);
            assertEquals(List.of(
                    "rect:100,40,200,100,ffffffff",
                    "rect:10,15,40,20,ffffffff"),
                    frame.stream().map(AlignableScenariosTest::describe).toList(),
                    "frame order: panel first (insertion), the aligned label at its final spot");
        }
    }

    /** One primitive as a readable {@code kind:a,b,c,d,color} line. */
    private static String describe(StubAdapter.StubRenderPort.Prim prim) {
        return prim.kind() + ":" + prim.a() + "," + prim.b() + ","
                + prim.c() + "," + prim.d() + ","
                + String.format("%08x", prim.color());
    }
}
