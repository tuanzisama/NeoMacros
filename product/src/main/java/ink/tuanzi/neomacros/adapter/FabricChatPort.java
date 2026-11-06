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
        //? if <1.21.11 {
        /*
        runOnMain(() -> Minecraft.getInstance().gui.hud.getChat()
                .addMessage(Component.literal(message)));
        */
        //?}
//? if >=1.21.11 {
        runOnMain(() -> Minecraft.getInstance().gui.hud.getChat()
                .addClientSystemMessage(Component.literal(message)));
//?}
    }

    @Override
    public void actionbar(String message) {
        runOnMain(() -> Minecraft.getInstance().gui.hud.setOverlayMessage(
                Component.literal(message), false));
    }

    @Override
    public void logClickable(String text, String command) {
        //? if <1.21.11 {
        /*
        runOnMain(() -> {
            Component line = Component.literal(text).withStyle(style -> style
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(command))
                    .withUnderlined(true));
            Minecraft.getInstance().gui.hud.getChat().addMessage(line);
        });
        */
        //?}
//? if >=1.21.11 {
        runOnMain(() -> {
            Component line = Component.literal(text).withStyle(style -> style
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(command))
                    .withUnderlined(true));
            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(line);
        });
//?}
    }

    @Override
    public void logLink(String text, String url) {
        // The sealed ClickEvent hierarchy (1.21.5+) takes the URL as a URI;
        // only the chat surface differs across the 1.21.11 pivot.
        //? if <1.21.11 {
        /*
        runOnMain(() -> {
            Component line = Component.literal(text).withStyle(style -> style
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(
                            java.net.URI.create(url)))
                    .withUnderlined(true));
            Minecraft.getInstance().gui.hud.getChat().addMessage(line);
        });
        */
        //?}
//? if >=1.21.11 {
        runOnMain(() -> {
            Component line = Component.literal(text).withStyle(style -> style
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(
                            java.net.URI.create(url)))
                    .withUnderlined(true));
            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(line);
        });
//?}
    }

    @Override
    public void onChatReceive(Consumer<ChatComponent> sink) {
        ChatReceiveHub.register(sink);
    }

    @Override
    public void title(String title, String subtitle, Integer fadeIn, Integer stay,
            Integer fadeOut) {
        runOnMain(() -> {
            Minecraft.getInstance().gui.hud.clearTitles();
            if (title != null) {
                Minecraft.getInstance().gui.hud.setTitle(Component.literal(title));
            }
            if (subtitle != null) {
                Minecraft.getInstance().gui.hud.setSubtitle(Component.literal(subtitle));
            }
            if (fadeIn != null && stay != null && fadeOut != null) {
                Minecraft.getInstance().gui.hud.setTimes(fadeIn, stay, fadeOut);
            }
        });
    }

    @Override
    public void toast(String title, String message) {
        runOnMain(() -> {
            Component heading = Component.literal(title == null ? "" : title);
            Component body = Component.literal(message == null ? "" : message);
            var toast = new net.minecraft.client.gui.components.toasts.SystemToast(
                    net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId
                            .PERIODIC_NOTIFICATION, heading, body);
            Minecraft.getInstance().gui.toastManager().addToast(toast);
        });
    }

    @Override
    public int textWidth(String text) {
        Integer width = MainThread.run(() ->
                Minecraft.getInstance().font.width(text));
        return width != null ? width : 0;
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
