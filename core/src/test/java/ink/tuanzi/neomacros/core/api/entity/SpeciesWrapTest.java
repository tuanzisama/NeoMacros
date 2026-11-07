package ink.tuanzi.neomacros.core.api.entity;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
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
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The species handle tree: wrapping dispatches to the deepest registered
 * species class, whose members read the port's species state channel.
 */
class SpeciesWrapTest {

    private CannedEntities entities;
    private UUID sheepId;
    private UUID zombieId;
    private UUID villagerId;
    private UUID plainId;

    @BeforeEach
    void install() {
        entities = new CannedEntities();
        sheepId = UUID.randomUUID();
        zombieId = UUID.randomUUID();
        villagerId = UUID.randomUUID();
        plainId = UUID.randomUUID();
        entities.entities.put(sheepId, living(sheepId, "minecraft:sheep", "Sheep", 0, 0, 8));
        entities.entities.put(zombieId, living(zombieId, "minecraft:zombie", "Zombie", 1, 1, 20));
        entities.entities.put(villagerId, living(villagerId, "minecraft:villager", "Villager", 2, 2, 20));
        entities.entities.put(plainId, living(plainId, "minecraft:pig", "Pig", 3, 3, 10));
        entities.species.put(sheepId, Map.of(
                "color", "minecraft:white",
                "sheared", false));
        entities.species.put(zombieId, Map.of(
                "noAi", false,
                "underWaterConverting", true,
                "target", sheepId.toString()));
        entities.species.put(villagerId, Map.of(
                "profession", "minecraft:farmer",
                "level", 5,
                "variant", "minecraft:plains"));
        ScriptApiEnvironment.install(null, new SinglePortAdapter(entities));
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void sheepWrapsToSpeciesWithMembers() {
        EntityApi.Entity handle = EntityApi.wrap(sheepId);
        assertTrue(handle instanceof SpeciesEntities.SheepEntity, "sheep wraps as Sheep");
        assertTrue(handle instanceof EntityApi.LivingEntity, "sheep stays a living entity");
        SpeciesEntities.SheepEntity sheep = (SpeciesEntities.SheepEntity) handle;
        assertEquals("minecraft:white", sheep.getColor());
        assertEquals(false, sheep.isSheared());
    }

    @Test
    void zombieCarriesMobMembersAndTarget() {
        EntityApi.Entity handle = EntityApi.wrap(zombieId);
        assertTrue(handle instanceof SpeciesEntities.ZombieEntity, "zombie wraps as Zombie");
        SpeciesEntities.ZombieEntity zombie = (SpeciesEntities.ZombieEntity) handle;
        assertEquals(false, zombie.isNoAi());
        assertEquals(true, zombie.isUnderWaterConverting());
        EntityApi.Entity target = zombie.getTarget();
        assertTrue(target instanceof SpeciesEntities.SheepEntity, "target wraps as the sheep");
        assertEquals(sheepId, target.getUuid() == null ? null : UUID.fromString(target.getUuid()));
    }

    @Test
    void villagerCarriesTradeData() {
        EntityApi.Entity handle = EntityApi.wrap(villagerId);
        assertTrue(handle instanceof SpeciesEntities.VillagerEntity, "villager wraps as Villager");
        SpeciesEntities.VillagerEntity villager = (SpeciesEntities.VillagerEntity) handle;
        assertEquals("minecraft:farmer", villager.getProfession());
        assertEquals(5, villager.getLevel());
        assertEquals("minecraft:plains", villager.getVariant());
    }

    @Test
    void unregisteredSpeciesFallToLivingEntity() {
        EntityApi.Entity handle = EntityApi.wrap(plainId);
        assertFalse(handle instanceof SpeciesEntities.AnimalEntity,
                "unregistered species stay on the base tree");
        assertTrue(handle instanceof EntityApi.LivingEntity);
        assertEquals("minecraft:pig", handle.snapshot().type(),
                "the unregistered species keeps its identity");
    }

    /** Minimal living snapshot factory (the compat ctor leaves living=false). */
    private static EntitySnapshot living(UUID uuid, String type, String name, double x,
            double z, double health) {
        return new EntitySnapshot(uuid, type, name, new Pos3d(x, 64, z), health, health,
                new Pos3d(0, 0, 0), new Pos3d(0, 0, 0), new Rot2d(0, 0), 1.8,
                true, false, false, false, false, false, false, false,
                "standing", "south", "", null, java.util.List.<java.util.UUID>of(), null,
                null, null, true);
    }

    /** Entity port over a canned snapshot/state map the test fills directly. */
    static final class CannedEntities implements EntityPort {
        final Map<UUID, EntitySnapshot> entities = new java.util.LinkedHashMap<>();
        final Map<UUID, Map<String, Object>> species = new java.util.LinkedHashMap<>();

        @Override
        public Optional<EntitySnapshot> byUuid(UUID uuid) {
            return Optional.ofNullable(entities.get(uuid));
        }

        @Override
        public java.util.List<EntitySnapshot> inRadius(Pos3d center, double range,
                java.util.function.Predicate<EntitySnapshot> filter) {
            return java.util.List.of();
        }

        @Override
        public Map<String, Object> speciesState(UUID uuid) {
            return species.getOrDefault(uuid, Map.of());
        }
    }

    /** Adapter that only serves the entity port; other ports are unused here. */
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
        public WorldPort world() {
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
