package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.StubAdapter;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-028 interaction-override scenarios over the real guest engine: the
 * {@code interactions} entry point, the target override write/read-back
 * through {@code player.target}, the break outcome record and one-shot
 * callback, the held-use override and the {@code reset()} chain — each step
 * asserting the recorded port dispatch.
 */
class InteractionOverrideScenariosTest {

    @TempDir
    Path dir;

    @Test
    void interactionOverrideFaceScenario() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            StubAdapter stub = e2e.adapter();
            var self = new PlayerSnapshot(UUID.randomUUID(), "tester",
                    new Pos3d(0, 64, 0), new Rot2d(0, 0), 20, 20, 300);
            stub.playerPort().setSelf(self);
            // player.target resolves its handle through the entity port, so
            // the local player must be visible there by its own UUID.
            stub.entityPort().setEntities(List.of(new EntitySnapshot(self.uuid(),
                    "minecraft:player", "tester", new Pos3d(0, 64, 0), 20)));

            String source = String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  (async () => {",
                    "    const checks = {};",
                    "    try {",
                    "      const self = neo.player.self;",
                    "      const ix = (self === undefined || self === null)",
                    "          ? undefined : self.interactions;",
                    "      checks.handle = ix !== undefined && ix !== null",
                    "          && typeof ix === 'object';",
                    "      checks.actionsShape = typeof ix.setTarget === 'function'",
                    "          && typeof ix.breakBlock === 'function'",
                    "          && typeof ix.interact === 'function'",
                    "          && typeof ix.reset === 'function'",
                    "          && typeof ix.addBreakCallback === 'function'",
                    "          && typeof ix.isTargetInRange === 'function';",
                    "      checks.propsOff = ix.targetOverride === false",
                    "          && ix.breakOverride === false",
                    "          && ix.interactOverride === false;",
                    "",
                    "      await ix.setTarget(new neo.BlockPos(1, 64, 1), 'up');",
                    "      checks.stored = ix.targetOverride === true;",
                    "      checks.targetReadBack = self.target !== undefined",
                    "          && self.target !== null && self.target.face === 'up'",
                    "          && self.target.hit === true;",
                    "",
                    "      const br = await ix.breakBlock(new neo.BlockPos(1, 64, 1));",
                    "      checks.breakResult = br !== undefined && br !== null",
                    "          && br.reason === 'SUCCESS'",
                    "          && br.pos.x === 1 && br.pos.y === 64;",
                    "",
                    "      let callback = null;",
                    "      ix.addBreakCallback((reason, x, y, z) => {",
                    "        callback = [reason, x, y, z];",
                    "      }, false);",
                    "      await ix.cancelBreak();",
                    "      checks.callback = callback !== null",
                    "          && callback[0] === 'CANCELLED';",
                    "",
                    "      await ix.clearTarget();",
                    "      checks.cleared = ix.targetOverride === false;",
                    "",
                    "      await ix.holdInteract(true);",
                    "      checks.holdOn = ix.interactOverride === true;",
                    "      await ix.holdInteract(false);",
                    "      checks.holdOff = ix.interactOverride === false;",
                    "      const remaining = await ix.holdInteractFor(10);",
                    "      checks.timedHold = remaining === 0",
                    "          && ix.interactOverride === false;",
                    "",
                    "      await ix.interact();",
                    "      await ix.reset();",
                    "      checks.afterReset = ix.targetOverride === false",
                    "          && ix.breakOverride === false",
                    "          && ix.interactOverride === false;",
                    "",
                    "      const pass = Object.values(checks).every(Boolean);",
                    "      const failed = Object.keys(checks).filter((k) => !checks[k]);",
                    "      neo.__test.report('g028-interaction', pass,",
                    "          'failed=' + failed.join(','));",
                    "    } catch (err) {",
                    "      neo.__test.report('g028-interaction', false, 'error: ' + err);",
                    "    }",
                    "  })();",
                    "});",
                    "export function startup() {}");

            Path folder = dir.resolve("scripts").resolve("event")
                    .resolve("g028-interaction");
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("neomacro.yml"), String.join("\n",
                    "name: g028-interaction",
                    "version: 1.0.0",
                    "author: e2e",
                    "config:",
                    "  event:",
                    "    on: [game:start]",
                    ""));
            Files.writeString(folder.resolve("index.js"), source);

            e2e.loadScript(dir.resolve("scripts").resolve("g028-interaction.js"));
            stub.lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("g028-interaction");
            assertTrue(report != null && report.pass(),
                    "report was: " + report + " reports=" + e2e.bridge().all()
                            + " errors=" + e2e.scriptErrors());
            assertEquals(List.of(
                    "setTarget:1,64,1:up",
                    "breakBlockAt:1,64,1",
                    "addBreakCallback:false",
                    "cancelBreak",
                    "clearTarget",
                    "holdInteract:true",
                    "holdInteract:false",
                    "holdInteractFor:10:true",
                    "useItem:main",
                    "resetChecks",
                    "clearTarget",
                    "breakOverride:false:RESET",
                    "holdInteract:false"),
                    stub.playerPort().actions(),
                    "action log was: " + stub.playerPort().actions());
        }
    }
}
