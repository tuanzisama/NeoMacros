package ink.tuanzi.neomacros.core.api.player;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.DialogPort;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.InputPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.AdvancementData;
import ink.tuanzi.neomacros.port.model.AdvancementDefinitionData;
import ink.tuanzi.neomacros.port.model.AdvancementProgressData;
import ink.tuanzi.neomacros.port.model.AdvancementProgressDetailData;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.EffectSnapshot;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-018 progress/statistics faces over a canned player port: the statistics
 * raw/formatted/type-name reads, the advancement tree queries (missing
 * among them), the definition face (requirements/experience/loot/recipes/
 * JSON) and the progress-derived face (percentage, fraction, satisfied
 * groups, criterion dates), plus the absent-id semantics.
 */
class AdvancementStatsFaceTest {

    private static final String ROOT = "minecraft:story/root";
    private static final String MINE_STONE = "minecraft:story/mine_stone";
    private static final String NO_DETAIL = "minecraft:story/no_detail";
    private static final long STONE_AT = 1_700_000_000_000L;

    private CannedPlayer port;
    private PlayerApi.LocalPlayer local;
    private PlayerApi.Advancements advancements;

    @BeforeEach
    void install() {
        port = new CannedPlayer();
        ScriptApiEnvironment.install(null, new PlayerOnlyAdapter(port));
        port.self = new PlayerSnapshot(UUID.randomUUID(), "tester",
                new Pos3d(0, 64, 0), new Rot2d(0, 0), 20, 20, 300);

        port.statistics = Map.of(
                "minecraft:mined:minecraft:stone", 12,
                "minecraft:custom:minecraft:play_time", 61);
        port.formattedStatistics = Map.of(
                "minecraft:mined:minecraft:stone", "12",
                "minecraft:custom:minecraft:play_time", "1:01");
        port.statTypeNames = Map.of(
                "minecraft:mined", "Mined",
                "minecraft:custom", "Custom");

        port.advancements = List.of(
                new AdvancementData(ROOT, null, "Minecraft", "The start.",
                        "task", false),
                new AdvancementData(MINE_STONE, ROOT, "Stone Age",
                        "Mine stone.", "task", false),
                new AdvancementData(NO_DETAIL, ROOT, "No Detail",
                        "No progress detail.", "task", false));
        port.progressById = Map.of(
                ROOT, new AdvancementProgressData(true,
                        List.of("root_made"), List.of()),
                MINE_STONE, new AdvancementProgressData(false,
                        List.of("mined_stone"), List.of("mined_cobblestone")),
                NO_DETAIL, new AdvancementProgressData(true,
                        List.of("only_criterion"), List.of()));
        port.definitionById = Map.of(
                ROOT, new AdvancementDefinitionData(ROOT, List.of(),
                        0, List.of(), List.of()),
                MINE_STONE, new AdvancementDefinitionData(MINE_STONE,
                        List.of(List.of("mined_cobblestone"),
                                List.of("mined_stone")),
                        10, List.of("minecraft:stone"),
                        List.of("minecraft:stone_pickaxe")),
                NO_DETAIL, new AdvancementDefinitionData(NO_DETAIL,
                        List.of(List.of("only_criterion")),
                        0, List.of(), List.of()));
        port.detailById = Map.of(
                MINE_STONE, new AdvancementProgressDetailData(
                        Map.of("mined_stone", STONE_AT), "1/2"));
        port.jsonById = Map.of(MINE_STONE,
                "{\"id\":\"minecraft:story/mine_stone\"}");

        local = new PlayerApi.LocalPlayer(port.self.uuid());
        advancements = local.getAdvancements();
    }

