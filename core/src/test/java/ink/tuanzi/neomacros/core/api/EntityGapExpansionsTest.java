package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.entity.EntityApi;
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
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos3d;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The G-012 entity base-state face: the base entity state channel (glow,
 * custom name, air, speed), the living traits (armor slots, attributes,
 * undead/baby/bow pull), the client-side writes, and the two-handle
 * predicates, all driven through the API over a canned port.
 */
class EntityGapExpansionsTest {

    private static final UUID PIG = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    private CannedEntity port;
    private EntityApi.Entity entity;
    private EntityApi.LivingEntity living;

    @BeforeEach
    void install() {
        port = new CannedEntity();
        ScriptApiEnvironment.install(null, new SinglePortAdapter(port));
        entity = new EntityApi.Entity(PIG);
        living = new EntityApi.LivingEntity(PIG);
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void baseStateReadsBackEveryMember() {
        port.state.put("glowing", true);
        port.state.put("glowColor", 255);
        port.state.put("customName", "Bob");
        port.state.put("customNameVisible", true);
        port.state.put("air", 280);
        port.state.put("maxAir", 300);
        port.state.put("speed", 3.5);

        assertTrue(entity.getGlowing());
        assertEquals(255, entity.getGlowColor().intValue());
        assertEquals("Bob", entity.getCustomName());
        assertTrue(entity.isCustomNameVisible());
        assertEquals(280, entity.getAir().intValue());
        assertEquals(300, entity.getMaxAir().intValue());
        assertEquals(3.5, entity.getSpeed().doubleValue());
    }

    @Test
    void baseStateIsNullWhenThePortServesNothing() {
        assertNull(entity.getGlowing());
        assertNull(entity.getGlowColor());
        assertNull(entity.getCustomName());
        assertNull(entity.isCustomNameVisible());
        assertNull(entity.getAir());
        assertNull(entity.getMaxAir());
        assertNull(entity.getSpeed());
    }

    @Test
    void baseWritesReachThePortInOrder() throws Exception {
        entity.setGlowing(true).get();
        entity.resetGlowing().get();
        entity.setGlowingColor(255).get();
        entity.resetGlowingColor().get();
        entity.setCustomName("Bob").get();
        entity.setCustomName(null).get();
        entity.setCustomNameVisible(false).get();

        assertEquals(List.of(
                "setGlowing:true",
                "resetGlowing",
                "setGlowingColor:255",
                "resetGlowingColor",
                "setCustomName:Bob",
                "setCustomName:null",
                "setCustomNameVisible:false"), port.writes);
    }

    @Test
    void livingTraitsReadBackEveryMember() {
        port.living.put("headArmor",
                new ItemStackData("minecraft:diamond_helmet", 1, "Helmet"));
        port.living.put("chestArmor", null);
        port.living.put("legArmor",
                new ItemStackData("minecraft:iron_leggings", 1, "Leggings"));
        port.living.put("footArmor", new ItemStackData("minecraft:boots", 1, "Boots"));
        port.living.put("armorToughness", 2.0);
        port.living.put("defaultMaxHealth", 20.0);
        port.living.put("breathesUnderwater", true);
        port.living.put("noDrag", false);
        port.living.put("partOfGame", true);
        port.living.put("undead", true);
        port.living.put("baby", true);
        port.living.put("bowPull", 0.75);

        assertEquals("minecraft:diamond_helmet", living.getHeadArmor().getId());
        assertNull(living.getChestArmor(), "an empty armor slot reads as undefined");
        assertEquals("minecraft:iron_leggings", living.getLegArmor().getId());
        assertEquals("minecraft:boots", living.getFootArmor().getId());
        assertEquals(2.0, living.getArmorToughness().doubleValue());
        assertEquals(20.0, living.getDefaultMaxHealth().doubleValue());
        assertTrue(living.isBreathesUnderwater());
        assertFalse(living.isNoDrag());
        assertTrue(living.isPartOfGame());
        assertTrue(living.isUndead());
        assertTrue(living.isBaby());
        assertEquals(0.75, living.getBowPull().doubleValue());
    }

    @Test
    void livingTraitsAreUndefinedWhenThePortServesNothing() {
        assertNull(living.getHeadArmor());
        assertNull(living.getChestArmor());
        assertNull(living.getLegArmor());
        assertNull(living.getFootArmor());
        assertNull(living.getArmorToughness());
        assertNull(living.getDefaultMaxHealth());
        assertNull(living.isBreathesUnderwater());
        assertNull(living.isNoDrag());
        assertNull(living.isPartOfGame());
        assertNull(living.isUndead());
        assertNull(living.isBaby());
        assertNull(living.getBowPull());
    }

    @Test
    void predicatesReachThePortWithTheirArguments() {
        port.canBeAffectedResult = true;
        assertTrue(living.canBeAffected("speed"));
        assertEquals("minecraft:speed", port.lastEffectId);
        assertTrue(living.canBeAffected("minecraft:strength"));
        assertEquals("minecraft:strength", port.lastEffectId);

        port.canAttackResult = true;
        assertTrue(living.canAttack(new EntityApi.Entity(TARGET)));
        assertFalse(living.canAttack(null), "a missing target never attacks");

        port.canSeeResult = true;
        assertTrue(living.hasLineOfSight(new EntityApi.Entity(TARGET), null));
        assertTrue(port.lastSimpleCast, "the simple cast is the default");
        living.hasLineOfSight(new EntityApi.Entity(TARGET), false);
        assertFalse(port.lastSimpleCast, "the extended cast reaches the port");
        assertFalse(living.hasLineOfSight(null, null), "a missing target never sees");

        port.canAttackResult = false;
        port.canSeeResult = false;
        assertFalse(living.canAttack(new EntityApi.Entity(TARGET)));
        assertFalse(living.hasLineOfSight(new EntityApi.Entity(TARGET), null));
        port.canBeAffectedResult = false;
        assertFalse(living.canBeAffected("minecraft:unknown-effect-id"));
    }

    /** Entity port over canned state maps plus recorded writes. */
    static final class CannedEntity implements EntityPort {
        final Map<String, Object> state = new LinkedHashMap<>();
        final Map<String, Object> living = new LinkedHashMap<>();
        final List<String> writes = new ArrayList<>();
        volatile boolean canAttackResult;
        volatile boolean canBeAffectedResult;
        volatile boolean canSeeResult;
        volatile boolean lastSimpleCast = true;
        volatile String lastEffectId;

        @Override
        public Optional<EntitySnapshot> byUuid(UUID uuid) {
            return Optional.of(new EntitySnapshot(uuid, "minecraft:pig", "Pig",
                    new Pos3d(0, 64, 0), 5.5));
        }

        @Override
        public List<EntitySnapshot> inRadius(Pos3d center, double range,
                java.util.function.Predicate<EntitySnapshot> filter) {
            return List.of();
        }

        @Override
        public Map<String, Object> entityState(UUID uuid) {
            return new LinkedHashMap<>(state);
        }

        @Override
        public Map<String, Object> livingExtras(UUID uuid) {
            return new LinkedHashMap<>(living);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setGlowing(UUID uuid, boolean glowing) {
            writes.add("setGlowing:" + glowing);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> resetGlowing(UUID uuid) {
            writes.add("resetGlowing");
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setGlowingColor(UUID uuid, int color) {
            writes.add("setGlowingColor:" + color);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> resetGlowingColor(UUID uuid) {
            writes.add("resetGlowingColor");
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setCustomName(UUID uuid, String name) {
            writes.add("setCustomName:" + name);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setCustomNameVisible(
                UUID uuid, boolean visible) {
            writes.add("setCustomNameVisible:" + visible);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean canAttack(UUID uuid, UUID target) {
            return canAttackResult;
        }

        @Override
        public boolean canBeAffected(UUID uuid, String effectId) {
            lastEffectId = effectId;
            return canBeAffectedResult;
        }

        @Override
        public boolean canSee(UUID uuid, UUID target, boolean simpleCast) {
            lastSimpleCast = simpleCast;
            return canSeeResult;
        }
    }

    /** Adapter that only serves the entity port; the rest is unused here. */
    record SinglePortAdapter(EntityPort entity) implements NeomacrosAdapter {
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

        @Override
        public WorldPort world() {
            throw new UnsupportedOperationException();
        }
    }
}
