package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.DisplayHub;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Feeds the locally produced action-bar overlay path (system messages that
 * the client routes to the overlay instead of the chat log, such as item
 * consumption hints). The server packet path rides
 * {@link ClientPacketListenerMixin}.
 */
@Mixin(net.minecraft.client.multiplayer.chat.ChatListener.class)
public abstract class ChatListenerMixin {

    //? if >=26.2 {
    @ModifyArg(method = "handleOverlay", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Hud;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private net.minecraft.network.chat.Component neomacros$onLocalOverlay(
            net.minecraft.network.chat.Component message) {
        return DisplayHub.fireActionbar("local", message);
    }
    //?} elif >=26.1 {
    /*
    @ModifyArg(method = "handleOverlay", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private net.minecraft.network.chat.Component neomacros$onLocalOverlay(
            net.minecraft.network.chat.Component message) {
        return DisplayHub.fireActionbar("local", message);
    }
    */
    //?} else {
    /*
    @ModifyArg(method = "handleSystemMessage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private net.minecraft.network.chat.Component neomacros$onLocalOverlay(
            net.minecraft.network.chat.Component message) {
        return DisplayHub.fireActionbar("local", message);
    }
    */
    //?}
}
