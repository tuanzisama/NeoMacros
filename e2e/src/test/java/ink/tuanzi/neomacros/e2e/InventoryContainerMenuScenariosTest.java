package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.StubAdapter;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.model.EnchantmentData;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Container menu-face scenarios: one canned open screen per specialized
 * container, opened through {@code neo.player.inventory} and read back by a
 * real user-format script. Each scenario asserts the G-020 menu data reads
 * and, where the container has write actions, the recorded dispatch.
 */
class InventoryContainerMenuScenariosTest {

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
    void furnaceMenuFaceReadsRawTickCountsAndFuelQueries() throws Exception {
        scenario("furnace", "furnace", 40, stub -> {
            stub.state.put("isLit", Boolean.TRUE);
            stub.state.put("fuelTime", 12);
            stub.state.put("fuelTimeTotal", 200);
            stub.state.put("smeltingTime", 30);
            stub.state.put("smeltingTimeTotal", 200);
            stub.menuSlots.put(0, new ItemStackData("minecraft:iron_ore", 1, "Iron Ore"));
            stub.menuSlots.put(1, new ItemStackData("minecraft:coal", 1, "Coal"));
            stub.menuSlots.put(2, new ItemStackData("minecraft:iron_ingot", 1, "Iron Ingot"));
            stub.fuelAnswer = Boolean.TRUE;
            stub.smeltableAnswer = Boolean.FALSE;
            stub.fuelValues = Map.of("minecraft:coal", 1600);
        }, List.of(
                "checks.kind = inv.kind === 'furnace';",
                "checks.smelted = inv.smeltedItem !== undefined",
                "    && inv.smeltedItem.id === 'minecraft:iron_ore';",
                "checks.fuel = inv.fuel !== undefined && inv.fuel.id === 'minecraft:coal';",
                "checks.output = inv.output !== undefined",
                "    && inv.output.id === 'minecraft:iron_ingot';",
                "checks.grid = inv.craftingWidth === 1 && inv.craftingHeight === 1",
                "    && inv.craftingSlotCount === 1 && inv.inputSize === 1;",
                "checks.gridCell = Array.isArray(inv.input) && inv.input.length === 1",
                "    && inv.input[0].length === 1",
                "    && inv.input[0][0].id === 'minecraft:iron_ore';",
                "checks.times = inv.fuelTime === 12 && inv.fuelTimeTotal === 200",
                "    && inv.smeltingTime === 30 && inv.smeltingTimeTotal === 200;",
                "checks.lit = inv.lit === true;",
                "checks.fuelQuery = inv.isFuel(inv.smeltedItem) === true;",
                "checks.smeltQuery = inv.isSmeltable(inv.smeltedItem) === false;",
                "checks.fuelValues = inv.fuelValues['minecraft:coal'] === 1600;",
                "checks.book = inv.recipeBookOpened === undefined;"),
                null, List.of());
    }

    @Test
    void anvilMenuFaceReadsRenameBoxAndCost() throws Exception {
        scenario("anvil", "anvil", 40, stub -> {
            stub.anvilName = "Renamed Sword";
            stub.state.put("cost", 7);
            stub.menuSlots.put(0, new ItemStackData("minecraft:iron_sword", 1, "Sword"));
            stub.menuSlots.put(2, new ItemStackData("minecraft:iron_sword", 1, "Renamed Sword"));
        }, List.of(
                "checks.kind = inv.kind === 'anvil';",
                "checks.itemName = inv.itemName === 'Renamed Sword';",
                "checks.cost = inv.cost === 7;",
                "checks.costLimit = inv.costLimit === 40;",
                "checks.input = inv.firstInput !== undefined",
                "    && inv.firstInput.id === 'minecraft:iron_sword';",
                "checks.output = inv.result !== undefined",
                "    && inv.result.id === 'minecraft:iron_sword';"),
                List.of("inv.setItemName('Excalibur')"),
                List.of("anvilName:Excalibur"));
    }

    @Test
    void beaconMenuFaceSelectsAndAppliesEffects() throws Exception {
        scenario("beacon", "beacon", 39, stub -> {
            stub.state.put("levels", 4);
            stub.state.put("primaryEffect", "minecraft:haste");
            stub.state.put("hasPayment", Boolean.TRUE);
            stub.menuSlots.put(0, new ItemStackData("minecraft:emerald", 3, "Emerald"));
        }, List.of(
                "checks.kind = inv.kind === 'beacon';",
                "checks.levels = inv.levels === 4;",
                "checks.primary = inv.primaryEffect === 'minecraft:haste';",
                "checks.secondary = inv.secondaryEffect === undefined;",
                "checks.payment = inv.payment !== undefined",
                "    && inv.payment.id === 'minecraft:emerald';"),
                List.of("inv.selectPrimary('minecraft:haste')",
                        "inv.selectSecondary('minecraft:resistance')",
                        "inv.applyEffects()"),
                List.of("beaconPrimary:minecraft:haste",
                        "beaconSecondary:minecraft:resistance",
                        "beaconApply"));
    }

