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
import ink.tuanzi.neomacros.port.model.BossBarSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ScoreboardData;
import ink.tuanzi.neomacros.port.model.TabListData;
import ink.tuanzi.neomacros.port.model.TpsData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world gap expansions: level name, moon phase, tab list, boss bars,
 * scoreboards, tick rate estimate, and the sound/particle actions, driven
 * through the facade over a canned port.
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
        Map<String, Object> view = api.getTps();
        assertEquals(19.8, view.get("instant"));
        assertEquals(19.5, view.get("m1"));
        assertEquals(19.9, view.get("m5"));
        assertEquals(20.0, view.get("m15"));
    }

    @Test
    void tpsIsNullBeforeFirstSample() {
        world.tps = null;
        assertNull(api.getTps());
    }

    @Test
    void tabListRendersEntries() {
        world.tabList = new TabListData("header", "footer", List.of(
                new TabListData.TabListEntry("u-1", "Alice", 42, "survival", "Al"),
                new TabListData.TabListEntry("u-2", "Bob", 7, "creative", null)));
        Map<String, Object> view = api.getTabList();
        assertEquals("header", view.get("header"));
        assertEquals("footer", view.get("footer"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) view.get("entries");
        assertEquals(2, entries.size());
        assertEquals("u-1", entries.get(0).get("uuid"));
        assertEquals("Alice", entries.get(0).get("name"));
        assertEquals(42, entries.get(0).get("latency"));
        assertEquals("survival", entries.get(0).get("gamemode"));
        assertEquals("Al", entries.get(0).get("displayName"));
        assertNull(entries.get(1).get("displayName"));
    }

    @Test
    void bossBarsRenderShapeAndStayEmptyByDefault() {
        assertTrue(api.getBossBars().isEmpty());
        world.bossBars.add(new BossBarSnapshot("b-1", "Ender Dragon", 0.5f, "purple"));
        List<Map<String, Object>> bars = api.getBossBars();
        assertEquals(1, bars.size());
        assertEquals("b-1", bars.get(0).get("uuid"));
        assertEquals("Ender Dragon", bars.get(0).get("name"));
        assertEquals(0.5f, bars.get(0).get("progress"));
        assertEquals("purple", bars.get(0).get("color"));
    }

    @Test
    void scoreboardsRenderObjectivesAndScores() {
        world.scoreboard = new ScoreboardData(
                List.of(new ScoreboardData.ObjectiveData("kills", "Kills",
                        "playerKillCount", "integer")),
                List.of(new ScoreboardData.ScoreEntryData("kills", "Alice", 12)));
        Map<String, Object> view = api.getScoreboards();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> objectives =
                (List<Map<String, Object>>) view.get("objectives");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scores = (List<Map<String, Object>>) view.get("scores");
        assertEquals(1, objectives.size());
        assertEquals("kills", objectives.get(0).get("name"));
        assertEquals("playerKillCount", objectives.get(0).get("criteria"));
        assertEquals("integer", objectives.get(0).get("renderType"));
        assertEquals(12, scores.get(0).get("value"));
        assertEquals("Alice", scores.get(0).get("owner"));
    }

    @Test
    void scoreboardsNullWithoutWorld() {
        world.scoreboard = null;
        assertNull(api.getScoreboards());
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

    /** World port with canned fields the tests mutate directly. */
    static final class CannedWorld implements WorldPort {
        String levelName = "";
        int moonPhase;
        TabListData tabList;
        ScoreboardData scoreboard;
        TpsData tps;
        final List<BossBarSnapshot> bossBars = new java.util.ArrayList<>();
        String lastSound;
        double lastVolume;
        double lastPitch;
        String lastParticle;
        int lastCount;

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
        public java.util.Optional<TabListData> tabList() {
            return java.util.Optional.ofNullable(tabList);
        }

        @Override
        public java.util.List<BossBarSnapshot> bossBars() {
            return List.copyOf(bossBars);
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
            throw new UnsupportedOperationException();
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
        public RenderPort render() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Draw3DPort draw3d() {
            throw new UnsupportedOperationException();
        }
    }
}
