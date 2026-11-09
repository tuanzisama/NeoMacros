package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
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
 * Inventory action scenarios: a real user-format script drives the inventory
 * handle over the stub adapter, and the recorded stub action log asserts the
 * composed click order (grabAll = pickup + gather, closeAndDrop = one hop).
 */
class InventoryActionScenariosTest {

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
    void inventoryActionSequenceRecordsInOrder() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            var stub = e2e.adapter().inventoryPort();
            stub.slots.put(0, new ItemStackData("minecraft:diamond", 3, "Diamond"));
            stub.slots.put(1, new ItemStackData("minecraft:diamond", 2, "Diamond"));
            stub.slots.put(2, new ItemStackData("minecraft:apple", 1, "Apple"));
            e2e.adapter().playerPort().setSelf(new PlayerSnapshot(UUID.randomUUID(), "tester",
                    new Pos3d(0, 64, 0), new Rot2d(0, 0), 20, 20, 300));
            fixture(dir.resolve("scripts"), "inv.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  try {",
                    "    const inv = neo.player.inventory;",
                    "    const free = inv.findFreeHotbarSlot();",
                    "    const counts = inv.itemCount;",
                    "    inv.setSelectedHotbarSlot(3).then(() =>",
                    "      inv.grabAll(0).then(() =>",
                    "        inv.closeAndDrop().then(",
                    "          () => neo.__test.report('inv', free === 3 && counts['minecraft:diamond'] === 5,",
                    "            'free=' + free + ' diamonds=' + counts['minecraft:diamond']),",
                    "          (e) => neo.__test.report('inv', false, 'closeAndDrop: ' + e))));",
                    "  } catch (err) {",
                    "    neo.__test.report('inv', false, 'sync-error: ' + err);",
                    "  }",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("inv.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("inv");
            assertTrue(report != null && report.pass(),
                    "report was: " + report + " reports=" + e2e.bridge().all());
            assertEquals(List.of(
                    "selectHotbar:3",
                    "click:0:0",
                    "pickupAll:0",
                    "closeAndDrop"), stub.actions(),
                    "action log was: " + stub.actions());
        }
    }
}
