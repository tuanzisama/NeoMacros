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
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.ItemStackData;
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
    void armorStandCarriesVisibilityAndSixPoseTriples() {
        UUID standId = UUID.randomUUID();
        entities.entities.put(standId,
                living(standId, "minecraft:armor_stand", "Stand", 20, 20, 20));
        Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put("marker", false);
        state.put("small", true);
        state.put("invisible", true);
        state.put("showArms", true);
        state.put("showBasePlate", false);
        state.put("bodyPose", new ink.tuanzi.neomacros.port.model.Pos3d(0, 22.5, 0));
        state.put("headPose", new ink.tuanzi.neomacros.port.model.Pos3d(-10, 5, 0));
        state.put("leftArmPose", new ink.tuanzi.neomacros.port.model.Pos3d(-90, 0, 0));
        state.put("rightArmPose", new ink.tuanzi.neomacros.port.model.Pos3d(0, 0, 0));
        state.put("leftLegPose", new ink.tuanzi.neomacros.port.model.Pos3d(0, 0, 0));
        state.put("rightLegPose", new ink.tuanzi.neomacros.port.model.Pos3d(1, 2, 3));
        entities.species.put(standId, state);

        EntityApi.Entity handle = EntityApi.wrap(standId);
        assertTrue(handle instanceof SpeciesEntities.ArmorStandEntity,
                "armor stand wraps as ArmorStand");
        SpeciesEntities.ArmorStandEntity stand =
                (SpeciesEntities.ArmorStandEntity) handle;
        assertEquals(true, stand.isInvisible());
        assertEquals(true, stand.showArms());
        assertEquals(false, stand.showBasePlate());
        assertEquals(true, stand.isSmall());
        assertEquals(new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 22.5, 0),
                stand.getBodyPose());
        assertEquals(new ink.tuanzi.neomacros.core.api.types.Pos3D(-10, 5, 0),
                stand.getHeadPose());
        assertEquals(new ink.tuanzi.neomacros.core.api.types.Pos3D(-90, 0, 0),
                stand.getLeftArmPose());
        assertEquals(new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 0, 0),
                stand.getRightArmPose());
        assertEquals(new ink.tuanzi.neomacros.core.api.types.Pos3D(0, 0, 0),
                stand.getLeftLegPose());
        assertEquals(new ink.tuanzi.neomacros.core.api.types.Pos3D(1, 2, 3),
                stand.getRightLegPose());
    }

    @Test
    void armorStandPoseReadsBackUndefinedWithoutState() {
        UUID standId = UUID.randomUUID();
        entities.entities.put(standId,
                living(standId, "minecraft:armor_stand", "Stand", 21, 21, 20));
        SpeciesEntities.ArmorStandEntity stand =
                (SpeciesEntities.ArmorStandEntity) EntityApi.wrap(standId);
        assertNull(stand.isInvisible());
        assertNull(stand.showArms());
        assertNull(stand.showBasePlate());
        assertNull(stand.getHeadPose());
        assertNull(stand.getBodyPose());
    }

    @Test
    void pandaCarriesGeneRecessiveFlags() {
        UUID pandaId = UUID.randomUUID();
        entities.entities.put(pandaId,
                living(pandaId, "minecraft:panda", "Panda", 22, 22, 20));
        entities.species.put(pandaId, Map.of(
                "mainGene", "minecraft:brown",
                "hiddenGene", "minecraft:normal",
                "mainGeneRecessive", true,
                "hiddenGeneRecessive", false));
        SpeciesEntities.PandaEntity panda =
                (SpeciesEntities.PandaEntity) EntityApi.wrap(pandaId);
        assertEquals("minecraft:brown", panda.getMainGene());
        assertEquals("minecraft:normal", panda.getHiddenGene());
        assertEquals(true, panda.isMainGeneRecessive());
        assertEquals(false, panda.isHiddenGeneRecessive());
    }

    @Test
    void displaysCarryVisibilityBoxTransformAndTextData() {
        UUID blockId = UUID.randomUUID();
        entities.entities.put(blockId,
                living(blockId, "minecraft:block_display", "Block", 23, 23, 1));
        Map<String, Object> blockState = new java.util.LinkedHashMap<>();
        blockState.put("billboard", "vertical");
        blockState.put("visibilityBoundingBox", new ink.tuanzi.neomacros.port.model.BoundingBoxData(
                new ink.tuanzi.neomacros.port.model.Pos3d(-0.5, 64, -0.5),
                new ink.tuanzi.neomacros.port.model.Pos3d(0.5, 65, 0.5)));
        blockState.put("block", "minecraft:stone");
        blockState.put("blockState", new ink.tuanzi.neomacros.port.model.BlockData(
                "minecraft:stone",
                new ink.tuanzi.neomacros.port.model.Pos3d(0, 64, 0),
                Map.of("facing", "north")));
        entities.species.put(blockId, blockState);
        SpeciesEntities.BlockDisplayEntity blockDisplay =
                (SpeciesEntities.BlockDisplayEntity) EntityApi.wrap(blockId);
        assertEquals("vertical", blockDisplay.getBillboard());
        assertEquals("minecraft:stone", blockDisplay.getBlock());
        ink.tuanzi.neomacros.core.api.types.BlockState rendered =
                blockDisplay.getBlockState();
        assertEquals("minecraft:stone", rendered.getId());
        assertEquals("north", rendered.getProps().get("facing"));
        EntityApi.BoundingBox box = blockDisplay.getVisibilityBoundingBox();
        assertEquals(new ink.tuanzi.neomacros.core.api.types.Pos3D(-0.5, 64, -0.5),
                box.min());
        assertEquals(new ink.tuanzi.neomacros.core.api.types.Pos3D(0.5, 65, 0.5),
                box.max());

        UUID itemId = UUID.randomUUID();
        entities.entities.put(itemId,
                living(itemId, "minecraft:item_display", "Item", 24, 24, 1));
        entities.species.put(itemId, Map.of(
                "billboard", "fixed",
                "transform", "ground"));
        SpeciesEntities.ItemDisplayEntity itemDisplay =
                (SpeciesEntities.ItemDisplayEntity) EntityApi.wrap(itemId);
        assertEquals("ground", itemDisplay.getTransform());

        UUID textId = UUID.randomUUID();
        entities.entities.put(textId,
                living(textId, "minecraft:text_display", "Text", 25, 25, 1));
        Map<String, Object> textState = new java.util.LinkedHashMap<>();
        textState.put("lineWidth", 200);
        textState.put("textOpacity", -1);
        textState.put("shadow", true);
        textState.put("seeThrough", false);
        textState.put("defaultBackground", true);
        textState.put("alignment", "left");
        entities.species.put(textId, textState);
        SpeciesEntities.TextDisplayEntity textDisplay =
                (SpeciesEntities.TextDisplayEntity) EntityApi.wrap(textId);
        assertEquals(200, textDisplay.getLineWidth());
        assertEquals(-1, textDisplay.getTextOpacity());
        assertEquals(true, textDisplay.hasShadow());
        assertEquals(false, textDisplay.hasSeeThrough());
        assertEquals(true, textDisplay.hasDefaultBackground());
        assertEquals("left", textDisplay.getAlignment());
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

    @Test
    void witherCarriesPoweredPhaseFlag() {
        UUID id = UUID.randomUUID();
        entities.entities.put(id, living(id, "minecraft:wither", "Wither", 5, 5, 300));
        entities.species.put(id, Map.of("invulnerableTicks", 0, "powered", true));
        SpeciesEntities.WitherBossEntity wither =
                (SpeciesEntities.WitherBossEntity) EntityApi.wrap(id);
        assertEquals(true, wither.isPowered());
        assertEquals(0, wither.getInvulnerableTicks().intValue());
    }

    @Test
    void endermanCarriesProvokedAndHeldBlockState() {
        UUID id = UUID.randomUUID();
        entities.entities.put(id, living(id, "minecraft:enderman", "Enderman", 6, 6, 40));
        entities.species.put(id, Map.of(
                "provoked", true,
                "screaming", false,
                "carriedBlock", "minecraft:sand",
                "heldBlock", new BlockData("minecraft:sand", new Pos3d(6, 64, 6),
                        Map.of("falling_block", "true"))));
        SpeciesEntities.EndermanEntity enderman =
                (SpeciesEntities.EndermanEntity) EntityApi.wrap(id);
        assertEquals(true, enderman.isProvoked());
        assertEquals(false, enderman.isScreaming());
        var held = enderman.getHeldBlock();
        assertEquals("minecraft:sand", held.getId());
        assertEquals("true", held.getProps().get("falling_block"));
    }

    @Test
    void endermanHeldBlockReadsBackUndefinedWithoutState() {
        UUID id = UUID.randomUUID();
        entities.entities.put(id, living(id, "minecraft:enderman", "Enderman", 6, 7, 40));
        entities.species.put(id, Map.of());
        SpeciesEntities.EndermanEntity enderman =
                (SpeciesEntities.EndermanEntity) EntityApi.wrap(id);
        assertNull(enderman.getHeldBlock());
    }

    @Test
    void guardianCarriesMovingFlag() {
        UUID id = UUID.randomUUID();
        entities.entities.put(id, living(id, "minecraft:guardian", "Guardian", 7, 7, 30));
        entities.species.put(id, Map.of("moving", true));
        SpeciesEntities.GuardianEntity guardian =
                (SpeciesEntities.GuardianEntity) EntityApi.wrap(id);
        assertEquals(true, guardian.isMoving());
        assertNull(guardian.getActiveTarget());
    }

    @Test
    void illagerCarriesCelebratingFlag() {
        UUID id = UUID.randomUUID();
        entities.entities.put(id, living(id, "minecraft:vindicator", "Vindicator", 8, 8, 24));
        entities.species.put(id, Map.of("armPose", "crossed", "celebrating", true));
        SpeciesEntities.AbstractIllagerEntity illager =
                (SpeciesEntities.AbstractIllagerEntity) EntityApi.wrap(id);
        assertEquals("crossed", illager.getArmPose());
        assertEquals(true, illager.isCelebrating());
    }

    @Test
    void effectCloudCarriesPackedColor() {
        UUID id = UUID.randomUUID();
        entities.entities.put(id, living(id, "minecraft:area_effect_cloud", "Cloud", 9, 9, 10));
        entities.species.put(id, Map.of(
                "radius", 3.0, "waiting", false, "waitTime", 0,
                "particle", "minecraft:entity_effect", "color", 0x80FF00FF));
        SpeciesEntities.AreaEffectCloudEntity cloud =
                (SpeciesEntities.AreaEffectCloudEntity) EntityApi.wrap(id);
        assertEquals(0x80FF00FF, cloud.getColor().intValue());
        assertEquals("minecraft:entity_effect", cloud.getParticle());
        assertEquals(3.0, cloud.getRadius().doubleValue());
    }

    @Test
    void fallingBlockCarriesOriginAndFullState() {
        UUID id = UUID.randomUUID();
        entities.entities.put(id, living(id, "minecraft:falling_block", "Falling", 10, 10, 10));
        entities.species.put(id, Map.of(
                "block", "minecraft:sand",
                "blockState", new BlockData("minecraft:sand", new Pos3d(10, 64, 10),
                        Map.of("falling_block", "true")),
                "startPos", new Pos3d(12, 70, 14)));
        SpeciesEntities.FallingBlockEntity falling =
                (SpeciesEntities.FallingBlockEntity) EntityApi.wrap(id);
        assertEquals("minecraft:sand", falling.getBlockState().getId());
        assertEquals("true", falling.getBlockState().getProps().get("falling_block"));
        var start = falling.getStartPos();
        assertEquals(12.0, start.getX());
        assertEquals(70.0, start.getY());
        assertEquals(14.0, start.getZ());
    }

    @Test
    void horseAndChestedCarryStandingAndChestState() {
        UUID horseId = UUID.randomUUID();
        entities.entities.put(horseId, living(horseId, "minecraft:horse", "Horse", 11, 11, 30));
        entities.species.put(horseId, Map.of("tamed", true, "standing", true, "saddled", false));
        SpeciesEntities.HorseEntity horse =
                (SpeciesEntities.HorseEntity) EntityApi.wrap(horseId);
        assertEquals(true, horse.isStanding());
        assertEquals(true, horse.isTamed());

        UUID donkeyId = UUID.randomUUID();
        entities.entities.put(donkeyId, living(donkeyId, "minecraft:donkey", "Donkey", 12, 12, 30));
        entities.species.put(donkeyId, Map.of("tamed", false, "standing", false, "hasChest", true));
        SpeciesEntities.DonkeyEntity donkey =
                (SpeciesEntities.DonkeyEntity) EntityApi.wrap(donkeyId);
        assertEquals(true, donkey.hasChest());
        assertEquals(false, donkey.isStanding());
    }

    @Test
    void axolotlCarriesVariantId() {
        UUID id = UUID.randomUUID();
        entities.entities.put(id, living(id, "minecraft:axolotl", "Axolotl", 13, 13, 14));
        entities.species.put(id, Map.of(
                "variant", "minecraft:lucy", "variantId", 0, "playingDead", false));
        SpeciesEntities.AxolotlEntity axolotl = (SpeciesEntities.AxolotlEntity) EntityApi.wrap(id);
        assertEquals("minecraft:lucy", axolotl.getVariant());
        assertEquals(0, axolotl.getVariantId().intValue());
    }

    @Test
    void tropicalFishCarriesSizeAndVarietyId() {
        UUID id = UUID.randomUUID();
        entities.entities.put(id, living(id, "minecraft:tropical_fish", "Fish", 14, 14, 3));
        entities.species.put(id, Map.of(
                "pattern", "minecraft:kob", "baseColor", "minecraft:white",
                "patternColor", "minecraft:orange", "size", "large",
                "varietyId", 0x01020304));
        SpeciesEntities.TropicalFishEntity fish =
                (SpeciesEntities.TropicalFishEntity) EntityApi.wrap(id);
        assertEquals("large", fish.getSize());
        assertEquals(0x01020304, fish.getVarietyId().intValue());
        assertEquals("minecraft:kob", fish.getPattern());
    }

    @Test
    void wolfCarriesBeggingFlag() {
        UUID id = UUID.randomUUID();
        entities.entities.put(id, living(id, "minecraft:wolf", "Wolf", 15, 15, 20));
        entities.species.put(id, Map.of(
                "collarColor", "minecraft:red", "begging", true, "angry", false, "wet", false));
        SpeciesEntities.WolfEntity wolf = (SpeciesEntities.WolfEntity) EntityApi.wrap(id);
        assertEquals(true, wolf.isBegging());
        assertEquals(false, wolf.isAngry());
    }

    @Test
    void boatCarriesPickResultStack() {
        UUID id = UUID.randomUUID();
        entities.entities.put(id, plain(id, "minecraft:oak_boat", "Boat", 16, 16));
        entities.species.put(id, Map.of(
                "underWater", false,
                "boatItem", new ItemStackData("minecraft:oak_boat", 1, "Oak Boat")));
        SpeciesEntities.BoatEntity boat = (SpeciesEntities.BoatEntity) EntityApi.wrap(id);
        var item = boat.getBoatItem();
        assertEquals("minecraft:oak_boat", item.getId());
        assertEquals(1, item.getCount());
        assertEquals(false, boat.isUnderWater());
    }

    @Test
    void animalPredicatesReachThePort() {
        UUID selfId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        entities.entities.put(selfId, living(selfId, "minecraft:cow", "Cow", 17, 17, 10));
        entities.entities.put(otherId, living(otherId, "minecraft:cow", "Cow", 18, 18, 10));
        entities.foodResult = true;
        entities.breedResult = true;
        SpeciesEntities.CowEntity self = (SpeciesEntities.CowEntity) EntityApi.wrap(selfId);
        SpeciesEntities.CowEntity other = (SpeciesEntities.CowEntity) EntityApi.wrap(otherId);
        assertTrue(self.isFood("wheat"));
        assertEquals("minecraft:wheat", entities.lastFoodId);
        entities.foodResult = false;
        assertFalse(self.isFood("wheat"));
        assertTrue(self.canBreedWith(other));
        entities.breedResult = false;
        assertFalse(self.canBreedWith(other));
        assertFalse(self.canBreedWith(null));
    }

    @Test
    void traderLlamaAndGlowFrameWrapByType() {
        UUID llamaId = UUID.randomUUID();
        entities.entities.put(llamaId, living(llamaId, "minecraft:trader_llama", "TraderLlama",
                19, 19, 22));
        entities.species.put(llamaId, Map.of("strength", 3, "llamaVariant", "minecraft:creamy"));
        assertTrue(EntityApi.wrap(llamaId) instanceof SpeciesEntities.LlamaEntity);

        UUID frameId = UUID.randomUUID();
        entities.entities.put(frameId, plain(frameId, "minecraft:glow_item_frame", "Frame",
                20, 20));
        assertTrue(EntityApi.wrap(frameId) instanceof SpeciesEntities.ItemFrameEntity);

        UUID muleId = UUID.randomUUID();
        entities.entities.put(muleId, living(muleId, "minecraft:mule", "Mule", 21, 21, 30));
        entities.species.put(muleId, Map.of("hasChest", true));
        SpeciesEntities.AbstractChestedHorseEntity mule =
                (SpeciesEntities.AbstractChestedHorseEntity) EntityApi.wrap(muleId);
        assertEquals(true, mule.hasChest());
    }

    /** Minimal non-living snapshot factory (plain entity kinds). */
    private static EntitySnapshot plain(UUID uuid, String type, String name, double x,
            double z) {
        return new EntitySnapshot(uuid, type, name, new Pos3d(x, 64, z), 0);
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
        boolean foodResult;
        boolean breedResult;
        String lastFoodId;

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

        @Override
        public boolean isFood(UUID uuid, String itemId) {
            lastFoodId = itemId;
            return foodResult;
        }

        @Override
        public boolean canBreedWith(UUID uuid, UUID other) {
            return breedResult;
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
