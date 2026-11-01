package ink.tuanzi.neomacros.adapter.mixin;

import ink.tuanzi.neomacros.adapter.ChatReceiveHub;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the chat receive source: every server system message and player
 * message the client displays is forwarded to the receive hub as a plain
 * component. Locally added client system messages are excluded so the mod's
 * own log output never loops back into scripts. The wrapped pre-26 branch
 * collapses both entry points into addMessage, so system and player text
 * cannot be told apart there.
 */
@Mixin(net.minecraft.client.gui.components.ChatComponent.class)
public abstract class ChatDisplayMixin {

    //? if >=26.1 {
    @Inject(method = "addServerSystemMessage", at = @At("HEAD"))
    private void neomacros$onServerSystemMessage(net.minecraft.network.chat.Component message, CallbackInfo ci) {
        ChatReceiveHub.fire(ink.tuanzi.neomacros.port.model.ChatComponent.plain(message.getString()));
    }

    @Inject(method = "addPlayerMessage", at = @At("HEAD"))
    private void neomacros$onPlayerMessage(net.minecraft.network.chat.Component message,
                                           net.minecraft.network.chat.MessageSignature signature,
                                           net.minecraft.client.multiplayer.chat.GuiMessageTag tag, CallbackInfo ci) {
        ChatReceiveHub.fire(ink.tuanzi.neomacros.port.model.ChatComponent.plain(message.getString()));
    }
    //?} else {
    /*
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void neomacros$onAnyMessage(net.minecraft.network.chat.Component message, CallbackInfo ci) {
        ChatReceiveHub.fire(ink.tuanzi.neomacros.port.model.ChatComponent.plain(message.getString()));
    }
    */
    //?}
}
