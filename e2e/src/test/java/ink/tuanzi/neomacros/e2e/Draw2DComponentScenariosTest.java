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
 * E2E-A scenarios for the G-034 component faces: a real script through the
 * real engine drives {@code Draw2DText}/{@code Draw2DRect}/{@code Draw2DLine}
 * — subclass upserts, text metrics, shadow, corner/endpoint views, the
 * folded color forms, the stroke face, the fluent chains and the disposed
 * throw — over the stub render port, with the paint primitives (shadow
 * text, line, transform scope) and the zero-port-traffic promise asserted
 * on the fired frame.
 */
class Draw2DComponentScenariosTest {

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
    void componentFacesThroughTheStubRenderPort() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g034-components.js", String.join("\n",
                    "const results = {};",
                    "const layer = neo.hud.layer('comp');",
                    // Text: metrics, shadow, the folded color face, fluent set.
                    "const t = layer.text('t', 'hello', {x: 5, y: 6, color: '#f00', shadow: true});",
                    "results['text-shape'] = typeof t.setText === 'function'",
                    "    && typeof t.hasShadow === 'function'",
                    "    && typeof t.setColor === 'function'",
                    "    && typeof t.text === 'string';",
                    "results['text-state'] = t.text === 'hello'",
                    "    && t.hasShadow() === true",
                    "    && t.width === 30 && t.height === 8",
                    "    && (t.color >>> 0) === 0xFFFF0000;",
                    "t.setText('hi').setShadow(false).setColor(0x00FF0000, 0x80);",
                    "results['text-set'] = t.text === 'hi' && t.width === 12",
                    "    && t.hasShadow() === false",
                    "    && (t.color >>> 0) === 0x80FF0000;",
                    // Rect: the two-corner view, resize, corner-order flip.
                    "const r = layer.rect('r', {x: 10, y: 10, w: 40, h: 20,",
                    "    color: 'red', rotation: 45});",
                    "results['rect'] = r.x1 === 10 && r.y1 === 10",
                    "    && r.x2 === 50 && r.y2 === 30",
                    "    && r.width === 40 && r.height === 20",
                    "    && (r.color >>> 0) === 0xFFFF5555;",
                    "r.setX2(70);",
                    "results['rect-x2'] = r.x2 === 70 && r.width === 60;",
                    "r.setPos1(90, 10);",
                    "results['rect-flip'] = r.x1 === 90 && r.x2 === 70",
                    "    && r.width === 20 && r.x === 70;",
                    // Line: reversed endpoints, extent, stroke, alpha.
                    "const l = layer.line('l', {x1: 100, y1: 50, x2: 40, y2: 80,",
                    "    color: '#0f0', width: 3});",
                    "results['line'] = l.x1 === 100 && l.y1 === 50",
                    "    && l.x2 === 40 && l.y2 === 80",
                    "    && l.strokeWidth === 3 && l.width === 60",
                    "    && l.height === 30 && l.scaledLeft === 40",
                    "    && l.scaledTop === 50",
                    "    && (l.color >>> 0) === 0xFF00FF00;",
                    "l.setAlpha(64);",
                    "results['line-alpha'] = l.alpha === 64 && (l.color >>> 24) === 64;",
                    "l.setStrokeWidth(2);",
                    "results['line-stroke'] = l.strokeWidth === 2;",
                    // Disposal: the component mutators throw, reads read undefined.
                    "layer.remove('l');",
                    "let threw = false;",
                    "try { l.setStrokeWidth(1); } catch (e) { threw = true; }",
                    "results['disposed'] = threw && l.strokeWidth === undefined",
                    "    && l.x1 === undefined;",
                    "neo.__test.report('g034-components',",
                    "    Object.values(results).every(Boolean), JSON.stringify(results));",
                    ""));

            e2e.loadScript(dir.resolve("scripts").resolve("g034-components.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("g034-components");
            assertNotNull(report, "component report missing; scriptErrors="
                    + e2e.scriptErrors());
            assertTrue(report.pass(), "component scenario failed: " + report.detail()
                    + " scriptErrors=" + e2e.scriptErrors());

            var port = e2e.adapter().renderPort();
            assertEquals(List.of("add:g034-components:comp:0"), port.events(),
                    "element writes stay inside core: no port traffic at all");

            List<StubAdapter.StubRenderPort.Prim> frame = port.fireFrame(0);
            assertEquals(List.of(
                    "text:5,6,hi,80ff0000",
                    // The rect kept its rotation: one transform scope about
                    // its min corner after the corner edits.
                    "transform:70,10,45,1x1",
                    "rect:70,10,20,20,ffff5555",
                    "transform-pop"),
                    frame.stream().map(Draw2DComponentScenariosTest::describe).toList(),
                    "frame: the fluent text at its spot, the rotated rect "
                            + "inside its transform scope, the removed line gone");
        }
    }

    /** One primitive as a readable {@code kind:...} line. */
    private static String describe(StubAdapter.StubRenderPort.Prim prim) {
        return switch (prim.kind()) {
            case "text", "text-shadow" -> prim.kind() + ":" + prim.a() + ","
                    + prim.b() + "," + prim.text() + ","
                    + String.format("%08x", prim.color());
            case "rect" -> "rect:" + prim.a() + "," + prim.b() + ","
                    + prim.c() + "," + prim.d() + ","
                    + String.format("%08x", prim.color());
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
