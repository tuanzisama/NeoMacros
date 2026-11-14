package ink.tuanzi.neomacros.e2e;

import ink.tuanzi.neomacros.e2e.harness.E2ERuntime;
import ink.tuanzi.neomacros.e2e.harness.StubAdapter;
import ink.tuanzi.neomacros.e2e.harness.TestBridge;
import ink.tuanzi.neomacros.port.model.AdvancementData;
import ink.tuanzi.neomacros.port.model.AdvancementDefinitionData;
import ink.tuanzi.neomacros.port.model.AdvancementProgressData;
import ink.tuanzi.neomacros.port.model.AdvancementProgressDetailData;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-018 progress/statistics scenarios over the real guest engine: the
 * statistics raw/formatted/type-name reads, the advancement tree queries
 * (missing among them), the definition face and the progress-derived face
 * (percentage, fraction, criterion dates), each step read through the
 * handle property surface a script actually sees.
 */
class AdvancementStatsScenariosTest {

    private static final String ROOT = "minecraft:story/root";
    private static final String MINE_STONE = "minecraft:story/mine_stone";
    private static final String NO_DETAIL = "minecraft:story/no_detail";
    private static final long STONE_AT = 1_700_000_000_000L;

    @TempDir
    Path dir;

    @Test
    void advancementAndStatisticsScenario() throws Exception {
        try (E2ERuntime e2e = E2ERuntime.create(dir.resolve("scripts"),
                dir.resolve("storage"))) {
            StubAdapter stub = e2e.adapter();
            var self = new PlayerSnapshot(UUID.randomUUID(), "tester",
                    new Pos3d(0, 64, 0), new Rot2d(0, 0), 20, 20, 300);
            stub.playerPort().setSelf(self);
            // The statistics entry point resolves through the entity lookup,
            // so the local player must be visible there by its own UUID.
            stub.entityPort().setEntities(List.of(new EntitySnapshot(self.uuid(),
                    "minecraft:player", "tester", new Pos3d(0, 64, 0), 20)));

            stub.playerPort().setStatistics(Map.of(
                    "minecraft:mined:minecraft:stone", 12,
                    "minecraft:custom:minecraft:play_time", 61));
            stub.playerPort().setFormattedStatistics(Map.of(
                    "minecraft:mined:minecraft:stone", "12",
                    "minecraft:custom:minecraft:play_time", "1:01"));
            stub.playerPort().setStatTypeNames(Map.of(
                    "minecraft:mined", "Mined",
                    "minecraft:custom", "Custom"));
            stub.playerPort().setAdvancements(List.of(
                    new AdvancementData(ROOT, null, "Minecraft", "The start.",
                            "task", false),
                    new AdvancementData(MINE_STONE, ROOT, "Stone Age",
                            "Mine stone.", "task", false),
                    new AdvancementData(NO_DETAIL, ROOT, "No Detail",
                            "No progress detail.", "task", false)));
            stub.playerPort().setAdvancementProgress(Map.of(
                    ROOT, new AdvancementProgressData(true,
                            List.of("root_made"), List.of()),
                    MINE_STONE, new AdvancementProgressData(false,
                            List.of("mined_stone"),
                            List.of("mined_cobblestone")),
                    NO_DETAIL, new AdvancementProgressData(true,
                            List.of("only_criterion"), List.of())));
            stub.playerPort().setAdvancementDefinitions(Map.of(
                    ROOT, new AdvancementDefinitionData(ROOT, List.of(),
                            0, List.of(), List.of()),
                    MINE_STONE, new AdvancementDefinitionData(MINE_STONE,
                            List.of(List.of("mined_cobblestone"),
                                    List.of("mined_stone")),
                            10, List.of("minecraft:stone"),
                            List.of("minecraft:stone_pickaxe")),
                    NO_DETAIL, new AdvancementDefinitionData(NO_DETAIL,
                            List.of(List.of("only_criterion")),
                            0, List.of(), List.of())));
            stub.playerPort().setAdvancementProgressDetails(Map.of(
                    MINE_STONE, new AdvancementProgressDetailData(
                            Map.of("mined_stone", STONE_AT), "1/2")));
            stub.playerPort().setAdvancementJson(Map.of(MINE_STONE,
                    "{\"id\":\"minecraft:story/mine_stone\"}"));

            String source = String.join("\n",
                    "neo.events.on('game:start', () => {",
                    "  (async () => {",
                    "    const checks = {};",
                    "    try {",
                    "      const self = neo.player.self;",
                    "",
                    "      const st = self.statistics;",
                    "      checks.statsHandle = st !== undefined && st !== null",
                    "          && typeof st === 'object';",
                    "      checks.statsRaw = st.value('minecraft:mined',",
                    "          'minecraft:stone') === 12;",
                    "      checks.statsMined = st.mined('minecraft:stone') === 12;",
                    "      checks.statsKeys = Object.keys(st.snapshot).length === 2;",
                    "      checks.statsFormatted =",
                    "          st.formattedValue('minecraft:mined:minecraft:stone')",
                    "          === '12';",
                    "      checks.statsFormattedMap = st.formattedStatMap[",
                    "          'minecraft:custom:minecraft:play_time'] === '1:01';",
                    "      const typeText = st.statText('minecraft:mined:minecraft:stone');",
                    "      checks.statsText = typeText !== undefined",
                    "          && typeText !== null && typeText.content === 'Mined';",
                    "",
                    "      const advs = self.advancements;",
                    "      checks.advsHandle = advs !== undefined && advs !== null",
                    "          && typeof advs === 'object';",
                    "      checks.roots = advs.roots.length === 1",
                    "          && advs.roots[0] === 'minecraft:story/root';",
                    "      checks.missing = advs.missing.length === 1",
                    "          && advs.missing[0] === 'minecraft:story/mine_stone';",
                    "      checks.started = advs.started.length === 1",
                    "          && advs.started[0] === 'minecraft:story/mine_stone';",
                    "      checks.completed = advs.completed.length === 2;",
                    "",
                    "      const a = advs.get('minecraft:story/mine_stone');",
                    "      checks.advHandle = a !== undefined && a !== null",
                    "          && typeof a === 'object';",
                    "      checks.parent = a.parentId === 'minecraft:story/root';",
                    "      checks.requirements = a.requirements.length === 2",
                    "          && a.requirements[1][0] === 'mined_stone';",
                    "      checks.experience = a.experience === 10;",
                    "      checks.loot = a.loot.length === 1",
                    "          && a.loot[0] === 'minecraft:stone';",
                    "      checks.recipes = a.recipes.length === 1",
                    "          && a.recipes[0] === 'minecraft:stone_pickaxe';",
                    "      checks.json = typeof a.toJson === 'function'",
                    "          && a.toJson() ===",
                    "          '{\"id\":\"minecraft:story/mine_stone\"}';",
                    "",
                    "      checks.progress = a.progress !== undefined",
                    "          && a.progress !== null && a.progress.done === false",
                    "          && a.progress.obtained.length === 1",
                    "          && a.progress.remaining[0] === 'mined_cobblestone';",
                    "      checks.percentage = a.percentage === 0.5;",
                    "      checks.count = a.obtainedRequirementCount === 1;",
                    "      checks.fraction = a.fraction !== undefined",
                    "          && a.fraction !== null && a.fraction.content === '1/2';",
                    "      checks.dates = a.criterionDates['mined_stone']",
                    "          === 1700000000000;",
                    "      checks.earliest = a.earliestProgressObtainDate === 1700000000000;",
                    "      checks.criterion =",
                    "          a.getCriterionProgress('mined_stone') === 1700000000000",
                    "          && a.getCriterionProgress('never_known') === -1;",
                    "      checks.criterionObtained =",
                    "          a.isCriteriaObtained('mined_stone') === true",
                    "          && a.isCriteriaObtained('mined_cobblestone') === false;",
                    "",
                    "      const absent = advs.get('minecraft:unknown/advancement');",
                    "      checks.absent = absent === undefined;",
                    "",
                    "      const pass = Object.values(checks).every(Boolean);",
                    "      const failed = Object.keys(checks).filter((k) => !checks[k]);",
                    "      neo.__test.report('g018-advancement-stats', pass,",
                    "          'failed=' + failed.join(','));",
                    "    } catch (err) {",
                    "      neo.__test.report('g018-advancement-stats', false,",
                    "          'error: ' + err);",
                    "    }",
                    "  })();",
                    "});",
                    "export function startup() {}");

            Path folder = dir.resolve("scripts").resolve("event")
                    .resolve("g018-advancement-stats");
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("neomacro.yml"), String.join("\n",
                    "name: g018-advancement-stats",
                    "version: 1.0.0",
                    "author: e2e",
                    "config:",
                    "  event:",
                    "    on: [game:start]",
                    ""));
            Files.writeString(folder.resolve("index.js"), source);

            e2e.loadScript(dir.resolve("scripts").resolve(
                    "g018-advancement-stats.js"));
            stub.lifecyclePort().fireGameStart();
            e2e.awaitSettle();

            TestBridge.Report report = e2e.bridge().get("g018-advancement-stats");
            assertTrue(report != null && report.pass(),
                    "report was: " + report + " reports=" + e2e.bridge().all()
                            + " errors=" + e2e.scriptErrors());
        }
    }
}
