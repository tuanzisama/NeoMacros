package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-036B e2e: the cycle/lock handles, the shared widget face, and the
 * secondary builder family over the stub port — handle state writes
 * round-trip into the control snapshots, transient {@code click}/
 * {@code cycle} patches reach the port, builder specs carry their
 * option lists, build-time value labels and the alternate-list toggle
 * into the neutral form, and the registered callbacks fire from control
 * events.
 */
class ScreenSecondaryControlsScenariosTest {

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

    /** The first recorded neutral control with one id. */
    private static Map<String, Object> addedControl(
            ink.tuanzi.neomacros.e2e.harness.StubAdapter.StubScreen screen,
            String id) {
        for (Map<String, Object> control : screen.addedControls()) {
            if (id.equals(control.get("id"))) {
                return control;
            }
        }
        throw new AssertionError("no added control '" + id + "': "
                + screen.addedControls());
    }

    @Test
    void secondaryHandlesBuildersAndWidgetFaceDriveControlState() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g036b-handles.js", String.join("\n",
                    "let changes = 0;",
                    "let clicks = 0;",
                    "let step = 'open';",
                    "try {",
                    "  const gui = neo.player.buildScreen({",
                    "    title: 'Secondary',",
                    "    controls: [",
                    "      { type: 'cycleButton', id: 'mode', x: 10, y: 40,",
                    "        width: 98, height: 20, values: ['a', 'b', 'c'],",
                    "        initial: 'b', prefix: 'Mode: ' },",
                    "      { type: 'lockButton', id: 'gate', x: 10, y: 70,",
                    "        width: 16, height: 16 },",
                    "      { type: 'button', id: 'buy', x: 10, y: 100, width: 98,",
                    "        height: 20, label: 'Buy' },",
                    "    ],",
                    "  });",
                    "  step = 'handles';",
                    "  const mode = gui.cycleButton('mode');",
                    "  const gate = gui.lockButton('gate');",
                    "  const buy = gui.button('buy');",
                    "  neo.__test.report('handles',",
                    "      mode.value === 'b' && mode.stringValue === 'b'",
                    "          && mode.setValue('c') === false && mode.value === 'c'",
                    "          && mode.setValue('c') === true",
                    "          && gate.locked === false",
                    "          && buy.message === 'Buy' && buy.x === 10",
                    "          && buy.width === 98 && buy.scaledWidth === 98",
                    "          && buy.active === true && buy.zIndex === 0,",
                    "      JSON.stringify({ value: mode.value, locked: gate.locked,",
                    "        message: buy.message, x: buy.x }));",
                    "  step = 'writes';",
                    "  mode.cycle(1);",
                    "  buy.click();",
                    "  gate.setLocked(true);",
                    "  buy.setPos(11, 101);",
                    "  buy.setMessage('Buy!');",
                    "  neo.__test.report('writes',",
                    "      mode.value === 'c' && gate.locked === true",
                    "          && buy.x === 11 && buy.y === 101",
                    "          && buy.message === 'Buy!'",
                    "          && gui.control('buy').x === 11",
                    "          && gui.control('gate').locked === true,",
                    "      JSON.stringify({ x: gui.control('buy').x,",
                    "        locked: gui.control('gate').locked,",
                    "        message: buy.message }));",
                    "  step = 'tooltips';",
                    "  buy.setTooltip(['tip one', 'tip two']);",
                    "  buy.addTooltip('tip three');",
                    "  neo.__test.report('tooltips',",
                    "      buy.tooltips.length === 3 && buy.tooltips[0] === 'tip one'",
                    "          && buy.removeTooltip(1) === true",
                    "          && buy.removeTooltip('tip three') === true",
                    "          && buy.removeTooltip('nope') === false",
                    "          && buy.tooltips.length === 1",
                    "          && buy.tooltips[0] === 'tip one',",
                    "      JSON.stringify(buy.tooltips));",
                    "  step = 'builders';",
                    "  const cyclicSpec = new neo.CyclicButtonBuilder()",
                    "      .id('mode2').pos(10, 130).size(98, 20)",
                    "      .values(['x', 'y']).alternatives(['p', 'q']).initially('y')",
                    "      .option('Mode: ').omitTextOption(true)",
                    "      .valueToText((v) => v.toUpperCase())",
                    "      .alternateToggle(() => true)",
                    "      .alpha(0.5)",
                    "      .onChange(() => { changes++; })",
                    "      .build();",
                    "  gui.add(cyclicSpec);",
                    "  gui.add(new neo.LockButtonBuilder()",
                    "      .id('gate2').pos(10, 160).size(16, 16).locked(true)",
                    "      .onClick(() => { clicks++; })",
                    "      .build());",
                    "  gui.add(new neo.ButtonBuilder()",
                    "      .id('b2').pos(10, 190).size(98, 20)",
                    "      .alpha(0.5).moveTo(12, 192).build());",
                    "  const mode2 = gui.cycleButton('mode2');",
                    "  const gate2 = gui.lockButton('gate2');",
                    "  const b2 = gui.button('b2');",
                    "  let mismatch = false;",
                    "  try { gui.cycleButton('gate'); } catch (e) { mismatch = true; }",
                    "  neo.__test.report('builders',",
                    "      cyclicSpec.type === 'cycleButton'",
                    "          && cyclicSpec.initial === 'y'",
                    "          && cyclicSpec.omitTextOption === true",
                    "          && cyclicSpec.alpha === 0.5",
                    "          && cyclicSpec.valueLabels.x === 'X'",
                    "          && cyclicSpec.alternateToggle !== undefined",
                    "          && mode2.value === 'y' && mode2.stringValue === 'Y'",
                    "          && gate2.locked === true",
                    "          && b2.x === 12 && b2.y === 192",
                    "          && mismatch,",
                    "      JSON.stringify({ type: cyclicSpec.type,",
                    "        labels: cyclicSpec.valueLabels, value: mode2.value,",
                    "        stringValue: mode2.stringValue, gate2: gate2.locked,",
                    "        b2: { x: b2.x, y: b2.y }, mismatch: mismatch }));",
                    "} catch (e) {",
                    "  neo.__test.report('handles', false, step + ' -> ' + String(e)",
                    "      + ' @@ ' + String((e && e.stack) || ''));",
                    "  neo.__test.report('writes', false, 'skipped: ' + step);",
                    "  neo.__test.report('tooltips', false, 'skipped: ' + step);",
                    "  neo.__test.report('builders', false, 'skipped: ' + step);",
                    "}",
                    "let reported = false;",
                    "neo.events.on('tick', () => {",
                    "  if (!reported && changes >= 1 && clicks >= 1) {",
                    "    reported = true;",
                    "    neo.__test.report('callbacks', changes === 1 && clicks === 1,",
                    "        'changes=' + changes + ' clicks=' + clicks);",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("g036b-handles.js"));
            e2e.awaitSettle();
            assertReport(e2e, "handles");
            assertReport(e2e, "writes");
            assertReport(e2e, "tooltips");
            assertReport(e2e, "builders");

            var screen = e2e.adapter().screenPort();

            // Transient patches reached the port: one cycle, one press.
            assertTrue(screen.patches().stream()
                            .anyMatch(patch -> Integer.valueOf(1).equals(patch.get("cycle"))),
                    "cycle patch recorded: " + screen.patches());
            assertTrue(screen.patches().stream()
                            .anyMatch(patch -> Boolean.TRUE.equals(patch.get("click"))),
                    "click patch recorded: " + screen.patches());

            // The builder product crossed as neutral data: option lists,
            // build-time labels, alpha, and the alternateToggle marker.
            Map<String, Object> cyclic = addedControl(screen, "mode2");
            assertEquals(List.of("x", "y"), cyclic.get("values"));
            assertEquals(List.of("p", "q"), cyclic.get("alternatives"));
            assertEquals(Boolean.TRUE, cyclic.get("omitTextOption"));
            assertEquals(0.5, ((Number) cyclic.get("alpha")).doubleValue(), 1e-9);
            assertEquals(Boolean.TRUE, cyclic.get("alternateToggle"),
                    "the toggle crosses as a marker flag, not the function");
            @SuppressWarnings("unchecked")
            Map<String, String> labels =
                    (Map<String, String>) cyclic.get("valueLabels");
            assertEquals("X", labels.get("x"));
            Map<String, Object> lock = addedControl(screen, "gate2");
            assertEquals(Boolean.TRUE, lock.get("locked"));
            Map<String, Object> moved = addedControl(screen, "b2");
            assertEquals(12, moved.get("x"));
            assertEquals(192, moved.get("y"));
            assertEquals(0.5, ((Number) moved.get("alpha")).doubleValue(), 1e-9);

            // The registered list toggle answers through the sink; a
            // literal control without one stays on the default list.
            assertTrue(screen.fireAlternateToggle("mode2"),
                    "the builder-supplied supplier answers");
            assertFalse(screen.fireAlternateToggle("mode"),
                    "no toggle answers the default list");

            // Builder callbacks fire from their control events.
            screen.fireControlEvent("mode2", "change",
                    Map.of("id", "mode2", "type", "cycleButton", "value", "y"));
            screen.fireControlEvent("gate2", "click",
                    Map.of("id", "gate2", "type", "lockButton"));
            e2e.awaitSettle();
            e2e.adapter().lifecyclePort().fireTick(1);
            e2e.awaitSettle();
            assertReport(e2e, "callbacks");
            assertTrue(screen.actions().contains("write:1:buy"),
                    "the widget-face writes reached the port: " + screen.actions());
        }
    }
}
