package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.StubAdapter;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Container specialized-face scenarios: one canned open screen per
 * specialized container (loom, villager, horse, creative), opened through
 * {@code neo.player.inventory} and read back by a real user-format script.
 * Each scenario asserts the G-021 reads and, where the container has write
 * actions, the recorded dispatch.
 */
class InventoryContainerSpecializedScenariosTest {

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
                "author: " + "e2e",
                "config:",
                "  event:",
                "    on: [game:start]",
                ""));
        Files.writeString(folder.resolve("index.js"), source + "\nexport function startup() {}\n");
    }

    /** Runs one scenario: inject the canned screen, load the script, settle, assert. */
    private void scenario(String name, String kind, int size,
            Consumer<StubAdapter.StubInventory> inject, List<String> body,
            List<String> writes, List<String> expectedActions) throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            StubAdapter.StubInventory stub = e2e.adapter().inventoryPort();
            stub.opened = Optional.of(new InventoryPort.ContainerInfo(kind, kind, size, 3));
            inject.accept(stub);
            e2e.adapter().playerPort().setSelf(new PlayerSnapshot(UUID.randomUUID(), "tester",
                    new Pos3d(0, 64, 0), new Rot2d(0, 0), 20, 20, 300));

            List<String> script = new ArrayList<>();
            script.add("neo.events.on('game:start', () => {");
            script.add("  try {");
            script.add("    const inv = neo.player.inventory;");
            script.add("    const checks = {};");
            script.addAll(body);
            if (writes == null) {
                script.add("    const pass = Object.values(checks).every(Boolean);");
                script.add("    const failed = Object.keys(checks).filter((k) => !checks[k]);");
                script.add("    neo.__test.report('" + name + "', pass,");
                script.add("        'failed=' + failed.join(','));");
            } else {
                script.add("    Promise.all([" + String.join(", ", writes) + "]).then(");
                script.add("        (results) => {");
                script.add("          checks.write = results.every((r) => r !== false);");
                script.add("          const pass = Object.values(checks).every(Boolean);");
                script.add("          const failed = Object.keys(checks).filter((k) => !checks[k]);");
                script.add("          neo.__test.report('" + name + "', pass,");
                script.add("              'failed=' + failed.join(','));");
                script.add("        }, (e) => neo.__test.report('" + name + "', false, 'write: ' + e));");
            }
            script.add("  } catch (err) {");
            script.add("    neo.__test.report('" + name + "', false, 'sync-error: ' + err);");
            script.add("  }");
            script.add("});");

            fixture(dir.resolve("scripts"), name + ".js", String.join("\n", script));
            e2e.loadScript(dir.resolve("scripts").resolve(name + ".js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get(name);
            assertTrue(report != null && report.pass(),
                    "report was: " + report + " reports=" + e2e.bridge().all()
                            + " errors=" + e2e.scriptErrors());
            assertEquals(expectedActions, stub.actions(),
                    "action log was: " + stub.actions());
        }
    }

    @Test
    void loomFaceListsPatternsAndAppliesOne() throws Exception {
        scenario("loom", "loom", 40, stub -> {
            stub.loomPatternIds.add("minecraft:base");
            stub.loomPatternIds.add("minecraft:gradient");
            stub.loomSelectAnswer = Boolean.TRUE;
        }, List.of(
                "checks.kind = inv.kind === 'loom';",
                "checks.patterns = Array.isArray(inv.patterns) && inv.patterns.length === 2",
                "    && inv.patterns[0] === 'minecraft:base'",
                "    && inv.patterns[1] === 'minecraft:gradient';"),
                List.of("inv.selectPattern(0)",
                        "inv.selectPatternId('minecraft:gradient')"),
                List.of("loomPattern:0", "loomPattern:1"));
    }

    @Test
    void villagerFaceReadsRestockLevelAndSelectsATrade() throws Exception {
        scenario("villager", "villager", 39, stub -> {
            stub.state.put("canRestock", Boolean.TRUE);
            stub.state.put("leveled", Boolean.TRUE);
            stub.state.put("futureTraderXp", 7);
        }, List.of(
                "checks.kind = inv.kind === 'villager';",
                "checks.restock = inv.canRestock() === true;",
                "checks.leveled = inv.leveled === true;",
                "checks.reward = inv.futureTraderXp === 7;",
                "checks.trades = Array.isArray(inv.trades);"),
                List.of("inv.selectTrade(0)"),
                List.of("selectTrade:0"));
    }

    @Test
    void horseFaceReadsMountStateAndCarriedSlots() throws Exception {
        scenario("horse", "horse", 40, stub -> {
            stub.state.put("canBeSaddled", Boolean.TRUE);
            stub.state.put("isSaddled", Boolean.FALSE);
            stub.state.put("hasArmorSlot", Boolean.TRUE);
            stub.state.put("hasChest", Boolean.TRUE);
            stub.state.put("inventorySize", 3);
            stub.state.put("horse", UUID.randomUUID().toString());
            stub.menuSlots.put(2, new ItemStackData("minecraft:hay_block", 16, "Hay Bale"));
            stub.menuSlots.put(3, new ItemStackData("minecraft:golden_carrot", 4, "Carrot"));
        }, List.of(
                "checks.kind = inv.kind === 'horse';",
                "checks.saddleable = inv.canBeSaddled() === true;",
                "checks.saddled = inv.saddled === false;",
                "checks.armorSlot = inv.hasArmorSlot() === true;",
                "checks.chest = inv.hasChest() === true;",
                "checks.size = inv.inventorySize === 3;",
                "checks.carried = Array.isArray(inv.horseInventory)",
                "    && inv.horseInventory.length === 3",
                "    && inv.horseInventory[0].id === 'minecraft:hay_block'",
                "    && inv.horseInventory[2] == null;",
                "checks.mount = inv.horse === undefined",
                "    || typeof inv.horse === 'object';"),
                null, List.of());
    }

    @Test
    void creativeFaceListsTabsAndDrivesTheScreen() throws Exception {
        scenario("creative", "creative", 46, stub -> {
            stub.creativeTabs.add(new InventoryPort.CreativeTab("Building Blocks", "CATEGORY"));
            stub.creativeTabs.add(new InventoryPort.CreativeTab("Search", "SEARCH"));
            stub.creativeTabs.add(new InventoryPort.CreativeTab("Inventory", "INVENTORY"));
            stub.creativeTabs.add(new InventoryPort.CreativeTab("Hotbar", "HOTBAR"));
            stub.shownItems.add(new ItemStackData("minecraft:diamond", 1, "Diamond"));
            stub.slots.put(36, new ItemStackData("minecraft:iron_boots", 1, "Boots"));
            stub.slots.put(40, new ItemStackData("minecraft:shield", 1, "Shield"));
            stub.savedHotbar.add(new ItemStackData("minecraft:bow", 1, "Bow"));
        }, List.of(
                "checks.kind = inv.kind === 'creative';",
                "checks.tabs = Array.isArray(inv.tabNames) && inv.tabNames.length === 4",
                "    && inv.tabNames[1] === 'Search'",
                "    && inv.tabTexts.length === 4;",
                "checks.shown = Array.isArray(inv.shownItems)",
                "    && inv.shownItems.length === 1",
                "    && inv.shownItems[0].id === 'minecraft:diamond';",
                "checks.equipment = inv.offhand.id === 'minecraft:shield'",
                "    && inv.boots.id === 'minecraft:iron_boots';",
                "checks.hotbar = inv.isInHotbar(0) === true",
                "    && inv.isInHotbar(40) === true",
                "    && inv.isInHotbar(9) === false;",
                "checks.saved = Array.isArray(inv.getSavedHotbar(0))",
                "    && inv.getSavedHotbar(0).length === 1",
                "    && inv.getSavedHotbar(0)[0].id === 'minecraft:bow';"),
                List.of("inv.selectSearch()",
                        "inv.selectTab('Hotbar')",
                        "inv.search('diamond')",
                        "inv.scroll(0.5)",
                        "inv.saveHotbar(1)"),
                List.of("selectTab:1", "selectTab:3", "search:diamond",
                        "scrollBy:0.5", "saveHotbar:1"));
    }
}
