package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos2d;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Container common face scenarios: a real user-format script opens the
 * canned chest handle and reads the sync id, region names, slot corner,
 * slot under the mouse and the raw menu hatch, then exercises the player
 * equipment/craft-grid/recipe-book reads; the stub action log asserts the
 * dispatched open.
 */
class InventoryContainerScenariosTest {

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
    void openContainerAndReadTheCommonFace() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            var stub = e2e.adapter().inventoryPort();
            stub.opened = Optional.of(new InventoryPort.ContainerInfo("Chest", "chest", 63, 7));
            stub.regions.put(0, "container");
            stub.regions.put(54, "hotbar");
            stub.slotPositions.put(0, new Pos2d(7, 9));
            stub.underMouse = 9;
            stub.rawMenu = new Object();
            stub.slots.put(40, new ItemStackData("minecraft:shield", 1, "Shield"));
            stub.recipeBook = true;
            e2e.adapter().playerPort().setSelf(new PlayerSnapshot(UUID.randomUUID(), "tester",
                    new Pos3d(0, 64, 0), new Rot2d(0, 0), 20, 20, 300));
            fixture(dir.resolve("scripts"), "container.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  try {",
                    "    const inv = neo.player.inventory;",
                    "    const pos = inv.getSlotPos(0);",
                    "    const self = neo.player.self.inventory;",
                    "    const checks = {",
                    "      kind: inv.kind === 'chest',",
                    "      syncId: inv.containerId === 7,",
                    "      region: inv.regionOf(0) === 'container'",
                    "          && inv.regionOf(54) === 'hotbar'",
                    "          && inv.regionOf(63) === undefined,",
                    "      slotPos: pos !== undefined && pos.x === 7 && pos.y === 9,",
                    "      underMouse: inv.slotUnderMouse === 9,",
                    "      raw: inv.raw !== undefined && inv.raw !== null,",
                    "      hotbar: self.isInHotbar(40) && !self.isInHotbar(9),",
                    "      equipment: self.offhand !== undefined",
                    "          && self.offhand.id === 'minecraft:shield',",
                    "      grid: self.craftingWidth === 2 && self.craftingSlotCount === 4",
                    "          && self.inputSize === 4,",
                    "      recipeBook: self.recipeBookOpened === true,",
                    "      openPromise: false,",
                    "    };",
                    "    inv.open().then(",
                    "      () => {",
                    "        checks.openPromise = true;",
                    "        const pass = Object.values(checks).every(Boolean);",
                    "        const failed = Object.keys(checks).filter((k) => !checks[k]);",
                    "        neo.__test.report('container', pass,",
                    "            'failed=' + failed.join(',') + ' kind=' + inv.kind);",
                    "      },",
                    "      (e) => neo.__test.report('container', false, 'open: ' + e));",
                    "  } catch (err) {",
                    "    neo.__test.report('container', false, 'sync-error: ' + err);",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("container.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("container");
            assertTrue(report != null && report.pass(),
                    "report was: " + report + " reports=" + e2e.bridge().all()
                            + " errors=" + e2e.scriptErrors());
            assertEquals(List.of("open:chest"), stub.actions(),
                    "action log was: " + stub.actions());
        }
    }
}
