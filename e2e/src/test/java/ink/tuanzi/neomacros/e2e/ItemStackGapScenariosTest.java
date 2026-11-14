package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockTypeData;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-027 item face scenario: a real user-format script reads a canned
 * inventory stack carrying the item-type facts, the port-backed registry
 * and evaluation faces, the block-item composition, and the undefined
 * edges (absent facts, absent answers) over the stub adapter.
 */
class ItemStackGapScenariosTest {

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

    @Test
    void itemFaceMembersReachTheGuest() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            var inventory = e2e.adapter().inventoryPort();
            inventory.slots.put(0, swordStack());
            inventory.slots.put(1, chestStack());
            inventory.slots.put(2, new ItemStackData("minecraft:iron_ingot", 1, "Iron Ingot"));
            e2e.adapter().playerPort().setSelf(new PlayerSnapshot(UUID.randomUUID(), "tester",
                    new Pos3d(0, 64, 0), new Rot2d(0, 0), 20, 20, 300));
            e2e.adapter().worldPort().setBlock(chestBlock());
            e2e.adapter().worldPort().setBlockType(chestType());
            var item = e2e.adapter().itemPort();
            item.creativeTabs = List.of("Combat");
            item.groupIcons = List.of(new ItemStackData("minecraft:iron_sword", 1, "Iron Sword"));
            item.tags = List.of("minecraft:swords");
            item.defaultStack = new ItemStackData("minecraft:diamond_sword", 1, "Diamond Sword");
            item.hasRecipeRemainder = Boolean.FALSE;
            item.attackDamage = 7.0D;
            item.onCooldown = Boolean.FALSE;
            item.cooldownProgress = 0.5F;
            item.correctToolForDrops = Boolean.TRUE;
            item.destroySpeed = 2.0F;
            item.repairable = Boolean.TRUE;

            fixture(dir.resolve("scripts"), "itemface.js", String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  const results = {};",
                    "  const inv = neo.player.inventory;",
                    "  const s = inv.slot(0);",
                    "  results['stack.handle'] = s !== undefined;",
                    "  if (s) {",
                    "    results['stack.facts'] = s.tool === true",
                    "        && s.wearable === false",
                    "        && s.fireproof === false",
                    "        && s.blockItem === false",
                    "        && s.enchantability === 15",
                    "        && s.canFitInsideContainerItems() === false;",
                    "    results['stack.hidden'] = s.enchantmentsHidden === true",
                    "        && s.modifiersHidden === false",
                    "        && s.unbreakableHidden === false",
                    "        && s.canDestroyHidden === false",
                    "        && s.canPlaceHidden === false",
                    "        && s.dyeHidden === false;",
                    "    results['stack.restrictions'] = s.hasDestroyRestrictions() === true",
                    "        && Array.isArray(s.destroyRestrictions)",
                    "        && s.destroyRestrictions[0] === 'minecraft:stone'",
                    "        && s.hasPlaceRestrictions() === false",
                    "        && s.placeRestrictions === undefined;",
                    "    results['stack.creativeTab'] = Array.isArray(s.creativeTab)",
                    "        && s.creativeTab[0] === 'Combat';",
                    "    results['stack.groupIcon'] = Array.isArray(s.groupIcon)",
                    "        && s.groupIcon[0].id === 'minecraft:iron_sword';",
                    "    results['stack.tags'] = Array.isArray(s.tags)",
                    "        && s.tags[0] === 'minecraft:swords';",
                    "    results['stack.defaultStack'] = s.defaultStack !== undefined",
                    "        && s.defaultStack.id === 'minecraft:diamond_sword';",
                    "    results['stack.remainder'] = s.recipeRemainder === undefined",
                    "        && s.hasRecipeRemainder() === false;",
                    "    results['stack.cooldown'] = s.onCooldown === false",
                    "        && s.cooldownProgress === 0.5;",
                    "    results['stack.attackDamage'] = s.attackDamage === 7;",
                    "    results['stack.durability'] = s.maxDamage === 250;",
                    "    const material = inv.slot(2);",
                    "    results['stack.repair'] = s.canBeRepairedWith(material) === true;",
                    "    const wblock = neo.world.blockAt(1, 64, 2);",
                    "    results['stack.evaluation'] = wblock !== undefined",
                    "        && s.isCorrectToolForDrops(wblock) === true",
                    "        && s.getDestroySpeed(wblock.state) === 2;",
                    "    const b = inv.slot(1);",
                    "    results['stack.blockItem'] = b !== undefined",
                    "        && b.blockItem === true",
                    "        && b.block !== undefined",
                    "        && b.block.id === 'minecraft:chest'",
                    "        && b.block.props.facing === 'north';",
                    "    results['stack.absentEdges'] = s.block === undefined",
                    "        && s.recipeRemainder === undefined;",
                    "  }",
                    "  const pass = Object.values(results).every(Boolean);",
                    "  for (const [key, value] of Object.entries(results)) {",
                    "    neo.chat.log('[E2E] ' + key + '=' + value);",
                    "  }",
                    "  neo.__test.report('item-face-gap', pass, JSON.stringify(results));",
                    "});",
                    ""));
            e2e.loadScript(dir.resolve("scripts").resolve("itemface.js"));

            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = awaitReport(e2e, "item-face-gap");
            assertTrue(report != null && report.pass(),
                    "item-face-gap report: "
                            + (report == null ? "missing" : report.detail())
                            + " script-errors: " + e2e.scriptErrors());
        }
    }

    /** The canned diamond sword carrying the G-027 item facts. */
    private static ItemStackData swordStack() {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("maxStackSize", 1);
        facts.put("tool", true);
        facts.put("wearable", false);
        facts.put("fireproof", false);
        facts.put("blockItem", false);
        facts.put("canFitInsideContainerItems", false);
        facts.put("enchantability", 15);
        facts.put("enchantmentsHidden", true);
        facts.put("modifiersHidden", false);
        facts.put("unbreakableHidden", false);
        facts.put("canDestroyHidden", false);
        facts.put("canPlaceHidden", false);
        facts.put("dyeHidden", false);
        facts.put("hasDestroyRestrictions", true);
        facts.put("destroyRestrictions", List.of("minecraft:stone"));
        facts.put("hasPlaceRestrictions", false);
        return new ItemStackData("minecraft:diamond_sword", 1, "Diamond Sword",
                0, 250, List.of(), List.of(), facts);
    }

    /** The canned chest block item carrying only the block identity facts. */
    private static ItemStackData chestStack() {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("blockItem", true);
        facts.put("blockId", "minecraft:chest");
        facts.put("tool", false);
        return new ItemStackData("minecraft:chest", 1, "Chest", 0, 0,
                List.of(), List.of(), facts);
    }

    /** The canned world block behind the evaluation argument. */
    private static BlockData chestBlock() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("hardness", 2.5);
        return new BlockData("minecraft:chest", new Pos3d(1, 64, 2),
                Map.of("facing", "north"), extras);
    }

    /** The canned registry facts of the chest block type. */
    private static BlockTypeData chestType() {
        return new BlockTypeData("minecraft:chest", "Chest",
                List.of("minecraft:wooden_materials"),
                2.5f, 0.6f, 0.6f, 1.0f, false, false,
                Map.of("facing", "north"),
                List.of(Map.of("facing", "north")),
                new ItemStackData("minecraft:chest", 1, "Chest"));
    }
}