    @Test
    void brewingMenuFaceReadsSlotsAndPreviewState() throws Exception {
        scenario("brewing", "brewing", 41, stub -> {
            stub.state.put("fuel", 5);
            stub.state.put("brewingTicks", 100);
            stub.state.put("totalFuel", 20);
            stub.state.put("totalBrewingTicks", 400);
            stub.menuSlots.put(0, new ItemStackData("minecraft:potion", 1, "Potion",
                    0, 0, List.of(), List.of(), Map.of("potion", "minecraft:awkward")));
            stub.menuSlots.put(1, new ItemStackData("minecraft:potion", 1, "Potion"));
            stub.menuSlots.put(2, new ItemStackData("minecraft:potion", 1, "Potion"));
            stub.menuSlots.put(3, new ItemStackData("minecraft:gunpowder", 1, "Gunpowder"));
            stub.menuSlots.put(4, new ItemStackData("minecraft:blaze_powder", 1, "Blaze Powder"));
            stub.brewableAnswer = Boolean.TRUE;
            stub.ingredientAnswer = Boolean.TRUE;
            stub.recipeAnswer = Boolean.TRUE;
            stub.previewResult = new ItemStackData("minecraft:splash_potion", 1, "Splash Potion");
        }, List.of(
                "checks.kind = inv.kind === 'brewing';",
                "checks.bottles = inv.bottles.length === 3",
                "    && inv.bottles[0].id === 'minecraft:potion';",
                "checks.first = inv.bottles[0] !== undefined",
                "    && inv.bottles[0].id === 'minecraft:potion';",
                "checks.ingredient = inv.ingredient !== undefined",
                "    && inv.ingredient.id === 'minecraft:gunpowder';",
                "checks.fuelSlot = inv.fuel !== undefined",
                "    && inv.fuel.id === 'minecraft:blaze_powder';",
                "checks.ticks = inv.brewingTicks === 100 && inv.totalBrewingTicks === 400;",
                "checks.maxFuelUses = inv.totalFuelUses === 20;",
                "checks.fuelUses = inv.fuelUses === 5;",
                "checks.canBrew = inv.canBrewCurrentInput() === true;",
                "checks.ingredientOk = inv.isValidIngredient(inv.ingredient) === true;",
                "checks.brewable = inv.isBrewablePotion(inv.bottles[0]) === true;",
                "checks.recipeOk = inv.isValidRecipe(inv.bottles[0], inv.ingredient) === true;",
                "checks.preview = inv.previewPotion(inv.bottles[0], inv.ingredient) !== undefined",
                "    && inv.previewPotion(inv.bottles[0], inv.ingredient).id",
                "        === 'minecraft:splash_potion';",
                "checks.previews = inv.previewPotions().length === 3",
                "    && inv.previewPotions()[0].id === 'minecraft:splash_potion';"),
                null, List.of());
    }

    @Test
    void enchantingMenuFaceReadsCluesAndBuysAnOption() throws Exception {
        List<EnchantmentData> clues = new ArrayList<>();
        clues.add(new EnchantmentData("minecraft:sharpness", 5));
        clues.add(null);
        clues.add(new EnchantmentData("minecraft:unbreaking", 3));
        scenario("enchanting", "enchanting", 38, stub -> {
            stub.state.put("costs", new int[] {10, 20, 30});
            stub.state.put("goldCount", 3);
            stub.state.put("enchantments", clues);
            stub.menuSlots.put(0, new ItemStackData("minecraft:diamond_sword", 1, "Sword"));
            stub.menuSlots.put(1, new ItemStackData("minecraft:lapis_lazuli", 4, "Lapis"));
        }, List.of(
                "checks.kind = inv.kind === 'enchanting';",
                "checks.costs = inv.costs[0] === 10 && inv.costs[2] === 30;",
                "checks.gold = inv.goldCount === 3;",
                "checks.clues = inv.enchantments.length === 3",
                "    && inv.enchantments[0].id === 'minecraft:sharpness'",
                "    && inv.enchantments[0].level === 5",
                "    && inv.enchantments[1] == null;",
                "checks.ids = inv.enchantmentIds[0] === 'minecraft:sharpness'",
                "    && inv.enchantmentIds[1] == null;",
                "checks.item = inv.item !== undefined",
                "    && inv.item.id === 'minecraft:diamond_sword';"),
                List.of("inv.doEnchant(0)"),
                List.of("enchant:0"));
    }

