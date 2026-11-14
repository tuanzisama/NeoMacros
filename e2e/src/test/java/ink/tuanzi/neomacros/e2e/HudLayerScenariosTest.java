package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
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
 * E2E-A scenarios for the G-032 Draw2D container: a real script through
 * the real engine drives {@code neo.hud.layer} over the stub render port —
 * owner-qualified registration, element upsert/removal identity, z-ordered
 * frame replay ({@code StubRenderPort.fireFrame}), the frozen
 * {@code addText}/{@code removeText} Promise face on the {@code $legacy}
 * layer, and module-unload auto-clear (拍板③).
 */
class HudLayerScenariosTest {

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
    void layerLifecycleThroughTheStubRenderPort() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g032-hud-layer.js", String.join("\n",
                    "const results = {};",
                    "const a = neo.hud.layer('main');",
                    "const b = neo.hud.layer('overlay', {z: 10});",
                    // Idempotency without relying on guest wrapper identity
                    // (every crossing mints a fresh JS instance): mutate
                    // through the second handle, read through the first.
                    "const again = neo.hud.layer('main');",
                    "again.setZIndex(3);",
                    "results['idempotent'] = a.zIndex === 3;",
                    "again.setZIndex(0);",
                    "results['face'] = a.id === 'main' && a.zIndex === 0",
                    "    && b.zIndex === 10 && a.width === 640 && a.height === 360",
                    "    && a.disposed === false && a.visible === true;",
                    "const h1 = a.text('hello', 'hi', {x: 1, y: 2, color: 0xFF00FF00});",
                    "a.text('hello2', 'later', {x: 3, y: 4, zIndex: 5});",
                    "a.rect('box', {x: 10, y: 10, w: 5, h: 5, color: 'red'});",
                    "a.text('hello', 'hi2', {x: 1, y: 2});",
                    "a.text('hello2', 'later2', {x: 3, y: 4, zIndex: 5});",
                    // Same id + same kind keeps the original handle alive (a
                    // kind change would have disposed it instead).
                    "results['upsert'] = h1.disposed === false && h1.id === 'hello';",
                    "results['idsBefore'] = a.ids.length === 3 && a.ids[0] === 'hello'",
                    "    && a.ids[1] === 'box' && a.ids[2] === 'hello2'",
                    "    && a.elements.length === 3;",
                    "const el = a.element('hello');",
                    "results['handle'] = el !== undefined && el.id === 'hello'",
                    "    && el.disposed === false;",
                    "a.remove('hello');",
                    "results['removed'] = a.element('hello') === undefined",
                    "    && el.disposed === true && a.ids.length === 2;",
                    "let reserved = false;",
                    "try { neo.hud.layer('$nope'); } catch (e) { reserved = true; }",
                    "results['reserved'] = reserved;",
                    "results['layers'] = neo.hud.layers.length === 2;",
                    "neo.hud.addText('greet', 'hello world', 5, 6, 0xFFFFFFFF).then(() => {",
                    "  results['addPromise'] = true;",
                    "  return neo.hud.removeText('ghost');",
                    "}).then(() => {",
                    "  results['removePromise'] = true;",
                    "  results['layersStay'] = neo.hud.layers.length === 2;",
                    "  neo.__test.report('g032-hud-layer',",
                    "      Object.values(results).every(Boolean), JSON.stringify(results));",
                    "});",
                    ""));

            e2e.loadScript(dir.resolve("scripts").resolve("g032-hud-layer.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("g032-hud-layer");
            assertNotNull(report, "hud layer report missing; scriptErrors="
                    + e2e.scriptErrors());
            assertTrue(report.pass(), "hud layer scenario failed: " + report.detail()
                    + " scriptErrors=" + e2e.scriptErrors());

            var port = e2e.adapter().renderPort();
            String owner = "g032-hud-layer";
            assertEquals(List.of(
                    "add:" + owner + ":main:0",
                    "add:" + owner + ":overlay:10",
                    "setz:" + owner + ":main:3",
                    "setz:" + owner + ":main:0",
                    "add:" + owner + ":$legacy:0"),
                    port.events(),
                    "layers register under the owner namespace; the idempotency "
                            + "probe schedules exactly two setLayerZ calls");

            List<ink.tuanzi.neomacros.e2e.harness.StubAdapter.StubRenderPort.Prim> frame =
                    port.fireFrame(0);
            assertEquals(List.of(
                    "text:5,6,hello world,ffffffff",
                    "rect:10,10,5,5,ffff5555",
                    "text:3,4,later2,ffffffff"),
                    frame.stream().map(HudLayerScenariosTest::describe).toList(),
                    "frame order: $legacy (z0, '$' < name), main's z0 rect then z5 text, "
                            + "overlay empty; the upserted text shows its new value");

            assertEquals(List.of(owner + ":$legacy", owner + ":main", owner + ":overlay"),
                    port.registeredLayers(), "render order follows (z, id)");

            // 拍板③: unloading the module disposes every layer it owns.
            e2e.modules().unload(owner);
            assertEquals(List.of(
                    "add:" + owner + ":main:0",
                    "add:" + owner + ":overlay:10",
                    "setz:" + owner + ":main:3",
                    "setz:" + owner + ":main:0",
                    "add:" + owner + ":$legacy:0",
                    "remove:" + owner + ":main",
                    "remove:" + owner + ":overlay",
                    "remove:" + owner + ":$legacy"),
                    port.events(), "module unload auto-clears the owned layers");
            assertEquals(List.of(), port.registeredLayers());
        }
    }

    /** One primitive as a readable {@code kind:a,b,c,d,color,text} line. */
    private static String describe(
            ink.tuanzi.neomacros.e2e.harness.StubAdapter.StubRenderPort.Prim prim) {
        return prim.kind() + ":" + prim.a() + "," + prim.b() + ","
                + (prim.kind().equals("text") ? prim.text() + "," : "")
                + (prim.kind().equals("rect") ? prim.c() + "," + prim.d() + "," : "")
                + String.format("%08x", prim.color());
    }
}
