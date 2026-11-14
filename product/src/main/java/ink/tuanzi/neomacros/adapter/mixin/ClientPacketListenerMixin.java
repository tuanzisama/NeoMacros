package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.ChatSendHub;
import ink.tuanzi.neomacros.adapter.DisplayHub;
import ink.tuanzi.neomacros.port.model.ChatSendVerdict;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the title/subtitle/action-bar display sources: the packet handlers in
 * the client network listener forward the server's component to the HUD, and
 * the modify-arg injection swaps in the (possibly replaced or suppressed)
 * component. Returning null suppresses the display; the vanilla fade timers
 * still reset.
 *
 * <p>The outgoing-chat inject rides {@code sendChat} (every player send path,
 * script sends included): a cancel verdict swallows the message, a rewrite
 * verdict cancels the original call and redispatches the replaced text with
 * the sinks muted.</p>
 */
@Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void neomacros$onSendChat(String message, CallbackInfo ci) {
        ChatSendVerdict verdict = ChatSendHub.fire(message);
        if (verdict.canceled()) {
            ci.cancel();
            return;
        }
        if (verdict.message() != null && !verdict.message().equals(message)) {
            ci.cancel();
            ChatSendHub.muted(() -> ((net.minecraft.client.multiplayer.ClientPacketListener)
                    (Object) this).sendChat(verdict.message()));
        }
    }

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
