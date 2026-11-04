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
 * T-402 scenarios: the types module hands out the real guest class
 * constructors; the global and module channels yield the identical object;
 * exported constructors support instanceof checks and user subclassing.
 */
class TypesModuleScenariosTest {

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
    void typesModuleIdentityAndInstanceof() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "types.js", String.join("\n",
                    "import { events } from '@neomacros/core';",
                    "import { player } from '@neomacros/player';",
                    "import { Entity } from '@neomacros/entity';",
                    "import { report } from '@neomacros/__test';",
                    "events.on('player:join', () => {",
                    "  const self = player.self;",
                    "  report('types-module',",
                    "    neo.types.Entity === Entity",
                    "    && neo.types.LocalPlayer === self.constructor",
                    "    && self instanceof neo.types.LocalPlayer",
                    "    && typeof neo.types.LocalPlayer === 'function',",
                    "    JSON.stringify({ same: neo.types.Entity === Entity }));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("types.js"));

            var adapter = e2e.adapter();
            adapter.playerPort().setSelf(new PlayerSnapshot(UUID.randomUUID(), "tester",
                    new Pos3d(0, 64, 0), new Rot2d(0, 0), 10.5, 20, 300));
            adapter.lifecyclePort().fireTick(1);
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("types-module");
            assertTrue(report != null && report.pass(),
                    "types report: " + (report == null ? "missing" : report.detail()));
        }
    }

    @Test
    void userSubclassExtendsExportedConstructor() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "subclass.js", String.join("\n",
                    "import { events } from '@neomacros/core';",
                    "import { player } from '@neomacros/player';",
                    "import { report } from '@neomacros/__test';",
                    "events.on('player:join', () => {",
                    "  const self = player.self;",
                    "  class Tracked extends neo.types.Player {",
                    "    shout() { return 'hi ' + this.name; }",
                    "  }",
                    "  const tracked = new Tracked(self.__cell);",
                    "  report('user-subclass',",
                    "    tracked instanceof neo.types.Player",
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
