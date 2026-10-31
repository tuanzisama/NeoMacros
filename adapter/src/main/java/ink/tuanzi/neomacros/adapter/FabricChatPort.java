package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Chat output backed by the vanilla client surfaces: server-bound sends go
 * through the connection, local log lines through the chat HUD, and the
 * action bar through the HUD overlay message. Incoming display messages fire
 * through {@link ChatReceiveHub}.
 */
public final class FabricChatPort implements ChatPort {

    @Override
    public void say(String message) {
        Minecraft.getInstance().getConnection().sendChat(message);
    }

    @Override
    public void log(String message) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(message));
    }

    @Override
    public void actionbar(String message) {
        Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal(message), false);
    }

    @Override
    public void onChatReceive(Consumer<ChatComponent> sink) {
        ChatReceiveHub.register(sink);
    }
}
