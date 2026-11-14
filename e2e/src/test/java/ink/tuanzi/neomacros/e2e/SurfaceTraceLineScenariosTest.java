package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.StubAdapter;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ShapeSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-033B E2E scenarios: a real script driving TraceLine,
 * EntityTraceLine (entity binding by UUID) and Surface over the stub 3D
 * port — builder chains, property read-back, the quick forms
 * ({@code addTraceLine}/{@code addEntityTraceLine}/{@code addDraw2D}),
 * {@code addSurface}, {@code removeTraceLine}/{@code removeDraw2D} — plus
 * Java-side assertions on the keyed specs the port received.
 */
class SurfaceTraceLineScenariosTest {

    @TempDir
    Path dir;

    /** One living zombie snapshot for the entity-binding path. */
    private static final UUID ZOMBIE =
            UUID.fromString("8f1d0f2a-6c4b-4a5e-9d3c-7b2e1a9c0d11");

    private static EntitySnapshot zombie() {
        return new EntitySnapshot(ZOMBIE, "minecraft:zombie", "Zombie",
                new Pos3d(0, 64, 0), 20);
    }

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
    void surfaceAndTraceLineLifecycleThroughTheStubPort() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g033b-surface.js", String.join("\n",
                    "const results = {};",
                    "const layer = neo.render.layer('g033b');",
                    // TraceLine: target pos, colour/alpha, always-on-top.
                    "const trace = layer.traceLineBuilder()",
                    "    .setPos(new neo.Pos3D(1, 64, 2))",
                    "    .setColor(0x00FF0000, 200)",
                    "    .setAlwaysOnTop(true)",
                    "    .buildAndAdd();",
                    "results['traceProps'] = trace.pos.x === 1 && trace.pos.y === 64",
                    "    && trace.pos.z === 2 && trace.color === 0xFF0000",
                    "    && trace.alpha === 200 && trace.alwaysOnTop === true;",
                    // EntityTraceLine: bind by UUID, offset, colour, depth bucket.",
                    "const e = neo.entity.lookup('" + ZOMBIE + "');",
                    "const entityLine = layer.entityTraceLineBuilder();",
                    "entityLine.setEntity(e);",
                    "entityLine.setYOffset(2.5);",
                    "entityLine.setColor(0x0000FF00, 64);",
                    "entityLine.setAlwaysOnTop(false);",
                    "entityLine.buildAndAdd();",
                    "results['entityProps'] = entityLine.entity.uuid === '" + ZOMBIE + "'",
                    "    && entityLine.yOffset === 2.5 && entityLine.color === 0x00FF00",
                    "    && entityLine.alpha === 64 && entityLine.alwaysOnTop === false;",
                    // Surface: placement, size, subdivisions, flags, pixel math.
                    "const panel = layer.surfaceBuilder()",
                    "    .setPos(new neo.Pos3D(0.5, 64, 0.5))",
                    "    .setSizes(2, 1)",
                    "    .setMinSubdivisions(100)",
                    "    .cull(true)",
                    "    .renderBack(true)",
                    "    .buildAndAdd();",
                    "results['panelProps'] = panel.pos.x === 0.5 && panel.sizes.x === 2",
                    "    && panel.sizes.y === 1 && panel.minSubdivisions === 100",
                    "    && panel.culled === true && panel.shouldRenderBack() === true",
                    "    && panel.rotatingCenter === true && panel.zIndex === 0;",
                    "results['panelPixels'] = panel.width === 200 && panel.height === 100;",
                    // Quick forms with their JsMacros defaults.
                    "const quickTrace = layer.addTraceLine(new neo.Pos3D(4, 64, 4),",
                    "    0x00FF0000, 128);",
                    "results['quickTrace'] = quickTrace.pos.x === 4",
                    "    && quickTrace.color === 0xFF0000 && quickTrace.alpha === 128",
                    "    && quickTrace.alwaysOnTop === true;",
                    "const quickEntity = layer.addEntityTraceLine(e, 0x0000FF00, 32, 3.0, false);",
                    "results['quickEntity'] = quickEntity.yOffset === 3.0",
                    "    && quickEntity.alpha === 32 && quickEntity.alwaysOnTop === false;",
                    "const quickPanel = layer.addDraw2D(8, 64, 8);",
                    "results['quickPanel'] = quickPanel.minSubdivisions === 200",
                    "    && quickPanel.sizes.x === 1 && quickPanel.width === 200",
                    "    && quickPanel.culled === false && quickPanel.shouldRenderBack() === false;",
                    // Element bookkeeping: addSurface / removeTraceLine / removeDraw2D.
                    "results['elements'] = layer.elements.length === 6;",
                    "layer.removeTraceLine(quickTrace);",
                    "results['removeTrace'] = layer.elements.length === 5;",
                    "const detached = layer.surfaceBuilder().build();",
                    "layer.addSurface(detached);",
                    "results['addSurface'] = layer.elements.length === 6;",
                    "layer.removeDraw2D(detached);",
                    "results['removeDraw2D'] = layer.elements.length === 5;",
                    "neo.__test.report('g033b-surface',",
                    "    Object.values(results).every(Boolean), JSON.stringify(results));",
                    ""));

            // The top-level script runs during loadScript, and
            // neo.entity.lookup resolves through the entity port — seed
            // the zombie first.
            e2e.adapter().entityPort().setEntities(List.of(zombie()));
            e2e.loadScript(dir.resolve("scripts").resolve("g033b-surface.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = awaitReport(e2e, "g033b-surface");
            assertNotNull(report, "g033b report missing: " + e2e.scriptErrors());
            assertTrue(report.pass(), "g033b assertions failed: " + report.detail());

            StubAdapter.StubDraw3D draw3d = (StubAdapter.StubDraw3D) e2e.adapter().draw3d();
            List<StubAdapter.StubDraw3D.KeyedShape> keyed = draw3d.keyedShapes();

            // Owner namespacing: every write of this module is prefixed.
            assertTrue(keyed.stream().allMatch(entry -> entry.layerId().startsWith("g033b-surface:")),
                    "all keyed writes must be owner-namespaced: " + keyed);

            // TraceLine element: camera trace toward the scripted target.
            assertTrue(keyed.stream().anyMatch(entry ->
                    "put".equals(entry.op())
                            && "g033b-surface:g033b".equals(entry.layerId())
                            && entry.shapeId().startsWith("e")
                            && entry.spec() instanceof ShapeSpec.CameraTrace trace
                            && trace.target().equals(new Pos3d(1, 64, 2))
                            && trace.argbColor() == 0xC8FF0000
                            && !trace.cull()), "camera trace put missing: " + keyed);

            // Entity binding: the EntityTrace carries the UUID and offset.
            assertTrue(keyed.stream().anyMatch(entry ->
                    "put".equals(entry.op())
                            && entry.spec() instanceof ShapeSpec.EntityTrace trace
                            && ZOMBIE.equals(trace.entityUuid())
                            && trace.yOffset() == 2.5
                            && trace.argbColor() == 0x4000FF00
                            && trace.cull()), "entity trace put missing: " + keyed);
            // The quick form lands its own entity trace (offset 3, alpha
            // 32, alwaysOnTop=false -> depth tested).
            assertTrue(keyed.stream().anyMatch(entry ->
                    "put".equals(entry.op())
                            && entry.spec() instanceof ShapeSpec.EntityTrace trace
                            && ZOMBIE.equals(trace.entityUuid())
                            && trace.yOffset() == 3.0
                            && trace.argbColor() == 0x2000FF00
                            && trace.cull()), "quick entity trace put missing: " + keyed);

            // Surface panel: placement, geometry and render flags.
            assertTrue(keyed.stream().anyMatch(entry ->
                    "put".equals(entry.op())
                            && entry.spec() instanceof ShapeSpec.Panel panel
                            && panel.origin().equals(new Pos3d(0.5, 64, 0.5))
                            && panel.width() == 2.0
                            && panel.height() == 1.0
                            && panel.rotateCenter()
                            && panel.renderBack()
                            && panel.cull()
                            && panel.boundEntity() == null),
                    "surface panel put missing: " + keyed);
            // The addDraw2D quick form keeps its own defaults (1x1, corner,
            // no back face, no depth culling).
            assertTrue(keyed.stream().anyMatch(entry ->
                    "put".equals(entry.op())
                            && entry.spec() instanceof ShapeSpec.Panel panel
                            && panel.origin().equals(new Pos3d(8, 64, 8))
                            && panel.width() == 1.0
                            && !panel.rotateCenter()
                            && !panel.renderBack()
                            && !panel.cull()),
                    "quick panel put missing: " + keyed);

            // removeTraceLine / removeDraw2D hit the port as element removes.
            assertTrue(keyed.stream().anyMatch(entry ->
                    "remove".equals(entry.op())
                            && "g033b-surface:g033b".equals(entry.layerId())),
                    "element remove missing: " + keyed);
        }
    }
}
