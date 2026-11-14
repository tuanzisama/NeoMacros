package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.StubAdapter;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.ShapeSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-033 E2E scenarios: a real script driving the retained Draw3D layer
 * (box/line builders, property read-back, live mutation, remove/reAdd,
 * clear, the immediate drawBox primitive) over the stub 3D port — plus the
 * owner namespacing ({@code <module>:<id>}) and the unload auto-clear that
 * release hooks provide.
 */
class Draw3DContainerScenariosTest {

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

    /** Polls the bridge until the scenario report lands or the deadline passes. */
    private static TestBridge.Report awaitReport(E2ERuntime e2e, String key) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            TestBridge.Report report = e2e.bridge().get(key);
            if (report != null) {
                return report;
            }
            Thread.sleep(20);
        }
        return e2e.bridge().get(key);
    }

    @Test
    void draw3dLayerLifecycleThroughTheStubPort() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g033-draw3d.js", String.join("\n",
                    "const results = {};",
                    "const layer = neo.render.layer('g033');",
                    "results['id'] = layer.id === 'g033';",
                    "const box = layer.boxBuilder()",
                    "    .setPos(0, 64, 0, 1, 65, 1)",
                    "    .setColor(0x00FF0000)",
                    "    .setFillColor(0x0000FF00, 128)",
                    "    .setFill(true)",
                    "    .cull(true)",
                    "    .buildAndAdd();",
                    "results['boxProps'] = box.color === 0xFF0000 && box.alpha === 255",
                    "    && box.fillColor === 0x00FF00 && box.fillAlpha === 128",
                    "    && box.filled === true && box.culled === true;",
                    "results['pos'] = box.pos1.x === 0 && box.pos1.y === 64",
                    "    && box.pos1.z === 0 && box.pos2.x === 1",
                    "    && box.pos2.y === 65 && box.pos2.z === 1;",
                    "const line = layer.lineBuilder()",
                    "    .setPos(0, 0, 0, 8, 72, 0)",
                    "    .setColor(0x00FF0000, 200)",
                    "    .setAlwaysOnTop(true)",
                    "    .buildAndAdd();",
                    "results['lineProps'] = line.color === 0xFF0000 && line.alpha === 200",
                    "    && line.alwaysOnTop === true && line.culled === false;",
                    "results['elements'] = layer.elements.length === 2;",
                    "box.setAlpha(64);",
                    "results['alphaWrite'] = box.alpha === 64;",
                    // full-range packed literal lands through the long parameter
                    "line.setColor(0xFF00FF00, 64);",
                    "results['wideColor'] = line.color === 0x00FF00 && line.alpha === 64;",
                    "layer.remove(line);",
                    "results['removed'] = layer.elements.length === 1;",
                    "layer.reAddElement(line);",
                    "results['readd'] = layer.elements.length === 2;",
                    // the immediate primitive shares the layer model but not the element list
                    "neo.render.drawBox('legacy', new neo.Pos3D(0, 64, 0),",
                    "    new neo.Pos3D(1, 65, 1), 0x112233);",
                    "results['legacy'] = layer.elements.length === 2;",
                    "layer.clear();",
                    "results['cleared'] = layer.elements.length === 0;",
                    "neo.__test.report('g033-draw3d',",
                    "    Object.values(results).every(Boolean), JSON.stringify(results));",
                    ""));

            e2e.loadScript(dir.resolve("scripts").resolve("g033-draw3d.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = awaitReport(e2e, "g033-draw3d");
            assertNotNull(report, "draw3d report missing: " + e2e.scriptErrors());
            assertTrue(report.pass(), "draw3d assertions failed: " + report.detail());

            StubAdapter.StubDraw3D draw3d = (StubAdapter.StubDraw3D) e2e.adapter().draw3d();
            List<StubAdapter.StubDraw3D.KeyedShape> keyed = draw3d.keyedShapes();

            // Owner namespacing: every write of this module is prefixed.
            assertTrue(keyed.stream().allMatch(entry -> entry.layerId().startsWith("g033-draw3d:")),
                    "all keyed writes must be owner-namespaced: " + keyed);

            // Box element: created under an element id with the packed colours
            // the script set (zero-alpha rule applied on the way in).
            assertTrue(keyed.stream().anyMatch(entry ->
                    "put".equals(entry.op())
                            && "g033-draw3d:g033".equals(entry.layerId())
                            && entry.shapeId().startsWith("e")
                            && entry.spec() instanceof ShapeSpec.Box box
                            && box.argbColor() == 0xFFFF0000
                            && box.argbFillColor() == 0x8000FF00
                            && box.fill()
                            && box.cull()), "box put missing: " + keyed);
            // The alpha mutation re-puts the same element id with alpha 64.
            assertTrue(keyed.stream().anyMatch(entry ->
                    "put".equals(entry.op())
                            && "g033-draw3d:g033".equals(entry.layerId())
                            && entry.shapeId().startsWith("e")
                            && entry.spec() instanceof ShapeSpec.Box box
                            && box.argbColor() == 0x40FF0000), "box upsert missing");

            // Line element: initial 200-alpha put, remove, and a re-add under
            // a fresh id after the full-range colour write.
            StubAdapter.StubDraw3D.KeyedShape linePut = keyed.stream()
                    .filter(entry -> "put".equals(entry.op())
                            && entry.spec() instanceof ShapeSpec.Line line
                            && line.argbColor() == 0xC8FF0000)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("initial line put missing: " + keyed));
            assertTrue(keyed.stream().anyMatch(entry ->
                    "remove".equals(entry.op())
                            && linePut.shapeId().equals(entry.shapeId())
                            && "g033-draw3d:g033".equals(entry.layerId())),
            "line remove missing: " + keyed);
            assertTrue(keyed.stream().anyMatch(entry ->
                    "put".equals(entry.op())
                            && !entry.shapeId().equals(linePut.shapeId())
                            && entry.spec() instanceof ShapeSpec.Line line
                            && line.argbColor() == 0x4000FF00), "line re-add missing");

            // The immediate primitive lands as a raw-id box on its own layer.
            assertTrue(keyed.stream().anyMatch(entry ->
                    "put".equals(entry.op())
                            && "g033-draw3d:legacy".equals(entry.layerId())
                            && entry.shapeId().startsWith("r")
                            && entry.spec() instanceof ShapeSpec.Box box
                            && box.argbColor() == 0x112233), "legacy drawBox missing");

            // Script-side clear hit the port.
            assertTrue(keyed.stream().anyMatch(entry ->
                    "clear".equals(entry.op())
                            && "g033-draw3d:g033".equals(entry.layerId())),
            "layer clear missing: " + keyed);

            // Unloading the module auto-clears every layer it owns — the
            // legacy layer (never cleared by the script) proves the hook.
            e2e.modules().unload("g033-draw3d");
            assertTrue(draw3d.keyedShapes().stream().anyMatch(entry ->
                    "clear".equals(entry.op())
                            && "g033-draw3d:legacy".equals(entry.layerId())),
            "unload must clear owned layers");
        }
    }
}
