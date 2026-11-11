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
        entities.entities.put(plainId, living(plainId, "minecraft:squid", "Squid", 3, 3, 10));
        entities.species.put(sheepId, Map.of(
                "color", "minecraft:white",
                "sheared", false,
                "shearable", true,
                "jeb", false));
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
        assertEquals("minecraft:squid", handle.snapshot().type(),
                "the unregistered species keeps its identity");
    }

    @Test
    void sheepCarriesGapBatchMembers() {
        SpeciesEntities.SheepEntity sheep = (SpeciesEntities.SheepEntity) EntityApi.wrap(sheepId);
        assertEquals(true, sheep.isShearable());
        assertEquals(false, sheep.isJeb());
    }

    @Test
    void arrowWrapsWithProjectileMembers() {
        UUID arrowId = UUID.randomUUID();
        entities.entities.put(arrowId, living(arrowId, "minecraft:arrow", "Arrow", 4, 4, 1));
        entities.species.put(arrowId, Map.of(
                "critical", true,
                "pierceLevel", 2,
                "color", 0x123456));
        EntityApi.Entity handle = EntityApi.wrap(arrowId);
        assertTrue(handle instanceof SpeciesEntities.ArrowEntity, "arrow wraps as Arrow");
        assertTrue(handle instanceof SpeciesEntities.ProjectileEntity,
                "arrow sits on the projectile tree");
        SpeciesEntities.ArrowEntity arrow = (SpeciesEntities.ArrowEntity) handle;
        assertEquals(true, arrow.isCritical());
        assertEquals(2, arrow.getPierceLevel());
        assertEquals(0x123456, arrow.getColor());
    }

    @Test
    void boatsWrapByType() {
        UUID boatId = UUID.randomUUID();
        entities.entities.put(boatId, living(boatId, "minecraft:oak_boat", "Boat", 5, 5, 4));
        entities.species.put(boatId, Map.of("underWater", false));
        EntityApi.Entity boat = EntityApi.wrap(boatId);
        assertTrue(boat instanceof SpeciesEntities.BoatEntity, "oak boat wraps as Boat");
        assertEquals(false, ((SpeciesEntities.BoatEntity) boat).isUnderWater());

        UUID chestId = UUID.randomUUID();
        entities.entities.put(chestId, living(chestId, "minecraft:chest_boat", "ChestBoat", 6, 6, 4));
        EntityApi.Entity chestBoat = EntityApi.wrap(chestId);
        assertTrue(chestBoat instanceof SpeciesEntities.ChestBoatEntity,
                "chest boat wraps as ChestBoat");
        assertTrue(chestBoat instanceof SpeciesEntities.BoatEntity,
                "chest boat stays on the boat tree");
    }

    @Test
    void slimeAndMagmaCubeShareTheSlimeHandle() {
        UUID slimeId = UUID.randomUUID();
        entities.entities.put(slimeId, living(slimeId, "minecraft:slime", "Slime", 7, 7, 10));
        entities.species.put(slimeId, Map.of("size", 3, "noAi", false));
        SpeciesEntities.SlimeEntity slime = (SpeciesEntities.SlimeEntity) EntityApi.wrap(slimeId);
        assertEquals(3, slime.getSize());

        UUID magmaId = UUID.randomUUID();
        entities.entities.put(magmaId, living(magmaId, "minecraft:magma_cube", "MagmaCube", 8, 8, 10));
        assertTrue(EntityApi.wrap(magmaId) instanceof SpeciesEntities.SlimeEntity,
                "magma cube reuses the slime handle");
    }

    @Test
    void guardianWrapsWithLaserTarget() {
        UUID guardianId = UUID.randomUUID();
        entities.entities.put(guardianId, living(guardianId, "minecraft:guardian", "Guardian", 9, 9, 20));
        entities.species.put(guardianId, Map.of(
                "activeTarget", sheepId.toString(),
                "noAi", false));
        SpeciesEntities.GuardianEntity guardian =
                (SpeciesEntities.GuardianEntity) EntityApi.wrap(guardianId);
        EntityApi.Entity target = guardian.getActiveTarget();
        assertTrue(target instanceof SpeciesEntities.SheepEntity, "laser target wraps as the sheep");
    }

    @Test
    void interactionCarriesBoxAndHistory() {
        UUID interactionId = UUID.randomUUID();
        entities.entities.put(interactionId,
                living(interactionId, "minecraft:interaction", "Interaction", 10, 10, 20));
        entities.species.put(interactionId, new java.util.LinkedHashMap<String, Object>(Map.of(
                "width", 1.5,
                "height", 2.0,
                "response", true,
                "lastAttacker", sheepId.toString())));
        SpeciesEntities.InteractionEntity interaction =
                (SpeciesEntities.InteractionEntity) EntityApi.wrap(interactionId);
        assertEquals(1.5, interaction.getWidth());
        assertEquals(2.0, interaction.getHeight());
        assertEquals(true, interaction.isResponsive());
        assertTrue(interaction.getLastAttacker() instanceof SpeciesEntities.SheepEntity);
        assertNull(interaction.getLastInteracted());
    }

    @Test
    void paintingCarriesArtworkData() {
        UUID paintingId = UUID.randomUUID();
        entities.entities.put(paintingId, living(paintingId, "minecraft:painting", "Painting", 11, 11, 1));
        entities.species.put(paintingId, Map.of(
                "artId", "minecraft:wanderer",
                "width", 1,
                "height", 2));
        SpeciesEntities.PaintingEntity painting =
                (SpeciesEntities.PaintingEntity) EntityApi.wrap(paintingId);
        assertTrue(painting instanceof SpeciesEntities.HangingEntity, "painting hangs");
        assertEquals("minecraft:wanderer", painting.getArtId());
        assertEquals(1, painting.getWidth());
        assertEquals(2, painting.getHeight());
    }

    @Test
    void textDisplayInheritsDisplayMembers() {
        UUID displayId = UUID.randomUUID();
        entities.entities.put(displayId, living(displayId, "minecraft:text_display", "Text", 12, 12, 1));
        Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put("billboard", "center");
        state.put("brightness", null);
        state.put("displayWidth", 1.0);
        state.put("displayHeight", 1.0);
        state.put("glowColorOverride", 0xFFFFFF);
        state.put("viewRange", 1.0);
        state.put("shadowRadius", 0.0);
        state.put("shadowStrength", 1.0);
        state.put("text", "hello");
        state.put("backgroundColor", 0x40000000);
        entities.species.put(displayId, state);
        EntityApi.Entity handle = EntityApi.wrap(displayId);
        assertTrue(handle instanceof SpeciesEntities.TextDisplayEntity,
                "text display wraps as TextDisplay");
        SpeciesEntities.TextDisplayEntity display = (SpeciesEntities.TextDisplayEntity) handle;
        assertEquals("hello", display.getText());
        assertEquals(0x40000000, display.getBackgroundColor());
        assertEquals("center", display.getBillboard());
        assertNull(display.getBrightness());
    }

    @Test
    void tntMinecartCarriesFuse() {
        UUID cartId = UUID.randomUUID();
        entities.entities.put(cartId, living(cartId, "minecraft:tnt_minecart", "TntCart", 13, 13, 10));
        entities.species.put(cartId, Map.of("fuse", 45, "primed", true));
        SpeciesEntities.MinecartTntEntity cart =
                (SpeciesEntities.MinecartTntEntity) EntityApi.wrap(cartId);
        assertEquals(45, cart.getFuse());
        assertEquals(true, cart.isPrimed());
    }

    @Test
    void tameableBaseCarriesOwnerState() {
        entities.species.put(zombieId, Map.of(
                "noAi", false,
                "underWaterConverting", true,
                "target", sheepId.toString()));
        UUID wolfId = UUID.randomUUID();
        entities.entities.put(wolfId, living(wolfId, "minecraft:wolf", "Wolf", 14, 14, 20));
        entities.species.put(wolfId, Map.of(
                "collarColor", "minecraft:red",
                "tamed", true,
                "sitting", true,
                "owner", zombieId.toString(),
                "angry", false,
                "wet", true,
                "armor", "minecraft:wolf_armor"));
        SpeciesEntities.WolfEntity wolf = (SpeciesEntities.WolfEntity) EntityApi.wrap(wolfId);
        assertEquals(true, wolf.isTamed());
        assertEquals(true, wolf.isSitting());
        assertEquals(true, wolf.isWet());
        assertEquals(false, wolf.isAngry());
        assertEquals("minecraft:wolf_armor", wolf.getArmor());
        EntityApi.Entity owner = wolf.getOwner();
        assertTrue(owner instanceof SpeciesEntities.ZombieEntity, "owner wraps as the zombie");
    }

    @Test
    void illagerFamilySharesArmPose() {
        UUID pillagerId = UUID.randomUUID();
        entities.entities.put(pillagerId, living(pillagerId, "minecraft:pillager", "Pillager", 15, 15, 24));
        entities.species.put(pillagerId, Map.of(
                "armPose", "crossed",
                "chargingCrossbow", true,
                "castingSpell", false,
                "noAi", false));
        SpeciesEntities.PillagerEntity pillager =
                (SpeciesEntities.PillagerEntity) EntityApi.wrap(pillagerId);
        assertEquals("crossed", pillager.getArmPose());
        assertEquals(true, pillager.isChargingCrossbow());

        UUID evokerId = UUID.randomUUID();
        entities.entities.put(evokerId, living(evokerId, "minecraft:evoker", "Evoker", 16, 16, 24));
        entities.species.put(evokerId, Map.of(
                "armPose", "spellcasting",
                "castingSpell", true));
        SpeciesEntities.EvokerEntity evoker =
                (SpeciesEntities.EvokerEntity) EntityApi.wrap(evokerId);
        assertTrue(evoker instanceof SpeciesEntities.SpellcasterIllagerEntity,
                "evoker sits on the spellcaster tree");
        assertEquals(true, evoker.isCastingSpell());
    }

    @Test
    void pigWrapsWithSaddleState() {
        UUID pigId = UUID.randomUUID();
        entities.entities.put(pigId, living(pigId, "minecraft:pig", "Pig", 17, 17, 10));
        entities.species.put(pigId, Map.of("saddled", true));
        SpeciesEntities.PigEntity pig = (SpeciesEntities.PigEntity) EntityApi.wrap(pigId);
        assertEquals(true, pig.isSaddled());
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
