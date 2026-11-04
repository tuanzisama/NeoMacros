package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lane-world E2E-A scenarios: the player join flow through handles and world
 * queries, and the cancellable interaction channel, over the real engine and
 * the stub adapter.
 */
class LaneWorldScenariosTest {

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
    void playerJoinHandleQueryAndBlockLookup() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "lane.js", String.join("\n",
                    "import { events } from '@neomacros/core';",
                    "import { world } from '@neomacros/world';",
                    "import { entity } from '@neomacros/entity';",
                    "import { player } from '@neomacros/player';",
                    "import { report } from '@neomacros/__test';",
                    "let joined = 0;",
                    "events.on('player:join', (ev) => {",
                    "  joined++;",
                    "  const self = player.self;",
                    "  const block = world.blockAt(1, 64, 2);",
                    "  const found = entity.lookup(ev.player.uuid);",
                    "  report('lane-world', joined === 1",
                    "      && self !== undefined && self.name === 'tester' && self.health === 10.5",
                    "      && block !== undefined && block.state.id === 'minecraft:stone'",
                    "      && found !== undefined && found.type === 'minecraft:pig' && found.health === 5.5,",
                    "    JSON.stringify({ self: self === undefined ? null : self.name,",
                    "      block: block === undefined ? null : block.state.id,",
                    "      found: found === undefined ? null : found.type }));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("lane.js"));

            var adapter = e2e.adapter();
            UUID uuid = UUID.randomUUID();
            adapter.playerPort().setSelf(new PlayerSnapshot(uuid, "tester",
                    new Pos3d(0, 64, 0), new Rot2d(0, 0), 10.5, 20, 300));
            adapter.worldPort().setBlock(new BlockData("minecraft:stone", new Pos3d(1, 64, 2)));
            adapter.entityPort().setEntities(List.of(new EntitySnapshot(uuid, "minecraft:pig",
                    "tester", new Pos3d(2, 64, 1), 5.5)));

            adapter.lifecyclePort().fireTick(1);
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("lane-world");
            assertTrue(report != null && report.pass(),
                    "lane-world report: " + (report == null ? "missing" : report.detail()));
        }
    }

    @Test
    void interactionSyncCancelReachesAdapter() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "guard.js", String.join("\n",
                    "import { events } from '@neomacros/core';",
                    "import { report } from '@neomacros/__test';",
                    "let saw = null;",
                    "events.onSync('player:attackEntity', (ev) => {",
                    "  saw = ev.target.type;",
                    "  return ev.target.type === 'minecraft:iron_golem';",
                    "});",
                    "events.on('game:start', () => report('lane-guard', saw === null, 'registered'));",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("guard.js"));

            var adapter = e2e.adapter();
            var golem = new EntitySnapshot(UUID.randomUUID(), "minecraft:iron_golem",
                    "Iron Golem", new Pos3d(0, 64, 0), 50.0);
            var pig = new EntitySnapshot(UUID.randomUUID(), "minecraft:pig",
                    "Pig", new Pos3d(1, 64, 0), 5.0);

            assertTrue(adapter.playerPort().fireAttackEntity(golem),
                    "golem attack must be canceled by the sync subscriber");
            assertFalse(adapter.playerPort().fireAttackEntity(pig),
                    "pig attack must pass through");
            assertEquals(null, e2e.bridge().get("lane-guard"));
        }
    }

    @Test
    void handleReadsUndefinedWhenEntityVanishes() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            fixture(dir.resolve("scripts"), "vanish.js", String.join("\n",
                    "import { events } from '@neomacros/core';",
                    "import { entity } from '@neomacros/entity';",
                    "import { report } from '@neomacros/__test';",
                    "events.on('tick', () => {",
                    "  if (ticks() !== 3) return;",
                    "  const found = entity.lookup('11111111-2222-3333-4444-555555555555');",
                    "  report('lane-vanish', found === undefined,",
                    "    found === undefined ? 'absent reads undefined' : 'unexpected handle');",
                    "});",
                    "let n = 0;",
                    "function ticks() { return ++n; }",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("vanish.js"));

            var adapter = e2e.adapter();
            adapter.lifecyclePort().fireTick(1);
            adapter.lifecyclePort().fireTick(2);
            adapter.lifecyclePort().fireTick(3);
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("lane-vanish");
            assertTrue(report != null && report.pass(),
                    "lane-vanish report: " + (report == null ? "missing" : report.detail()));
        }
    }
}
