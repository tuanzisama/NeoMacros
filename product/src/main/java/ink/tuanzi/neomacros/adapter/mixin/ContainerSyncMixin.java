package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.InventoryUpdateHub;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the inventory update source: every server container sync (single
 * slot and full menu resync) is forwarded to the update hub after the menu
 * state is applied, so post-event reads see fresh slots. Menu id 0 covers
 * the player inventory while no container screen is open.
 */
@Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
public abstract class ContainerSyncMixin {

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void neomacros$onSetSlot(net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket packet,
                                     CallbackInfo ci) {
        InventoryUpdateHub.fire(packet.getContainerId(), packet.getSlot(), false);
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void neomacros$onContainerContent(net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket packet,
                                              CallbackInfo ci) {
        InventoryUpdateHub.fire(packet.containerId(), -1, true);
    }
}
