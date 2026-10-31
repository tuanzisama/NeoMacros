package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.ChunkEventHub;
import ink.tuanzi.neomacros.adapter.WorldEventHub;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires world block update and chunk load events from the client level.
 * sendBlockUpdated is the single funnel the network handler uses for every
 * received block state change; onChunkLoaded runs when a chunk arrives from
 * the server.
 */
@Mixin(net.minecraft.client.multiplayer.ClientLevel.class)
public abstract class ClientLevelWorldMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void neomacros$onBlockUpdate(BlockPos pos, BlockState oldState,
            BlockState newState, int flags, CallbackInfo ci) {
        try {
            WorldEventHub.fireBlockUpdate(new WorldEventHub.BlockUpdateData(
                    pos.getX(), pos.getY(), pos.getZ(),
                    BuiltInRegistries.BLOCK.getKey(newState.getBlock()).toString()));
        } catch (Exception ignored) {
            // Level teardown can race the registry lookup during shutdown
        }
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    private void neomacros$onChunkLoaded(ChunkPos pos, CallbackInfo ci) {
        ChunkEventHub.fireLoad(pos.x(), pos.z());
    }
}
