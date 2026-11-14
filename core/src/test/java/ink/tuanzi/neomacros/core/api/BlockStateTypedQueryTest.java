package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.types.BlockState;
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
import ink.tuanzi.neomacros.port.model.BlockTypeData;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The G-029 typed state-property query face: the generic props accessors
 * ({@code getProp}/{@code hasProp}/{@code getPropRange}), the frequent
 * typed convenience members, the vocabulary-gated {@code type}/{@code shape}
 * families, and the undefined-for-unknown boundary that the props-fallback
 * tier of the 99 accessors rides on, all driven over canned
 * registry facts.
 */
class BlockStateTypedQueryTest {

    private RangeWorld world;

    @BeforeEach
    void install() {
        world = new RangeWorld();
        ScriptApiEnvironment.install(null, new SingleAdapter(world));
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void convenienceStringMembersReadThePropsMap() {
        BlockState stairs = state("minecraft:oak_stairs", Map.of(
                "facing", "east", "axis", "x", "half", "bottom",
                "shape", "straight", "hinge", "left", "instrument", "harp",
                "part", "head", "face", "wall"));
        assertEquals("east", stairs.getFacing());
        assertEquals("x", stairs.getAxis());
        assertEquals("bottom", stairs.getHalf());
        assertEquals("straight", stairs.getStairShape());
        assertEquals("left", stairs.getDoorHinge());
        assertEquals("harp", stairs.getInstrument());
        assertEquals("head", stairs.getBedPart());
        assertEquals("wall", stairs.getFace());
    }

    @Test
    void convenienceIntMembersParseNumericProps() {
        BlockState probe = state("minecraft:probe", Map.of(
                "level", "7", "honey_level", "5", "power", "15",
                "delay", "4", "distance", "3", "note", "12"));
        assertEquals(7, probe.getLevel());
        assertEquals(5, probe.getHoneyLevel());
        assertEquals(15, probe.getPower());
        assertEquals(4, probe.getDelay());
        assertEquals(3, probe.getDistance());
        assertEquals(12, probe.getNote());
    }

    @Test
    void convenienceBooleanMembersParseBooleanProps() {
        BlockState probe = state("minecraft:probe", Map.of(
                "waterlogged", "true", "snowy", "false", "occupied", "true",
                "extended", "false", "persistent", "true", "hanging", "false",
                "enabled", "true", "conditional", "false"));
        assertTrue(probe.getWaterlogged());
        assertFalse(probe.getSnowy());
        assertTrue(probe.getOccupied());
        assertFalse(probe.getExtended());
        assertTrue(probe.getPersistent());
        assertFalse(probe.getHanging());
        assertTrue(probe.getEnabled());
        assertFalse(probe.getConditional());
    }

    @Test
    void absentOrMalformedPropertiesReadUndefined() {
        BlockState stone = state("minecraft:stone", Map.of("facing", "north"));
        assertNull(stone.getLevel(), "absent level is undefined");
        assertNull(stone.getWaterlogged(), "absent waterlogged is undefined");
        assertNull(stone.getSlabType(), "absent type is undefined");
        assertNull(state("minecraft:probe", Map.of("level", "wet")).getLevel(),
                "a non-numeric level is undefined");
        assertNull(state("minecraft:probe", Map.of("waterlogged", "yes")).getWaterlogged(),
                "a malformed boolean is undefined");
        assertNull(stone.getProp("half"));
        assertFalse(stone.hasProp("half"));
    }

    @Test
    void vocabGatedTypeAndShapeMembersRejectForeignValues() {
        BlockTypeData chest = type("minecraft:chest", Map.of("type", "left"));
        BlockTypeData slab = type("minecraft:oak_slab", Map.of("type", "double"));
        BlockTypeData piston = type("minecraft:piston_head", Map.of("type", "sticky"));
        BlockTypeData stairs = type("minecraft:oak_stairs", Map.of("shape", "inner_left"));
        BlockTypeData rail = type("minecraft:rail", Map.of("shape", "south_east"));

        assertEquals("left", state(chest).getChestType());
        assertNull(state(chest).getSlabType(), "a chest is not a slab");
        assertNull(state(chest).getPistonType(), "a chest is not a piston head");

        assertEquals("double", state(slab).getSlabType());
        assertNull(state(slab).getChestType(), "a slab is not a chest");

        assertEquals("sticky", state(piston).getPistonType());
        assertNull(state(piston).getSlabType(), "a piston head is not a slab");

        assertEquals("inner_left", state(stairs).getStairShape());
        assertNull(state(stairs).getRailShape(), "a stair shape is not a rail shape");

        assertEquals("south_east", state(rail).getRailShape());
        assertNull(state(rail).getStairShape(), "a rail shape is not a stair shape");
    }

    @Test
    void genericPropLookupIsCaseInsensitiveAndSafeOnNull() {
        BlockState stairs = state("minecraft:oak_stairs",
                Map.of("facing", "east", "half", "bottom"));
        assertEquals("east", stairs.getProp("FACING"), "lookup ignores case");
        assertEquals("east", stairs.getProp("Facing"));
        assertEquals("bottom", stairs.getProp("half"));
        assertNull(stairs.getProp("no_such_property"));
        assertNull(stairs.getProp(null));
        assertTrue(stairs.hasProp("HALF"));
        assertFalse(stairs.hasProp(null));
    }

    @Test
    void propRangeReadsTheBlockTypeDefinedStates() {
        world.type = new BlockTypeData("minecraft:water", "Water",
                List.of(), 100f, 0f, 0f, 0f, false, false,
                Map.of("level", "0"),
                levelStates(16), null);

        assertEquals(List.of(0, 15),
                state("minecraft:water", Map.of("level", "7")).getPropRange("level"),
                "the range spans every defined level state");
        assertEquals(List.of(0, 15),
                state("minecraft:water", Map.of("level", "1")).getPropRange("LEVEL"),
                "range lookup ignores case too");
    }

    @Test
    void propRangeIsUndefinedWithoutTheBlockTypeOrANumericProperty() {
        BlockState probe = state("minecraft:probe",
                Map.of("level", "7", "facing", "north"));
        assertNull(probe.getPropRange("level"), "no block type facts: undefined");
        assertNull(probe.getPropRange(null));

        world.type = type("minecraft:probe", Map.of("facing", "north"));
        assertNull(probe.getPropRange("facing"), "a non-numeric property: undefined");
        assertNull(probe.getPropRange("age"), "an absent property: undefined");
    }

    @Test
    void propsFallbackTierReadsAnyOfTheVanillaKeys() {
        // Sampled keys of the 68-member props-fallback tier: every one is a
        // plain props read, so one representative sweep proves the channel.
        Map<String, String> props = new java.util.LinkedHashMap<>();
        props.put("attachment", "floor");
        props.put("east", "tall");
        props.put("north", "up");
        props.put("south", "none");
        props.put("west", "side");
        props.put("drag", "true");
        props.put("bottom", "true");
        props.put("short", "false");
        props.put("falling", "true");
        props.put("hatch", "2");
        props.put("slot_0_occupied", "true");
        props.put("leaves", "large");
        props.put("tilt", "full");
        props.put("vertical_direction", "up");
        props.put("thickness", "base");
        props.put("mode", "compare");
        props.put("sculk_sensor_phase", "active");
        props.put("trial_spawner_state", "warm");
        props.put("vault_state", "active");
        props.put("flower_amount", "3");
        props.put("dusted", "2");
        props.put("rotation", "9");
        props.put("charges", "4");
        props.put("stage", "1");
        props.put("moisture", "7");
        props.put("pickles", "4");
        props.put("candles", "3");
        props.put("eggs", "2");
        props.put("bites", "4");
        props.put("layers", "2");
        props.put("age", "3");
        props.put("has_bottle_0", "true");
        props.put("has_record", "false");
        BlockState probe = state("minecraft:probe", props);
        assertEquals("floor", probe.getProp("attachment"));
        assertEquals("tall", probe.getProp("east"));
        assertEquals("up", probe.getProp("north"));
        assertEquals("none", probe.getProp("south"));
        assertEquals("side", probe.getProp("west"));
        assertEquals("true", probe.getProp("drag"));
        assertEquals("true", probe.getProp("bottom"));
        assertEquals("false", probe.getProp("short"));
        assertEquals("true", probe.getProp("falling"));
        assertEquals("2", probe.getProp("hatch"), "the member is getHatched, the key is hatch");
        assertEquals("true", probe.getProp("slot_0_occupied"));
        assertEquals("large", probe.getProp("leaves"));
        assertEquals("full", probe.getProp("tilt"));
        assertEquals("up", probe.getProp("vertical_direction"));
        assertEquals("base", probe.getProp("thickness"));
        assertEquals("compare", probe.getProp("mode"));
        assertEquals("active", probe.getProp("sculk_sensor_phase"));
        assertEquals("warm", probe.getProp("trial_spawner_state"));
        assertEquals("3", probe.getProp("flower_amount"));
        assertEquals("2", probe.getProp("dusted"));
        assertEquals("9", probe.getProp("rotation"));
        assertEquals("4", probe.getProp("charges"));
        assertEquals("3", probe.getProp("age"));
        assertEquals("true", probe.getProp("has_bottle_0"));
        assertEquals("false", probe.getProp("has_record"));
        // The wall/wire families share their four keys per side.
        assertEquals("tall", probe.getProp("east"),
                "wall shapes and wire connections ride props['east']");
        assertNull(probe.getProp("no_such_property"), "unknown keys stay undefined");
    }

    /** One canned state over the given props. */
    private static BlockState state(String id, Map<String, String> props) {
        return new BlockState(id, props);
    }

    /** One canned state over the props of a canned block type. */
    private static BlockState state(BlockTypeData type) {
        return new BlockState(type.id(), type.defaultState());
    }

    /** One canned block type whose defined states carry the given props. */
    private static BlockTypeData type(String id, Map<String, String> defaultState) {
        List<Map<String, String>> states = new ArrayList<>();
        states.add(Map.of());
        states.add(defaultState);
        return new BlockTypeData(id, id, List.of(), 1f, 1f, 1f, 1f, false, false,
                defaultState, states, new ItemStackData(id, 1, id));
    }

    /** {@code count} states walking one numeric property from 0 upward. */
    private static List<Map<String, String>> levelStates(int count) {
        List<Map<String, String>> states = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            states.add(Map.of("level", String.valueOf(i)));
        }
        return states;
    }

    /** World port serving only the block-type facts the range reads need. */
    static final class RangeWorld implements WorldPort {
        BlockTypeData type;

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
        public Optional<ink.tuanzi.neomacros.port.model.BlockData> blockAt(
                ink.tuanzi.neomacros.port.model.Pos3d pos) {
            return Optional.empty();
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.RayHit> raycast(
                ink.tuanzi.neomacros.port.model.Pos3d origin,
                ink.tuanzi.neomacros.port.model.Pos3d direction,
                ink.tuanzi.neomacros.port.model.RayOptions options) {
            return Optional.empty();
        }

        @Override
        public Optional<BlockTypeData> blockType(String blockId) {
            return Optional.ofNullable(type);
        }
    }

    /** Adapter that serves the world port; every other port is unused here. */
    record SingleAdapter(WorldPort world) implements NeomacrosAdapter {
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
