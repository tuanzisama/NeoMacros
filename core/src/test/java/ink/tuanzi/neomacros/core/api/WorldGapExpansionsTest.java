package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.InputPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.core.api.world.WorldApi;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BossBarInfoData;
import ink.tuanzi.neomacros.port.model.ChunkData;
import ink.tuanzi.neomacros.port.model.LightData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ScoreboardData;
import ink.tuanzi.neomacros.port.model.ScoreboardObjectiveData;
import ink.tuanzi.neomacros.port.model.TabListEntryInfoData;
import ink.tuanzi.neomacros.port.model.TabListInfoData;
import ink.tuanzi.neomacros.port.model.TeamData;
import ink.tuanzi.neomacros.port.model.TpsData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The world gap expansions: level name, moon phase, tab list, boss bars,
 * scoreboards, tick rate estimate, the sound/particle actions, and the G-010
 * world queries (respawn position, light levels, sound-file playback), driven
 * through the API over a canned port.
 */
class WorldGapExpansionsTest {

    private CannedWorld world;
    private WorldApi api;

    @BeforeEach
    void install() {
        world = new CannedWorld();
        ScriptApiEnvironment.install(null, new SinglePortAdapter(world));
        api = new WorldApi();
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void levelNamePassesThrough() {
        world.levelName = "New World";
        assertEquals("New World", api.getLevelName());
    }

    @Test
    void moonPhasePassesThrough() {
        world.moonPhase = 4;
        assertEquals(4, api.getMoonPhase());
    }

    @Test
    void tpsRendersWindowShape() {
        world.tps = new TpsData(19.8, 19.5, 19.9, 20.0);
        TpsData view = api.getTps();
        assertEquals(19.8, view.instant());
        assertEquals(19.5, view.m1());
        assertEquals(19.9, view.m5());
        assertEquals(20.0, view.m15());
    }

    @Test
    void tpsIsNullBeforeFirstSample() {
        world.tps = null;
        assertNull(api.getTps());
    }

    @Test
    void tabListRendersEntries() {
        TeamData team = new TeamData("red", "Red Team", "red", 14, 0xFF5555,
                List.of("Alice"));
        world.tabList = new TabListInfoData("header", "footer", List.of(
                new TabListEntryInfoData("u-1", "Alice", 42, "survival", "Al",
                        new byte[] {1, 2, 3},
                        "minecraft:textures/entity/steve.png",
                        "https://skins.example/steve.png",
                        "minecraft:textures/entity/cape.png", null, true, false, team),
                new TabListEntryInfoData("u-2", "Bob", 7, "creative", null,
                        null, null, null, null, null, false, true, null)));
        TabListInfoData view = api.getTabList();
        assertEquals("header", view.header());
        assertEquals("footer", view.footer());
        List<TabListEntryInfoData> entries = view.entries();
        assertEquals(2, entries.size());
        assertEquals("u-1", entries.get(0).uuid());
        assertEquals("Alice", entries.get(0).name());
        assertEquals(42, entries.get(0).latency());
        assertEquals("survival", entries.get(0).gamemode());
        assertEquals("Al", entries.get(0).displayName());
        assertNull(entries.get(1).displayName());
        // G-018B profile facts: key, skin, cape, slim model, team
        assertEquals(3, entries.get(0).publicKey().length);
        assertEquals("minecraft:textures/entity/steve.png",
                entries.get(0).skinTexture());
        assertEquals("https://skins.example/steve.png", entries.get(0).skinUrl());
        assertEquals("minecraft:textures/entity/cape.png",
                entries.get(0).capeTexture());
        assertNull(entries.get(0).elytraTexture());
        assertTrue(entries.get(0).cape());
        assertFalse(entries.get(0).slimModel());
        assertEquals("red", entries.get(0).team().colorName());
        assertNull(entries.get(1).publicKey());
        assertNull(entries.get(1).skinTexture());
        assertNull(entries.get(1).capeTexture());
        assertFalse(entries.get(1).cape());
        assertTrue(entries.get(1).slimModel());
        assertNull(entries.get(1).team());
    }

    @Test
    void bossBarsRenderShapeAndStayEmptyByDefault() {
        assertTrue(api.getBossBars().isEmpty());
        world.bossBars.add(new BossBarInfoData("b-1", "Ender Dragon", 0.5f, "purple",
                "notched_10", 0xAA00AA, "dark_purple"));
        List<BossBarInfoData> bars = api.getBossBars();
        assertEquals(1, bars.size());
        assertEquals("b-1", bars.get(0).uuid());
        assertEquals("Ender Dragon", bars.get(0).name());
        assertEquals(0.5f, bars.get(0).progress());
        assertEquals("purple", bars.get(0).color());
        // G-018B bar style and derived color facts
        assertEquals("notched_10", bars.get(0).style());
        assertEquals(0xAA00AA, bars.get(0).colorValue());
        assertEquals("dark_purple", bars.get(0).colorFormat());
    }

    @Test
    void scoreboardsRenderObjectivesAndScores() {
        world.scoreboard = new ScoreboardData(
                List.of(new ScoreboardData.ObjectiveData("kills", "Kills",
                        "playerKillCount", "integer")),
                List.of(new ScoreboardData.ScoreEntryData("kills", "Alice", 12)));
        ScoreboardData view = api.getScoreboards();
        List<ScoreboardData.ObjectiveData> objectives = view.objectives();
        List<ScoreboardData.ScoreEntryData> scores = view.scores();
        assertEquals(1, objectives.size());
        assertEquals("kills", objectives.get(0).name());
        assertEquals("playerKillCount", objectives.get(0).criteria());
        assertEquals("integer", objectives.get(0).renderType());
        assertEquals(12, scores.get(0).value());
        assertEquals("Alice", scores.get(0).owner());
    }

    @Test
    void scoreboardsNullWithoutWorld() {
        world.scoreboard = null;
        assertNull(api.getScoreboards());
    }

    @Test
    void scoreboardObjectiveRendersDerivedFace() {
        ScoreboardObjectiveData objective = new ScoreboardObjectiveData(
                "sidebar", "Sidebar", "dummy", "integer",
                Map.of("Alice", 12, "Bob", 7),
                Map.of(12, "Alice", 7, "Bob"),
                List.of("Alice", "Bob"),
                List.of("Alice", "Bob"),
                List.of("Alice", "Bob"));
        world.displayObjectives.put(1, objective);

        ScoreboardObjectiveData sidebar = api.getObjectiveSlot(1);
        assertNotNull(sidebar, "slot 1 serves the canned objective");
        assertEquals("sidebar", sidebar.name());
        assertEquals("Sidebar", sidebar.displayName());
        assertEquals("dummy", sidebar.criteria());
        assertEquals("integer", sidebar.renderType());
        assertEquals(12, sidebar.playerScores().get("Alice"));
        assertEquals(7, sidebar.playerScores().get("Bob"));
        assertEquals("Alice", sidebar.scoreToDisplayName().get(12));
        assertEquals("Bob", sidebar.scoreToDisplayName().get(7));
        assertEquals(List.of("Alice", "Bob"), sidebar.texts());
        assertEquals(List.of("Alice", "Bob"), sidebar.knownPlayers());
        assertEquals(List.of("Alice", "Bob"), sidebar.knownPlayersDisplayNames());

        assertNull(api.getObjectiveSlot(99), "out-of-range slot reads undefined");
        assertNull(api.getObjectiveForTeamColorIndex(-1),
                "negative colour index reads undefined");
        assertNull(api.getObjectiveForTeamColorIndex(16),
                "colour index past white reads undefined");

        // No local team yet: the plain sidebar fallback.
        ScoreboardObjectiveData fallback = api.getCurrentScoreboard();
        assertNotNull(fallback, "the plain sidebar answers without a team");
        assertEquals("sidebar", fallback.name());

        // With a team whose colour index maps onto slot 17, the team
        // sidebar wins and the colour-index lookup resolves it too.
        world.localTeam = new TeamData("red", "Red Team", "red", 14, 0xFF5555,
                List.of("Alice"));
        world.displayObjectives.put(17, objective);
        assertEquals("sidebar", api.getCurrentScoreboard().name());
        assertNotNull(api.getObjectiveForTeamColorIndex(14));
        assertEquals("sidebar", api.getObjectiveForTeamColorIndex(14).name());
    }

    @Test
    void teamsAndTeamColorsRenderFromCannedTeams() {
        TeamData red = new TeamData("red", "Red Team", "red", 14, 0xFF5555,
                List.of("Alice"));
        TeamData plain = new TeamData("plain", "Plain Team", "reset", -1, -1,
                List.of("Bob"));
        world.teams.add(red);
        world.teams.add(plain);

        List<TeamData> teams = api.getTeams();
        assertEquals(2, teams.size());
        assertEquals("Red Team", teams.get(0).displayName());
        assertEquals(List.of("Alice"), teams.get(0).players());
        assertEquals(14, teams.get(0).colorIndex());
        assertEquals(0xFF5555, teams.get(0).colorValue());

        var alice = new ink.tuanzi.neomacros.core.api.player.PlayerApi.Player(
                java.util.UUID.randomUUID());

        // Local player on the coloured team.
        world.localTeam = red;
        assertEquals("red", api.getPlayerTeam().name());
        assertEquals(14, api.getPlayerTeamColorIndex());
        assertEquals(0xFF5555, api.getTeamColor());
        assertEquals("red", api.getTeamColorName());
        assertEquals("red", api.getTeamColorFormatting());

        // The explicit player resolves through team membership.
        assertEquals("red", api.getPlayerTeam(alice).name());
        assertEquals(14, api.getPlayerTeamColorIndex(alice));
        assertEquals(0xFF5555, api.getTeamColor(alice));
        assertEquals("red", api.getTeamColorName(alice));
        assertEquals("red", api.getTeamColorFormatting(alice));

        // A team without a colour reports the reset formatting.
        world.localTeam = plain;
        assertEquals("plain", api.getPlayerTeam().name());
        assertEquals(-1, api.getPlayerTeamColorIndex());
        assertEquals(-1, api.getTeamColor());
        assertEquals("reset", api.getTeamColorName());
        assertEquals("reset", api.getTeamColorFormatting());

        // No world/team at all: absent team, -1 sentinels, null names.
        world.teams.clear();
        world.localTeam = null;
        assertNull(api.getPlayerTeam());
        assertNull(api.getPlayerTeam(alice));
        assertEquals(-1, api.getPlayerTeamColorIndex());
        assertEquals(-1, api.getPlayerTeamColorIndex(alice));
        assertEquals(-1, api.getTeamColor());
        assertNull(api.getTeamColorName());
        assertNull(api.getTeamColorName(alice));
        assertNull(api.getTeamColorFormatting(alice));
        assertNull(api.getPlayerTeam(null), "a null player reads null");
    }

    @Test
    void chunkAtRendersHandleMembers() {
        world.chunk = new ChunkData(-3, 7, -64, 319, 4321,
                Map.of("world_surface", new int[256]), List.of());
        var handle = api.chunkAt(-45, 64, 118);
        assertEquals(-3, handle.getChunkX());
        assertEquals(7, handle.getChunkZ());
        assertEquals(-64, handle.getMinY());
        assertEquals(319, handle.getMaxY());
        assertEquals(4321L, handle.getInhabitedTime());
        assertEquals(0, handle.heightAt(-45, 118));
        assertNull(handle.heightAt(5, 5), "height outside the chunk is absent");
        assertTrue(handle.contains(-48, 64, 112));
        assertFalse(handle.contains(-32, 64, 112));
        assertTrue(handle.getBlockEntities().isEmpty());
    }

    @Test
    void chunkAtIsNullWhenUnloaded() {
        world.chunk = null;
        assertNull(api.chunkAt(0, 64, 0));
    }

    @Test
    void findBlocksDelegatesAndWrapsPositions() {
        world.foundBlocks.add(new Pos3d(1, 64, 1));
        world.foundBlocks.add(new Pos3d(2, 64, 2));
        var blocks = api.findBlocks(Map.of("types", "minecraft:diamond_ore",
                "center", Map.of("x", 0, "y", 64, "z", 0)));
        assertEquals(2, blocks.size());
        assertEquals(1.0, blocks.get(0).getPos().getX());
    }

    @Test
    void findBlocksRejectsMissingTypesOrCenter() {
        try {
            api.findBlocks(Map.of("center", Map.of("x", 0, "y", 0, "z", 0)));
            throw new AssertionError("expected rejection");
        } catch (IllegalArgumentException expected) {
            // missing types as designed
        }
        try {
            api.findBlocks(Map.of("types", "minecraft:stone"));
            throw new AssertionError("expected rejection");
        } catch (IllegalArgumentException expected) {
            // missing center as designed
        }
    }

    @Test
    void playSoundAndParticlesDelegateToPort() {
        api.playSound("minecraft:entity.pig.ambient", 1, 2, 3, 0.5, 1.5);
        assertEquals("minecraft:entity.pig.ambient", world.lastSound);
        assertEquals(0.5, world.lastVolume);
        assertEquals(1.5, world.lastPitch);
        api.spawnParticle("cloud", 4, 5, 6, 10, 0.2, 0.2, 0.2, 0.1);
        assertEquals("cloud", world.lastParticle);
        assertEquals(10, world.lastCount);
    }

    @Test
    void respawnPosRendersBlockPosHandle() {
        world.respawnPos = new Pos3d(12, 64, -8);
        var handle = api.getRespawnPos();
        assertEquals(12.0, handle.getX());
        assertEquals(64.0, handle.getY());
        assertEquals(-8.0, handle.getZ());
    }

    @Test
    void respawnPosIsNullWithoutWorld() {
        world.respawnPos = null;
        assertNull(api.getRespawnPos());
    }

    @Test
    void lightAtRendersBothLayers() {
        world.light = new LightData(15, 7);
        LightData view = api.lightAt(0, 64, 0);
        assertEquals(15, view.sky());
        assertEquals(7, view.block());
    }

    @Test
    void lightAtIsNullWithoutWorld() {
        world.light = null;
        assertNull(api.lightAt(0, 64, 0));
    }

    @Test
    void playSoundFileDelegatesAndResolvesOnce() {
        var started = api.playSoundFile("sfx/hit.wav", 0.5);
        assertTrue(started.isDone(), "the canned port resolves immediately");
        assertEquals(List.of("sfx/hit.wav"), world.playedSoundFiles);
        assertEquals(0.5, world.lastSoundFileVolume);
    }

    /** World port with canned fields the tests mutate directly. */
    static final class CannedWorld implements WorldPort {
        String levelName = "";
        int moonPhase;
        TabListInfoData tabList;
        ScoreboardData scoreboard;
        TpsData tps;
        ChunkData chunk;
        final List<BossBarInfoData> bossBars = new java.util.ArrayList<>();
        final List<TeamData> teams = new java.util.ArrayList<>();
        TeamData localTeam;
        final java.util.Map<Integer, ScoreboardObjectiveData> displayObjectives =
                new java.util.HashMap<>();
        final List<Pos3d> foundBlocks = new java.util.ArrayList<>();
        String lastSound;
        double lastVolume;
        double lastPitch;
        String lastParticle;
        int lastCount;
        Pos3d respawnPos;
        LightData light;
        final List<String> playedSoundFiles = new java.util.ArrayList<>();
        double lastSoundFileVolume;

        @Override
        public long time() {
            return 0;
        }

        @Override
        public String dimension() {
            return "minecraft:overworld";
        }

        @Override
        public boolean isRaining() {
            return false;
        }

        @Override
        public java.util.Optional<BlockData> blockAt(Pos3d pos) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.RayHit> raycast(
                Pos3d origin, Pos3d direction,
                ink.tuanzi.neomacros.port.model.RayOptions options) {
            return java.util.Optional.empty();
        }

        @Override
        public String levelName() {
            return levelName;
        }

        @Override
        public int moonPhase() {
            return moonPhase;
        }

        @Override
        public java.util.Optional<TabListInfoData> tabList() {
            return java.util.Optional.ofNullable(tabList);
        }

        @Override
        public java.util.List<BossBarInfoData> bossBars() {
            return List.copyOf(bossBars);
        }

        @Override
        public java.util.List<TeamData> teams() {
            return List.copyOf(teams);
        }

        @Override
        public java.util.Optional<TeamData> localPlayerTeam() {
            return java.util.Optional.ofNullable(localTeam);
        }

        @Override
        public java.util.Optional<ScoreboardObjectiveData> displayObjective(int slot) {
            return java.util.Optional.ofNullable(displayObjectives.get(slot));
        }

        @Override
        public java.util.Optional<ScoreboardData> scoreboards() {
            return java.util.Optional.ofNullable(scoreboard);
        }

        @Override
        public java.util.Optional<TpsData> tps() {
            return java.util.Optional.ofNullable(tps);
        }

        @Override
        public java.util.Optional<ChunkData> chunkAt(Pos3d pos) {
            return java.util.Optional.ofNullable(chunk);
        }

        @Override
        public java.util.List<Pos3d> findBlocks(java.util.List<String> blockIds,
                Pos3d center, int range, int limit) {
            return java.util.List.copyOf(foundBlocks);
        }

        @Override
        public java.util.List<Pos3d> findBlocksMatching(java.util.List<String> blockIds,
                String idPattern, java.util.Map<String, Object> stateFilters,
                Pos3d center, int range, int limit, boolean sphere) {
            return java.util.List.copyOf(foundBlocks);
        }

        @Override
        public void playSound(String soundId, Pos3d pos, double volume, double pitch) {
            lastSound = soundId;
            lastVolume = volume;
            lastPitch = pitch;
        }

        @Override
        public void spawnParticle(String particleId, Pos3d pos, int count, Pos3d offset,
                double speed) {
            lastParticle = particleId;
            lastCount = count;
        }

        @Override
        public java.util.Optional<Pos3d> respawnPos() {
            return java.util.Optional.ofNullable(respawnPos);
        }

        @Override
        public java.util.Optional<LightData> lightAt(Pos3d pos) {
            return java.util.Optional.ofNullable(light);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> playSoundFile(String path,
                double volume) {
            playedSoundFiles.add(path);
            lastSoundFileVolume = volume;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    /** Adapter that only serves the world port; other ports are unused here. */
    record SinglePortAdapter(WorldPort world) implements NeomacrosAdapter {
        @Override
        public LifecyclePort lifecycle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityPort entity() {
            // Canned so player handles resolve a scoreboard name for the
            // G-018B team lookups; only byUuid is ever consulted here.
            return new EntityPort() {
                @Override
                public java.util.Optional<ink.tuanzi.neomacros.port.model.EntitySnapshot>
                        byUuid(java.util.UUID uuid) {
                    return java.util.Optional.of(
                            new ink.tuanzi.neomacros.port.model.EntitySnapshot(uuid,
                                    "minecraft:player", "Alice", new Pos3d(0, 64, 0), 20));
                }

                @Override
                public List<ink.tuanzi.neomacros.port.model.EntitySnapshot> inRadius(
                        Pos3d center, double range,
                        java.util.function.Predicate<
                                ink.tuanzi.neomacros.port.model.EntitySnapshot> filter) {
                    return List.of();
                }
            };
        }

        @Override
        public PlayerPort player() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientPort client() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InventoryPort inventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ink.tuanzi.neomacros.port.DialogPort dialog() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPort render() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Draw3DPort draw3d() {
            throw new UnsupportedOperationException();
        }
    }
}
