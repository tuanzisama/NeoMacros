package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Chat output backed by the vanilla client surfaces: server-bound sends go
 * through the connection, local log lines through the chat HUD, and the
 * action bar through the HUD overlay message. GUI calls are marshaled onto
 * the game main thread (scripts call these from context loop threads).
 * Incoming display messages fire through {@link ChatReceiveHub}.
 */
public final class FabricChatPort implements ChatPort {

    @Override
    public void say(String message) {
        runOnMain(() -> Minecraft.getInstance().getConnection().sendChat(message));
    }

    @Override
    public void log(String message) {
        runOnMain(() -> Minecraft.getInstance().gui.hud.getChat()
                .addClientSystemMessage(Component.literal(message)));
    }

    @Override
    public void actionbar(String message) {
        runOnMain(() -> Minecraft.getInstance().gui.hud
                .setOverlayMessage(Component.literal(message), false));
    }

    @Override
    public void logClickable(String text, String command) {
        runOnMain(() -> {
            Component line = Component.literal(text).withStyle(style -> style
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(command))
                    .withUnderlined(true));
            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(line);
        });
    }

    @Override
    public void onChatReceive(Consumer<ChatComponent> sink) {
        ChatReceiveHub.register(sink);
    }

    /**
     * Marshals one GUI touch onto the game main thread and waits for it, so
     * the caller observes completion ordering. Called from context loop
     * threads; the main thread is running the game and will drain the task.
     */
    private static void runOnMain(Runnable task) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                task.run();
            } finally {
                done.complete(null);
            }
        });
        try {
            done.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException ignored) {
            // timeouts surface as missing output rather than crashing the loop
        }
    }
}
