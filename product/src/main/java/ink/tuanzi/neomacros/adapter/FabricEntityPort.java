package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.AabbData;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
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
                entity.isSwimming(), entity.isFallFlying(),
                entity.isInWater(), entity.isInLava(), entity.isOnFire(),
                entity.getPose() != null
                        ? entity.getPose().name().toLowerCase(java.util.Locale.ROOT) : "standing",
                facingOf(entity),
                entity.level() != null ? entity.level().dimension().toString() : "",
                new AabbData(new Pos3d(bb.getMinX(), bb.getMinY(), bb.getMinZ()),
                        new Pos3d(bb.getMaxX(), bb.getMaxY(), bb.getMaxZ())),
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
}
