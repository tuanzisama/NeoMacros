package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import ink.tuanzi.neomacros.port.model.SignEditData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-015 E2E scenarios: the world, input, and screen event payloads carry the
 * triaged members (the sign-edit block position handle as the new field, the
 * wheel scroll offset, the open-screen display name, and the destination
 * dimension), driven through the real engine over the stub adapter.
 */
class WorldInputScreenEventScenariosTest {

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

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(UUID.randomUUID(), "tester", new Pos3d(0, 64, 0),
                new Rot2d(0, 0), 20.0, 20, 300);
    }

    @Test
    void worldInputAndScreenPayloadsCarryTheTriagedMembers() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "world-input-screen-payload.js", String.join("\n",
                    "neo.events.on('screen:signEdit', (ev) => {",
                    "  neo.__test.report('sign',",
                    "      ev.pos !== undefined && ev.pos.x === 5 && ev.pos.y === 70",
                    "          && ev.pos.z === -3",
                    "          && Array.isArray(ev.lines) && ev.lines[0] === 'hello',",
                    "      JSON.stringify({ pos: ev.pos &&",
                    "        [ev.pos.x, ev.pos.y, ev.pos.z], lines: ev.lines }));",
                    "});",
                    "neo.events.on('input:wheel', (ev) => {",
                    "  neo.__test.report('wheel', ev.amount === 2.5, String(ev.amount));",
                    "});",
                    "neo.events.on('screen:open', (ev) => {",
                    "  neo.__test.report('open',",
                    "      ev.title === 'Chest' && ev.screen === 'GenericContainerScreen',",
                    "      JSON.stringify({ title: ev.title, screen: ev.screen,",
                    "        kind: ev.kind }));",
                    "});",
                    "neo.events.on('world:dimensionChange', (ev) => {",
                    "  neo.__test.report('dimension',",
                    "      ev.from === 'minecraft:overworld'",
                    "          && ev.to === 'minecraft:the_nether',",
                    "      JSON.stringify({ from: ev.from, to: ev.to }));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("world-input-screen-payload.js"));

            var adapter = e2e.adapter();

            // Baseline: player present, no screen yet, overworld dimension.
            adapter.playerPort().setSelf(player());
            adapter.worldPort().dimension = "minecraft:overworld";
            adapter.lifecyclePort().fireTick(1);
            e2e.awaitSettle();

            // The screen opens and the dimension switches on the next tick;
            // the wheel and the sign editor fire directly through the ports.
            adapter.lifecyclePort().setScreen(Optional.of(new LifecyclePort.ScreenInfo(
                    "GenericContainerScreen", "container", "Chest")));
            adapter.worldPort().dimension = "minecraft:the_nether";
            adapter.lifecyclePort().fireTick(2);
            adapter.inputPort().onScrollFire(2.5);
            adapter.playerPort().fireSignEdit(new SignEditData(
                    5, 70, -3, false, List.of("hello", "", "", "")));
            e2e.awaitSettle();

            assertReport(e2e, "sign");
            assertReport(e2e, "wheel");
            assertReport(e2e, "open");
            assertReport(e2e, "dimension");
            assertTrue(e2e.scriptErrors().isEmpty(),
                    "script errors: " + e2e.scriptErrors());
        }
    }
}
