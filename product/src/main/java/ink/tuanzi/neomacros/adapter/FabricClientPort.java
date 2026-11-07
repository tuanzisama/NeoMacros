package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.model.ModData;
import ink.tuanzi.neomacros.port.model.OptionsData;
import ink.tuanzi.neomacros.port.model.Pos2d;
import ink.tuanzi.neomacros.port.model.ServerInfoData;
import ink.tuanzi.neomacros.port.model.WindowData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Client context port over the running game client process. Connection
 * lifecycle actions dispatch onto the game main thread and resolve once
 * initiated; process shutdown bypasses threading entirely.
 */
public final class FabricClientPort implements ClientPort {

    @Override
    public int fps() {
        return MainThread.run(Minecraft.getInstance()::getFps);
    }

    @Override
    public String gameVersion() {
        // 26.x official names: WorldVersion accessors are record-style
        return MainThread.run(() -> SharedConstants.getCurrentVersion().name());
    }

    @Override
    public Object minecraftInstance() {
        return Minecraft.getInstance();
    }

    @Override
    public CompletableFuture<Void> connect(String address) {
        return MainThread.run(() -> {
            ServerAddress parsed;
            try {
                parsed = ServerAddress.parseString(address);
            } catch (Exception e) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("invalid server address: " + address, e));
            }
            ServerData data = new ServerData(address, address, ServerData.Type.OTHER);
            Minecraft.getInstance().disconnect(null, false);
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(null,
                    Minecraft.getInstance(), parsed, data, false, null);
            return CompletableFuture.completedFuture((Void) null);
        });
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return MainThread.run(() -> {
            Minecraft.getInstance().disconnect(null, false);
            return CompletableFuture.completedFuture((Void) null);
        });
    }

    @Override
    public CompletableFuture<Void> loadWorld(String name) {
        return MainThread.run(() -> {
            Minecraft mc = Minecraft.getInstance();
            try {
                mc.createWorldOpenFlows().openWorld(name, () -> { });
            } catch (Exception e) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("cannot load world: " + name, e));
            }
            return CompletableFuture.completedFuture((Void) null);
        });
    }

    @Override
    public void shutdown() {
        Minecraft.getInstance().stop();
    }

    @Override
    public void forceShutdown() {
        Runtime.getRuntime().halt(0);
    }

    @Override
    public Optional<Integer> ping() {
        return MainThread.run(() -> {
            var connection = Minecraft.getInstance().getConnection();
            var player = Minecraft.getInstance().player;
            if (connection == null || player == null) {
                return Optional.empty();
            }
            for (var info : connection.getOnlinePlayers()) {
                var profile = info.getProfile();
                if (profile.id().equals(player.getUUID())) {
                    return Optional.of(info.getLatency());
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public List<ModData> mods() {
        List<ModData> out = new ArrayList<>();
        for (var container : FabricLoader.getInstance().getAllMods()) {
            var metadata = container.getMetadata();
            List<String> authors = new ArrayList<>();
            metadata.getAuthors().forEach(person -> authors.add(person.getName()));
            out.add(new ModData(metadata.getId(), metadata.getName(),
                    metadata.getVersion().getFriendlyString(), authors));
        }
        return out;
    }

    @Override
    public boolean isModLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    @Override
    public boolean devEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public void grabMouse() {
        MainThread.run(() -> {
            Minecraft.getInstance().mouseHandler.grabMouse();
            return null;
        });
    }

    @Override
    public void releaseMouse() {
        MainThread.run(() -> {
            Minecraft.getInstance().mouseHandler.releaseMouse();
            return null;
        });
    }

    @Override
    public String clipboard() {
        return MainThread.run(() -> Minecraft.getInstance().keyboardHandler.getClipboard());
    }

    @Override
    public void setClipboard(String value) {
        MainThread.run(() -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
            return null;
        });
    }

    @Override
    public Optional<WindowData> window() {
        return MainThread.run(() -> {
            var window = Minecraft.getInstance().getWindow();
            if (window == null) {
                return Optional.empty();
            }
            return Optional.of(new WindowData(window.getWidth(), window.getHeight(),
                    window.getGuiScaledWidth(), window.getGuiScaledHeight()));
        });
    }

    @Override
    public Pos2d mouse() {
        return MainThread.run(() -> {
            var handler = Minecraft.getInstance().mouseHandler;
            return new Pos2d(handler.xpos(), handler.ypos());
        });
    }

    @Override
    public Optional<ServerInfoData> serverInfo() {
        return MainThread.run(() -> {
            ServerData data = Minecraft.getInstance().getCurrentServer();
            if (data == null) {
                return Optional.empty();
            }
            String label = data.name != null && !data.name.isBlank() ? data.name : data.ip;
            return Optional.of(new ServerInfoData(label, data.ip, data.ping));
        });
    }

    @Override
    public Optional<OptionsData> options() {
        return MainThread.run(() -> {
            var options = Minecraft.getInstance().options;
            if (options == null) {
                return Optional.empty();
            }
            return Optional.of(new OptionsData(options.fov().get(), options.gamma().get(),
                    options.guiScale().get(), options.renderDistance().get()));
        });
    }

    @Override
    public List<String> registryIds(String kind) {
        var registry = registryOf(kind);
        List<String> out = new ArrayList<>();
        for (Identifier id : registry.keySet()) {
            out.add(id.toString());
        }
        out.sort(String::compareTo);
        return out;
    }

    @Override
    public Optional<String> registryLookup(String kind, String id) {
        var registry = registryOf(kind);
        Identifier resolved = id != null && id.indexOf(':') >= 0
                ? Identifier.tryParse(id)
                : Identifier.withDefaultNamespace(id == null ? "" : id);
        if (resolved == null || !registry.containsKey(resolved)) {
            return Optional.empty();
        }
        return Optional.of(resolved.toString());
    }

    @Override
    public java.util.concurrent.CompletableFuture<ink.tuanzi.neomacros.port.model.ImageData> screenshot() {
        java.util.concurrent.CompletableFuture<ink.tuanzi.neomacros.port.model.ImageData> future =
                new java.util.concurrent.CompletableFuture<>();
        MainThread.run(() -> {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.client.Screenshot.takeScreenshot(
                    mc.gameRenderer.mainRenderTarget(), image -> {
                java.nio.file.Path temp = null;
                try {
                    temp = java.nio.file.Files.createTempFile("neomacros-shot", ".png");
                    image.writeToFile(temp);
                    byte[] bytes = java.nio.file.Files.readAllBytes(temp);
                    future.complete(new ink.tuanzi.neomacros.port.model.ImageData(
                            image.getWidth(), image.getHeight(), bytes));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                } finally {
                    image.close();
                    if (temp != null) {
                        try {
                            java.nio.file.Files.deleteIfExists(temp);
                        } catch (java.io.IOException ignored) {
                            // temp cleanup is best-effort
                        }
                    }
                }
            });
            return null;
        });
        return future;
    }

    /** Resolves a registry kind name onto the built-in registry. */
    private static net.minecraft.core.Registry<?> registryOf(String kind) {
        var id = net.minecraft.resources.Identifier.tryParse(
                kind != null && kind.indexOf(':') >= 0 ? kind : "minecraft:" + kind);
        var found = BuiltInRegistries.REGISTRY.getOptional(id);
        if (found.isEmpty()) {
            throw new IllegalArgumentException("unknown registry kind: " + kind);
        }
        return found.get();
    }

    /** Option names shared by every supported node (checked before lookup). */
    private static final java.util.Set<String> OPTION_NAMES = java.util.Set.of(
            "allowServerListing", "ambientOcclusion", "attackIndicator", "autoJump",
            "autoSuggestions", "backgroundForChatOnly", "biomeBlendRadius", "bobView",
            "chatColors", "chatDelay", "chatHeightFocused", "chatHeightUnfocused",
            "chatLineSpacing", "chatLinks", "chatLinksPrompt", "chatOpacity",
            "chatScale", "chatVisibility", "chatWidth", "cloudRange", "cloudStatus",
            "damageTiltStrength", "darkMojangStudiosBackground", "darknessEffectScale",
            "directionalAudio", "discreteMouseScroll", "enableVsync",
            "entityDistanceScaling", "entityShadows", "forceUnicodeFont", "fov",
            "fovEffectScale", "framerateLimit", "fullscreen", "gamma", "glintSpeed",
            "glintStrength", "guiScale", "hideLightningFlash", "hideMatchedNames",
            "hideSplashTexts", "highContrast", "highContrastBlockOutline",
            "inactivityFpsLimit", "japaneseGlyphVariants", "mainHand",
            "menuBackgroundBlurriness", "mipmapLevels", "mouseWheelSensitivity",
            "musicFrequency", "narrator", "narratorHotkey", "notificationDisplayTime",
            "onlyShowSecureChat", "operatorItemsTab", "panoramaSpeed", "particles",
            "prioritizeChunkUpdates", "realmsNotifications", "reducedDebugInfo",
            "renderDistance", "rotateWithMinecart", "screenEffectScale",
            "sensitivity", "showAutosaveIndicator", "showSubtitles",
            "simulationDistance", "soundDevice", "telemetryOptInExtra",
            "textBackgroundOpacity", "toggleCrouch", "toggleSprint");

    @Override
    public java.util.Optional<Object> optionValue(String name) {
        return MainThread.run(() -> {
            var options = Minecraft.getInstance().options;
            if (options == null || name == null || !OPTION_NAMES.contains(name)) {
                return java.util.Optional.empty();
            }
            try {
                var instance = (net.minecraft.client.OptionInstance<?>)
                        options.getClass().getMethod(name).invoke(options);
                Object value = instance.get();
                if (value instanceof Enum<?> constant) {
                    return java.util.Optional.of(
                            constant.name().toLowerCase(java.util.Locale.ROOT));
                }
                if (value instanceof Number number) {
                    return java.util.Optional.of(number.doubleValue());
                }
                return java.util.Optional.ofNullable(value);
            } catch (ReflectiveOperationException | ClassCastException error) {
                return java.util.Optional.empty();
            }
        });
    }
}
