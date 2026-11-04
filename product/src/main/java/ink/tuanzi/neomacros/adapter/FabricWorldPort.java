package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.RayHit;
import ink.tuanzi.neomacros.port.model.RayOptions;
import ink.tuanzi.neomacros.port.model.BlockData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * World data services over the vanilla client world. Every query is
 * marshaled onto the game main thread; without a loaded world the queries
 * answer empty (time 0, dimension "", no rain, no blocks).
 */
public final class FabricWorldPort implements WorldPort {

    @Override
    public long time() {
        ClientLevel level = MainThread.run(() -> Minecraft.getInstance().level);
        return level != null ? level.getDefaultClockTime() : 0L;
    }

    @Override
    public String dimension() {
        ClientLevel level = MainThread.run(() -> Minecraft.getInstance().level);
        return level != null ? level.dimension().identifier().toString() : "";
    }

    @Override
    public boolean isRaining() {
        ClientLevel level = MainThread.run(() -> Minecraft.getInstance().level);
        return level != null && level.isRaining();
    }

    @Override
    public Optional<BlockData> blockAt(Pos3d target) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return Optional.empty();
            }
            BlockPos blockPos = BlockPos.containing(target.x(), target.y(), target.z());
            BlockState state = level.getBlockState(blockPos);
            if (state.isAir()) {
                return Optional.empty();
            }
            return Optional.of(new BlockData(blockId(state), toPos3d(blockPos),
                    stateProperties(state)));
        });
    }

    @Override
    public Optional<RayHit> raycast(Pos3d origin, Pos3d direction, RayOptions options) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return Optional.empty();
            }
            Vec3 from = new Vec3(origin.x(), origin.y(), origin.z());
            Vec3 dir = new Vec3(direction.x(), direction.y(), direction.z());
            if (dir.lengthSqr() == 0) {
                dir = new Vec3(0, 0, 1);
            } else {
                dir = dir.normalize();
            }
            Vec3 to = from.add(dir.scale(options.maxDist()));
            var clip = new ClipContext(from, to,
                    options.blocks() ? ClipContext.Block.COLLIDER : ClipContext.Block.COLLIDER,
                    options.fluids() ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                    Minecraft.getInstance().player);
            BlockHitResult blockHit = options.blocks() ? level.clip(clip)
                    : new BlockHitResult(from, net.minecraft.core.Direction.UP,
                            net.minecraft.core.BlockPos.containing(from), false);
            double blockDist = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                    ? Double.MAX_VALUE
                    : blockHit.getLocation().distanceTo(from);
            EntityHitResult entityHit = options.entities()
                    ? pickEntity(level, from, to, blockDist)
                    : null;
            if (entityHit != null
                    && (blockDist >= Double.MAX_VALUE
                        || entityHit.getLocation().distanceTo(from) < blockDist)) {
                var entity = entityHit.getEntity();
                double dist = entityHit.getLocation().distanceTo(from);
                var snapshot = new ink.tuanzi.neomacros.port.model.EntitySnapshot(
                        entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                        entity.getName().getString(), toPos3dFrom(entity.position()),
                        entity instanceof net.minecraft.world.entity.LivingEntity living
                                ? living.getHealth() : 0);
                return Optional.of(new RayHit(null, snapshot, toPos3dFrom(entityHit.getLocation()), dist));
            }
            if (blockDist == Double.MAX_VALUE) {
                return Optional.empty();
            }
            BlockPos blockPos = blockHit.getBlockPos();
            BlockState state = level.getBlockState(blockPos);
            var data = new BlockData(blockId(state), toPos3d(blockPos));
            return Optional.of(new RayHit(data, null, toPos3dFrom(blockHit.getLocation()), blockDist));
        });
    }

    /** Lifts a generic world position into the neutral position type. */
    private static Pos3d toPos3dFrom(Vec3 pos) {
        return new Pos3d(pos.x, pos.y, pos.z);
    }

    /** Picks the first entity whose bounding box the ray intersects within reach. */
    private static EntityHitResult pickEntity(ClientLevel level, Vec3 from, Vec3 to,
            double blockDist) {
        EntityHitResult best = null;
        double bestDist = Math.min(blockDist, Double.MAX_VALUE);
        double reach = Math.min(blockDist, from.distanceTo(to));
        for (var entity : level.entitiesForRendering()) {
            var box = entity.getBoundingBox().inflate(0.1);
            var hit = box.clip(from, to);
            if (hit.isEmpty()) {
                continue;
            }
            double dist = hit.get().distanceTo(from);
            if (dist <= reach && dist < bestDist) {
                bestDist = dist;
                best = new EntityHitResult(entity, hit.get());
            }
        }
        return best;
    }


    /** Flattens the vanilla state property values into a string map. */
    private static Map<String, String> stateProperties(BlockState state) {
        Map<String, String> out = new LinkedHashMap<>();
        state.getProperties().forEach(property ->
                out.put(property.getName(), String.valueOf(state.getValue(property))));
        return out;
    }
    /** The namespaced identifier of one block state. */
    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** Lifts a block position into the neutral position type. */
    private static Pos3d toPos3d(BlockPos pos) {
        return new Pos3d(pos.getX(), pos.getY(), pos.getZ());
    }
}
