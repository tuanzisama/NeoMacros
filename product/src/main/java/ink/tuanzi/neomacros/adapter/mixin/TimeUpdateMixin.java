package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.TpsTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds time sync packet arrivals into the remote tick rate estimator.
 */
@Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
public abstract class TimeUpdateMixin {

    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void neomacros$onSetTime(
            net.minecraft.network.protocol.game.ClientboundSetTimePacket packet,
            CallbackInfo ci) {
        TpsTracker.recordPacket();
    }
}
