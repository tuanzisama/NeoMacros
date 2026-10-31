package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.ChunkEventHub;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires the chunk unload event from the client chunk cache; drop runs when
 * the server removes a chunk from the client's view distance.
 */
@Mixin(net.minecraft.client.multiplayer.ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {

    @Inject(method = "drop", at = @At("HEAD"))
    private void neomacros$onChunkUnload(ChunkPos pos, CallbackInfo ci) {
        ChunkEventHub.fireUnload(pos.x(), pos.z());
    }
}
