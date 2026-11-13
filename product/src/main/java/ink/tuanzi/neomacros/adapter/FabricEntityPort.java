package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.BoundingBoxData;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Entity query services over the rendered client world. Every query is
 * marshaled onto the game main thread; results are point-in-time snapshots.
 */
public final class FabricEntityPort implements EntityPort {

    @Override
    public Optional<EntitySnapshot> byUuid(UUID uuid) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return Optional.empty();
            }
            for (Entity entity : level.entitiesForRendering()) {
                if (entity.getUUID().equals(uuid)) {
                    return Optional.of(snapshotOf(entity));
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<EntitySnapshot> inRadius(Pos3d center, double range, Predicate<EntitySnapshot> filter) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return List.of();
            }
            double rangeSquared = range * range;
            List<EntitySnapshot> hits = new ArrayList<>();
            for (Entity entity : level.entitiesForRendering()) {
                double dx = entity.getX() - center.x();
                double dy = entity.getY() - center.y();
                double dz = entity.getZ() - center.z();
                if (dx * dx + dy * dy + dz * dz > rangeSquared) {
                    continue;
                }
                EntitySnapshot snapshot = snapshotOf(entity);
                if (filter.test(snapshot)) {
                    hits.add(snapshot);
                }
            }
            return List.copyOf(hits);
        });
    }

    @Override
    public List<EntitySnapshot> inBox(Pos3d min, Pos3d max, Predicate<EntitySnapshot> filter) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return List.of();
            }
            List<EntitySnapshot> hits = new ArrayList<>();
            for (Entity entity : level.entitiesForRendering()) {
                double x = entity.getX();
                double y = entity.getY();
                double z = entity.getZ();
                if (x < min.x() || x > max.x() || y < min.y() || y > max.y()
                        || z < min.z() || z > max.z()) {
                    continue;
                }
                EntitySnapshot snapshot = snapshotOf(entity);
                if (filter.test(snapshot)) {
                    hits.add(snapshot);
                }
            }
            return List.copyOf(hits);
        });
    }

    /** Copies one entity's current state into the neutral snapshot. */
    static EntitySnapshot snapshotOf(Entity entity) {
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        boolean living = entity instanceof LivingEntity;
        LivingEntity livingEntity = living ? (LivingEntity) entity : null;
        Vec3 delta = entity.getDeltaMovement();
        Vec3 view = entity.getViewVector(1.0f);
        var bb = entity.getBoundingBox();
        List<UUID> passengers = new ArrayList<>();
        for (Entity rider : entity.getPassengers()) {
            passengers.add(rider.getUUID());
        }
        Entity vehicle = entity.getVehicle();
        ItemStackData held = null;
        ItemStackData offhand = null;
        if (livingEntity != null) {
            held = stackData(livingEntity.getMainHandItem());
            offhand = stackData(livingEntity.getOffhandItem());
        } else if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
            held = stackData(itemEntity.getItem());
        }
        double health = livingEntity != null ? livingEntity.getHealth() : 0.0;
        double maxHealth = livingEntity != null ? livingEntity.getMaxHealth() : 0.0;
        return new EntitySnapshot(entity.getUUID(), typeId, entity.getName().getString(),
                new Pos3d(entity.getX(), entity.getY(), entity.getZ()), health, maxHealth,
                new Pos3d(delta.x, delta.y, delta.z),
                new Pos3d(view.x, view.y, view.z),
                new Rot2d(entity.getYRot(), entity.getXRot()),
                entity.getEyeHeight(),
                entity.onGround(), entity.isShiftKeyDown(), entity.isSprinting(),
                entity.isSwimming(), livingEntity != null && livingEntity.isFallFlying(),
                entity.isInWater(), entity.isInLava(), entity.isOnFire(),
                entity.getPose() != null
                        ? entity.getPose().name().toLowerCase(java.util.Locale.ROOT) : "standing",
                facingOf(entity),
                entity.level() != null ? entity.level().dimension().toString() : "",
                // 26.x official names: AABB exposes public final fields
                new BoundingBoxData(new Pos3d(bb.minX, bb.minY, bb.minZ),
                        new Pos3d(bb.maxX, bb.maxY, bb.maxZ)),
                List.copyOf(passengers),
                vehicle != null ? vehicle.getUUID() : null,
                held, offhand, living);
    }

    /** Copies one item stack into the neutral data, or null for empty stacks. */
    private static ItemStackData stackData(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return FabricInventoryPort.stackDataOf(stack);
    }

    /** The discretized horizontal facing direction name. */
    private static String facingOf(Entity entity) {
        var direction = entity.getDirection();
        return direction != null ? direction.getName() : "south";
    }


    @Override
    public java.util.Optional<Object> rawByUuid(java.util.UUID uuid) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return java.util.Optional.empty();
        }
        for (var entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) {
                return java.util.Optional.of(entity);
            }
        }
        return java.util.Optional.empty();
    }

    @Override
    public Map<String, Object> speciesState(UUID uuid) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return Map.of();
            }
            for (Entity entity : level.entitiesForRendering()) {
                if (!entity.getUUID().equals(uuid)) {
                    continue;
                }
                Map<String, Object> state = new java.util.LinkedHashMap<>();
                collectSpeciesState(entity, state);
                return state;
            }
            return Map.of();
        });
    }

    /** Fills the neutral species state from one live entity. */
    private static void collectSpeciesState(Entity entity, Map<String, Object> state) {
        if (entity instanceof Mob mob) {
            state.put("noAi", mob.isNoAi());
            net.minecraft.world.entity.LivingEntity target = mob.getTarget();
            state.put("target", target != null ? target.getUUID().toString() : null);
        }
        if (entity instanceof Sheep sheep) {
            state.put("color", namespaced(sheep.getColor()));
            state.put("sheared", sheep.isSheared());
            state.put("shearable", sheep.readyForShearing());
            var customName = sheep.getCustomName();
            state.put("jeb", customName != null && "jeb_".equalsIgnoreCase(customName.getString()));
        }
        if (entity instanceof Wolf wolf) {
            state.put("collarColor", namespaced(wolf.getCollarColor()));
            //? if <26.1 {
            /*
            state.put("angry", wolf.getRemainingPersistentAngerTime() > 0);
            */
            //?} else {
            state.put("angry", wolf.getPersistentAngerEndTime() > wolf.level().getGameTime());
            //?}
            state.put("wet", wolf.getWetShade(1.0F) > 0.0F);
            state.put("armor", equipmentId(wolf.getItemBySlot(
                    net.minecraft.world.entity.EquipmentSlot.BODY)));
        }
        if (entity instanceof net.minecraft.world.entity.TamableAnimal tameable) {
            state.put("tamed", tameable.isTame());
            state.put("sitting", tameable.isInSittingPose());
            var ownerRef = tameable.getOwnerReference();
            state.put("owner", ownerRef != null ? ownerRef.getUUID().toString() : null);
        }
        if (entity instanceof Cat cat) {
            state.put("collarColor", namespaced(cat.getCollarColor()));
            state.put("variant", registeredName(cat.getVariant()));
        }
        if (entity instanceof Villager villager) {
            var data = villager.getVillagerData();
            state.put("profession", registeredName(data.profession()));
            state.put("level", data.level());
            state.put("variant", registeredName(data.type()));
        }
        if (entity instanceof Creeper creeper) {
            state.put("powered", creeper.isPowered());
            state.put("ignited", creeper.isIgnited());
            state.put("swellDir", creeper.getSwellDir());
            state.put("swell", (double) creeper.getSwelling(1.0F));
        }
        if (entity instanceof Zombie zombie) {
            state.put("underWaterConverting", zombie.isUnderWaterConverting());
        }
        if (entity instanceof PrimedTnt tnt) {
            state.put("fuse", tnt.getFuse());
        }
        if (entity instanceof Bat bat) {
            state.put("resting", bat.isResting());
        }
        if (entity instanceof MushroomCow mooshroom) {
            state.put("variant", namespaced(mooshroom.getVariant()));
            state.put("shearable", mooshroom.readyForShearing());
        }
        if (entity instanceof net.minecraft.world.entity.monster.Enderman enderman) {
            var carried = enderman.getCarriedBlock();
            state.put("carriedBlock", carried != null
                    ? BuiltInRegistries.BLOCK.getKey(carried.getBlock()).toString() : null);
            state.put("screaming", enderman.isCreepy());
        }
        if (entity instanceof net.minecraft.world.entity.monster.Phantom phantom) {
            state.put("size", phantom.getPhantomSize());
        }
        if (entity instanceof net.minecraft.world.entity.monster.spider.Spider spider) {
            state.put("climbing", spider.isClimbing());
        }
        if (entity instanceof net.minecraft.world.entity.animal.equine.AbstractHorse horse) {
            state.put("tamed", horse.isTamed());
            state.put("eating", horse.isEating());
            state.put("bred", horse.isBred());
            state.put("inventorySize", horse.getInventorySize());
            state.put("saddled", !horse.getItemBySlot(
                    net.minecraft.world.entity.EquipmentSlot.SADDLE).isEmpty());
            var ownerRef = horse.getOwnerReference();
            state.put("owner", ownerRef != null ? ownerRef.getUUID().toString() : null);
        }
        if (entity instanceof net.minecraft.world.entity.animal.equine.Horse horse2) {
            state.put("variant", namespaced(horse2.getVariant()));
        }
        if (entity instanceof net.minecraft.world.entity.animal.equine.Llama llama) {
            state.put("strength", llama.getStrength());
            state.put("llamaVariant", namespaced(llama.getVariant()));
        }
        if (entity instanceof net.minecraft.world.entity.animal.golem.IronGolem golem) {
            state.put("playerCreated", golem.isPlayerCreated());
        }
        if (entity instanceof net.minecraft.world.entity.animal.axolotl.Axolotl axolotl) {
            state.put("variant", serialName(axolotl.getVariant()));
            state.put("playingDead", axolotl.isPlayingDead());
            state.put("fromBucket", axolotl.fromBucket());
        }
        if (entity instanceof net.minecraft.world.entity.animal.bee.Bee bee) {
            state.put("nectar", bee.hasNectar());
            state.put("stung", bee.hasStung());
            //? if <26.1 {
            /*
            state.put("angry", bee.getRemainingPersistentAngerTime() > 0);
            */
            //?} else {
            state.put("angry", bee.getPersistentAngerEndTime() > bee.level().getGameTime());
            //?}
        }
        if (entity instanceof net.minecraft.world.entity.animal.fox.Fox fox) {
            state.put("sitting", fox.isSitting());
            state.put("variant", namespaced(fox.getVariant()));
            state.put("pouncing", fox.isPouncing());
            state.put("sleeping", fox.isSleeping());
            state.put("faceplanted", fox.isFaceplanted());
            state.put("crouching", fox.isCrouching());
        }
        if (entity instanceof net.minecraft.world.entity.animal.frog.Frog frog) {
            state.put("variant", registeredName(frog.getVariant()));
            state.put("croaking", frog.croakAnimationState.isStarted());
            var tongue = frog.getTongueTarget();
            state.put("tongueTarget", tongue
                    .map(net.minecraft.world.entity.Entity::getUUID)
                    .map(UUID::toString).orElse(null));
        }
        if (entity instanceof net.minecraft.world.entity.animal.goat.Goat goat) {
            state.put("leftHorn", goat.hasLeftHorn());
            state.put("rightHorn", goat.hasRightHorn());
            state.put("screaming", goat.isScreamingGoat());
        }
        if (entity instanceof net.minecraft.world.entity.animal.panda.Panda panda) {
            state.put("mainGene", namespaced(panda.getMainGene()));
            state.put("hiddenGene", namespaced(panda.getHiddenGene()));
            state.put("mainGeneRecessive", panda.getMainGene().isRecessive());
            state.put("hiddenGeneRecessive", panda.getHiddenGene().isRecessive());
            state.put("sneezing", panda.isSneezing());
            state.put("onBack", panda.isOnBack());
            state.put("eating", panda.isEating());
            state.put("lazy", panda.isLazy());
            state.put("sitting", panda.isSitting());
            state.put("worried", panda.isWorried());
            state.put("playful", panda.isPlayful());
            state.put("rolling", panda.isRolling());
            state.put("brown", panda.isBrown());
            state.put("weak", panda.isWeak());
            state.put("scared", panda.isScared());
        }
        if (entity instanceof net.minecraft.world.entity.animal.parrot.Parrot parrot) {
            state.put("variant", serialName(parrot.getVariant()));
            state.put("partying", parrot.isPartyParrot());
        }
        if (entity instanceof net.minecraft.world.entity.animal.polarbear.PolarBear polarBear) {
            state.put("standing", polarBear.isStanding());
        }
        if (entity instanceof net.minecraft.world.entity.monster.Shulker shulker) {
            state.put("color", namespaced(shulker.getColor()));
            state.put("attachFace", shulker.getAttachFace().name()
                    .toLowerCase(java.util.Locale.ROOT));
            state.put("peek", (double) shulker.getClientPeekAmount(1.0F));
        }
        if (entity instanceof net.minecraft.world.entity.animal.golem.SnowGolem snowGolem) {
            state.put("pumpkin", snowGolem.hasPumpkin());
            state.put("shearable", snowGolem.readyForShearing());
        }
        if (entity instanceof net.minecraft.world.entity.animal.turtle.Turtle turtle) {
            state.put("hasEgg", turtle.hasEgg());
            //? if >=26.2 {
            var home = turtle.getHomePos();
            state.put("homePos", new Pos3d(home.getX(), home.getY(), home.getZ()));
            //?}
        }
        if (entity instanceof net.minecraft.world.entity.monster.Vex vex) {
            state.put("charging", vex.isCharging());
        }
        if (entity instanceof net.minecraft.world.entity.monster.Witch witch) {
            state.put("drinkingPotion", witch.isDrinkingPotion());
        }
        if (entity instanceof net.minecraft.world.entity.animal.dolphin.Dolphin dolphin) {
            state.put("moistness", dolphin.getMoistnessLevel());
            state.put("hasFish", dolphin.gotFish());
        }
        if (entity instanceof net.minecraft.world.entity.monster.warden.Warden warden) {
            state.put("angerLevel", serialName(warden.getAngerLevel()));
        }
        if (entity instanceof net.minecraft.world.entity.monster.Ghast ghast) {
            state.put("charging", ghast.isCharging());
        }
        if (entity instanceof net.minecraft.world.entity.animal.rabbit.Rabbit rabbit) {
            state.put("variant", namespaced(rabbit.getVariant()));
        }
        if (entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither) {
            state.put("invulnerableTicks", wither.getInvulnerableTicks());
        }
        if (entity instanceof net.minecraft.world.entity.animal.allay.Allay allay) {
            state.put("dancing", allay.isDancing());
            state.put("spinning", allay.isSpinning());
            state.put("hasItemInHand", allay.hasItemInHand());
            state.put("flapping", allay.isFlapping());
        }
        if (entity instanceof net.minecraft.world.entity.animal.cow.Cow cow) {
            state.put("variant", registeredName(cow.getVariant()));
        }
        if (entity instanceof net.minecraft.world.entity.animal.armadillo.Armadillo armadillo) {
            state.put("state", namespaced(armadillo.getState()));
            state.put("scared", armadillo.isScared());
        }
        if (entity instanceof net.minecraft.world.entity.animal.camel.Camel camel) {
            state.put("dashing", camel.isDashing());
            state.put("sitting", camel.isCamelSitting());
            state.put("poseTransition", camel.isInPoseTransition());
            state.put("poseTime", camel.getPoseTime());
        }
        if (entity instanceof net.minecraft.world.entity.animal.sniffer.Sniffer sniffer) {
            state.put("searching", sniffer.isSearching());
            state.put("tempted", sniffer.isTempted());
            state.put("canSniff", sniffer.canSniff());
        }
        if (entity instanceof net.minecraft.world.entity.animal.fish.AbstractFish fish) {
            state.put("fromBucket", fish.fromBucket());
        }
        if (entity instanceof net.minecraft.world.entity.animal.fish.AbstractSchoolingFish schooling) {
            state.put("hasFollowers", schooling.hasFollowers());
            state.put("follower", schooling.isFollower());
            state.put("canBeFollowed", schooling.canBeFollowed());
        }
        if (entity instanceof net.minecraft.world.entity.animal.fish.Salmon salmon) {
            state.put("variant", namespaced(salmon.getVariant()));
        }
        if (entity instanceof net.minecraft.world.entity.animal.fish.Pufferfish pufferfish) {
            state.put("puffState", pufferfish.getPuffState());
        }
        if (entity instanceof net.minecraft.world.entity.animal.fish.TropicalFish tropicalFish) {
            state.put("pattern", namespaced(tropicalFish.getPattern()));
            state.put("baseColor", namespaced(tropicalFish.getBaseColor()));
            state.put("patternColor", namespaced(tropicalFish.getPatternColor()));
        }
        if (entity instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin abstractPiglin) {
            state.put("converting", abstractPiglin.isConverting());
            state.put("adult", abstractPiglin.isAdult());
        }
        if (entity instanceof net.minecraft.world.entity.monster.illager.Pillager pillager) {
            state.put("chargingCrossbow", pillager.isChargingCrossbow());
        }
        if (entity instanceof net.minecraft.world.entity.monster.illager.Evoker evoker) {
            state.put("castingSpell", evoker.isCastingSpell());
        }
        if (entity instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader wanderingTrader) {
            state.put("despawnDelay", wanderingTrader.getDespawnDelay());
        }
        if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon enderDragon) {
            state.put("phase", enderDragon.getEntityData()
                    .get(net.minecraft.world.entity.boss.enderdragon.EnderDragon.DATA_PHASE));
            state.put("deathTime", enderDragon.dragonDeathTime);
        }
        if (entity instanceof net.minecraft.world.entity.monster.breeze.Breeze breeze) {
            state.put("sliding", breeze.slide.isStarted());
        }
        if (entity instanceof net.minecraft.world.entity.monster.creaking.Creaking creaking) {
            state.put("active", creaking.isActive());
            state.put("tearingDown", creaking.isTearingDown());
            state.put("heartBound", creaking.isHeartBound());
            state.put("canMove", creaking.canMove());
        }
        if (entity instanceof net.minecraft.world.entity.monster.zombie.ZombieVillager zombieVillager) {
            var villagerData = zombieVillager.getVillagerData();
            state.put("profession", registeredName(villagerData.profession()));
            state.put("level", villagerData.level());
            state.put("variant", registeredName(villagerData.type()));
            state.put("converting", zombieVillager.isConverting());
        }
        if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand armorStand) {
            state.put("marker", armorStand.isMarker());
            state.put("small", armorStand.isSmall());
            state.put("invisible", armorStand.isInvisible());
            state.put("showArms", armorStand.showArms());
            state.put("showBasePlate", armorStand.showBasePlate());
            state.put("bodyPose", poseData(armorStand.getBodyPose()));
            state.put("headPose", poseData(armorStand.getHeadPose()));
            state.put("leftArmPose", poseData(armorStand.getLeftArmPose()));
            state.put("rightArmPose", poseData(armorStand.getRightArmPose()));
            state.put("leftLegPose", poseData(armorStand.getLeftLegPose()));
            state.put("rightLegPose", poseData(armorStand.getRightLegPose()));
        }
        if (entity instanceof net.minecraft.world.entity.item.FallingBlockEntity fallingBlock) {
            var blockState = fallingBlock.getBlockState();
            state.put("block", BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString());
        }
        if (entity instanceof net.minecraft.world.entity.decoration.ItemFrame itemFrame) {
            state.put("item", FabricInventoryPort.stackDataOf(itemFrame.getItem()));
            state.put("rotation", itemFrame.getRotation());
        }
        if (entity instanceof net.minecraft.world.entity.AreaEffectCloud effectCloud) {
            state.put("radius", (double) effectCloud.getRadius());
            state.put("waiting", effectCloud.isWaiting());
            state.put("waitTime", effectCloud.getWaitTime());
            state.put("particle", BuiltInRegistries.PARTICLE_TYPE
                    .getKey(effectCloud.getParticle().getType()).toString());
        }

        // Projectiles.
        if (entity instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow) {
            state.put("critical", arrow.isCritArrow());
            state.put("pierceLevel", (int) arrow.getPierceLevel());
        }
        if (entity instanceof net.minecraft.world.entity.projectile.arrow.Arrow tippedArrow) {
            state.put("color", tippedArrow.getColor());
        }
        if (entity instanceof net.minecraft.world.entity.projectile.arrow.ThrownTrident trident) {
            state.put("foil", trident.isFoil());
        }
        if (entity instanceof net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull skull) {
            state.put("charged", skull.isDangerous());
        }
        if (entity instanceof net.minecraft.world.entity.projectile.FishingHook hook) {
            var hooked = hook.getHookedIn();
            state.put("hooked", hooked != null ? hooked.getUUID().toString() : null);
            state.put("openWater", hook.isOpenWaterFishing());
            var angler = hook.getPlayerOwner();
            state.put("angler", angler != null ? angler.getUUID().toString() : null);
        }

        // Vehicles.
        if (entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat boat) {
            state.put("underWater", boat.isUnderWater());
        }
        if (entity instanceof net.minecraft.world.entity.vehicle.minecart.MinecartTNT tntCart) {
            state.put("fuse", tntCart.getFuse());
            state.put("primed", tntCart.isPrimed());
        }

        // Decorations and technical entities.
        if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal) {
            var beam = crystal.getBeamTarget();
            state.put("beamTarget", beam != null
                    ? new Pos3d(beam.getX(), beam.getY(), beam.getZ()) : null);
            state.put("showBottom", crystal.showsBottom());
        }
        if (entity instanceof net.minecraft.world.entity.Interaction interaction) {
            state.put("width", (double) interaction.getWidth());
            state.put("height", (double) interaction.getHeight());
            state.put("response", interaction.getResponse());
            var lastAttacker = interaction.getLastAttacker();
            state.put("lastAttacker", lastAttacker != null
                    ? lastAttacker.getUUID().toString() : null);
            var lastInteracted = interaction.getTarget();
            state.put("lastInteracted", lastInteracted != null
                    ? lastInteracted.getUUID().toString() : null);
        }
        if (entity instanceof net.minecraft.world.entity.decoration.painting.Painting painting) {
            var art = painting.getVariant();
            state.put("artId", art.getRegisteredName());
            state.put("width", art.value().width());
            state.put("height", art.value().height());
        }

        // Display entities.
        if (entity instanceof net.minecraft.world.entity.Display display) {
            state.put("billboard", display.getBillboardConstraints().name()
                    .toLowerCase(java.util.Locale.ROOT));
            int packedBrightness = display.getPackedBrightnessOverride();
            state.put("brightness", packedBrightness
                    != net.minecraft.world.entity.Display.NO_BRIGHTNESS_OVERRIDE
                    ? packedBrightness : null);
            state.put("displayWidth", (double) display.getWidth());
            state.put("displayHeight", (double) display.getHeight());
            state.put("glowColorOverride", display.getGlowColorOverride());
            state.put("viewRange", (double) display.getViewRange());
            state.put("shadowRadius", (double) display.getShadowRadius());
            state.put("shadowStrength", (double) display.getShadowStrength());
            // The culling box at the current position: 26.3 replaced the
            // plain getter with the partial-tick form; 1.0 samples the
            // current tick's end, i.e. the box as-is.
            //? if <26.3 {
            /*
            var cullingBox = display.getBoundingBoxForCulling();
            */
            //?} else {
            var cullingBox = display.getInterpolatedBoundingBox(1.0F);
            //?}
            state.put("visibilityBoundingBox", new BoundingBoxData(
                    new Pos3d(cullingBox.minX, cullingBox.minY, cullingBox.minZ),
                    new Pos3d(cullingBox.maxX, cullingBox.maxY, cullingBox.maxZ)));
        }
        if (entity instanceof net.minecraft.world.entity.Display.BlockDisplay blockDisplay) {
            var renderedState = blockDisplay.getBlockState();
            state.put("block", BuiltInRegistries.BLOCK
                    .getKey(renderedState.getBlock()).toString());
            state.put("blockState", new ink.tuanzi.neomacros.port.model.BlockData(
                    BuiltInRegistries.BLOCK.getKey(renderedState.getBlock()).toString(),
                    new Pos3d(entity.getX(), entity.getY(), entity.getZ()),
                    statePropertiesOf(renderedState)));
        }
        if (entity instanceof net.minecraft.world.entity.Display.ItemDisplay itemDisplay) {
            state.put("item", FabricInventoryPort.stackDataOf(itemDisplay.getItemStack()));
            state.put("transform", itemDisplay.getItemTransform().getSerializedName());
        }
        if (entity instanceof net.minecraft.world.entity.Display.TextDisplay textDisplay) {
            state.put("text", textDisplay.getText().getString());
            state.put("backgroundColor", textDisplay.getBackgroundColor());
            state.put("lineWidth", textDisplay.getLineWidth());
            state.put("textOpacity", (int) textDisplay.getTextOpacity());
            byte flags = textDisplay.getFlags();
            state.put("shadow",
                    (flags & net.minecraft.world.entity.Display.TextDisplay.FLAG_SHADOW) != 0);
            state.put("seeThrough", (flags
                    & net.minecraft.world.entity.Display.TextDisplay.FLAG_SEE_THROUGH) != 0);
            state.put("defaultBackground", (flags
                    & net.minecraft.world.entity.Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND)
                    != 0);
            state.put("alignment", net.minecraft.world.entity.Display.TextDisplay
                    .getAlign(flags).name().toLowerCase(java.util.Locale.ROOT));
        }

        // Remaining mobs.
        if (entity instanceof net.minecraft.world.entity.monster.cubemob.Slime slime) {
            state.put("size", slime.getSize());
        }
        if (entity instanceof net.minecraft.world.entity.monster.Guardian guardian) {
            var laserTarget = guardian.getActiveAttackTarget();
            state.put("activeTarget", laserTarget != null
                    ? laserTarget.getUUID().toString() : null);
        }
        if (entity instanceof net.minecraft.world.entity.monster.illager.AbstractIllager illager) {
            state.put("armPose", illager.getArmPose().name().toLowerCase(java.util.Locale.ROOT));
        }
        if (entity instanceof net.minecraft.world.entity.monster.illager.SpellcasterIllager spellcaster) {
            state.put("castingSpell", spellcaster.isCastingSpell());
        }
        if (entity instanceof net.minecraft.world.entity.monster.Strider strider) {
            state.put("saddled", !strider.getItemBySlot(
                    net.minecraft.world.entity.EquipmentSlot.SADDLE).isEmpty());
            state.put("shivering", strider.isSuffocating());
        }
        if (entity instanceof net.minecraft.world.entity.animal.pig.Pig pig) {
            state.put("saddled", !pig.getItemBySlot(
                    net.minecraft.world.entity.EquipmentSlot.SADDLE).isEmpty());
        }
    }

    /** The registry id of one equipment stack, or null when empty. */
    private static String equipmentId(net.minecraft.world.item.ItemStack stack) {
        return stack.isEmpty()
                ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Copies one armor-stand pose rotation into a neutral triple. */
    private static Pos3d poseData(net.minecraft.core.Rotations pose) {
        return new Pos3d(pose.x(), pose.y(), pose.z());
    }

    /** The vanilla state properties of one block state, keyed by name. */
    private static Map<String, String> statePropertiesOf(
            net.minecraft.world.level.block.state.BlockState state) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        state.getProperties().forEach(property ->
                out.put(property.getName(), String.valueOf(state.getValue(property))));
        return out;
    }

    /** Namespaces a serializable enum constant, e.g. {@code minecraft:white}. */
    private static String namespaced(net.minecraft.util.StringRepresentable value) {
        return "minecraft:" + value.getSerializedName();
    }

    /** The registry name behind a registry holder. */
    private static String registeredName(Holder<?> holder) {
        return holder.getRegisteredName();
    }

    @Override
    public Map<String, Object> livingExtras(UUID uuid) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return Map.of();
            }
            for (Entity entity : level.entitiesForRendering()) {
                if (!entity.getUUID().equals(uuid)
                        || !(entity instanceof LivingEntity living)) {
                    continue;
                }
                Map<String, Object> state = new java.util.LinkedHashMap<>();
                state.put("absorption", living.getAbsorptionAmount());
                state.put("armorValue", (double) living.getArmorValue());
                state.put("noGravity", living.isNoGravity());
                if (living instanceof net.minecraft.world.entity.monster.Enemy enemyCheck) {
                    state.put("hostile", true);
                }
                if (living instanceof net.minecraft.world.entity.player.Player player2) {
                    var abilities = player2.getAbilities();
                    state.put("invulnerable", abilities.invulnerable);
                    state.put("flying", abilities.flying);
                    state.put("mayFly", abilities.mayfly);
                    state.put("instabuild", abilities.instabuild);
                    state.put("mayBuild", abilities.mayBuild);
                    state.put("flySpeed", (double) abilities.getFlyingSpeed());
                    state.put("walkingSpeed", (double) abilities.getWalkingSpeed());
                }
                state.put("useItemRemainingTicks", living.getUseItemRemainingTicks());
                state.put("tags", new java.util.ArrayList<>(living.entityTags()));
                collectEquipmentAndTraits(living, state);
                java.util.List<Object> effects = new java.util.ArrayList<>();
                for (net.minecraft.world.effect.MobEffectInstance effect : living.getActiveEffects()) {
                    java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
                    entry.put("id", effect.getEffect().getRegisteredName());
                    entry.put("duration", effect.getDuration());
                    entry.put("amplifier", effect.getAmplifier());
                    entry.put("infinite", effect.isInfiniteDuration());
                    effects.add(entry);
                }
                state.put("effects", effects);
                return state;
            }
            return Map.of();
        });
    }

    /** Renders a serializable enum constant as its lowercase id form. */
    private static String serialName(Object value) {
        if (value instanceof net.minecraft.util.StringRepresentable representable) {
            return representable.getSerializedName();
        }
        if (value instanceof Enum enumValue) {
            return enumValue.name().toLowerCase(java.util.Locale.ROOT);
        }
        return String.valueOf(value);
    }

    /** The glow mode that leaves the game's own decision in place. */
    private static final int AUTO_GLOW = 1;

    /** The glow colour marker meaning "no override installed". */
    private static final int NO_COLOR = -1;

    /**
     * The script-installed glow overrides, keyed by the live entity. Weak
     * keys drop the entry with the entity, so a long session never grows a
     * table of unloaded entities. Every access runs on the game main thread
     * (render decisions and adapter writes both do).
     */
    private static final java.util.Map<Entity, GlowOverride> GLOW_OVERRIDES =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** One entity's glow overrides: forced mode and outline colour. */
    private record GlowOverride(int forceMode, int color) {
    }

    /**
     * The forced glow mode behind one entity: {@code 0} forces the outline
     * off, {@code 2} forces it on and {@code 1} leaves the game's decision
     * alone. Read by the glow mixin on every render decision.
     */
    public static int neomacros$forceGlowMode(Entity entity) {
        GlowOverride state = entity != null ? GLOW_OVERRIDES.get(entity) : null;
        return state == null ? AUTO_GLOW : state.forceMode();
    }

    /**
     * The glow outline colour override behind one entity, or null when the
     * team-derived colour applies. Read by the glow mixin on every outline
     * colour lookup.
     */
    public static Integer neomacros$glowColorOverride(Entity entity) {
        GlowOverride state = entity != null ? GLOW_OVERRIDES.get(entity) : null;
        return state == null || state.color() == NO_COLOR ? null : state.color();
    }

    /** The current overrides of one entity, or the defaults when unset. */
    private static GlowOverride glowOf(Entity entity) {
        GlowOverride state = GLOW_OVERRIDES.get(entity);
        return state != null ? state : new GlowOverride(AUTO_GLOW, NO_COLOR);
    }

    /** Stores one entity's overrides, dropping the entry when both reset. */
    private static void putGlow(Entity entity, int forceMode, int color) {
        if (forceMode == AUTO_GLOW && color == NO_COLOR) {
            GLOW_OVERRIDES.remove(entity);
        } else {
            GLOW_OVERRIDES.put(entity, new GlowOverride(forceMode, color));
        }
    }

    @Override
    public Map<String, Object> entityState(UUID uuid) {
        return MainThread.run(() -> {
            Entity entity = findEntity(uuid);
            if (entity == null) {
                return Map.of();
            }
            Map<String, Object> state = new java.util.LinkedHashMap<>();
            state.put("glowing", entity.isCurrentlyGlowing());
            state.put("glowColor", entity.getTeamColor());
            var customName = entity.getCustomName();
            state.put("customName", customName != null ? customName.getString() : null);
            state.put("customNameVisible", entity.isCustomNameVisible());
            state.put("air", entity.getAirSupply());
            state.put("maxAir", entity.getMaxAirSupply());
            double stepX = entity.getX() - entity.xo;
            double stepZ = entity.getZ() - entity.zo;
            state.put("speed", Math.sqrt(stepX * stepX + stepZ * stepZ) * 20.0);
            return state;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> setGlowing(UUID uuid, boolean glowing) {
        return dispatch(() -> {
            Entity entity = requireEntity(uuid);
            GlowOverride current = glowOf(entity);
            putGlow(entity, glowing ? 2 : 0, current.color());
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> resetGlowing(UUID uuid) {
        return dispatch(() -> {
            Entity entity = requireEntity(uuid);
            putGlow(entity, AUTO_GLOW, glowOf(entity).color());
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> setGlowingColor(UUID uuid, int color) {
        return dispatch(() -> {
            Entity entity = requireEntity(uuid);
            putGlow(entity, glowOf(entity).forceMode(), color);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> resetGlowingColor(UUID uuid) {
        return dispatch(() -> {
            Entity entity = requireEntity(uuid);
            putGlow(entity, glowOf(entity).forceMode(), NO_COLOR);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> setCustomName(UUID uuid, String name) {
        return dispatch(() -> {
            Entity entity = requireEntity(uuid);
            entity.setCustomName(name == null
                    ? null : net.minecraft.network.chat.Component.literal(name));
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> setCustomNameVisible(
            UUID uuid, boolean visible) {
        return dispatch(() -> requireEntity(uuid).setCustomNameVisible(visible));
    }

    @Override
    public boolean canAttack(UUID uuid, UUID target) {
        Boolean allowed = MainThread.run(() -> {
            Entity self = findEntity(uuid);
            Entity other = findEntity(target);
            return self instanceof LivingEntity attacker
                    && other instanceof LivingEntity victim
                    && attacker.canAttack(victim);
        });
        return Boolean.TRUE.equals(allowed);
    }

    @Override
    public boolean canBeAffected(UUID uuid, String effectId) {
        Boolean allowed = MainThread.run(() -> {
            Entity self = findEntity(uuid);
            if (!(self instanceof LivingEntity living)) {
                return false;
            }
            var effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                    .get(net.minecraft.resources.Identifier.tryParse(effectId));
            if (effect.isEmpty()) {
                return false;
            }
            return living.canBeAffected(
                    new net.minecraft.world.effect.MobEffectInstance(effect.get(), 1));
        });
        return Boolean.TRUE.equals(allowed);
    }

    @Override
    public boolean canSee(UUID uuid, UUID target, boolean simpleCast) {
        Boolean visible = MainThread.run(() -> {
            Entity self = findEntity(uuid);
            Entity other = findEntity(target);
            if (self == null || other == null || self.level() != other.level()) {
                return false;
            }
            var from = new Vec3(self.getX(), self.getEyeY(), self.getZ());
            java.util.function.Function<Vec3, Boolean> sees = to -> self.level()
                    .clip(new net.minecraft.world.level.ClipContext(from, to,
                            net.minecraft.world.level.ClipContext.Block.VISUAL,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, self))
                    .getType() == net.minecraft.world.phys.HitResult.Type.MISS;
            double targetX = other.getX();
            double targetY = other.getY();
            double targetZ = other.getZ();
            if (sees.apply(new Vec3(targetX, other.getEyeY(), targetZ))
                    || sees.apply(new Vec3(targetX, targetY + 0.5, targetZ))
                    || sees.apply(new Vec3(targetX, targetY, targetZ))) {
                return true;
            }
            if (simpleCast) {
                return false;
            }
            var box = other.getBoundingBox();
            double halfX = (box.maxX - box.minX) / 2;
            double halfZ = (box.maxZ - box.minZ) / 2;
            int steps = (int) ((box.maxY - box.minY) / 0.1);
            for (int i = 0; i < steps; i++) {
                double y = targetY + i * 0.1;
                if (sees.apply(new Vec3(targetX + halfX, y, targetZ))
                        || sees.apply(new Vec3(targetX - halfX, y, targetZ))
                        || sees.apply(new Vec3(targetX, y, targetZ + halfZ))
                        || sees.apply(new Vec3(targetX, y, targetZ - halfZ))) {
                    return true;
                }
            }
            return false;
        });
        return Boolean.TRUE.equals(visible);
    }

    /** Fills the armor slots and the trait entries of the living extras. */
    private static void collectEquipmentAndTraits(LivingEntity living,
            Map<String, Object> state) {
        state.put("headArmor", stackData(living.getItemBySlot(
                net.minecraft.world.entity.EquipmentSlot.HEAD)));
        state.put("chestArmor", stackData(living.getItemBySlot(
                net.minecraft.world.entity.EquipmentSlot.CHEST)));
        state.put("legArmor", stackData(living.getItemBySlot(
                net.minecraft.world.entity.EquipmentSlot.LEGS)));
        state.put("footArmor", stackData(living.getItemBySlot(
                net.minecraft.world.entity.EquipmentSlot.FEET)));
        var toughness = living.getAttribute(
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
        state.put("armorToughness", toughness != null ? toughness.getValue() : null);
        var maxHealth = living.getAttribute(
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        state.put("defaultMaxHealth", maxHealth != null ? maxHealth.getBaseValue() : null);
        state.put("breathesUnderwater", living.canBreatheUnderwater());
        state.put("noDrag", living.shouldDiscardFriction());
        state.put("partOfGame", living.canBeSeenByAnyone());
        state.put("undead", living.getType().builtInRegistryHolder()
                .is(net.minecraft.tags.EntityTypeTags.UNDEAD));
        state.put("baby", living.isBaby());
        state.put("bowPull", living.getMainHandItem().getItem()
                instanceof net.minecraft.world.item.BowItem
                ? (double) net.minecraft.world.item.BowItem
                        .getPowerForTime(living.getTicksUsingItem())
                : 0.0);
    }

    /** Finds one loaded entity by UUID, or null when it is absent. */
    private static Entity findEntity(UUID uuid) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || uuid == null) {
            return null;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) {
                return entity;
            }
        }
        return null;
    }

    /** Resolves one handle, or reports that the entity is gone. */
    private static Entity requireEntity(UUID uuid) {
        Entity entity = findEntity(uuid);
        if (entity == null) {
            throw new IllegalStateException("handle no longer valid");
        }
        return entity;
    }

    /** Runs a write on the game main thread and resolves once it landed. */
    private static java.util.concurrent.CompletableFuture<Void> dispatch(Runnable action) {
        java.util.concurrent.CompletableFuture<Void> future =
                new java.util.concurrent.CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }
}
