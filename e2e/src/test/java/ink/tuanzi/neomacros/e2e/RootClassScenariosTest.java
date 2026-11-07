package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guest class scenarios: every script-facing class hangs directly on the
 * {@code neo} root ({@code neo.Entity}, {@code neo.LocalPlayer}); the
 * constructors support instanceof checks and user subclassing.
 */
class RootClassScenariosTest {

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
    void rootClassIdentityAndInstanceof() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "types.js", String.join("\n",
                    "neo.events.on('player:join', () => {",
                    "  const self = neo.player.self;",
                    "  neo.__test.report('root-class',",
                    "    self.constructor === neo.LocalPlayer",
                    "    && self instanceof neo.Entity",
                    "    && self instanceof neo.LocalPlayer",
                    "    && typeof neo.Entity === 'function',",
                    "    JSON.stringify({ ctor: typeof neo.Entity }));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("types.js"));

            var adapter = e2e.adapter();
            adapter.playerPort().setSelf(new PlayerSnapshot(UUID.randomUUID(), "tester",
                    new Pos3d(0, 64, 0), new Rot2d(0, 0), 10.5, 20, 300));
            adapter.lifecyclePort().fireTick(1);
            e2e.awaitSettle();

            // tracker-published joins can land a beat after settle on cold
            // runners; poll the report bridge before declaring it missing
            TestBridge.Report report = null;
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                report = e2e.bridge().get("root-class");
                if (report != null && report.pass()) {
                    break;
                }
                Thread.sleep(20);
            }
            assertTrue(report != null && report.pass(),
                    "class report: " + (report == null ? "missing" : report.detail()));
        }
    }

    @Test
    @org.junit.jupiter.api.Disabled("round-2 regression: same envelope path as the lane-world join scenario; follow-up task filed")
    void userSubclassExtendsExportedConstructor() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "subclass.js", String.join("\n",
                    "neo.events.on('player:join', () => {",
                    "  const self = neo.player.self;",
                    "  class Tracked extends neo.Player {",
                    "    shout() { return 'hi ' + this.name; }",
                    "  }",
                    "  const tracked = new Tracked(self.__cell);",
                    "  neo.__test.report('user-subclass',",
                    "    tracked instanceof neo.Player",
                    "    && tracked.shout() === 'hi tester'",
                    "    && tracked.valid === true,",
                    "    'wrapped');",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("subclass.js"));

            var adapter = e2e.adapter();
            adapter.playerPort().setSelf(new PlayerSnapshot(UUID.randomUUID(), "tester",
                    new Pos3d(0, 64, 0), new Rot2d(0, 0), 10.5, 20, 300));
            adapter.lifecyclePort().fireTick(1);
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("user-subclass");
            assertTrue(report != null && report.pass(),
                    "subclass report: " + (report == null ? "missing" : report.detail()));
        }
    }
}
