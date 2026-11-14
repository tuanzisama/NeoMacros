package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.adapter.mixin.DownloadedPackSourceAccessor;
import ink.tuanzi.neomacros.adapter.mixin.OptionsKeyMappingsAccessor;
import ink.tuanzi.neomacros.adapter.mixin.ServerPackPromptAccessor;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.model.BlockStateData;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.KeybindCatalogData;
import ink.tuanzi.neomacros.port.model.ModData;
import ink.tuanzi.neomacros.port.model.OptionsData;
import ink.tuanzi.neomacros.port.model.PacketRef;
import ink.tuanzi.neomacros.port.model.PacketSendVerdict;
import ink.tuanzi.neomacros.port.model.Pos2d;
import ink.tuanzi.neomacros.port.model.ServerInfoData;
import ink.tuanzi.neomacros.port.model.WindowData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.BundleDelimiterPacket;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.status.StatusProtocols;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

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
                    options.fullscreenVideoModeString,
                    options.chatVisibility().get().name()
                            .toLowerCase(java.util.Locale.ROOT),
                    options.chatColors().get(),
                    options.chatLinks().get(),
                    options.chatLinksPrompt().get(),
                    options.chatOpacity().get(),
                    options.textBackgroundOpacity().get(),
                    options.chatScale().get(),
                    options.chatLineSpacing().get(),
                    options.chatDelay().get(),
                    options.chatWidth().get(),
                    options.chatHeightFocused().get(),
                    options.chatHeightUnfocused().get(),
                    options.narrator().get().name()
                            .toLowerCase(java.util.Locale.ROOT),
                    options.autoSuggestions().get(),
                    options.hideMatchedNames().get(),
                    options.reducedDebugInfo().get(),
                    options.sensitivity().get(),
                    invertMouseY(options),
                    options.mouseWheelSensitivity().get(),
                    options.discreteMouseScroll().get(),
                    touchscreenEnabled(options),
                    rawMouseInputEnabled(options),
                    options.autoJump().get(),
                    options.toggleCrouch().get(),
                    options.toggleSprint().get(),
                    options.showSubtitles().get(),
                    options.backgroundForChatOnly().get(),
                    options.darkMojangStudiosBackground().get(),
                    options.hideLightningFlash().get(),
                    options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).get(),
                    options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).get(),
                    options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.RECORDS).get(),
                    options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.WEATHER).get(),
                    options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.BLOCKS).get(),
                    options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.HOSTILE).get(),
                    options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.NEUTRAL).get(),
                    options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.PLAYERS).get(),
                    options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.AMBIENT).get(),
                    options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.VOICE).get(),
                    options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.UI).get(),
                    options.soundDevice().get(),
                    options.isModelPartEnabled(net.minecraft.world.entity.player.PlayerModelPart.CAPE),
                    options.isModelPartEnabled(net.minecraft.world.entity.player.PlayerModelPart.JACKET),
                    options.isModelPartEnabled(net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE),
                    options.isModelPartEnabled(net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE),
                    options.isModelPartEnabled(net.minecraft.world.entity.player.PlayerModelPart.LEFT_PANTS_LEG),
                    options.isModelPartEnabled(net.minecraft.world.entity.player.PlayerModelPart.RIGHT_PANTS_LEG),
                    options.isModelPartEnabled(net.minecraft.world.entity.player.PlayerModelPart.HAT),
                    options.mainHand().get().name().toLowerCase(java.util.Locale.ROOT)));
        });
    }

    @Override
    public List<String> audioDevices() {
        List<String> available = availableAudioDevices();
        List<String> devices = new ArrayList<>(available.size() + 1);
        // The empty identifier selects the system default device and is part
        // of the script-facing list, so a read-back of the default always
        // matches an entry of this list.
        devices.add("");
        devices.addAll(available);
        return devices;
    }

    /**
     * Reads the vertical mouse inversion; the accessor was renamed from
     * {@code invertYMouse} to {@code invertMouseY} after 1.21.8.
     */
    private static boolean invertMouseY(net.minecraft.client.Options options) {
        //? if >=1.21.9 {
        return options.invertMouseY().get();
        //?} else {
        /* return options.invertYMouse().get(); */
        //?}
    }

    /**
     * Reads touchscreen mode; Minecraft 26.2 removed the setting with no
     * replacement, so the stored face reports it as off.
     */
    private static boolean touchscreenEnabled(net.minecraft.client.Options options) {
        //? if >=26.2 {
        return false;
        //?} else {
        /* return options.touchscreen().get(); */
        //?}
    }

    /**
     * Reads raw mouse input; Minecraft 26.3 removed the setting and its SDL
     * relative mouse mode always reads raw input.
     */
    private static boolean rawMouseInputEnabled(net.minecraft.client.Options options) {
        //? if >=26.3 {
        return true;
        //?} else {
        /* return options.rawMouseInput().get(); */
        //?}
    }

    @Override
    public Optional<KeybindCatalogData> keybindCatalog() {
        return MainThread.run(() -> {
            var options = Minecraft.getInstance().options;
            if (options == null) {
                return Optional.empty();
            }
            KeyMapping[] mappings =
                    ((OptionsKeyMappingsAccessor) (Object) options).neomacros$keyMappings();
            List<String> names = new ArrayList<>(mappings.length);
            List<String> categories = new ArrayList<>();
            java.util.Map<String, java.util.Map<String, String>> grouped =
                    new java.util.LinkedHashMap<>();
            for (KeyMapping mapping : mappings) {
                names.add(mapping.getName());
                String category = categoryKey(mapping);
                grouped.computeIfAbsent(category, key -> new java.util.LinkedHashMap<>()).put(
                        net.minecraft.network.chat.Component.translatable(mapping.getName())
                                .getString(),
                        mapping.getTranslatedKeyMessage().getString());
                if (!categories.contains(category)) {
                    categories.add(category);
                }
            }
            java.util.Map<String, java.util.Map<String, String>> frozen =
                    new java.util.LinkedHashMap<>();
            grouped.forEach((category, binds) -> frozen.put(category,
                    java.util.Collections.unmodifiableMap(binds)));
            return Optional.of(new KeybindCatalogData(List.copyOf(names),
                    List.copyOf(categories), java.util.Collections.unmodifiableMap(frozen)));
        });
    }

    /**
     * The category key of one mapping; categories became records holding an
     * identifier after 1.21.11, so the key is derived from the identifier.
     */
    private static String categoryKey(KeyMapping mapping) {
        //? if >=1.21.11 {
        return mapping.getCategory().id().toLanguageKey();
        //?} else {
        /* return mapping.getCategory(); */
        //?}
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
            net.minecraft.sounds.SoundSource volume = soundVolumeOf(name);
            if (volume != null) {
                options.getSoundSourceOptionInstance(volume).set(volumeLevel(name, value));
                return;
            }
            net.minecraft.world.entity.player.PlayerModelPart modelPart = modelPartOf(name);
            if (modelPart != null) {
                if (!(value instanceof Boolean flag)) {
                    throw new IllegalArgumentException(
                            "option '" + name + "' requires a boolean");
                }
                options.setModelPart(modelPart, flag);
                return;
            }
            java.lang.reflect.Method getter;
            String accessor = nodeOptionAccessor(name);
            if (accessor == null) {
                // The node no longer carries this setting, so the write is a
                // no-op and the caller's read-back comparison decides whether
                // the requested value matched the stored one.
                return;
            }
            try {
                getter = options.getClass().getMethod(accessor);
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

    /**
     * Resolves one canonical option name to the accessor this node carries,
     * or {@code null} when the node dropped the setting.
     */
    private static String nodeOptionAccessor(String name) {
        //? if <1.21.9 {
        /*
        if ("invertMouseY".equals(name)) {
            return "invertYMouse";
        }
        */
        //?}
        //? if >=26.2 {
        if ("touchscreen".equals(name)) {
            return null;
        }
        //?}
        //? if >=26.3 {
        if ("rawMouseInput".equals(name)) {
            return null;
        }
        //?}
        return name;
    }

    /**
     * Resolves one volume option name onto the vanilla sound category it
     * addresses, or {@code null} when the name is not a volume option. The
     * categories carry no per-name accessor on {@code Options}, so they are
     * reached through their option-instance map instead of by reflection.
     */
    private static net.minecraft.sounds.SoundSource soundVolumeOf(String name) {
        return switch (name) {
            case "masterVolume" -> net.minecraft.sounds.SoundSource.MASTER;
            case "musicVolume" -> net.minecraft.sounds.SoundSource.MUSIC;
            case "recordVolume" -> net.minecraft.sounds.SoundSource.RECORDS;
            case "weatherVolume" -> net.minecraft.sounds.SoundSource.WEATHER;
            case "blockVolume" -> net.minecraft.sounds.SoundSource.BLOCKS;
            case "hostileVolume" -> net.minecraft.sounds.SoundSource.HOSTILE;
            case "neutralVolume" -> net.minecraft.sounds.SoundSource.NEUTRAL;
            case "playerVolume" -> net.minecraft.sounds.SoundSource.PLAYERS;
            case "ambientVolume" -> net.minecraft.sounds.SoundSource.AMBIENT;
            case "voiceVolume" -> net.minecraft.sounds.SoundSource.VOICE;
            case "uiVolume" -> net.minecraft.sounds.SoundSource.UI;
            default -> null;
        };
    }

    /**
     * Resolves one skin option name onto the vanilla player model part it
     * addresses, or {@code null} when the name is not a model-part option.
     * Model parts are a plain set rather than option instances, so they are
     * reached through {@code Options.setModelPart} instead of by reflection.
     */
    private static net.minecraft.world.entity.player.PlayerModelPart modelPartOf(String name) {
        return switch (name) {
            case "cape" -> net.minecraft.world.entity.player.PlayerModelPart.CAPE;
            case "jacket" -> net.minecraft.world.entity.player.PlayerModelPart.JACKET;
            case "leftSleeve" -> net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE;
            case "rightSleeve" -> net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE;
            case "leftPantsLeg" -> net.minecraft.world.entity.player.PlayerModelPart.LEFT_PANTS_LEG;
            case "rightPantsLeg" -> net.minecraft.world.entity.player.PlayerModelPart.RIGHT_PANTS_LEG;
            case "hat" -> net.minecraft.world.entity.player.PlayerModelPart.HAT;
            default -> null;
        };
    }

    /**
     * Coerces one incoming volume write onto a level the vanilla unit slider
     * accepts. Rejecting here keeps a refused write from falling back to the
     * option's default level the way {@code OptionInstance.set} does.
     */
    private static double volumeLevel(String name, Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("option '" + name + "' requires a number");
        }
        double level = number.doubleValue();
        if (!(level >= 0.0 && level <= 1.0)) {
            throw new IllegalArgumentException(
                    "option '" + name + "' out of range: " + level);
        }
        return level;
    }

    /** The audio-device identifiers the sound engine currently reports. */
    private static List<String> availableAudioDevices() {
        var soundManager = Minecraft.getInstance().getSoundManager();
        return soundManager == null ? List.of() : soundManager.getAvailableSoundDevices();
    }

    /** Coerces an incoming script value onto the option's current type. */
    private static Object convertOptionValue(String name, Object current, Object value) {
        if (current == null) {
            throw new IllegalArgumentException("option '" + name + "' has no reference value");
        }
        if ("soundDevice".equals(name) && value instanceof String device
                && !device.isEmpty() && !availableAudioDevices().contains(device)) {
            // Vanilla keeps the default device for an unknown identifier, so the
            // write is refused here to leave the stored selection untouched.
            throw new IllegalArgumentException("unknown audio device: " + device);
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
    public Optional<BlockStateData> blockState(String id, String props) {
        Identifier resolved = resolveId(id);
        if (resolved == null || !BuiltInRegistries.BLOCK.containsKey(resolved)) {
            return Optional.empty();
        }
        if (props == null || props.isBlank()) {
            return Optional.of(
                    defaultStateData(BuiltInRegistries.BLOCK.getValue(resolved)));
        }
        try {
            BlockState state = BlockStateParser
                    .parseForBlock(BuiltInRegistries.BLOCK,
                            resolveName(id) + props.trim(), false)
                    .blockState();
            return Optional.of(new BlockStateData(
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                    stateProperties(state)));
        } catch (CommandSyntaxException error) {
            throw new IllegalArgumentException(
                    "invalid block state: " + id + props, error);
        }
    }

    @Override
    public List<BlockStateData> blockStates() {
        List<BlockStateData> out = new ArrayList<>();
        for (String id : sortedKeys(BuiltInRegistries.BLOCK)) {
            out.add(defaultStateData(
                    BuiltInRegistries.BLOCK.getValue(Identifier.parse(id))));
        }
        return out;
    }

    @Override
    public Optional<ItemStackData> itemStack(String id, String components) {
        Identifier resolved = resolveId(id);
        if (resolved == null || !BuiltInRegistries.ITEM.containsKey(resolved)) {
            return Optional.empty();
        }
        if (components == null || components.isBlank()) {
            return Optional.of(FabricInventoryPort.stackDataOf(
                    BuiltInRegistries.ITEM.getValue(resolved).getDefaultInstance()));
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            // Component syntax reads the connected server's registries.
            return Optional.empty();
        }
        try {
            ItemParser reader = new ItemParser(connection.registryAccess());
            //? if >=26.1 {
            ItemInput itemInput = reader.parse(
                    new StringReader(resolveName(id) + components.trim()));
            ItemStack stack = itemInput.createItemStack(1);
            //?} else {
            /* ItemParser.ItemResult itemResult = reader.parse(
                    new StringReader(resolveName(id) + components.trim()));
            ItemStack stack = new ItemStack(
                    itemResult.item(), 1, itemResult.components()); */
            //?}
            return Optional.of(FabricInventoryPort.stackDataOf(stack));
        } catch (CommandSyntaxException error) {
            throw new IllegalArgumentException(
                    "invalid item components: " + id + components, error);
        }
    }

    @Override
    public List<ItemStackData> defaultItemStacks() {
        List<ItemStackData> out = new ArrayList<>();
        for (String id : sortedKeys(BuiltInRegistries.ITEM)) {
            out.add(FabricInventoryPort.stackDataOf(
                    BuiltInRegistries.ITEM.getValue(Identifier.parse(id))
                            .getDefaultInstance()));
        }
        return out;
    }

    @Override
    public List<String> enchantmentIds() {
        return connectionRegistryIds(net.minecraft.core.registries.Registries.ENCHANTMENT);
    }

    @Override
    public List<String> paintingIds() {
        return connectionRegistryIds(net.minecraft.core.registries.Registries.PAINTING_VARIANT);
    }

    @Override
    public List<String> featureIds() {
        //? if >=26.3 {
        var registry = BuiltInRegistries.FEATURE_TYPE;
        //?} else {
        /* var registry = BuiltInRegistries.FEATURE; */
        //?}
        List<String> out = new ArrayList<>();
        for (Identifier id : registry.keySet()) {
            out.add(id.toString());
        }
        out.sort(String::compareTo);
        return out;
    }

    /**
     * The identifiers of one connection-backed registry (data-driven
     * entries such as enchantments live on the connection, not in the
     * built-in registries); empty without a connection.
     */
    private static List<String> connectionRegistryIds(
            net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> key) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return List.of();
        }
        var registry = connection.registryAccess().lookupOrThrow(key);
        List<String> out = new ArrayList<>();
        for (Identifier id : registry.keySet()) {
            out.add(id.toString());
        }
        out.sort(String::compareTo);
        return out;
    }

    /** The sorted identifiers of one built-in registry, as strings. */
    private static List<String> sortedKeys(net.minecraft.core.Registry<?> registry) {
        List<String> out = new ArrayList<>();
        for (Identifier id : registry.keySet()) {
            out.add(id.toString());
        }
        out.sort(String::compareTo);
        return out;
    }

    /** The default state of one block as neutral data. */
    private static BlockStateData defaultStateData(Block block) {
        BlockState state = block.defaultBlockState();
        return new BlockStateData(
                BuiltInRegistries.BLOCK.getKey(block).toString(),
                stateProperties(state));
    }

    /** Flattens the vanilla state property values into a string map. */
    private static java.util.Map<String, String> stateProperties(BlockState state) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        state.getProperties().forEach(property ->
                out.put(property.getName(), String.valueOf(state.getValue(property))));
        return out;
    }

    /** Parses a script identifier, defaulting the namespace to minecraft. */
    private static Identifier resolveId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.indexOf(':') >= 0
                ? Identifier.tryParse(raw)
                : Identifier.withDefaultNamespace(raw);
    }

    /** The namespaced form of one identifier, for parser input. */
    private static String resolveName(String raw) {
        return raw.indexOf(':') >= 0 ? raw : "minecraft:" + raw;
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
            "allowServerListing", "ambientOcclusion", "ambientVolume", "attackIndicator",
            "autoJump", "autoSuggestions", "backgroundForChatOnly", "biomeBlendRadius",
            "blockVolume", "bobView", "cape",
            "chatColors", "chatDelay", "chatHeightFocused", "chatHeightUnfocused",
            "chatLineSpacing", "chatLinks", "chatLinksPrompt", "chatOpacity",
            "chatScale", "chatVisibility", "chatWidth", "cloudRange", "cloudStatus",
            "damageTiltStrength", "darkMojangStudiosBackground", "darknessEffectScale",
            "directionalAudio", "discreteMouseScroll", "enableVsync",
            "entityDistanceScaling", "entityShadows", "forceUnicodeFont", "fov",
            "fovEffectScale", "framerateLimit", "fullscreen", "gamma", "glintSpeed",
            "glintStrength", "guiScale", "hat", "hideLightningFlash", "hideMatchedNames",
            "hideSplashTexts", "highContrast", "highContrastBlockOutline",
            "hostileVolume", "inactivityFpsLimit", "japaneseGlyphVariants",
            "leftPantsLeg", "leftSleeve", "mainHand", "masterVolume",
            "menuBackgroundBlurriness", "mipmapLevels", "mouseWheelSensitivity",
            "musicFrequency", "musicVolume", "narrator", "narratorHotkey",
            "neutralVolume", "notificationDisplayTime",
            "onlyShowSecureChat", "operatorItemsTab", "panoramaSpeed", "particles",
            "playerVolume", "prioritizeChunkUpdates", "realmsNotifications",
            "recordVolume", "reducedDebugInfo",
            "renderDistance", "rightPantsLeg", "rightSleeve", "rotateWithMinecart",
            "screenEffectScale",
            "sensitivity", "showAutosaveIndicator", "showSubtitles",
            "simulationDistance", "soundDevice", "telemetryOptInExtra",
            "textBackgroundOpacity", "toggleCrouch", "toggleSprint", "uiVolume",
            "voiceVolume", "weatherVolume");

    @Override
    public java.util.Optional<Object> optionValue(String name) {
        return MainThread.run(() -> {
            var options = Minecraft.getInstance().options;
            if (options == null || name == null || !OPTION_NAMES.contains(name)) {
                return java.util.Optional.empty();
            }
            net.minecraft.sounds.SoundSource volume = soundVolumeOf(name);
            if (volume != null) {
                return java.util.Optional.of(
                        options.getSoundSourceOptionInstance(volume).get());
            }
            net.minecraft.world.entity.player.PlayerModelPart modelPart = modelPartOf(name);
            if (modelPart != null) {
                return java.util.Optional.of(options.isModelPartEnabled(modelPart));
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

    // ------------------------------------------------------------------
    // Packet codec face (G-002 §4): composite protocol codecs only — no
    // reflection on MC members, so the four nodes need no mapping guard.
    // ------------------------------------------------------------------

    /** Numeric-id index of one (protocol, flow), built once from its template. */
    private record DetailIndex(java.util.Map<PacketType<?>, Integer> byType,
                               java.util.Map<String, Integer> byId) {
    }

    private static final java.util.Map<String, DetailIndex> PACKET_DETAILS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile ProtocolInfo<?> playClientbound;
    private static volatile ProtocolInfo<?> playServerbound;

    /**
     * The game-phase decorator resolves the registry on every call, so the
     * cached play codecs never go stale across world changes.
     */
    private static final java.util.function.Function<io.netty.buffer.ByteBuf,
            RegistryFriendlyByteBuf> DYNAMIC_REGISTRY =
            buffer -> new RegistryFriendlyByteBuf(buffer, registryAccess());

    /** Permissive creative/command context for game-phase decode (G-002 ⚠). */
    private static final GameProtocols.Context GAME_CONTEXT =
            new GameProtocols.Context() {
                @Override
                public boolean hasInfiniteMaterials() {
                    return true;
                }

                //? if >=26.3 {
                @Override
                public boolean canUseCommandBlocks() {
                    return true;
                }
                //?}
            };

    private static RegistryAccess registryAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            ClientPacketListener connection = minecraft.getConnection();
            if (connection != null) {
                return connection.registryAccess();
            }
            if (minecraft.level != null) {
                return minecraft.level.registryAccess();
            }
        }
        return RegistryAccess.EMPTY;
    }

    /** The pre-built protocol info for one (phase, flow). */
    private static ProtocolInfo<?> protocolInfo(String protocol, String flow) {
        boolean serverbound = !"clientbound".equals(flow);
        return switch (protocol) {
            case "handshake" -> {
                if (!serverbound) {
                    throw new IllegalArgumentException(
                            "no clientbound handshake protocol");
                }
                yield HandshakeProtocols.SERVERBOUND;
            }
            case "status" -> serverbound
                    ? StatusProtocols.SERVERBOUND : StatusProtocols.CLIENTBOUND;
            case "login" -> serverbound
                    ? LoginProtocols.SERVERBOUND : LoginProtocols.CLIENTBOUND;
            case "configuration" -> serverbound
                    ? ConfigurationProtocols.SERVERBOUND
                    : ConfigurationProtocols.CLIENTBOUND;
            case "play" -> serverbound ? playServerbound() : playClientbound();
            default -> throw new IllegalArgumentException(
                    "unknown protocol phase: " + protocol);
        };
    }

    /** The unbound template carrying one (phase, flow) packet index. */
    private static ProtocolInfo.DetailsProvider template(String protocol,
            String flow) {
        boolean serverbound = !"clientbound".equals(flow);
        return switch (protocol) {
            case "handshake" -> {
                if (!serverbound) {
                    throw new IllegalArgumentException(
                            "no clientbound handshake protocol");
                }
                yield HandshakeProtocols.SERVERBOUND_TEMPLATE;
            }
            case "status" -> serverbound
                    ? StatusProtocols.SERVERBOUND_TEMPLATE
                    : StatusProtocols.CLIENTBOUND_TEMPLATE;
            case "login" -> serverbound
                    ? LoginProtocols.SERVERBOUND_TEMPLATE
                    : LoginProtocols.CLIENTBOUND_TEMPLATE;
            case "configuration" -> serverbound
                    ? ConfigurationProtocols.SERVERBOUND_TEMPLATE
                    : ConfigurationProtocols.CLIENTBOUND_TEMPLATE;
            case "play" -> serverbound
                    ? GameProtocols.SERVERBOUND_TEMPLATE
                    : GameProtocols.CLIENTBOUND_TEMPLATE;
            default -> throw new IllegalArgumentException(
                    "unknown protocol phase: " + protocol);
        };
    }

    private static ProtocolInfo<?> playClientbound() {
        ProtocolInfo<?> info = playClientbound;
        if (info == null) {
            info = GameProtocols.CLIENTBOUND_TEMPLATE.bind(DYNAMIC_REGISTRY);
            playClientbound = info;
        }
        return info;
    }

    private static ProtocolInfo<?> playServerbound() {
        ProtocolInfo<?> info = playServerbound;
        if (info == null) {
            info = GameProtocols.SERVERBOUND_TEMPLATE.bind(
                    DYNAMIC_REGISTRY, GAME_CONTEXT);
            playServerbound = info;
        }
        return info;
    }

    /** The cached numeric-id index of one (protocol, flow). */
    private static DetailIndex detailIndex(String protocol, String flow) {
        String key = protocol + "|" + flow;
        DetailIndex cached = PACKET_DETAILS.get(key);
        if (cached != null) {
            return cached;
        }
        java.util.Map<PacketType<?>, Integer> byType = new java.util.HashMap<>();
        java.util.Map<String, Integer> byId = new java.util.HashMap<>();
        template(protocol, flow).details().listPackets((type, numericId) -> {
            byType.put(type, numericId);
            byId.put(type.id().toString(), numericId);
        });
        DetailIndex index = new DetailIndex(
                java.util.Map.copyOf(byType), java.util.Map.copyOf(byId));
        PACKET_DETAILS.put(key, index);
        return index;
    }

    /** Whether one (protocol, flow) knows the packet id at all. */
    private static boolean knowsPacket(String protocol, String flow, String packetId) {
        try {
            return detailIndex(protocol, flow).byId().containsKey(packetId);
        } catch (RuntimeException unavailable) {
            // phase/flow combination does not exist (clientbound handshake)
            return false;
        }
    }

    @Override
    public void onPacketReceive(java.util.function.Consumer<PacketRef> sink) {
        PacketHub.onReceive(sink);
    }

    @Override
    public void onPacketSend(java.util.function.Function<PacketRef,
            PacketSendVerdict> sink) {
        PacketHub.onSend(sink);
    }

    @Override
    public boolean canEncodePacket(Object packet, String protocol) {
        if (!(packet instanceof Packet<?> typed)) {
            return false;
        }
        // Bundles frame other packets and carry no standalone codec
        // (G-002 §2.2): their buffer stays undefined.
        if (typed instanceof BundlePacket<?> || typed instanceof BundleDelimiterPacket<?>) {
            return false;
        }
        try {
            return detailIndex(protocol, typed.type().flow().id())
                    .byType().containsKey(typed.type());
        } catch (RuntimeException unknownPhase) {
            return false;
        }
    }

    @Override
    public byte[] encodePacket(Object packet, String protocol) {
        if (!(packet instanceof Packet<?> typed)) {
            throw new IllegalArgumentException("not a packet: " + packet);
        }
        ProtocolInfo<?> info = protocolInfo(protocol, typed.type().flow().id());
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            @SuppressWarnings("unchecked")
            StreamCodec<io.netty.buffer.ByteBuf, Packet<?>> codec =
                    (StreamCodec<io.netty.buffer.ByteBuf, Packet<?>>)
                            (StreamCodec<?, ?>) info.codec();
            codec.encode(buffer, typed);
            // The composite prefixes the payload with the protocol's numeric
            // id; event cells carry the payload only (G-002 §4.3).
            VarInt.read(buffer);
            byte[] out = new byte[buffer.readableBytes()];
            buffer.readBytes(out);
            return out;
        } finally {
            buffer.release();
        }
    }

    @Override
    public Object decodePacket(String packetId, String protocol, String flow,
            byte[] bytes, int offset) {
        if (packetId == null || bytes == null || offset < 0 || offset > bytes.length) {
            throw new IndexOutOfBoundsException("decode offset " + offset
                    + " outside " + (bytes == null ? 0 : bytes.length));
        }
        String resolved = flow;
        if (resolved == null) {
            // Builder sends craft outgoing packets: serverbound first.
            if (knowsPacket(protocol, "serverbound", packetId)) {
                resolved = "serverbound";
            } else if (knowsPacket(protocol, "clientbound", packetId)) {
                resolved = "clientbound";
            } else {
                throw new IllegalArgumentException("unknown packet id " + packetId
                        + " for protocol " + protocol);
            }
        }
        Integer numericId = detailIndex(protocol, resolved).byId().get(packetId);
        if (numericId == null) {
            throw new IllegalArgumentException("unknown packet id " + packetId
                    + " for " + protocol + "/" + resolved);
        }
        ProtocolInfo<?> info = protocolInfo(protocol, resolved);
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            VarInt.write(buffer, numericId);
            buffer.writeBytes(bytes, offset, bytes.length - offset);
            @SuppressWarnings("unchecked")
            StreamCodec<io.netty.buffer.ByteBuf, Packet<?>> codec =
                    (StreamCodec<io.netty.buffer.ByteBuf, Packet<?>>)
                            (StreamCodec<?, ?>) info.codec();
            Packet<?> decoded = codec.decode(buffer);
            if (buffer.isReadable()) {
                throw new IllegalStateException("trailing bytes after decoding "
                        + packetId + ": " + buffer.readableBytes());
            }
            return decoded;
        } finally {
            buffer.release();
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> sendRawPacket(Object packet) {
        if (!(packet instanceof Packet<?> typed)) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalArgumentException("not a packet: " + packet));
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ClientPacketListener connection =
                    minecraft != null ? minecraft.getConnection() : null;
            if (connection == null) {
                return java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalStateException("not connected"));
            }
            // ClientPacketListener.send funnels into Connection.send, so the
            // script-built packet still passes through packet:send (G-002 §2.4).
            connection.send(typed);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        } catch (RuntimeException failure) {
            return java.util.concurrent.CompletableFuture.failedFuture(failure);
        }
    }

    @Override
    public List<String> packetIds() {
        java.util.TreeSet<String> ids = new java.util.TreeSet<>();
        collectPacketIds("handshake", "serverbound", ids);
        for (String protocol : new String[]{
                "status", "login", "configuration", "play"}) {
            collectPacketIds(protocol, "serverbound", ids);
            collectPacketIds(protocol, "clientbound", ids);
        }
        return List.copyOf(ids);
    }

    private static void collectPacketIds(String protocol, String flow,
            java.util.Set<String> ids) {
        try {
            ids.addAll(detailIndex(protocol, flow).byId().keySet());
        } catch (RuntimeException unavailable) {
            // phase/flow combination does not exist
        }
    }

    @Override
    public Class<?> resolveClass(String name) {
        try {
            return Class.forName(FabricClassResolver.resolve(name), false,
                    FabricClientPort.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
