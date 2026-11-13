package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.adapter.mixin.DownloadedPackSourceAccessor;
import ink.tuanzi.neomacros.adapter.mixin.ServerPackPromptAccessor;
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
            Minecraft mc = Minecraft.getInstance();
            var options = mc.options;
            if (options == null) {
                return Optional.empty();
            }
            var window = mc.getWindow();
            var repository = mc.getResourcePackRepository();
            var level = mc.level;
            return Optional.of(new OptionsData(
                    options.fov().get(),
                    options.gamma().get(),
                    options.guiScale().get(),
                    options.renderDistance().get(),
                    options.biomeBlendRadius().get(),
                    graphicsModeName(options),
                    options.prioritizeChunkUpdates().get().name()
                            .toLowerCase(java.util.Locale.ROOT),
                    options.ambientOcclusion().get(),
                    options.simulationDistance().get(),
                    options.framerateLimit().get(),
                    options.enableVsync().get(),
                    options.bobView().get(),
                    options.attackIndicator().get().name()
                            .toLowerCase(java.util.Locale.ROOT),
                    options.cloudStatus().get().name()
                            .toLowerCase(java.util.Locale.ROOT),
                    options.fullscreen().get(),
                    options.particles().get().name()
                            .toLowerCase(java.util.Locale.ROOT),
                    options.mipmapLevels().get(),
                    options.entityShadows().get(),
                    options.screenEffectScale().get(),
                    options.entityDistanceScaling().get(),
                    options.fovEffectScale().get(),
                    options.showAutosaveIndicator().get(),
                    options.getCameraType().ordinal(),
                    options.smoothCamera,
                    window == null ? 0 : window.getScreenWidth(),
                    window == null ? 0 : window.getScreenHeight(),
                    options.languageCode,
                    level == null ? null : level.getLevelData().isDifficultyLocked(),
                    List.copyOf(repository.getAvailableIds()),
                    List.copyOf(repository.getSelectedIds()),
                    serverPacksDisabled(mc),
                    options.fullscreenVideoModeString));
        });
    }

    /**
     * Reads the graphics preset as a lowercase name; the option accessor was
     * renamed from {@code graphicsMode} to {@code graphicsPreset} after
     * 1.21.11.
     */
    private static String graphicsModeName(net.minecraft.client.Options options) {
        //? if >=1.21.11 {
        return options.graphicsPreset().get().name().toLowerCase(java.util.Locale.ROOT);
        //?} else {
        /* return options.graphicsMode().get().name().toLowerCase(java.util.Locale.ROOT); */
        //?}
    }

    /** Reads the server-resource-pack rejection policy. */
    private static boolean serverPacksDisabled(Minecraft mc) {
        var source = mc.getDownloadedPackSource();
        if (source == null) {
            return false;
        }
        var manager = ((DownloadedPackSourceAccessor) (Object) source).neomacros$manager();
        if (manager == null) {
            return false;
        }
        var status = ((ServerPackPromptAccessor) (Object) manager)
                .neomacros$packPromptStatus();
        return status == net.minecraft.client.resources.server.ServerPackManager.PackPromptStatus
                .DECLINED;
    }

    @Override
    public CompletableFuture<Void> setOption(String name, Object value) {
        return dispatch(() -> {
            Minecraft mc = Minecraft.getInstance();
            var options = mc.options;
            if (options == null || name == null || value == null) {
                throw new IllegalArgumentException("option unavailable: " + name);
            }
            java.lang.reflect.Method getter;
            try {
                getter = options.getClass().getMethod(name);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("unknown option: " + name, e);
            }
            if (getter.getParameterCount() != 0
                    || !net.minecraft.client.OptionInstance.class
                            .isAssignableFrom(getter.getReturnType())) {
                throw new IllegalArgumentException("not a writable option: " + name);
            }
            net.minecraft.client.OptionInstance<?> instance;
            try {
                instance = (net.minecraft.client.OptionInstance<?>) getter.invoke(options);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("option read failed: " + name, e);
            }
            if (instance == null) {
                throw new IllegalArgumentException("option unavailable: " + name);
            }
            Object converted = convertOptionValue(name, instance.get(), value);
            @SuppressWarnings("unchecked")
            var typed = (net.minecraft.client.OptionInstance<Object>) instance;
            typed.set(converted);
            if ("guiScale".equals(name)) {
                //? if >=26.1 {
                mc.resizeGui();
                //?} else {
                /* mc.resizeDisplay(); */
                //?}
            }
        });
    }

    /** Coerces an incoming script value onto the option's current type. */
    private static Object convertOptionValue(String name, Object current, Object value) {
        if (current == null) {
            throw new IllegalArgumentException("option '" + name + "' has no reference value");
        }
        try {
            if (current instanceof Enum<?> constant && value instanceof String text) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object parsed = Enum.valueOf((Class) constant.getClass(),
                        text.toUpperCase(java.util.Locale.ROOT));
                return parsed;
            }
            if (current instanceof Boolean && value instanceof Boolean) {
                return value;
            }
            if (current instanceof Integer && value instanceof Number number) {
                return number.intValue();
            }
            if (current instanceof Long && value instanceof Number number) {
                return number.longValue();
            }
            if ((current instanceof Double || current instanceof Float)
                    && value instanceof Number number) {
                return number.doubleValue();
            }
            if (current.getClass().isInstance(value)) {
                return value;
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "option '" + name + "' rejects value " + value, e);
        }
        throw new IllegalArgumentException(
                "option '" + name + "' rejects value " + value);
    }

    @Override
    public CompletableFuture<Void> saveOptions() {
        return dispatch(() -> {
            var options = Minecraft.getInstance().options;
            if (options == null) {
                throw new IllegalStateException("game options are unavailable");
            }
            options.save();
        });
    }

    @Override
    public CompletableFuture<Void> setEnabledResourcePacks(List<String> enabled) {
        return dispatch(() -> {
            Minecraft mc = Minecraft.getInstance();
            var options = mc.options;
            if (options == null) {
                throw new IllegalStateException("game options are unavailable");
            }
            var repository = mc.getResourcePackRepository();
            List<String> requested = enabled.stream().distinct()
                    .collect(java.util.stream.Collectors.toList());
            repository.setSelected(requested);
            List<String> current = List.copyOf(options.resourcePacks);
            options.resourcePacks.clear();
            options.incompatibleResourcePacks.clear();
            for (net.minecraft.server.packs.repository.Pack pack :
                    repository.getSelectedPacks()) {
                if (!pack.isFixedPosition()) {
                    options.resourcePacks.add(pack.getId());
                    if (!pack.getCompatibility().isCompatible()) {
                        options.incompatibleResourcePacks.add(pack.getId());
                    }
                }
            }
            options.save();
            if (!current.equals(List.copyOf(options.resourcePacks))) {
                mc.reloadResourcePacks();
            }
        });
    }

    @Override
    public CompletableFuture<Void> setServerResourcePacksDisabled(boolean disabled) {
        return dispatch(() -> {
            Minecraft mc = Minecraft.getInstance();
            var source = mc.getDownloadedPackSource();
            if (source == null) {
                throw new IllegalStateException("server pack source is unavailable");
            }
            boolean before = serverPacksDisabled(mc);
            if (disabled) {
                source.rejectServerPacks();
            } else {
                source.allowServerPacks();
            }
            if (serverPacksDisabled(mc) != before) {
                mc.reloadResourcePacks();
            }
        });
    }

    @Override
    public CompletableFuture<Void> setLanguage(String languageCode) {
        return dispatch(() -> {
            Minecraft mc = Minecraft.getInstance();
            var options = mc.options;
            var manager = mc.getLanguageManager();
            if (options == null) {
                throw new IllegalStateException("game options are unavailable");
            }
            if (manager.getLanguage(languageCode) == null) {
                throw new IllegalArgumentException("unknown language: " + languageCode);
            }
            manager.setSelected(languageCode);
            options.languageCode = languageCode;
            options.save();
            mc.reloadResourcePacks();
        });
    }

    @Override
    public CompletableFuture<Void> setDifficultyLocked(boolean locked) {
        return dispatch(() -> {
            Minecraft mc = Minecraft.getInstance();
            var connection = mc.getConnection();
            if (connection == null) {
                throw new IllegalStateException("no server connection");
            }
            connection.send(new net.minecraft.network.protocol.game
                    .ServerboundLockDifficultyPacket(locked));
        });
    }

    @Override
    public CompletableFuture<Void> setCameraMode(int mode) {
        return dispatch(() -> {
            var options = Minecraft.getInstance().options;
            if (options == null) {
                throw new IllegalStateException("game options are unavailable");
            }
            var types = net.minecraft.client.CameraType.values();
            if (mode < 0 || mode >= types.length) {
                throw new IllegalArgumentException("camera mode out of range: " + mode);
            }
            options.setCameraType(types[mode]);
        });
    }

    @Override
    public CompletableFuture<Void> setSmoothCamera(boolean enabled) {
        return dispatch(() -> {
            var options = Minecraft.getInstance().options;
            if (options == null) {
                throw new IllegalStateException("game options are unavailable");
            }
            options.smoothCamera = enabled;
        });
    }

    @Override
    public CompletableFuture<Void> setWindowSize(int width, int height) {
        return dispatch(() -> {
            Minecraft mc = Minecraft.getInstance();
            var options = mc.options;
            var window = mc.getWindow();
            if (options == null || window == null) {
                throw new IllegalStateException("game options are unavailable");
            }
            if (options.fullscreen().get()) {
                throw new IllegalStateException(
                        "cannot resize the window while fullscreen");
            }
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("window size must be positive");
            }
            window.setWindowed(width, height);
        });
    }

    @Override
    public void sendSyncedOptions() {
        MainThread.run(() -> {
            var options = Minecraft.getInstance().options;
            if (options != null) {
                options.broadcastOptions();
            }
            return null;
        });
    }

    /** Dispatches one action on the main thread; failures settle the future. */
    private static CompletableFuture<Void> dispatch(Runnable action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
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
