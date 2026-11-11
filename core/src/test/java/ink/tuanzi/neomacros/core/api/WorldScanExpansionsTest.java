package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.world.WorldApi;
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
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Rot2d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world-scan expansions: findBlocks state/id-glob/shape filters, the
 * sliced async scanBlocks promise, and the entity inRadius type filter plus
 * inBox query, driven through the API over canned ports.
 */
class WorldScanExpansionsTest {

    private CannedWorld world;
    private CannedEntities entities;
    private CannedPlayer player;
    private WorldApi api;
    private EntityApi entityApi;

    @BeforeEach
    void install() {
        world = new CannedWorld();
        entities = new CannedEntities();
        player = new CannedPlayer();
        SinglePortAdapter adapter = new SinglePortAdapter(world, entities, player);
        ScriptApiEnvironment.install(null, adapter);
        api = new WorldApi();
        entityApi = new EntityApi();
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void findBlocksPassesEnhancedFilters() {
        world.foundBlocks.add(new Pos3d(1, 64, 2));
        List<ink.tuanzi.neomacros.core.api.types.Block> out = api.findBlocks(Map.of(
                "idPattern", "minecraft:*_log",
                "states", Map.of("axis", "y"),
                "center", Map.of("x", 0, "y", 64, "z", 0),
                "range", 8,
                "limit", 64,
                "shape", "sphere"));
        assertEquals(1, out.size());
        assertEquals("minecraft:*_log", world.lastRequest.get("idPattern"));
        assertEquals(Map.of("axis", "y"), world.lastRequest.get("states"));
        assertEquals(8, world.lastRequest.get("range"));
        assertEquals(64, world.lastRequest.get("limit"));
        assertEquals(true, world.lastRequest.get("sphere"));
        assertEquals(new Pos3d(0, 64, 0), world.lastRequest.get("center"));
    }

    @Test
    void findBlocksAcceptsTypesAlone() {
        api.findBlocks(Map.of(
                "types", "minecraft:stone",
                "center", Map.of("x", 1, "y", 2, "z", 3)));
        assertEquals(List.of("minecraft:stone"), world.lastRequest.get("types"));
        assertEquals(null, world.lastRequest.get("idPattern"));
        assertEquals(16, world.lastRequest.get("range"), "default range stays 16");
        assertEquals(128, world.lastRequest.get("limit"), "default limit stays 128");
        assertEquals(false, world.lastRequest.get("sphere"));
    }

    @Test
    void findBlocksRejectsEmptyFilters() {
        assertThrows(IllegalArgumentException.class, () -> api.findBlocks(Map.of(
                "center", Map.of("x", 0, "y", 0, "z", 0))));
        assertThrows(IllegalArgumentException.class, () -> api.findBlocks(null));
    }

    @Test
    void scanBlocksDefaultsCenterToThePlayer() {
        player.self = new PlayerSnapshot(UUID.randomUUID(), "Tester",
                new Pos3d(10, 70, -4), new Rot2d(0, 0), 20, 20, 300);
        world.foundBlocks.add(new Pos3d(10, 70, -4));
        List<ink.tuanzi.neomacros.core.api.types.Block> out =
                api.scanBlocks(Map.of("types", "minecraft:diamond_ore")).join();
        assertEquals(1, out.size());
        assertEquals(new Pos3d(10, 70, -4), world.lastRequest.get("center"));
        assertEquals(65536, world.lastRequest.get("limit"), "async scans default to the big cap");
        assertEquals(true, world.lastRequest.get("async"));
    }

    @Test
    void scanBlocksRejectsWithoutAnyCenter() {
        assertThrows(IllegalArgumentException.class,
                () -> api.scanBlocks(Map.of("types", "minecraft:stone")));
    }

    @Test
    void scanBlocksPropagatesPortFailure() {
        world.scanFailure = new IllegalStateException("already scanning");
        CompletableFuture<List<ink.tuanzi.neomacros.core.api.types.Block>> future =
                api.scanBlocks(Map.of("types", "minecraft:stone",
                        "center", Map.of("x", 0, "y", 0, "z", 0)));
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void inRadiusFiltersByTypes() {
        UUID sheepId = UUID.randomUUID();
        UUID cowId = UUID.randomUUID();
        entities.entities.add(snapshot(sheepId, "minecraft:sheep", 1, 64, 1));
        entities.entities.add(snapshot(cowId, "minecraft:cow", 2, 64, 2));
        List<EntityApi.Entity> all = entityApi.inRadius(0, 64, 0, 16, null);
        assertEquals(2, all.size());
        List<EntityApi.Entity> sheepOnly = entityApi.inRadius(0, 64, 0, 16, "minecraft:sheep");
        assertEquals(1, sheepOnly.size());
        assertEquals(sheepId.toString(), sheepOnly.get(0).getUuid());
        List<EntityApi.Entity> both = entityApi.inRadius(0, 64, 0, 16,
                List.of("minecraft:sheep", "minecraft:cow"));
        assertEquals(2, both.size());
    }

    @Test
    void inBoxSelectsTheSpannedVolume() {
        UUID insideId = UUID.randomUUID();
        UUID outsideId = UUID.randomUUID();
        entities.entities.add(snapshot(insideId, "minecraft:sheep", 5, 64, 5));
        entities.entities.add(snapshot(outsideId, "minecraft:sheep", 50, 64, 50));
        List<EntityApi.Entity> hits = entityApi.inBox(Map.of(
                "min", Map.of("x", 0, "y", 60, "z", 0),
                "max", Map.of("x", 10, "y", 70, "z", 10)));
        assertEquals(1, hits.size());
        assertEquals(insideId.toString(), hits.get(0).getUuid());
    }

    @Test
    void inBoxAppliesTheTypeFilter() {
        entities.entities.add(snapshot(UUID.randomUUID(), "minecraft:sheep", 5, 64, 5));
        entities.entities.add(snapshot(UUID.randomUUID(), "minecraft:zombie", 6, 64, 6));
        List<EntityApi.Entity> hits = entityApi.inBox(Map.of(
                "min", Map.of("x", 0, "y", 60, "z", 0),
                "max", Map.of("x", 10, "y", 70, "z", 10),
                "types", "minecraft:zombie"));
        assertEquals(1, hits.size());
        assertEquals("minecraft:zombie", hits.get(0).getType());
    }

    @Test
    void inBoxRejectsInvertedOrMissingCorners() {
        assertThrows(IllegalArgumentException.class, () -> entityApi.inBox(Map.of(
                "min", Map.of("x", 10, "y", 60, "z", 0),
                "max", Map.of("x", 0, "y", 70, "z", 10))));
        assertThrows(IllegalArgumentException.class, () -> entityApi.inBox(Map.of(
                "min", Map.of("x", 0, "y", 60, "z", 0))));
        assertThrows(IllegalArgumentException.class, () -> entityApi.inBox(null));
    }

    /** One living snapshot at the position (health positive). */
    private static EntitySnapshot snapshot(UUID uuid, String type, double x, double y, double z) {
        return new EntitySnapshot(uuid, type, type, new Pos3d(x, y, z), 10, 10,
                new Pos3d(0, 0, 0), new Pos3d(0, 0, 0), new Rot2d(0, 0), 1.8,
                true, false, false, false, false, false, false, false,
                "standing", "south", "", null, List.<UUID>of(), null, null, null, true);
    }

    /** World port capturing the last scan request and returning canned hits. */
    static final class CannedWorld implements WorldPort {
        final List<Pos3d> foundBlocks = new ArrayList<>();
        Map<String, Object> lastRequest = new LinkedHashMap<>();
        RuntimeException scanFailure;

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
        public Optional<BlockData> blockAt(Pos3d pos) {
            return Optional.empty();
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.RayHit> raycast(Pos3d origin,
                Pos3d direction, ink.tuanzi.neomacros.port.model.RayOptions options) {
            return Optional.empty();
        }

        @Override
        public List<Pos3d> findBlocksMatching(List<String> blockIds, String idPattern,
                Map<String, Object> stateFilters, Pos3d center, int range, int limit,
                boolean sphere) {
            lastRequest.put("types", blockIds == null ? List.of() : blockIds);
            lastRequest.put("idPattern", idPattern);
            lastRequest.put("states", stateFilters);
            lastRequest.put("center", center);
            lastRequest.put("range", range);
            lastRequest.put("limit", limit);
            lastRequest.put("sphere", sphere);
            return List.copyOf(foundBlocks);
        }

        @Override
        public CompletableFuture<List<Pos3d>> scanBlocks(List<String> blockIds,
                String idPattern, Map<String, Object> stateFilters, Pos3d center,
                int range, int limit, boolean sphere) {
            lastRequest.put("types", blockIds == null ? List.of() : blockIds);
            lastRequest.put("idPattern", idPattern);
            lastRequest.put("states", stateFilters);
            lastRequest.put("center", center);
            lastRequest.put("range", range);
            lastRequest.put("limit", limit);
            lastRequest.put("sphere", sphere);
            lastRequest.put("async", true);
            CompletableFuture<List<Pos3d>> future = new CompletableFuture<>();
            if (scanFailure != null) {
                future.completeExceptionally(scanFailure);
            } else {
                future.complete(List.copyOf(foundBlocks));
            }
            return future;
        }
    }

    /** Entity port over a canned snapshot list. */
    static final class CannedEntities implements EntityPort {
        final List<EntitySnapshot> entities = new ArrayList<>();

        @Override
        public Optional<EntitySnapshot> byUuid(UUID uuid) {
            return entities.stream().filter(e -> e.uuid().equals(uuid)).findFirst();
        }

        @Override
        public List<EntitySnapshot> inRadius(Pos3d center, double range,
                Predicate<EntitySnapshot> filter) {
            double squared = range * range;
            List<EntitySnapshot> hits = new ArrayList<>();
            for (EntitySnapshot entity : entities) {
                double dx = entity.pos().x() - center.x();
                double dy = entity.pos().y() - center.y();
                double dz = entity.pos().z() - center.z();
                if (dx * dx + dy * dy + dz * dz <= squared && filter.test(entity)) {
                    hits.add(entity);
                }
            }
            return List.copyOf(hits);
        }

        @Override
        public List<EntitySnapshot> inBox(Pos3d min, Pos3d max,
                Predicate<EntitySnapshot> filter) {
            List<EntitySnapshot> hits = new ArrayList<>();
            for (EntitySnapshot entity : entities) {
                Pos3d pos = entity.pos();
                if (pos.x() < min.x() || pos.x() > max.x()
                        || pos.y() < min.y() || pos.y() > max.y()
                        || pos.z() < min.z() || pos.z() > max.z()) {
                    continue;
                }
                if (filter.test(entity)) {
                    hits.add(entity);
                }
            }
            return List.copyOf(hits);
        }
    }

    /** Player port serving one settable self snapshot; actions are unused. */
    static final class CannedPlayer implements PlayerPort {
        volatile PlayerSnapshot self;

        @Override
        public Optional<PlayerSnapshot> self() {
            return Optional.ofNullable(self);
        }

        @Override
        public List<ink.tuanzi.neomacros.port.model.EffectSnapshot> activeEffects() {
            return List.of();
        }

        @Override
        public void onAttackBlock(java.util.function.Function<
                ink.tuanzi.neomacros.port.model.BlockInteraction, Boolean> sink) {
        }

        @Override
        public void onAttackEntity(java.util.function.Function<EntitySnapshot, Boolean> sink) {
        }

        @Override
        public void onInteractBlock(java.util.function.Function<
                ink.tuanzi.neomacros.port.model.BlockInteraction, Boolean> sink) {
        }

        @Override
        public void onInteractEntity(java.util.function.Function<EntitySnapshot, Boolean> sink) {
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
        public CompletableFuture<Void> useBlock(
                ink.tuanzi.neomacros.port.model.BlockInteraction hit, String hand) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> useItem(String hand) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> attackBlock(
                ink.tuanzi.neomacros.port.model.BlockInteraction hit) {
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
    }

    /** Adapter wiring only the ports these tests touch. */
    record SinglePortAdapter(WorldPort world, EntityPort entity, PlayerPort player)
            implements NeomacrosAdapter {
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
            return world;
        }

        @Override
        public PlayerPort player() {
            return player;
        }

        @Override
        public EntityPort entity() {
            return entity;
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
