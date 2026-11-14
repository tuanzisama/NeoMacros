package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.ScreenPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-035 e2e: the script-built screen vertical slice over the stub port —
 * the open failure path that must throw a catchable script error without
 * crashing (the acceptance's "打不开也不崩"), the control/render/close
 * callback chains end to end, the {@code screen:change} script kind, and
 * the owner-unload disposer closing the session.
 */
class ScriptScreenScenariosTest {

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

    /** Asserts one reported key passed, with the script detail on failure. */
    private static void assertReport(E2ERuntime e2e, String key) throws Exception {
        TestBridge.Report report = awaitReport(e2e, key);
        assertTrue(report != null && report.pass(),
                key + " report: " + (report == null ? "missing" : report.detail())
                        + " | script errors: " + e2e.scriptErrors());
    }

    @Test
    void openFailureThrowsCatchableAndTheScriptKeepsRunning() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            e2e.adapter().screenPort().failOpen = true;
            fixture(dir.resolve("scripts"), "g035-fail-open.js", String.join("\n",
                    "let caught = false;",
                    "let detail = '';",
                    "try {",
                    "  neo.player.buildScreen({ title: 'Doomed' });",
                    "} catch (e) {",
                    "  caught = true;",
                    "  detail = String(e && e.message || e);",
                    "}",
                    "neo.__test.report('failOpen', caught, detail);",
                    "// The script survives the failure and keeps evaluating.",
                    "neo.__test.report('survived', true, 'after the failed open');",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("g035-fail-open.js"));
            e2e.awaitSettle();

            assertReport(e2e, "failOpen");
            assertReport(e2e, "survived");
            assertFalse(e2e.scriptErrors().contains("Exception"),
                    "script errors: " + e2e.scriptErrors());
            assertTrue(e2e.adapter().screenPort().actions().contains("open"));
        }
    }

    @Test
    void callbackChainsDriveThroughTheSessionSink() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g035-callbacks.js", String.join("\n",
                    "let clicks = 0;",
                    "let renders = 0;",
                    "let closes = 0;",
                    "let reportedMid = false;",
                    "let reportedClosed = false;",
                    "let gui;",
                    "let step = 'start';",
                    "try {",
                    "  step = 'build';",
                    "  gui = neo.player.buildScreen({",
                    "    title: 'Shop',",
                    "    controls: [",
                    "      { type: 'button', id: 'buy', x: 10, y: 40, width: 98, height: 20,",
                    "        label: 'Buy', onClick: (e) => { clicks++; } },",
                    "      { type: 'checkbox', id: 'dbg', x: 10, y: 70, width: 98, height: 20,",
                    "        label: 'Debug', checked: true },",
                    "    ],",
                    "    onClose: () => { closes++; },",
                    "    onRender: (e) => { renders++; },",
                    "  });",
                    "  step = 'on';",
                    "  gui.on('mouseDown', (e) => true);",
                    "  step = 'report';",
                    "  neo.__test.report('build',",
                    "      gui !== undefined && gui.title === 'Shop'",
                    "          && gui.name === 'ScriptScreen' && gui.closed === false",
                    "          && gui.returnTo === 'current'",
                    "          && gui.controls.length === 2",
                    "          && gui.control('dbg').checked === true",
                    "          && gui.control('buy').label === 'Buy',",
                    "      JSON.stringify({ title: gui.title, name: gui.name,",
                    "        controls: gui.controls.length }));",
                    "} catch (e) {",
                    "  neo.__test.report('build', false, step + ' -> ' + String(e)",
                    "      + ' @@ ' + String((e && e.stack) || ''));",
                    "}",
                    "neo.events.on('tick', () => {",
                    "  if (!gui) return;",
                    "  if (!reportedMid && clicks === 1 && renders === 1) {",
                    "    reportedMid = true;",
                    "    neo.__test.report('mid', gui.control('buy') !== undefined,",
                    "        JSON.stringify({ clicks, renders }));",
                    "  }",
                    "  if (!reportedClosed && gui.closed) {",
                    "    reportedClosed = true;",
                    "    let addThrew = false;",
                    "    try { gui.add({ type: 'button', id: 'late' }); }",
                    "    catch (e) { addThrew = true; }",
                    "    neo.__test.report('closed',",
                    "        closes === 1 && addThrew && gui.controls.length === 0,",
                    "        JSON.stringify({ closes, addThrew }));",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("g035-callbacks.js"));
            e2e.awaitSettle();
            assertReport(e2e, "build");

            var screen = e2e.adapter().screenPort();
            screen.fireControlEvent("buy", "click", Map.of("id", "buy", "type", "button"));
            screen.fireRender(1.0, 2.0, 0.5f);
            assertTrue(screen.fireMouseDown(new ScreenPort.MouseInput(
                    0, 1, 2, 0, 0, List.of(), null)),
                    "the on('mouseDown') handler returned true (consumed)");
            e2e.awaitSettle();

            e2e.adapter().lifecyclePort().fireTick(1);
            e2e.awaitSettle();
            assertReport(e2e, "mid");

            screen.fireClosed();
            e2e.adapter().lifecyclePort().fireTick(2);
            e2e.awaitSettle();
            assertReport(e2e, "closed");
            // fireClosed models the replacement path: the session ends with
            // no close-intent port call (design §6.1 path 3), so the action
            // log stays at the bare open.
            assertTrue(screen.actions().stream().noneMatch(a -> a.startsWith("close:")),
                    "replacement drove no close intent: " + screen.actions());
        }
    }

    @Test
    void screenChangeEventCarriesTheScriptKind() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g035-screen-kind.js", String.join("\n",
                    "neo.events.on('screen:change', (ev) => {",
                    "  neo.__test.report('kind',",
                    "      ev.kind === 'script' && ev.screen === 'ScriptScreen'",
                    "          && ev.title === 'Shop',",
                    "      JSON.stringify({ kind: ev.kind, screen: ev.screen,",
                    "        title: ev.title, phase: ev.phase }));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("g035-screen-kind.js"));
            e2e.awaitSettle();

            // The screen diff runs inside the per-tick player poll, so a
            // player snapshot must exist first.
            e2e.adapter().playerPort().setSelf(new ink.tuanzi.neomacros.port.model
                    .PlayerSnapshot(java.util.UUID.randomUUID(), "tester",
                    new ink.tuanzi.neomacros.port.model.Pos3d(0, 64, 0),
                    new ink.tuanzi.neomacros.port.model.Rot2d(0, 0), 20.0, 20, 300));
            e2e.adapter().lifecyclePort().setScreen(Optional.of(
                    new LifecyclePort.ScreenInfo("ScriptScreen", "script", "Shop")));
            e2e.adapter().lifecyclePort().fireTick(1);
            e2e.awaitSettle();

            assertReport(e2e, "kind");
        }
    }

    @Test
    void ownerUnloadClosesTheOpenSession() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g035-unload.js", String.join("\n",
                    "const gui = neo.player.buildScreen({",
                    "  title: 'Temp',",
                    "  onClose: () => {",
                    "    neo.__test.report('unload', true, 'close callback during unload');",
                    "  },",
                    "});",
                    "neo.__test.report('opened', gui !== undefined && !gui.closed,",
                    "    String(gui && gui.closed));",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("g035-unload.js"));
            e2e.awaitSettle();
            assertReport(e2e, "opened");
            assertTrue(e2e.adapter().screenPort().actions().contains("open"));

            // Unloading the owner runs the release hook that closes the session.
            e2e.modules().close();
            assertReport(e2e, "unload");
            assertTrue(e2e.adapter().screenPort().actions().contains("close:1"),
                    "unload drove the port close: " + e2e.adapter().screenPort().actions());
        }
    }
}
