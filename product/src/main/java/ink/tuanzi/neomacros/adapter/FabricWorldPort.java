package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.Pos3d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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
    public Optional<BlockData> blockAt(Pos3d pos) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return Optional.empty();
            }
            BlockPos blockPos = BlockPos.containing(pos.x(), pos.y(), pos.z());
            BlockState state = level.getBlockState(blockPos);
            if (state.isAir()) {
                return Optional.empty();
            }
            return Optional.of(new BlockData(blockId(state), toPos3d(blockPos)));
        });
    }

    @Override
    public Optional<BlockInteraction> raycast(Pos3d from, Pos3d to) {
        return MainThread.run(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return Optional.empty();
            }
            ClipContext context = new ClipContext(new Vec3(from.x(), from.y(), from.z()),
                    new Vec3(to.x(), to.y(), to.z()),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player);
            BlockHitResult hit = level.clip(context);
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                return Optional.empty();
            }
            BlockPos blockPos = hit.getBlockPos();
            BlockState state = level.getBlockState(blockPos);
            return Optional.of(new BlockInteraction(blockId(state), toPos3d(blockPos)));
        });
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
