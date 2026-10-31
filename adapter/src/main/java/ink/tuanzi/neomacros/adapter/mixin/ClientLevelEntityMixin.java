package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.EntityLifecycleHub;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires entity load/unload events from ClientLevel entity tracking.
 * addEntity fires entity:load; removeEntity fires entity:unload.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelEntityMixin {

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void neomacros$onEntityLoad(Entity entity, CallbackInfo ci) {
        EntityLifecycleHub.fireLoad(
                entity.getUUID().toString(),
                net.minecraft.core.registries.BuiltInRegistries.ENTITY
                        .getKey(entity.getType()).toString());
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void neomacros$onEntityUnload(int entityId,
            net.minecraft.world.entity.Entity.RemovalReason reason, CallbackInfo ci) {
        EntityLifecycleHub.fireUnload(String.valueOf(entityId));
    }
}
