package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.types.Block;
import ink.tuanzi.neomacros.core.api.types.BlockPos;
import ink.tuanzi.neomacros.core.api.types.BlockState;
import ink.tuanzi.neomacros.core.api.types.Chunk;
import ink.tuanzi.neomacros.core.api.types.RayHit;
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
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.BlockTypeData;
import ink.tuanzi.neomacros.port.model.ChunkData;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.RayOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The G-017 block/chunk gap face: the block type facts (default state, item
 * form, physics multipliers, tags, states, name), the block state physics
 * flags and position queries, the chunk geometry/heightmap/entity/
 * containment members, the raycast inside flag, and the raw block-entity
 * hatch, all driven through the API over canned ports. The boundaries cover
 * unloaded worlds (undefined), unknown tags (false), unknown heightmap
 * types (undefined), and absent extras (undefined).
 */
class BlockChunkGapExpansionsTest {

    private static final UUID SHEEP = UUID.randomUUID();

    private CannedWorld world;
    private CannedEntities entities;

    @BeforeEach
    void install() {
        world = new CannedWorld();
        entities = new CannedEntities();
        ScriptApiEnvironment.install(null, new DualPortAdapter(world, entities));
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void blockTypeMembersReadThroughTheRegistryFace() {
        world.block = stoneBlock();
        world.type = stoneType();

        Block block = new Block(new BlockPos(1, 64, 2));
        assertEquals("minecraft:stone", block.getState().getId(), "getId stays on state.id");
        assertEquals(Map.of("facing", "north"), block.getState().getProps(),
                "getBlockState maps to the state props");
        assertEquals("minecraft:stone", block.getDefaultState().getId(),
                "the default state carries the type identifier");
        assertEquals(Map.of("facing", "north"), block.getDefaultState().getProps());
        assertEquals("minecraft:stone", block.getDefaultItemStack().getId());
        assertEquals(1, block.getDefaultItemStack().getCount());
        assertFalse(block.canMobSpawnInside());
        assertTrue(block.hasDynamicBounds());
        assertEquals(1.5f, block.getBlastResistance());
        assertEquals(0.4f, block.getJumpVelocityMultiplier());
        assertEquals(0.6f, block.getSlipperiness());
        assertEquals(1.0f, block.getVelocityMultiplier());
        assertEquals(List.of("minecraft:base_stone_overworld"), block.getTags());
        assertEquals("Stone", block.getName());
        assertEquals(2, block.getStates().size());
        assertEquals(Map.of("half", "bottom"), block.getStates().get(1).getProps());
    }

    @Test
    void blockTagQueriesNormalizeAndAnswerFalseForUnknownTags() {
        world.block = stoneBlock();
        world.type = stoneType();

        Block block = new Block(new BlockPos(1, 64, 2));
        assertTrue(block.hasTag("base_stone_overworld"), "namespace defaults to minecraft");
        assertTrue(block.hasTag("#minecraft:base_stone_overworld"));
        assertFalse(block.hasTag("minecraft:does_not_exist"), "unknown tag is false");
        assertFalse(block.hasTag("does_not_exist"), "unknown bare tag is false");
    }

    @Test
    void blockTypeReadsAreUndefinedWithoutAWorldOrBlock() {
        Block block = new Block(new BlockPos(1, 64, 2));
        assertNull(block.getState());
        assertNull(block.getDefaultState());
        assertNull(block.getDefaultItemStack());
        assertNull(block.canMobSpawnInside());
        assertNull(block.hasDynamicBounds());
        assertNull(block.getBlastResistance());
        assertNull(block.getJumpVelocityMultiplier());
        assertNull(block.getSlipperiness());
        assertNull(block.getVelocityMultiplier());
        assertNull(block.getTags());
        assertNull(block.hasTag("minecraft:stone"));
        assertNull(block.getStates());
        assertNull(block.getName());
        assertNull(block.getRawBlockEntity());

        world.block = stoneBlock();
        assertNull(block.getDefaultState(), "a block without registry facts stays undefined");
        assertNull(block.hasTag("minecraft:stone"));
        assertNull(block.getName());
    }

    @Test
    void defaultItemStackIsUndefinedWithoutAnItemForm() {
        world.block = stoneBlock();
        world.type = new BlockTypeData("minecraft:fire", "Fire", List.of(),
                0.0f, 0.4f, 0.6f, 1.0f, false, false,
                Map.of(), List.of(), null);

        assertNull(new Block(new BlockPos(1, 64, 2)).getDefaultItemStack());
    }

    @Test
    void rawBlockEntityCrossesThePortHatch() {
        Object raw = new Object();
        world.rawBlockEntity = raw;
        assertSame(raw, new Block(new BlockPos(1, 64, 2)).getRawBlockEntity());

        world.rawBlockEntity = null;
        assertNull(new Block(new BlockPos(1, 64, 2)).getRawBlockEntity());
    }

    @Test
    void statePhysicsFlagsReadFromTheExtrasSnapshot() {
        world.block = stoneBlock();
        BlockState state = new Block(new BlockPos(1, 64, 2)).getState();

        assertTrue(state.exceedsCube());
        assertFalse(state.hasComparatorOutput());
        assertTrue(state.blocksMovement());
        assertFalse(state.isSolid());

        BlockState bare = new BlockState("minecraft:stone", Map.of());
        assertNull(bare.exceedsCube(), "absent extras read as undefined");
        assertNull(bare.hasComparatorOutput());
        assertNull(bare.blocksMovement());
        assertNull(bare.isSolid());
    }

    @Test
    void stateTagAndPositionQueriesDelegateToThePort() {
        world.block = stoneBlock();
        world.type = stoneType();
        world.allowsSpawning = true;
        world.suffocates = false;

        BlockState state = new Block(new BlockPos(1, 64, 2)).getState();
        assertTrue(state.hasTag("#minecraft:base_stone_overworld"));
        assertFalse(state.hasTag("minecraft:absent"));
        assertTrue(state.allowsSpawning(1, 64, 2, "sheep"));
        assertFalse(state.shouldSuffocate(1, 64, 2));

        world.allowsSpawning = null;
        world.suffocates = null;
        assertNull(state.allowsSpawning(1, 64, 2, "sheep"), "no world answers undefined");
        assertNull(state.shouldSuffocate(1, 64, 2));

        world.type = null;
        assertNull(state.hasTag("minecraft:stone"), "no registry facts answers undefined");
    }

    @Test
    void chunkGeometryIsAvailableWithoutAWorld() {
        Chunk chunk = new Chunk(-2, 3);
        assertEquals(-32, chunk.getMinBlock().getX());
        assertEquals(0, chunk.getMinBlock().getY());
        assertEquals(48, chunk.getMinBlock().getZ());
        BlockPos offset = chunk.offsetBlock(5, 100, 7);
        assertEquals(-27, offset.getX());
        assertEquals(100, offset.getY());
        assertEquals(55, offset.getZ());

        assertNull(chunk.getHeight(), "an unloaded chunk has no build height");
        assertNull(chunk.topYAt(0, 0, "world_surface"));
        assertTrue(chunk.getEntities().isEmpty());
        assertNull(chunk.containsAny("minecraft:stone"));
        assertNull(chunk.containsAll("minecraft:stone"));
    }

    @Test
    void chunkHeightAndBlockEntitiesReadTheCannedSnapshot() {
        world.chunk = new ChunkData(-2, 3, -64, 319, 7,
                Map.of("world_surface", new int[256]),
                List.of(new Pos3d(-25, 70, 50)));

        Chunk chunk = new Chunk(-2, 3);
        assertEquals(384, chunk.getHeight());
        assertEquals(-64, chunk.getMinY());
        assertEquals(319, chunk.getMaxY());
        assertEquals(1, chunk.getBlockEntities().size());
        assertEquals(-25, chunk.getBlockEntities().get(0).getX());
    }

    @Test
    void chunkTopYAtReadsHeightmapColumnsAndRejectsUnknownTypes() {
        int[] surface = new int[256];
        surface[3 + 4 * 16] = 96;
        world.chunk = new ChunkData(0, 0, -64, 319, 0,
                Map.of("world_surface", surface), List.of());

        Chunk chunk = new Chunk(0, 0);
        assertEquals(96, chunk.topYAt(3, 4, "world_surface"));
        assertEquals(96, chunk.topYAt(3, 4, "WORLD_SURFACE"), "type lookup ignores case");
        assertNull(chunk.topYAt(3, 4, "ocean_floor"), "an absent heightmap is undefined");
        assertNull(chunk.topYAt(3, 4, "not_a_heightmap"), "an unknown type is undefined");
        assertNull(chunk.topYAt(16, 4, "world_surface"), "out-of-range offsets are undefined");
        assertNull(chunk.topYAt(3, -1, "world_surface"));
    }

    @Test
    void chunkEntitiesWrapThePortSnapshots() {
        world.chunk = new ChunkData(0, 0, -64, 319, 0, Map.of(), List.of());
        entities.inBox.add(new EntitySnapshot(SHEEP, "minecraft:sheep", "Sheep",
                new Pos3d(4, 64, 4), 8));

        List<EntityApi.Entity> found = new Chunk(0, 0).getEntities();
        assertEquals(1, found.size());
        assertEquals(SHEEP.toString(), found.get(0).getUuid());
    }

    @Test
    void chunkContainmentNormalizesRequestsAndKeepsEmptyShortcuts() {
        world.chunk = new ChunkData(0, 0, -64, 319, 0, Map.of(), List.of());

        Chunk chunk = new Chunk(0, 0);
        assertFalse(chunk.containsAny(List.of()), "an empty any-request is false");
        assertTrue(chunk.containsAll(List.of()), "an empty all-request is true");
        assertFalse(chunk.containsAny((Object) null), "a null request is an empty request");
        assertNull(world.lastChunkContainsIds, "empty requests never reach the port");

        world.containsAny = true;
        world.containsAll = false;
        assertTrue(chunk.containsAny("minecraft:stone"));
        assertTrue(chunk.containsAny("stone"), "ids pass through for the adapter to resolve");
        assertFalse(chunk.containsAll(List.of("minecraft:stone", "minecraft:dirt")));
        assertEquals(List.of("minecraft:stone", "minecraft:dirt"), world.lastChunkContainsIds);
    }

    @Test
    void rayInsideBlockOnlyAppliesToBlockHits() {
        world.block = stoneBlock();
        world.ray = new ink.tuanzi.neomacros.port.model.RayHit(
                new BlockInteraction("minecraft:stone", new Pos3d(1, 64, 2), "up", true),
                null, new Pos3d(1.5, 64, 2), 2.5);
        assertTrue(ray().isInsideBlock());
        assertTrue(ray().isHit());
        assertEquals("up", ray().getFace());
        assertEquals("minecraft:stone", ray().getBlock().getState().getId());

        world.ray = new ink.tuanzi.neomacros.port.model.RayHit(
                new BlockInteraction("minecraft:stone", new Pos3d(1, 64, 2), "up", false),
                null, new Pos3d(1, 64, 2), 1.0);
        assertFalse(ray().isInsideBlock());

        world.ray = new ink.tuanzi.neomacros.port.model.RayHit(
                null, new EntitySnapshot(SHEEP, "minecraft:sheep", "Sheep",
                        new Pos3d(1, 64, 2), 8),
                new Pos3d(1, 64, 2), 1.0);
        assertNull(ray().isInsideBlock(), "entity hits carry no inside flag");
        assertNull(ray().getBlock());
        assertEquals(SHEEP.toString(), ray().getEntity().getUuid());

        world.ray = null;
        assertNull(ray().isInsideBlock(), "a miss answers undefined");
        assertFalse(ray().isHit());
    }

    /** One guest ray handle over the canned port. */
    private static RayHit ray() {
        return new RayHit(new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 64, 0),
                new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 0, 1),
                new RayOptions(32, true, true, false));
    }

    /** The canned block position with world physics extras attached. */
    private static BlockData stoneBlock() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("luminance", 0);
        extras.put("hardness", 1.5);
        extras.put("opaque", true);
        extras.put("exceedsCube", true);
        extras.put("hasComparatorOutput", false);
        extras.put("blocksMovement", true);
        extras.put("isSolid", false);
        return new BlockData("minecraft:stone", new Pos3d(1, 64, 2),
                Map.of("facing", "north"), extras);
    }

    /** The canned registry facts of the stone block type. */
    private static BlockTypeData stoneType() {
        return new BlockTypeData("minecraft:stone", "Stone",
                List.of("minecraft:base_stone_overworld"),
                1.5f, 0.4f, 0.6f, 1.0f, false, true,
                Map.of("facing", "north"),
                List.of(Map.of(), Map.of("half", "bottom")),
                new ItemStackData("minecraft:stone", 1, "Stone"));
    }

    /** World port over canned fields the tests mutate directly. */
    static final class CannedWorld implements WorldPort {
        BlockData block;
        BlockTypeData type;
        ChunkData chunk;
        ink.tuanzi.neomacros.port.model.RayHit ray;
        Boolean allowsSpawning;
        Boolean suffocates;
        Boolean containsAny;
        Boolean containsAll;
        Object rawBlockEntity;
        List<String> lastChunkContainsIds;

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
            return Optional.ofNullable(block);
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.RayHit> raycast(Pos3d origin,
                Pos3d direction, RayOptions options) {
            return Optional.ofNullable(ray);
        }

        @Override
        public Optional<ChunkData> chunkAt(Pos3d pos) {
            return Optional.ofNullable(chunk);
        }

        @Override
        public Optional<BlockTypeData> blockType(String blockId) {
            return Optional.ofNullable(type);
        }

        @Override
        public Optional<Boolean> allowsSpawningAt(Pos3d pos, String entityId) {
            return Optional.ofNullable(allowsSpawning);
        }

        @Override
        public Optional<Boolean> suffocatesAt(Pos3d pos) {
            return Optional.ofNullable(suffocates);
        }

        @Override
        public Optional<Boolean> chunkContains(int chunkX, int chunkZ, List<String> blockIds,
                boolean requireAll) {
            lastChunkContainsIds = blockIds;
            return Optional.ofNullable(requireAll ? containsAll : containsAny);
        }

        @Override
        public Optional<Object> rawBlockEntityByPos(Pos3d pos) {
            return Optional.ofNullable(rawBlockEntity);
        }
    }

    /** Entity port serving one canned in-box list. */
    static final class CannedEntities implements EntityPort {
        final List<EntitySnapshot> inBox = new ArrayList<>();

        @Override
        public Optional<EntitySnapshot> byUuid(UUID uuid) {
            return inBox.stream().filter(snapshot -> snapshot.uuid().equals(uuid)).findFirst();
        }

        @Override
        public List<EntitySnapshot> inRadius(Pos3d center, double range,
                java.util.function.Predicate<EntitySnapshot> filter) {
            return inBox.stream().filter(filter).toList();
        }

        @Override
        public List<EntitySnapshot> inBox(Pos3d min, Pos3d max,
                java.util.function.Predicate<EntitySnapshot> filter) {
            return inBox.stream().filter(filter).toList();
        }
    }

    /** Adapter that serves the world and entity ports; the rest is unused here. */
    record DualPortAdapter(WorldPort world, EntityPort entity) implements NeomacrosAdapter {
        @Override
        public LifecyclePort lifecycle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
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
