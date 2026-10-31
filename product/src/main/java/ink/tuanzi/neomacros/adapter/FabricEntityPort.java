package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
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
        double health = entity instanceof LivingEntity living ? living.getHealth() : 0.0;
        return new EntitySnapshot(entity.getUUID(), typeId, entity.getName().getString(),
                new Pos3d(entity.getX(), entity.getY(), entity.getZ()), health);
    }
}
