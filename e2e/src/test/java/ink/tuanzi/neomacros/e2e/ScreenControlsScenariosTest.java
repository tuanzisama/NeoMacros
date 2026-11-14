package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-036 e2e: the four basic control handles and the builder family over
 * the stub port — typed-handle state writes round-trip into the control
 * snapshots, builder specs lay out and carry their callbacks into
 * {@code gui.add}, the edit-box text filter answers through the session
 * sink, and a failed open with builders in flight stays a catchable
 * script error (the acceptance's "打不开也不崩").
 */
class ScreenControlsScenariosTest {

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
    void typedHandlesAndBuildersDriveControlState() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "g036-handles.js", String.join("\n",
                    "let clicks = 0;",
                    "let step = 'start';",
                    "try {",
                    "  step = 'open';",
                    "  const gui = neo.player.buildScreen({",
                    "    title: 'Controls',",
                    "    controls: [",
                    "      { type: 'button', id: 'buy', x: 10, y: 40, width: 98,",
                    "        height: 20, label: 'Buy', onClick: () => { clicks++; } },",
                    "      { type: 'checkbox', id: 'dbg', x: 10, y: 70, width: 98,",
                    "        height: 20, checked: false },",
                    "      { type: 'slider', id: 'amt', x: 10, y: 100, width: 98,",
                    "        height: 20, value: 0.5, steps: 4 },",
                    "      { type: 'editBox', id: 'name', x: 10, y: 130, width: 98,",
                    "        height: 20, text: 'hi', maxLength: 8 },",
                    "    ],",
                    "  });",
                    "  step = 'handles';",
                    "  gui.checkbox('dbg').toggle();",
                    "  gui.slider('amt').setSteps(10).setValue(0.25);",
                    "  gui.editBox('name').setValue('yo').setMaxLength(16);",
                    "  step = 'builders';",
                    "  gui.add(new neo.ButtonBuilder()",
                    "      .id('more').pos(10, 160).size(98, 20).message('More')",
                    "      .onClick(() => { clicks++; }).build());",
                    "  gui.add(new neo.CheckBoxBuilder()",
                    "      .id('chk').pos(10, 190).size(98, 20).checked(true).build());",
                    "  gui.add(new neo.SliderBuilder()",
                    "      .id('amt2').pos(10, 220).size(98, 20)",
                    "      .steps(5).initially(0.5).build());",
                    "  gui.add(new neo.TextFieldBuilder()",
                    "      .id('note').pos(10, 250).size(98, 20)",
                    "      .suggestion('hint').build());",
                    "  gui.add(new neo.TexturedButtonBuilder()",
                    "      .id('icon').pos(10, 280).size(16, 16)",
                    "      .enabledTexture('gui/on').disabledTexture('gui/off')",
                    "      .enabledFocusedTexture('gui/on_f')",
                    "      .disabledFocusedTexture('gui/off_f').build());",
                    "  step = 'assert';",
                    "  const dbg = gui.control('dbg');",
                    "  const amt = gui.control('amt');",
                    "  const name = gui.control('name');",
                    "  const more = gui.control('more');",
                    "  const chk = gui.control('chk');",
                    "  const amt2 = gui.control('amt2');",
                    "  const icon = gui.control('icon');",
                    "  neo.__test.report('handles',",
                    "      dbg.checked === true",
                    "          && amt.value === 0.25 && amt.steps === 10",
                    "          && name.text === 'yo' && name.maxLength === 16",
                    "          && gui.editBox('name').value === 'yo',",
                    "      JSON.stringify({ checked: dbg.checked, value: amt.value,",
                    "        steps: amt.steps, text: name.text,",
                    "        maxLength: name.maxLength }));",
                    "  neo.__test.report('builders',",
                    "      more.x === 10 && more.y === 160 && more.width === 98",
                    "          && more.height === 20 && more.label === 'More'",
                    "          && chk.checked === true",
                    "          && amt2.x === 10 && amt2.y === 220",
                    "          && amt2.steps === 5 && amt2.value === 0.5",
                    "          && icon.type === 'imageButton'",
                    "          && gui.button('more') !== undefined",
                    "          && gui.checkbox('chk').checked === true",
                    "          && gui.slider('amt2').steps === 5",
                    "          && gui.editBox('note').value === '',",
                    "      JSON.stringify({ more: { x: more.x, y: more.y,",
                    "        w: more.width, h: more.height, label: more.label },",
                    "        chk: chk.checked, amt2: { x: amt2.x, steps: amt2.steps,",
                    "        value: amt2.value }, icon: icon.type }));",
                    "  step = 'filter';",
                    "  gui.editBox('name').setFilter((text) => text.length <= 4);",
                    "  neo.__test.report('filterInstalled', true, 'filter set');",
                    "} catch (e) {",
                    "  neo.__test.report('handles', false, step + ' -> ' + String(e)",
                    "      + ' @@ ' + String((e && e.stack) || ''));",
                    "  neo.__test.report('builders', false, 'skipped: ' + step);",
                    "}",
                    "let reportedClick = false;",
                    "neo.events.on('tick', () => {",
                    "  if (!reportedClick && clicks >= 1) {",
                    "    reportedClick = true;",
                    "    neo.__test.report('clicked', clicks === 1, 'clicks=' + clicks);",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("g036-handles.js"));
            e2e.awaitSettle();
            assertReport(e2e, "handles");
            assertReport(e2e, "builders");
            assertReport(e2e, "filterInstalled");

            var screen = e2e.adapter().screenPort();
            // The installed filter gates user edits through the sink.
            assertTrue(screen.fireFilterText("name", "ok"),
                    "a candidate within the limit is accepted");
            assertFalse(screen.fireFilterText("name", "way-too-long"),
                    "a candidate over the limit is rejected");

            // The builder-built button's callback fires from its control event.
            screen.fireControlEvent("more", "click",
                    Map.of("id", "more", "type", "button"));
            e2e.awaitSettle();
            e2e.adapter().lifecyclePort().fireTick(1);
            e2e.awaitSettle();
            assertReport(e2e, "clicked");
            assertTrue(screen.actions().contains("write:1:dbg"),
                    "the checkbox toggle reached the port: " + screen.actions());
        }
    }

    @Test
    void failedOpenWithBuildersInFlightStaysCatchable() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            e2e.adapter().screenPort().failOpen = true;
            fixture(dir.resolve("scripts"), "g036-fail-open.js", String.join("\n",
                    "let caught = false;",
                    "let detail = '';",
                    "try {",
                    "  const gui = neo.player.buildScreen({ title: 'Doomed' });",
                    "  gui.add(new neo.ButtonBuilder().id('x').build());",
                    "} catch (e) {",
                    "  caught = true;",
                    "  detail = String(e && e.message || e);",
                    "}",
                    "// Builders themselves keep working after the failed open.",
                    "const spec = new neo.SliderBuilder().id('later')",
                    "    .pos(1, 2).size(3, 4).steps(2).initially(0.5).build();",
                    "neo.__test.report('failOpen', caught && spec.id === 'later'",
                    "    && spec.x === 1 && spec.steps === 2,",
                    "    detail + ' | ' + JSON.stringify({ id: spec.id, x: spec.x,",
                    "      steps: spec.steps }));",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("g036-fail-open.js"));
            e2e.awaitSettle();

            assertReport(e2e, "failOpen");
            assertFalse(e2e.scriptErrors().contains("Exception"),
                    "script errors: " + e2e.scriptErrors());
        }
    }
}
