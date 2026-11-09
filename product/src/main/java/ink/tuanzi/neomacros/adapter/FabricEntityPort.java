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
        }
        if (entity instanceof Wolf wolf) {
            state.put("collarColor", namespaced(wolf.getCollarColor()));
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
        }
        if (entity instanceof net.minecraft.world.entity.monster.Enderman enderman) {
            var carried = enderman.getCarriedBlock();
            state.put("carriedBlock", carried != null
                    ? BuiltInRegistries.BLOCK.getKey(carried.getBlock()).toString() : null);
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
        }
        if (entity instanceof net.minecraft.world.entity.animal.fox.Fox fox) {
            state.put("sitting", fox.isSitting());
        }
        if (entity instanceof net.minecraft.world.entity.animal.frog.Frog frog) {
            state.put("variant", registeredName(frog.getVariant()));
        }
        if (entity instanceof net.minecraft.world.entity.animal.goat.Goat goat) {
            state.put("leftHorn", goat.hasLeftHorn());
            state.put("rightHorn", goat.hasRightHorn());
            state.put("screaming", goat.isScreamingGoat());
        }
        if (entity instanceof net.minecraft.world.entity.animal.panda.Panda panda) {
            state.put("mainGene", namespaced(panda.getMainGene()));
            state.put("hiddenGene", namespaced(panda.getHiddenGene()));
            state.put("sneezing", panda.isSneezing());
            state.put("onBack", panda.isOnBack());
            state.put("eating", panda.isEating());
            state.put("lazy", panda.isLazy());
        }
        if (entity instanceof net.minecraft.world.entity.animal.parrot.Parrot parrot) {
            state.put("variant", serialName(parrot.getVariant()));
        }
        if (entity instanceof net.minecraft.world.entity.animal.polarbear.PolarBear polarBear) {
            state.put("standing", polarBear.isStanding());
        }
        if (entity instanceof net.minecraft.world.entity.monster.Shulker shulker) {
            state.put("color", namespaced(shulker.getColor()));
        }
        if (entity instanceof net.minecraft.world.entity.animal.golem.SnowGolem snowGolem) {
            state.put("pumpkin", snowGolem.hasPumpkin());
        }
        if (entity instanceof net.minecraft.world.entity.animal.turtle.Turtle turtle) {
            state.put("hasEgg", turtle.hasEgg());
        }
        if (entity instanceof net.minecraft.world.entity.monster.Vex vex) {
            state.put("charging", vex.isCharging());
        }
        if (entity instanceof net.minecraft.world.entity.monster.Witch witch) {
            state.put("drinkingPotion", witch.isDrinkingPotion());
        }
        if (entity instanceof net.minecraft.world.entity.animal.dolphin.Dolphin dolphin) {
            state.put("moistness", dolphin.getMoistnessLevel());
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
        }
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
                state.put("glowing", living.isCurrentlyGlowing());
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
                state.put("air", living.getAirSupply());
                state.put("maxAir", living.getMaxAirSupply());
                state.put("useItemRemainingTicks", living.getUseItemRemainingTicks());
                state.put("tags", new java.util.ArrayList<>(living.entityTags()));
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
}