    @AfterEach
    void uninstall() throws Exception {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void statisticsQueriesReadRawAndFormattedCounters() {
        PlayerApi.Statistics stats = local.getStatistics();
        assertNotNull(stats, "a world means a statistics handle");

        assertEquals(12, stats.value("minecraft:mined", "minecraft:stone"));
        assertEquals(12, stats.mined("minecraft:stone"));
        assertEquals(61, stats.custom("minecraft:play_time"));
        assertEquals(Map.of(
                "minecraft:mined:minecraft:stone", 12,
                "minecraft:custom:minecraft:play_time", 61),
                stats.getSnapshot());

        assertEquals("12",
                stats.formattedValue("minecraft:mined:minecraft:stone"));
        assertEquals("1:01",
                stats.formattedValue("minecraft:custom:minecraft:play_time"));
        assertNull(stats.formattedValue("minecraft:mined:minecraft:dirt"),
                "unknown or zero-valued keys read as undefined");
        assertEquals(Map.of(
                "minecraft:mined:minecraft:stone", "12",
                "minecraft:custom:minecraft:play_time", "1:01"),
                stats.getFormattedStatMap());
    }

    @Test
    void statTextResolvesTheStatTypeName() {
        PlayerApi.Statistics stats = local.getStatistics();

        var mined = stats.statText("minecraft:mined:minecraft:stone");
        assertNotNull(mined);
        assertEquals("Mined", mined.getContent());

        assertNull(stats.statText("minecraft:unknown:whatever"),
                "unknown stat types read as undefined");
    }

    @Test
    void advancementTreeQueriesSplitStartedMissingCompletedRoots() {
        assertNotNull(advancements);

        assertEquals(List.of(ROOT, MINE_STONE, NO_DETAIL), advancements.getIds());
        assertEquals(List.of(ROOT), advancements.getRoots());
        assertEquals(List.of(ROOT, NO_DETAIL), advancements.getCompleted());
        assertEquals(List.of(MINE_STONE), advancements.getStarted());
        assertEquals(List.of(MINE_STONE), advancements.getMissing(),
                "missing counts incomplete progress entries including empty ones");

        PlayerApi.Advancement root = advancements.get(ROOT);
        assertNotNull(root);
        assertNull(root.getParentId(), "roots have no parent");
        assertEquals(List.of(MINE_STONE, NO_DETAIL), advancements.children(ROOT));
        assertNull(advancements.get("minecraft:unknown/advancement"),
                "unknown ids read as undefined");
    }

    @Test
    void definitionFaceExposesRequirementsAndRewards() {
        PlayerApi.Advancement adv = advancements.get(MINE_STONE);
        assertNotNull(adv);

        assertEquals(List.of(List.of("mined_cobblestone"),
                List.of("mined_stone")), adv.getRequirements());
        assertEquals(2, adv.getRequirements().size(),
                "requirementCount maps to requirements.length");
        assertEquals(10, adv.getExperience());
        assertEquals(List.of("minecraft:stone"), adv.getLoot());
        assertEquals(List.of("minecraft:stone_pickaxe"), adv.getRecipes());
        assertEquals("{\"id\":\"minecraft:story/mine_stone\"}", adv.toJson());
    }

    @Test
    void progressFaceExpectsPercentageFractionAndCriterionDates() {
        PlayerApi.Advancement adv = advancements.get(MINE_STONE);
        assertNotNull(adv);

        PlayerApi.AdvancementProgress progress = adv.getProgress();
        assertNotNull(progress);
        assertFalse(progress.done());
        assertEquals(List.of("mined_stone"), progress.obtained());
        assertEquals(List.of("mined_cobblestone"), progress.remaining());

        assertEquals(0.5f, adv.getPercentage(), 0.0001f,
                "one of two requirement groups satisfied");
        assertEquals(1, adv.getObtainedRequirementCount());
        assertNotNull(adv.getFraction());
        assertEquals("1/2", adv.getFraction().getContent());

        Map<String, Long> dates = adv.getCriterionDates();
        assertNotNull(dates);
        assertEquals(STONE_AT, dates.get("mined_stone"));
        assertEquals(STONE_AT, adv.getEarliestProgressObtainDate());
        assertEquals(STONE_AT, adv.getCriterionProgress("mined_stone"));
        assertEquals(-1L, adv.getCriterionProgress("never_known"),
                "unknown or unobtained criteria read as -1");
        assertTrue(adv.isCriteriaObtained("mined_stone"));
        assertFalse(adv.isCriteriaObtained("mined_cobblestone"));
        assertFalse(adv.isCriteriaObtained("never_known"));
    }

    @Test
    void emptyRequirementsReadZeroPercentageAndUndefinedDates() {
        PlayerApi.Advancement root = advancements.get(ROOT);
        assertNotNull(root);

        assertTrue(root.getRequirements().isEmpty());
        assertEquals(0f, root.getPercentage(), 0.0001f,
                "an empty requirement set yields 0");
        assertEquals(0, root.getObtainedRequirementCount());
        assertNull(root.getFraction(),
                "an empty requirement set has no fraction text");
        assertNull(root.getCriterionDates(),
                "no progress detail means the date face reads undefined");
        assertNull(root.getCriterionProgress("root_made"));
        assertNull(root.getEarliestProgressObtainDate());
        assertTrue(root.isCriteriaObtained("root_made"),
                "obtained flags still read from the progress entry");
    }

    @Test
    void absentIdsAndWorldReadUndefined() {
        PlayerApi.Advancement unknown = advancements.get("minecraft:unknown/x");
        assertNull(unknown);

        port.self = null;
        assertNull(local.getStatistics(), "no world means no statistics handle");

        port.self = new PlayerSnapshot(UUID.randomUUID(), "tester",
                new Pos3d(0, 64, 0), new Rot2d(0, 0), 20, 20, 300);
        local = new PlayerApi.LocalPlayer(port.self.uuid());

        port.advancements = List.of();
        assertNull(local.getAdvancements(),
                "no client-known advancements means no handle");
    }

    /** Canned player port: only the G-018 read face carries state. */
    private static final class CannedPlayer implements PlayerPort {
        volatile PlayerSnapshot self;
        volatile Map<String, Integer> statistics = Map.of();
        volatile Map<String, String> formattedStatistics = Map.of();
        volatile Map<String, String> statTypeNames = Map.of();
        volatile List<AdvancementData> advancements = List.of();
        volatile Map<String, AdvancementProgressData> progressById = Map.of();
        volatile Map<String, AdvancementDefinitionData> definitionById = Map.of();
        volatile Map<String, AdvancementProgressDetailData> detailById = Map.of();
        volatile Map<String, String> jsonById = Map.of();

        @Override
        public Optional<PlayerSnapshot> self() {
            return Optional.ofNullable(self);
        }

        @Override
        public List<EffectSnapshot> activeEffects() {
            return List.of();
        }

        @Override
        public void onAttackBlock(Function<BlockInteraction, Boolean> sink) {
        }

        @Override
        public void onAttackEntity(Function<EntitySnapshot, Boolean> sink) {
        }

        @Override
        public void onInteractBlock(Function<BlockInteraction, Boolean> sink) {
        }

        @Override
        public void onInteractEntity(Function<EntitySnapshot, Boolean> sink) {
        }

        @Override
        public CompletableFuture<Void> attack(EntitySnapshot target) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> useEntity(EntitySnapshot target, String hand) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> useBlock(BlockInteraction hit, String hand) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> useItem(String hand) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> attackBlock(BlockInteraction hit) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> stopBreaking() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> swingHand(String hand) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> dropHeld(boolean all) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Map<String, Integer> statistics() {
            return statistics;
        }

        @Override
        public Map<String, String> formattedStatistics() {
            return formattedStatistics;
        }

        @Override
        public Optional<String> statTypeDisplayName(String statKey) {
            if (statKey == null || statKey.isEmpty()) {
                return Optional.empty();
            }
            int first = statKey.indexOf(':');
            int second = first < 0 ? -1 : statKey.indexOf(':', first + 1);
            String typeId = second < 0 ? statKey : statKey.substring(0, second);
            return Optional.ofNullable(statTypeNames.get(typeId));
        }

        @Override
        public List<AdvancementData> advancements() {
            return advancements;
        }

        @Override
        public Optional<AdvancementProgressData> advancementProgress(String id) {
            return Optional.ofNullable(progressById.get(id));
        }

        @Override
        public Optional<AdvancementDefinitionData> advancementDefinition(String id) {
            return Optional.ofNullable(definitionById.get(id));
        }

        @Override
        public Optional<AdvancementProgressDetailData> advancementProgressDetail(
                String id) {
            return Optional.ofNullable(detailById.get(id));
        }

        @Override
        public Optional<String> advancementJson(String id) {
            return Optional.ofNullable(jsonById.get(id));
        }
    }

    /**
     * Canned entity port: the statistics entry point resolves through the
     * entity lookup, so the local player must be visible by its own UUID.
     */
    private static final class CannedEntities implements EntityPort {
        private final CannedPlayer player;

        CannedEntities(CannedPlayer player) {
            this.player = player;
        }

        @Override
        public Optional<EntitySnapshot> byUuid(UUID uuid) {
            return player.self != null && uuid.equals(player.self.uuid())
                    ? Optional.of(new EntitySnapshot(player.self.uuid(),
                            "minecraft:player", player.self.name(),
                            new Pos3d(0, 64, 0), 20))
                    : Optional.empty();
        }

        @Override
        public List<EntitySnapshot> inRadius(Pos3d center, double range,
                java.util.function.Predicate<EntitySnapshot> filter) {
            return List.of();
        }
    }

    /** Adapter exposing only the player and entity ports; the rest stay unused. */
    private static final class PlayerOnlyAdapter implements NeomacrosAdapter {
        private final CannedPlayer player;

        PlayerOnlyAdapter(CannedPlayer player) {
            this.player = player;
        }

        @Override
        public LifecyclePort lifecycle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorldPort world() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityPort entity() {
            return new CannedEntities(player);
        }

        @Override
        public PlayerPort player() {
            return player;
        }

        @Override
        public InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InventoryPort inventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientPort client() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPort render() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DialogPort dialog() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Draw3DPort draw3d() {
            throw new UnsupportedOperationException();
        }
    }
}
