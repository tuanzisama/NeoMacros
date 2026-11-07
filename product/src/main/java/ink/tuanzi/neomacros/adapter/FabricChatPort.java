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
    public void renderRichText(java.util.List<ink.tuanzi.neomacros.port.model.TextSegmentData> segments,
            boolean actionBar) {
        runOnMain(() -> {
            net.minecraft.network.chat.MutableComponent out =
                    net.minecraft.network.chat.Component.literal("");
            for (ink.tuanzi.neomacros.port.model.TextSegmentData segment : segments) {
                net.minecraft.network.chat.MutableComponent part =
                        net.minecraft.network.chat.Component.literal(segment.text());
                net.minecraft.network.chat.Style style = net.minecraft.network.chat.Style.EMPTY;
                if (segment.color() != null) {
                    style = withColor(style, segment.color());
                }
                if (Boolean.TRUE.equals(segment.bold())) {
                    style = style.withBold(true);
                }
                if (Boolean.TRUE.equals(segment.italic())) {
                    style = style.withItalic(true);
                }
                if (Boolean.TRUE.equals(segment.underlined())) {
                    style = style.withUnderlined(true);
                }
                if (Boolean.TRUE.equals(segment.strikethrough())) {
                    style = style.withStrikethrough(true);
                }
                if (Boolean.TRUE.equals(segment.obfuscated())) {
                    style = style.withObfuscated(true);
                }
                if (segment.hoverText() != null) {
                    // 26.x made HoverEvent an interface; ShowText is the record impl
                    style = style.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                            net.minecraft.network.chat.Component.literal(segment.hoverText())));
                }
                //? if <26.1 {
                /*
                if (segment.hoverItem() != null) {
                    style = style.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowItem(
                            showItemPayload(segment.hoverItem())));
                }
                */
                //?}
                //? if >=26.1 {
                if (segment.hoverItem() != null) {
                    style = style.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowItem(
                            showItemPayload(segment.hoverItem())));
                }
                //?}
                if (segment.clickCommand() != null) {
                    style = style.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(
                            segment.clickCommand()));
                }
                if (segment.clickUrl() != null) {
                    style = style.withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(
                            java.net.URI.create(segment.clickUrl())));
                }
                out.append(part.withStyle(style));
            }
            if (actionBar) {
                Minecraft.getInstance().gui.hud.setOverlayMessage(out, false);
            } else {
                //? if <1.21.11 {
                /*
                Minecraft.getInstance().gui.hud.getChat().addMessage(out);
                */
                //?}
                //? if >=1.21.11 {
                Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(out);
                //?}
            }
        });
    }

    /** Rebuilds the show-item hover payload from one neutral snapshot. */
    //? if <26.1 {
    /*
    private static net.minecraft.world.item.ItemStack showItemPayload(
            ink.tuanzi.neomacros.port.model.ItemStackData hover) {
        var id = net.minecraft.resources.Identifier.parse(hover.id());
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown item: " + hover.id()));
        return new net.minecraft.world.item.ItemStack(item, hover.count());
    }
    */
    //?}
    //? if >=26.1 {
    private static net.minecraft.world.item.ItemStackTemplate showItemPayload(
            ink.tuanzi.neomacros.port.model.ItemStackData hover) {
        var id = net.minecraft.resources.Identifier.parse(hover.id());
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown item: " + hover.id()));
        return new net.minecraft.world.item.ItemStackTemplate(item, hover.count());
    }
    //?}

    /** Resolves a named formatting color or a #rrggbb hex color. */
    private static net.minecraft.network.chat.Style withColor(
            net.minecraft.network.chat.Style style, String color) {
        // Named colors: ChatFormatting is an enum on every node; the lookup
        // accessor renames across versions, so try both spellings.
        try {
            var byName = net.minecraft.ChatFormatting.valueOf(color.toUpperCase(java.util.Locale.ROOT));
            return style.withColor(byName);
        } catch (IllegalArgumentException ignored) {
            // not a named formatting color
        }
        // Hex form: #rrggbb
        var parsed = net.minecraft.network.chat.TextColor.parseColor(color).result().orElse(null);
        return parsed != null ? style.withColor(parsed) : style;
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