    @Test
    void grindstoneMenuFaceEstimatesTheExperienceFloor() throws Exception {
        scenario("grindstone", "grindstone", 40, stub -> {
            stub.state.put("simulatedXp", 7);
            stub.menuSlots.put(0, new ItemStackData("minecraft:diamond_sword", 1, "Sword"));
            stub.menuSlots.put(2, new ItemStackData("minecraft:diamond_sword", 1, "Sword"));
        }, List.of(
                "checks.kind = inv.kind === 'grindstone';",
                "checks.xp = inv.xpEstimate() === 7;",
                "checks.top = inv.topInput !== undefined",
                "    && inv.topInput.id === 'minecraft:diamond_sword';",
                "checks.result = inv.result !== undefined",
                "    && inv.result.id === 'minecraft:diamond_sword';"),
                null, List.of());
    }

    @Test
    void smithingMenuFaceReadsTheTemplateAndBaseSlots() throws Exception {
        scenario("smithing", "smithing", 40, stub -> {
            stub.menuSlots.put(0, new ItemStackData("minecraft:netherite_upgrade_smithing_template",
                    1, "Template"));
            stub.menuSlots.put(1, new ItemStackData("minecraft:netherite_sword", 1, "Base"));
            stub.menuSlots.put(2, new ItemStackData("minecraft:netherite_ingot", 1, "Addition"));
            stub.menuSlots.put(3, new ItemStackData("minecraft:netherite_sword", 1, "Result"));
        }, List.of(
                "checks.kind = inv.kind === 'smithing';",
                "checks.template = inv.template !== undefined",
                "    && inv.template.id",
                "        === 'minecraft:netherite_upgrade_smithing_template';",
                "checks.base = inv.base !== undefined && inv.base.id === 'minecraft:netherite_sword';",
                "checks.addition = inv.addition !== undefined",
                "    && inv.addition.id === 'minecraft:netherite_ingot';",
                "checks.result = inv.result !== undefined && inv.result.id === 'minecraft:netherite_sword';"),
                null, List.of());
    }

    @Test
    void stonecutterMenuFaceReadsRecipesAndSelectsOne() throws Exception {
        scenario("stonecutter", "stonecutter", 40, stub -> {
            stub.state.put("selectedRecipeIndex", 1);
            stub.state.put("recipeCount", 3);
            stub.state.put("hasInput", Boolean.TRUE);
            stub.state.put("recipes", List.of(
                    new ItemStackData("minecraft:stone_brick_stairs", 4, "Stairs"),
                    new ItemStackData("minecraft:stone_brick_slab", 2, "Slab"),
                    new ItemStackData("minecraft:stone_brick_wall", 1, "Wall")));
            stub.menuSlots.put(0, new ItemStackData("minecraft:stone", 16, "Stone"));
        }, List.of(
                "checks.kind = inv.kind === 'stonecutter';",
                "checks.input = inv.input !== undefined && inv.input.id === 'minecraft:stone';",
                "checks.selected = inv.selectedRecipeIndex === 1;",
                "checks.count = inv.recipeCount === 3;",
                "checks.canCraft = inv.canCraft() === true;",
                "checks.recipes = Array.isArray(inv.recipes) && inv.recipes.length === 3",
                "    && inv.recipes[0].id === 'minecraft:stone_brick_stairs'",
                "    && inv.recipes[1].id === 'minecraft:stone_brick_slab';"),
                List.of("inv.selectRecipe(0)"),
                List.of("stonecutter:0"));
    }

    @Test
    void absentMenuDataReadsUndefinedInsteadOfThrowing() throws Exception {
        scenario("absent", "chest", 63, stub -> {
            // No menu data injected at all: every G-020 read must stay undefined.
        }, List.of(
                "checks.kind = inv.kind === 'chest';",
                "checks.xp = inv.xpEstimate === undefined;",
                "checks.costLimit = inv.costLimit === undefined;",
                "checks.fuelValues = inv.fuelValues === undefined;",
                "checks.enchantments = inv.enchantments === undefined;",
                "checks.recipes = inv.recipes === undefined;",
                "checks.itemName = inv.itemName === undefined;",
                "checks.smeltedItem = inv.smeltedItem === undefined;"),
                null, List.of());
    }
}
