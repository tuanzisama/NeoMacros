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
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recipe book face scenarios: canned book entries on a crafting screen read
 * back through {@code neo.player.inventory} as {@code Recipe} handles
 * (craftableRecipes property, getRecipes(craftable) method, per-entry
 * craftability checks and the craft dispatch), plus the undefined reads
 * without an open recipe-book screen.
 */
class RecipeBookScenariosTest {

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
    private void scenario(String name, Consumer<StubAdapter.StubInventory> inject,
            List<String> body, List<String> writes, List<String> expectedActions) throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            StubAdapter.StubInventory stub = e2e.adapter().inventoryPort();
            stub.opened = Optional.of(new InventoryPort.ContainerInfo("crafting", "crafting", 10, 3));
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
                    "actions were: " + stub.actions());
        }
    }

    /** One planks recipe (craftable) and one stick recipe (not craftable now). */
    private static void twoEntries(StubAdapter.StubInventory stub) {
        stub.recipeEntries = List.of(
                new InventoryPort.RecipeEntryData("5", "minecraft:crafting",
                        new ItemStackData("minecraft:oak_planks", 4, "Oak Planks"),
                        List.of(List.of(new ItemStackData("minecraft:oak_log", 1, "Oak Log")))),
                new InventoryPort.RecipeEntryData("9", "minecraft:crafting",
                        new ItemStackData("minecraft:stick", 4, "Stick"),
                        List.of(List.of(
                                new ItemStackData("minecraft:oak_planks", 2, "Oak Planks"),
                                new ItemStackData("minecraft:birch_planks", 2, "Birch Planks")))));
        stub.craftableKeys = Set.of("5");
        stub.canCraftAnswers.put("5", Boolean.TRUE);
        stub.craftableAmounts.put("5", 12);
    }

    @Test
    void bookFaceListsEntriesAndRecipeHandlesCarryTheSnapshot() throws Exception {
        scenario("recipes-list", stub -> twoEntries(stub), List.of(
                "checks.craftable = Array.isArray(inv.craftableRecipes)",
                "    && inv.craftableRecipes.length === 1;",
                "checks.all = Array.isArray(inv.getRecipes(false))",
                "    && inv.getRecipes(false).length === 2;",
                "checks.filtered = Array.isArray(inv.getRecipes(true))",
                "    && inv.getRecipes(true).length === 1;",
                "const r = inv.getRecipes(false)[0];",
                "checks.key = r.recipeKey === '5';",
                "checks.category = r.category === 'minecraft:crafting';",
                "checks.output = r.output !== undefined && r.output.id === 'minecraft:oak_planks'",
                "    && r.output.count === 4;",
                "checks.ingredients = Array.isArray(r.ingredients) && r.ingredients.length === 1",
                "    && r.ingredients[0][0].id === 'minecraft:oak_log';",
                "checks.alt = inv.getRecipes(false)[1].ingredients[0].length === 2;",
                "checks.canCraft = r.canCraft() === true;",
                "checks.canCraftAmount = r.canCraft(10) === true;",
                "checks.amount = r.craftableAmount === 12;",
                "checks.identity = r instanceof neo.Recipe;"),
                null,
                List.of());
    }

    @Test
    void recipeCraftDispatchesThroughTheBook() throws Exception {
        scenario("recipes-craft", stub -> twoEntries(stub), List.of(
                "const r = inv.craftableRecipes[0];",
                "checks.got = r !== undefined && r.recipeKey === '5';"),
                List.of("r.craft(false)", "r.craft(true)"),
                List.of("craft:5:false", "craft:5:true"));
    }

    @Test
    void bookFaceReadsUndefinedWithoutARecipeBookScreen() throws Exception {
        scenario("recipes-none", stub -> {
            stub.recipeEntries = null;
        }, List.of(
                "checks.craftable = inv.craftableRecipes === undefined;",
                "checks.recipes = inv.getRecipes(false) === undefined;"),
                null,
                List.of());
    }

    @Test
    void recipeCraftRejectsWhenTheBookScreenClosed() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"), dir.resolve("storage"))) {
            StubAdapter.StubInventory stub = e2e.adapter().inventoryPort();
            stub.opened = Optional.of(new InventoryPort.ContainerInfo("crafting", "crafting", 10, 3));
            twoEntries(stub);
            e2e.adapter().playerPort().setSelf(new PlayerSnapshot(UUID.randomUUID(), "tester",
                    new Pos3d(0, 64, 0), new Rot2d(0, 0), 20, 20, 300));

            List<String> script = List.of(
                    "let recipe = undefined;",
                    "neo.events.on('game:start', () => {",
                    "  try {",
                    "    const inv = neo.player.inventory;",
                    "    const list = inv.getRecipes(false);",
                    "    if (!Array.isArray(list)) {",
                    "      neo.__test.report('recipes-reject', false,",
                    "          'list=' + JSON.stringify(list) + ' kind=' + inv.kind);",
                    "      return;",
                    "    }",
                    "    recipe = list[0];",
                    "  } catch (err) {",
                    "    neo.__test.report('recipes-reject', false, 'start: ' + err);",
                    "  }",
                    "});",
                    // The screen closed between the read and the craft: the
                    // follow-up craft settles as a rejection.
                    "neo.events.on('tick', () => {",
                    "  if (recipe === undefined) return;",
                    "  const r = recipe;",
                    "  recipe = undefined;",
                    "  r.craft(false).then(",
                    "    () => neo.__test.report('recipes-reject', false, 'resolved'),",
                    "    (e) => neo.__test.report('recipes-reject',",
                    "        String(e).indexOf('recipe-book screen') >= 0, 'err: ' + e));",
                    "});");
            fixture(dir.resolve("scripts"), "recipes-reject.js", String.join("\n", script));
            e2e.loadScript(dir.resolve("scripts").resolve("recipes-reject.js"));
            e2e.adapter().lifecyclePort().fireGameStart();
            e2e.awaitSettle();
            // The screen closed between the read and the craft: the follow-up
            // craft settles as a rejection.
            stub.recipeEntries = null;
            e2e.adapter().lifecyclePort().fireTick(1);
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("recipes-reject");
            assertTrue(report != null && report.pass(),
                    "report was: " + report + " reports=" + e2e.bridge().all()
                            + " errors=" + e2e.scriptErrors());
        }
    }
}
