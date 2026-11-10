package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.DisplayHub;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Feeds the title/subtitle/action-bar display sources: the packet handlers in
 * the client network listener forward the server's component to the HUD, and
 * the modify-arg injection swaps in the (possibly replaced or suppressed)
 * component. Returning null suppresses the display; the vanilla fade timers
 * still reset (JsMacros-parity cancel semantics).
 */
@Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    //? if >=26.2 {
    @ModifyArg(method = "setTitleText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;setTitle(Lnet/minecraft/network/chat/Component;)V"))
    private net.minecraft.network.chat.Component neomacros$onTitle(
            net.minecraft.network.chat.Component title) {
        return DisplayHub.fireTitle("title", title);
    }

    @ModifyArg(method = "setSubtitleText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;setSubtitle(Lnet/minecraft/network/chat/Component;)V"))
    private net.minecraft.network.chat.Component neomacros$onSubtitle(
            net.minecraft.network.chat.Component subtitle) {
        return DisplayHub.fireTitle("subtitle", subtitle);
    }

    @ModifyArg(method = "setActionBarText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private net.minecraft.network.chat.Component neomacros$onActionBar(
            net.minecraft.network.chat.Component message) {
        return DisplayHub.fireActionbar("server", message);
    }
    //?} else {
    /*
    @ModifyArg(method = "setTitleText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;setTitle(Lnet/minecraft/network/chat/Component;)V"))
    private net.minecraft.network.chat.Component neomacros$onTitle(
            net.minecraft.network.chat.Component title) {
        return DisplayHub.fireTitle("title", title);
    }

    @ModifyArg(method = "setSubtitleText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;setSubtitle(Lnet/minecraft/network/chat/Component;)V"))
    private net.minecraft.network.chat.Component neomacros$onSubtitle(
            net.minecraft.network.chat.Component subtitle) {
        return DisplayHub.fireTitle("subtitle", subtitle);
    }

    @ModifyArg(method = "setActionBarText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private net.minecraft.network.chat.Component neomacros$onActionBar(
            net.minecraft.network.chat.Component message) {
        return DisplayHub.fireActionbar("server", message);
    }
    */
    //?}
}
