package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.client.ClientApi;
import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.InputPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.ItemPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.BlockStateData;
import ink.tuanzi.neomacros.port.model.EnchantmentSpec;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.StatusEffectInfoData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The G-025 registry spectrum: typed item/block/effect/enchantment lookups,
 * the connection-backed id lists, bulk listings, and identifier parsing,
 * driven through the API over a canned port. The id-list and fluid faces
 * assert against the pre-existing {@code registryIds}/{@code registryLookup}
 * bridges (A members), and every lookup family covers the unknown-id =
 * undefined rule.
 */
class ClientRegistrySpectrumTest {

    private ClientApi api;

    @BeforeEach
    void install() {
        ScriptApiEnvironment.install(null,
                new RegistryAdapter(new CannedRegistryClient(), new CannedItemPort()));
        api = new ClientApi();
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void itemFamilyResolvesStacksAndRejectsUnknownIds() {
        var stone = api.getItem("stone");
        assertNotNull(stone, "known item resolves");
        assertEquals("minecraft:stone", stone.getId());

        assertNull(api.getItem("missing"), "unknown item reads undefined");
        assertNull(api.getItemStack("missing", null), "unknown stack reads undefined");

        var plain = api.getItemStack("minecraft:stone", null);
        assertNotNull(plain, "known stack resolves");
        assertEquals("minecraft:stone", plain.getId());

        var damaged = api.getItemStack("stone", "[damage=12]");
        assertNotNull(damaged, "component syntax resolves");
        assertEquals(12, damaged.getDamage());

        assertThrows(IllegalArgumentException.class,
                () -> api.getItemStack("stone", "[bogus=1]"),
                "malformed component syntax rejects");
    }

    @Test
    void blockFamilyResolvesDefaultAndParsedStates() {
        var stairs = api.getBlock("oak_stairs");
        assertNotNull(stairs, "known block resolves");
        assertEquals("minecraft:oak_stairs", stairs.getId());
        assertEquals("north", stairs.getProps().get("facing"));

        assertNull(api.getBlock("missing"), "unknown block reads undefined");
        assertNull(api.getBlockState("missing", null), "unknown state reads undefined");

        var south = api.getBlockState("oak_stairs", "[facing=south]");
        assertNotNull(south, "parsed state resolves");
        assertEquals("south", south.getProps().get("facing"));

        assertThrows(IllegalArgumentException.class,
                () -> api.getBlockState("oak_stairs", "[bogus=north]"),
                "malformed property syntax rejects");
    }

    @Test
    void statusEffectFamilyResolvesZeroTickEntries() {
        var speed = api.getStatusEffect("speed");
        assertNotNull(speed, "known effect resolves");
        assertEquals("minecraft:speed", speed.id());
        assertEquals(0, speed.duration());
        assertEquals(0, speed.amplifier());
        assertEquals(false, speed.infinite());

        assertNull(api.getStatusEffect("missing"), "unknown effect reads undefined");

        List<StatusEffectInfoData> all = api.getStatusEffects();
        assertEquals(2, all.size(), "bulk listing serves every canned effect");
        assertEquals("minecraft:speed", all.get(0).id());
        assertEquals("minecraft:slowness", all.get(1).id());
    }

    @Test
    void enchantmentFamilyCarriesLevelsAndRejectsUnknownIds() {
        var sharpness = api.getEnchantment("sharpness", 5);
        assertNotNull(sharpness, "known enchantment resolves");
        assertEquals("minecraft:sharpness", sharpness.getId());
        assertEquals(5, sharpness.getLevel());

        var bare = api.getEnchantment("minecraft:sharpness", null);
        assertNotNull(bare, "omitted level defaults to zero");
        assertEquals(0, bare.getLevel());
        assertEquals(1, bare.getMinLevel(), "spec facts stay reachable");
        assertEquals(5, bare.getMaxLevel());

        assertNull(api.getEnchantment("missing", 1), "unknown enchantment reads undefined");
        assertNull(api.getEnchantment(null, null), "null id reads undefined");
    }

    @Test
    void connectionBackedListsServeEnchantmentPaintingAndFeature() {
        assertEquals(List.of("minecraft:sharpness"), api.getEnchantmentIds());
        assertEquals(List.of("minecraft:kebab"), api.getPaintingIds());
        assertEquals(List.of("minecraft:ore"), api.getFeatureIds());
    }

    @Test
    void bulkListingsServeEveryCannedEntry() {
        List<ink.tuanzi.neomacros.core.api.types.ItemStack> items = api.getItems();
        assertEquals(2, items.size());
        assertEquals("minecraft:air", items.get(0).getId());
        assertEquals("minecraft:stone", items.get(1).getId());

        List<ink.tuanzi.neomacros.core.api.types.BlockState> blocks = api.getBlocks();
        assertEquals(2, blocks.size());
        assertEquals("minecraft:oak_stairs", blocks.get(0).getId());
        assertEquals("north", blocks.get(0).getProps().get("facing"));
    }

    @Test
    void idListFamiliesBridgeThroughRegistryIds() {
        // A members: the pre-existing generic face answers every builtin
        // id list, one kind string per JsMacros member.
        assertEquals(List.of("minecraft:air", "minecraft:stone"),
                api.registryIds("item"), "getItemIds");
        assertEquals(List.of("minecraft:air", "minecraft:oak_stairs"),
                api.registryIds("block"), "getBlockIds");
        assertEquals(List.of("minecraft:pig"),
                api.registryIds("entity_type"), "getEntityTypeIds");
        assertEquals(List.of("minecraft:speed", "minecraft:slowness"),
                api.registryIds("mob_effect"), "getStatusEffectIds");
        assertEquals(List.of("minecraft:generic_9x3"),
                api.registryIds("menu"), "getScreenHandlerIds");
        assertEquals(List.of("minecraft:generic.attack_damage"),
                api.registryIds("attribute"), "getEntityAttributeIds");
        assertEquals(List.of("minecraft:water"),
                api.registryIds("fluid"), "getFluidState id space");
        assertEquals(List.of("minecraft:structure_piece"),
                api.registryIds("worldgen/structure_piece"), "getStructureFeatureIds");

        assertThrows(IllegalArgumentException.class,
                () -> api.registryIds("nope"), "unknown kind still throws");
    }

    @Test
    void fluidLookupBridgesThroughRegistryLookup() {
        // A member: the resolved fluid id is the mappable value of
        // RegistryHelper.getFluidState (its 7 members are D, 15 号 §5.1).
        assertEquals("minecraft:water", api.registryLookup("fluid", "water"));
        assertEquals("minecraft:water", api.registryLookup("fluid", "minecraft:water"));
        assertNull(api.registryLookup("fluid", "missing"), "unknown fluid reads undefined");
    }

    @Test
    void identifierParsingNormalizesAndValidates() {
        assertEquals("minecraft:stone", api.parseNameSpace("stone"));
        assertEquals("minecraft:stone", api.parseNameSpace("minecraft:stone"));
        assertEquals("dirt:dirt", api.parseNameSpace("dirt:dirt"));

        assertEquals("minecraft:stone", api.parseIdentifier("stone"));
        assertEquals("minecraft:world/stone", api.parseIdentifier("world/stone"));

        assertThrows(IllegalArgumentException.class,
                () -> api.parseIdentifier("Bad:ID"), "uppercase rejects");
        assertThrows(IllegalArgumentException.class,
                () -> api.parseIdentifier("minecraft:"), "empty path rejects");
        assertThrows(IllegalArgumentException.class,
                () -> api.parseNameSpace(null), "null id rejects");
    }

    /** Adapter serving the registry client port and the enchantment item port. */
    record RegistryAdapter(ClientPort client, ItemPort item)
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

    /** Canned registry content served to the G-025 faces. */
    static final class CannedRegistryClient implements ClientPort {

        @Override
        public int fps() {
            return 60;
        }

        @Override
        public String gameVersion() {
            return "26.3";
        }

        @Override
        public Object minecraftInstance() {
            return null;
        }

        @Override
        public List<String> registryIds(String kind) {
            return switch (kind) {
                case "item" -> List.of("minecraft:air", "minecraft:stone");
                case "block" -> List.of("minecraft:air", "minecraft:oak_stairs");
                case "mob_effect" -> List.of("minecraft:speed", "minecraft:slowness");
                case "fluid" -> List.of("minecraft:water");
                case "entity_type" -> List.of("minecraft:pig");
                case "menu" -> List.of("minecraft:generic_9x3");
                case "attribute" -> List.of("minecraft:generic.attack_damage");
                case "worldgen/structure_piece" -> List.of("minecraft:structure_piece");
                default -> throw new IllegalArgumentException(
                        "unknown registry kind: " + kind);
            };
        }

        @Override
        public Optional<String> registryLookup(String kind, String id) {
            String full = id != null && id.indexOf(':') >= 0
                    ? id : "minecraft:" + id;
            return registryIds(kind).contains(full)
                    ? Optional.of(full) : Optional.empty();
        }

        @Override
        public Optional<StatusEffectInfoData> statusEffect(String id) {
            String full = id != null && id.indexOf(':') >= 0
                    ? id : "minecraft:" + id;
            if ("minecraft:speed".equals(full)) {
                // beneficial, not instant; fresh-instance flags
                return Optional.of(new StatusEffectInfoData(full, 0, 0, false,
                        false, true, true, false, true, false, false));
            }
            if ("minecraft:slowness".equals(full)) {
                // harmful, not instant; fresh-instance flags
                return Optional.of(new StatusEffectInfoData(full, 0, 0, false,
                        false, true, true, false, false, false, true));
            }
            return Optional.empty();
        }

        @Override
        public Optional<BlockStateData> blockState(String id, String props) {
            String full = id != null && id.indexOf(':') >= 0
                    ? id : "minecraft:" + id;
            if (!full.equals("minecraft:oak_stairs") && !full.equals("minecraft:stone")) {
                return Optional.empty();
            }
            if (props == null || props.isBlank()) {
                return Optional.of(new BlockStateData(full, Map.of("facing", "north")));
            }
            if (props.contains("bogus")) {
                throw new IllegalArgumentException("invalid block state: " + id + props);
            }
            return Optional.of(new BlockStateData(full, Map.of("facing", "south")));
        }

        @Override
        public List<BlockStateData> blockStates() {
            return List.of(
                    new BlockStateData("minecraft:oak_stairs", Map.of("facing", "north")),
                    new BlockStateData("minecraft:stone", Map.of()));
        }

        @Override
        public Optional<ItemStackData> itemStack(String id, String components) {
            String full = id != null && id.indexOf(':') >= 0
                    ? id : "minecraft:" + id;
            if (!full.equals("minecraft:stone") && !full.equals("minecraft:air")) {
                return Optional.empty();
            }
            if (components != null && components.contains("bogus")) {
                throw new IllegalArgumentException(
                        "invalid item components: " + id + components);
            }
            if (components != null && components.contains("damage=12")) {
                return Optional.of(new ItemStackData(full, 1, "Stone", 12, 0,
                        List.of(), List.of()));
            }
            return Optional.of(new ItemStackData(full, 1, "Stone"));
        }

        @Override
        public List<ItemStackData> defaultItemStacks() {
            return List.of(
                    new ItemStackData("minecraft:air", 1, "Air"),
                    new ItemStackData("minecraft:stone", 1, "Stone"));
        }

        @Override
        public List<String> enchantmentIds() {
            return List.of("minecraft:sharpness");
        }

        @Override
        public List<String> paintingIds() {
            return List.of("minecraft:kebab");
        }

        @Override
        public List<String> featureIds() {
            return List.of("minecraft:ore");
        }
    }

    /** Canned enchantment definitions behind the typed lookup. */
    static final class CannedItemPort implements ItemPort {

        @Override
        public Optional<EnchantmentSpec> enchantmentSpec(String enchantmentId) {
            String full = enchantmentId != null && enchantmentId.indexOf(':') >= 0
                    ? enchantmentId : "minecraft:" + enchantmentId;
            if (!full.equals("minecraft:sharpness")) {
                return Optional.empty();
            }
            return Optional.of(new EnchantmentSpec("minecraft:sharpness",
                    1, 5, 10, false, false, List.of("minecraft:diamond_sword")));
        }
    }
}
