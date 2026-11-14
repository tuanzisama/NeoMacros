package ink.tuanzi.neomacros.e2e.harness;

import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.RenderPort;

import ink.tuanzi.neomacros.port.InputPort;
import ink.tuanzi.neomacros.port.UnsupportedPortFeature;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.BlockData;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.BlockTypeData;
import ink.tuanzi.neomacros.port.model.BossBarInfoData;
import ink.tuanzi.neomacros.port.model.ChunkData;
import ink.tuanzi.neomacros.port.model.LightData;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ScoreboardData;
import ink.tuanzi.neomacros.port.model.ScoreboardObjectiveData;
import ink.tuanzi.neomacros.port.model.ShapeSpec;
import ink.tuanzi.neomacros.port.model.StatusEffectInfoData;
import ink.tuanzi.neomacros.port.model.TabListEntryInfoData;
import ink.tuanzi.neomacros.port.model.TabListInfoData;
import ink.tuanzi.neomacros.port.model.TeamData;
import ink.tuanzi.neomacros.port.model.TpsData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The E2E stub adapter: records chat output, holds lifecycle sinks that tests
 * fire explicitly, and exposes injection methods as the scripted counterpart
 * of the game's event sources. Ports without canned behavior in this harness
 * raise {@link UnsupportedPortFeature}.
 */
public final class StubAdapter implements NeomacrosAdapter {

    private final StubLifecycle lifecycle = new StubLifecycle();
    private final StubChat chat = new StubChat();
    private final StubWorld world = new StubWorld();
    private final StubEntity entity = new StubEntity();
    private final StubPlayer player = new StubPlayer();
    private final StubInput input = new StubInput();
    private final StubScreen screen = new StubScreen();

    /** The test-facing input port: fires key edges and wheel events. */
    public StubInput inputPort() {
        return input;
    }

    /** The test-facing screen port: records actions, fires sink events. */
    public StubScreen screenPort() {
        return screen;
    }

    /** Input port recording key-press sinks and firing wheel events. */
    public static final class StubInput implements ink.tuanzi.neomacros.port.InputPort {

        private volatile java.util.function.Function<ink.tuanzi.neomacros.port.model.KeyPressEdge, Boolean>
                keyPressSink;
        private volatile java.util.function.Consumer<Runnable> loopDispatcher = Runnable::run;
        private final List<java.util.function.Consumer<Double>> scrollSinks = new java.util.concurrent.CopyOnWriteArrayList<>();

        /** Bind table: bind name → bound key translation key (insertion order). */
        private final java.util.LinkedHashMap<String, String> bindKeys = new java.util.LinkedHashMap<>();
        /** Physical key states set through {@link #injectKey}. */
        private final java.util.Map<String, Boolean> keyStates = new java.util.LinkedHashMap<>();
        /** Bind states set through {@link #injectBind}. */
        private final java.util.Map<String, Boolean> bindStates = new java.util.LinkedHashMap<>();
        /** Ordered injection log ({@code key:}/{@code bind:}/{@code rebind:}) for assertions. */
        private final List<String> injections = new CopyOnWriteArrayList<>();

        /** Seeds the stub with the vanilla movement binds plus the use key. */
        public StubInput() {
            bindKeys.put("key.forward", "key.keyboard.w");
            bindKeys.put("key.back", "key.keyboard.s");
            bindKeys.put("key.left", "key.keyboard.a");
            bindKeys.put("key.right", "key.keyboard.d");
            bindKeys.put("key.jump", "key.keyboard.space");
            bindKeys.put("key.sneak", "key.keyboard.left.shift");
            bindKeys.put("key.sprint", "key.keyboard.left.control");
            bindKeys.put("key.use", "key.mouse.right");
        }

        /** The ordered injection log, for scenario assertions. */
        public List<String> injections() {
            return List.copyOf(injections);
        }

        @Override
        public void injectKey(String keyName, boolean down) {
            keyStates.put(keyName, down);
            injections.add("key:" + keyName + "=" + down);
        }

        @Override
        public void injectBind(String bindName, boolean down) {
            bindStates.put(bindName, down);
            injections.add("bind:" + bindName + "=" + down);
        }

        @Override
        public boolean bindDown(String bindName) {
            if (Boolean.TRUE.equals(bindStates.get(bindName))) {
                return true;
            }
            String key = bindKeys.get(bindName);
            return key != null && Boolean.TRUE.equals(keyStates.get(key));
        }

        @Override
        public List<ink.tuanzi.neomacros.port.model.KeybindData> keybinds() {
            List<ink.tuanzi.neomacros.port.model.KeybindData> rows = new ArrayList<>();
            for (java.util.Map.Entry<String, String> entry : bindKeys.entrySet()) {
                rows.add(new ink.tuanzi.neomacros.port.model.KeybindData(
                        entry.getKey(), entry.getValue()));
            }
            return rows;
        }

        @Override
        public void setBind(String bindName, String keyName) {
            if (!bindKeys.containsKey(bindName)) {
                throw new IllegalArgumentException("unknown keybind: " + bindName);
            }
            bindKeys.put(bindName, keyName);
            injections.add("rebind:" + bindName + "=" + keyName);
        }

        /** Sets the loop dispatcher (the runtime supplies the context loop). */
        public void setLoopDispatcher(java.util.function.Consumer<Runnable> dispatcher) {
            this.loopDispatcher = dispatcher;
        }

        /** Fires one raw key edge through the registered event sink on the loop. */
        public boolean fireEdge(ink.tuanzi.neomacros.port.model.KeyPressEdge edge) {
            var box = new Object() { boolean canceled = false; };
            loopDispatcher.accept(() -> box.canceled = fireInline(edge));
            return box.canceled;
        }

        /** Fires one wheel event through the registered sinks on the loop. */
        public void onScrollFire(double amount) {
            loopDispatcher.accept(() -> scrollSinks.forEach(s -> s.accept(amount)));
        }

        /** Inline fire (must already be on the loop). */
        boolean fireInline(ink.tuanzi.neomacros.port.model.KeyPressEdge edge) {
            return keyPressSink != null && Boolean.TRUE.equals(keyPressSink.apply(edge));
        }

        @Override
        public Object registerKeybind(String keyName, java.util.function.Consumer<Integer> sink) {
            throw new UnsupportedPortFeature("input");
        }

        @Override
        public void registerKeyPress(String keyName, Runnable sink) {
            throw new UnsupportedPortFeature("input");
        }

        @Override
        public void unregister(Object handle) {
            throw new UnsupportedPortFeature("input");
        }

        @Override
        public void registerScriptBinding(String scriptName, String keyName,
                java.util.function.Consumer<Integer> sink) {
            throw new UnsupportedPortFeature("input");
        }

        @Override
        public void unregisterScriptBinding(String scriptName) {
            throw new UnsupportedPortFeature("input");
        }

        @Override
        public void onScroll(java.util.function.Consumer<Double> sink) {
            scrollSinks.add(sink);
        }

        @Override
        public void onKeyPress(java.util.function.Function<ink.tuanzi.neomacros.port.model.KeyPressEdge, Boolean> sink) {
            keyPressSink = sink;
        }

        @Override
        public boolean fireKeyPress(ink.tuanzi.neomacros.port.model.KeyPressEdge edge) {
            return keyPressSink != null && Boolean.TRUE.equals(keyPressSink.apply(edge));
        }
    }

    public StubLifecycle lifecyclePort() {
        return lifecycle;
    }

    public StubChat chatPort() {
        return chat;
    }

    public StubWorld worldPort() {
        return world;
    }

    public StubEntity entityPort() {
        return entity;
    }

    public StubPlayer playerPort() {
        return player;
    }

    @Override
    public LifecyclePort lifecycle() {
        return lifecycle;
    }

    @Override
    public ChatPort chat() {
        return chat;
    }

    @Override
    public WorldPort world() {
        return world;
    }

    private final StubClient client = new StubClient();

    @Override
    public EntityPort entity() {
        return entity;
    }

    @Override
    public ink.tuanzi.neomacros.port.ClientPort client() {
        return client;
    }

    /** The test-facing client port: canned option face, recorded writes. */
    public StubClient clientPort() {
        return client;
    }

    /** Client port stub serving a mutable option face and recording writes. */
    public static final class StubClient implements ink.tuanzi.neomacros.port.ClientPort {

        /** The canned option face served to reads. */
        public volatile ink.tuanzi.neomacros.port.model.OptionsData options =
                cannedOptions();
        /** The canned connected-server entry (null = singleplayer). */
        public volatile ink.tuanzi.neomacros.port.model.ServerInfoData serverInfo;
        /** The canned installed-mods list served to {@code client.mods}. */
        public volatile List<ink.tuanzi.neomacros.port.model.ModInfoData> mods =
                List.of(new ink.tuanzi.neomacros.port.model.ModInfoData(
                        "neomacros", "NeoMacros", "0.1.0", List.of("tuanzi"),
                        "ECMAScript scripting for Minecraft clients", "BOTH",
                        List.of("fabricloader")));
        /** Names whose writes are accepted but not stored (simulated clamps). */
        public final java.util.Set<String> ignoredWrites =
                java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final List<String> actions = new CopyOnWriteArrayList<>();

        /** The recorded write actions in order. */
        public List<String> actions() {
            return List.copyOf(actions);
        }

        /** The current option face. */
        public ink.tuanzi.neomacros.port.model.OptionsData data() {
            return options;
        }

        /** A plausible canned option face for the harness. */
        public static ink.tuanzi.neomacros.port.model.OptionsData cannedOptions() {
            return new ink.tuanzi.neomacros.port.model.OptionsData(
                    70, 0.5, 2, 12,
                    3, "fancy", "nearby", true,
                    10, 120, true, true,
                    "crosshair", "fancy", false, "all",
                    4, true,
                    0.5, 1.0, 1.0, true,
                    0, false,
                    1920, 1080,
                    "en_us", Boolean.FALSE,
                    List.of("vanilla"), List.of("vanilla"),
                    false, null,
                    "full", true, true, true,
                    1.0, 0.5, 1.0, 0.0,
                    0.0, 1.0, 1.0, 0.4436,
                    "off", true, false, false,
                    0.5, false, 1.0, false,
                    false, true,
                    false, false, false,
                    false, false,
                    false, false,
                    1.0, 0.5, 0.75, 1.0,
                    1.0, 1.0, 1.0, 1.0,
                    1.0, 1.0, 0.25,
                    "speakers",
                    true, true, true, true,
                    true, true, true,
                    "right");
        }

        /** A plausible canned key-mapping catalog for the harness. */
        public static ink.tuanzi.neomacros.port.model.KeybindCatalogData cannedKeybinds() {
            return new ink.tuanzi.neomacros.port.model.KeybindCatalogData(
                    List.of("key.forward", "key.back", "key.left", "key.right"),
                    List.of("key.categories.movement"),
                    java.util.Map.of("key.categories.movement", java.util.Map.of(
                            "Forward", "W", "Back", "S", "Left", "A", "Right", "D")));
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.KeybindCatalogData> keybindCatalog() {
            return Optional.of(cannedKeybinds());
        }

        @Override
        public List<String> audioDevices() {
            return List.of("", "speakers", "headphones");
        }

        @Override
        public int fps() {
            return 60;
        }

        @Override
        public String gameVersion() {
            return "26.3-stub";
        }

        @Override
        public Object minecraftInstance() {
            return null;
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.OptionsData> options() {
            return Optional.of(options);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setOption(String name, Object value) {
            String rejection = invalidWrite(name, value);
            if (rejection != null) {
                return java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalArgumentException(rejection));
            }
            if (ignoredWrites.contains(name)) {
                actions.add("option:" + name);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            try {
                options = options.withOption(name, value);
            } catch (IllegalArgumentException e) {
                return java.util.concurrent.CompletableFuture.failedFuture(e);
            }
            actions.add("option:" + name);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        /** Mirrors the adapter's write validation so refused writes behave alike. */
        private String invalidWrite(String name, Object value) {
            if (name != null && name.endsWith("Volume")) {
                if (!(value instanceof Number number)) {
                    return "option '" + name + "' requires a number";
                }
                double level = number.doubleValue();
                if (!(level >= 0.0 && level <= 1.0)) {
                    return "option '" + name + "' out of range: " + level;
                }
            }
            if ("soundDevice".equals(name) && value instanceof String device
                    && !device.isEmpty() && !audioDevices().contains(device)) {
                return "unknown audio device: " + device;
            }
            if ("mainHand".equals(name) && value instanceof String hand
                    && !"left".equalsIgnoreCase(hand) && !"right".equalsIgnoreCase(hand)) {
                return "option '" + name + "' rejects value " + hand;
            }
            return null;
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> saveOptions() {
            actions.add("save");
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setEnabledResourcePacks(
                List<String> enabled) {
            options = options.withOption("enabledResourcePacks", enabled);
            actions.add("packs");
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setServerResourcePacksDisabled(
                boolean disabled) {
            options = options.withOption("serverPacksDisabled", disabled);
            actions.add("serverPacks:" + disabled);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setLanguage(String languageCode) {
            if (languageCode == null || !languageCode.startsWith("en_")) {
                return java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalArgumentException("unknown language: " + languageCode));
            }
            options = options.withOption("language", languageCode);
            actions.add("language:" + languageCode);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setDifficultyLocked(boolean locked) {
            actions.add("lock:" + locked);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setCameraMode(int mode) {
            if (mode < 0 || mode > 2) {
                return java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalArgumentException("camera mode out of range: " + mode));
            }
            options = options.withOption("cameraMode", mode);
            actions.add("camera:" + mode);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setSmoothCamera(boolean enabled) {
            options = options.withOption("smoothCamera", enabled);
            actions.add("smooth:" + enabled);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setWindowSize(int width, int height) {
            if (width <= 0 || height <= 0) {
                return java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalArgumentException("window size must be positive"));
            }
            options = options.withOption("width", width).withOption("height", height);
            actions.add("size:" + width + "x" + height);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void sendSyncedOptions() {
            actions.add("sync");
        }

        // ---- G-025 registry spectrum (canned read faces) ----

        @Override
        public List<String> registryIds(String kind) {
            return switch (kind) {
                case "item" -> List.of("minecraft:air", "minecraft:stone");
                case "block" -> List.of("minecraft:air", "minecraft:oak_stairs");
                case "mob_effect" -> List.of("minecraft:speed", "minecraft:slowness");
                case "fluid" -> List.of("minecraft:water");
                case "entity_type" -> List.of("minecraft:cow", "minecraft:pig");
                case "menu" -> List.of("minecraft:generic_9x3");
                default -> throw new IllegalArgumentException(
                        "unknown registry kind: " + kind);
            };
        }

        @Override
        public Optional<String> registryLookup(String kind, String id) {
            String full = id != null && id.indexOf(':') >= 0
                    ? id : "minecraft:" + id;
            return registryIds(kind).contains(full)
                    ? Optional.of(full) : Optional.empty();
        }

        @Override
        public Optional<StatusEffectInfoData> statusEffect(String id) {
            String full = id != null && id.indexOf(':') >= 0
                    ? id : "minecraft:" + id;
            if ("minecraft:speed".equals(full)) {
                // beneficial, not instant; fresh-instance flags
                return Optional.of(new StatusEffectInfoData(full, 0, 0, false,
                        false, true, true, false, true, false, false));
            }
            if ("minecraft:slowness".equals(full)) {
                // harmful, not instant; fresh-instance flags
                return Optional.of(new StatusEffectInfoData(full, 0, 0, false,
                        false, true, true, false, false, false, true));
            }
            return Optional.empty();
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.ServerInfoData> serverInfo() {
            return Optional.ofNullable(serverInfo);
        }

        @Override
        public List<ink.tuanzi.neomacros.port.model.ModInfoData> mods() {
            return mods;
        }

        @Override
        public boolean isModLoaded(String id) {
            return mods.stream().anyMatch(mod -> mod.id().equals(id));
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.BlockStateData> blockState(
                String id, String props) {
            String full = id != null && id.indexOf(':') >= 0
                    ? id : "minecraft:" + id;
            if (!"minecraft:oak_stairs".equals(full)) {
                return Optional.empty();
            }
            if (props != null && props.contains("bogus")) {
                throw new IllegalArgumentException("invalid block state: " + id + props);
            }
            if (props == null || props.isBlank()) {
                return Optional.of(new ink.tuanzi.neomacros.port.model.BlockStateData(
                        full, java.util.Map.of("facing", "north")));
            }
            return Optional.of(new ink.tuanzi.neomacros.port.model.BlockStateData(
                    full, java.util.Map.of("facing", "south")));
        }

        @Override
        public List<ink.tuanzi.neomacros.port.model.BlockStateData> blockStates() {
            return List.of(new ink.tuanzi.neomacros.port.model.BlockStateData(
                    "minecraft:oak_stairs", java.util.Map.of("facing", "north")));
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.ItemStackData> itemStack(
                String id, String components) {
            String full = id != null && id.indexOf(':') >= 0
                    ? id : "minecraft:" + id;
            if (!"minecraft:stone".equals(full) && !"minecraft:air".equals(full)) {
                return Optional.empty();
            }
            if (components != null && components.contains("bogus")) {
                throw new IllegalArgumentException(
                        "invalid item components: " + id + components);
            }
            if (components != null && components.contains("damage=12")) {
                return Optional.of(new ink.tuanzi.neomacros.port.model.ItemStackData(
                        full, 1, "Stone", 12, 0, List.of(), List.of()));
            }
            return Optional.of(new ink.tuanzi.neomacros.port.model.ItemStackData(
                    full, 1, "Stone"));
        }

        @Override
        public List<ink.tuanzi.neomacros.port.model.ItemStackData> defaultItemStacks() {
            return List.of(
                    new ink.tuanzi.neomacros.port.model.ItemStackData(
                            "minecraft:air", 1, "Air"),
                    new ink.tuanzi.neomacros.port.model.ItemStackData(
                            "minecraft:stone", 1, "Stone"));
        }

        @Override
        public List<String> enchantmentIds() {
            return List.of("minecraft:sharpness");
        }

        @Override
        public List<String> paintingIds() {
            return List.of("minecraft:kebab");
        }

        @Override
        public List<String> featureIds() {
            return List.of("minecraft:ore");
        }

        // ---- packet face (G-031) ------------------------------------

        private final List<Consumer<ink.tuanzi.neomacros.port.model.PacketRef>>
                packetReceiveSinks = new CopyOnWriteArrayList<>();
        private final List<Function<ink.tuanzi.neomacros.port.model.PacketRef,
                ink.tuanzi.neomacros.port.model.PacketSendVerdict>> packetSendSinks =
                new CopyOnWriteArrayList<>();

        /** Whether the canned packets report a standalone codec. */
        public volatile boolean packetEncodable = true;
        /** When true the re-decode step fails (fail-open scenarios). */
        public volatile boolean packetDecodeFails = false;
        /** Raw packets handed to {@link #sendRawPacket}, in order. */
        public final List<Object> sentPackets = new CopyOnWriteArrayList<>();

        @Override
        public void onPacketReceive(Consumer<ink.tuanzi.neomacros.port.model.PacketRef> sink) {
            packetReceiveSinks.add(sink);
        }

        @Override
        public void onPacketSend(Function<ink.tuanzi.neomacros.port.model.PacketRef,
                ink.tuanzi.neomacros.port.model.PacketSendVerdict> sink) {
            packetSendSinks.add(sink);
        }

        /** Fires the receive sinks directly (harness-side source). */
        public void firePacketReceive(ink.tuanzi.neomacros.port.model.PacketRef ref) {
            for (Consumer<ink.tuanzi.neomacros.port.model.PacketRef> sink : packetReceiveSinks) {
                sink.accept(ref);
            }
        }

        /** Fires the send sinks and combines their verdicts (PacketHub parity). */
        public ink.tuanzi.neomacros.port.model.PacketSendVerdict firePacketSend(
                ink.tuanzi.neomacros.port.model.PacketRef ref) {
            boolean canceled = false;
            Object replacement = null;
            for (Function<ink.tuanzi.neomacros.port.model.PacketRef,
                    ink.tuanzi.neomacros.port.model.PacketSendVerdict> sink : packetSendSinks) {
                ink.tuanzi.neomacros.port.model.PacketSendVerdict verdict = sink.apply(ref);
                if (verdict == null) {
                    continue;
                }
                canceled |= verdict.canceled();
                if (verdict.replacement() != null) {
                    replacement = verdict.replacement();
                }
            }
            if (replacement != null) {
                return new ink.tuanzi.neomacros.port.model.PacketSendVerdict(
                        canceled, replacement);
            }
            return canceled
                    ? new ink.tuanzi.neomacros.port.model.PacketSendVerdict(true, null)
                    : ink.tuanzi.neomacros.port.model.PacketSendVerdict.PASS;
        }

        @Override
        public boolean canEncodePacket(Object packet, String protocol) {
            return packetEncodable && packet != null;
        }

        @Override
        public byte[] encodePacket(Object packet, String protocol) {
            if (!canEncodePacket(packet, protocol)) {
                throw new IllegalStateException("stub packet has no codec");
            }
            // Canonical bytes: the payload text of the canned string packet.
            return String.valueOf(packet).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public Object decodePacket(String packetId, String protocol, String flow,
                byte[] bytes, int offset) {
            if (packetDecodeFails) {
                throw new IllegalStateException("stub decode failure");
            }
            return new String(bytes, offset, bytes.length - offset,
                    java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> sendRawPacket(Object packet) {
            if (!(packet instanceof String)) {
                return java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalArgumentException("not a stub packet: " + packet));
            }
            sentPackets.add(packet);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public List<String> packetIds() {
            return List.of("minecraft:keep_alive", "minecraft:swing");
        }
    }

    public ink.tuanzi.neomacros.port.InputPort input() {
        return input;
    }

    @Override
    public ink.tuanzi.neomacros.port.InventoryPort inventory() {
        return inventory;
    }

    @Override
    public ink.tuanzi.neomacros.port.ItemPort item() {
        return item;
    }

    @Override
    public ink.tuanzi.neomacros.port.DialogPort dialog() {
        return new ink.tuanzi.neomacros.port.DialogPort() {
        };
    }

    @Override
    public ink.tuanzi.neomacros.port.ScreenPort screen() {
        return screen;
    }

    /** The test-facing inventory port: reads canned data, records actions. */
    public StubInventory inventoryPort() {
        return inventory;
    }

    private final StubInventory inventory = new StubInventory();

    /** The test-facing item port: reads canned data. */
    public StubItem itemPort() {
        return item;
    }

    private final StubItem item = new StubItem();

    /** Item port stub serving canned item-face answers ({@code null} = undefined). */
    public static final class StubItem implements ink.tuanzi.neomacros.port.ItemPort {

        /** Canned creative tab display names ({@code null} = undefined). */
        public volatile java.util.List<String> creativeTabs;
        /** Canned tab icon stacks, same order as {@link #creativeTabs}. */
        public volatile java.util.List<ink.tuanzi.neomacros.port.model.ItemStackData> groupIcons;
        /** Canned item tags ({@code null} = undefined). */
        public volatile java.util.List<String> tags;
        /** Canned default stack ({@code null} = undefined). */
        public volatile ink.tuanzi.neomacros.port.model.ItemStackData defaultStack;
        /** Canned crafting remainder ({@code null} = undefined). */
        public volatile ink.tuanzi.neomacros.port.model.ItemStackData recipeRemainder;
        /** Canned remainder presence ({@code null} = undefined). */
        public volatile Boolean hasRecipeRemainder;
        /** Canned main-hand attack damage ({@code null} = undefined). */
        public volatile Double attackDamage;
        /** Canned cooldown answers ({@code null} = undefined). */
        public volatile Boolean onCooldown;
        public volatile Float cooldownProgress;
        /** Canned tool and repair evaluations ({@code null} = undefined). */
        public volatile Boolean correctToolForDrops;
        public volatile Float destroySpeed;
        public volatile Boolean repairable;
        /** Canned enchantment definition, relations and pairwise verdicts
         * ({@code null} = undefined). */
        public volatile ink.tuanzi.neomacros.port.model.EnchantmentSpec enchantmentSpec;
        public volatile ink.tuanzi.neomacros.port.model.EnchantmentRelations enchantmentRelations;
        public volatile java.util.function.BiPredicate<String, String> enchantmentCompatibility;
        /** Canned food component and enchantment listings ({@code null} = undefined). */
        public volatile ink.tuanzi.neomacros.port.model.FoodData food;
        public volatile java.util.List<String> possibleEnchantments;
        /** Canned enchanting-table subset ({@code null} = undefined). */
        public volatile java.util.List<String> possibleEnchantmentsFromTable;
        public volatile ink.tuanzi.neomacros.port.model.ChatComponent enchantmentLevelName;

        @Override
        public java.util.Optional<java.util.List<String>> creativeTabs(String itemId) {
            return java.util.Optional.ofNullable(creativeTabs);
        }

        @Override
        public java.util.Optional<java.util.List<ink.tuanzi.neomacros.port.model.ItemStackData>>
                groupIcons(String itemId) {
            return java.util.Optional.ofNullable(groupIcons);
        }

        @Override
        public java.util.Optional<java.util.List<String>> itemTags(String itemId) {
            return java.util.Optional.ofNullable(tags);
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData> defaultStack(
                String itemId) {
            return java.util.Optional.ofNullable(defaultStack);
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData> recipeRemainder(
                String itemId) {
            return java.util.Optional.ofNullable(recipeRemainder);
        }

        @Override
        public java.util.Optional<Boolean> hasRecipeRemainder(String itemId) {
            return java.util.Optional.ofNullable(hasRecipeRemainder);
        }

        @Override
        public java.util.Optional<Double> attackDamage(String itemId) {
            return java.util.Optional.ofNullable(attackDamage);
        }

        @Override
        public java.util.Optional<Boolean> onCooldown(String itemId) {
            return java.util.Optional.ofNullable(onCooldown);
        }

        @Override
        public java.util.Optional<Float> cooldownProgress(String itemId) {
            return java.util.Optional.ofNullable(cooldownProgress);
        }

        @Override
        public java.util.Optional<Boolean> correctToolForDrops(String itemId, String blockId,
                java.util.Map<String, String> props) {
            return java.util.Optional.ofNullable(correctToolForDrops);
        }

        @Override
        public java.util.Optional<Float> destroySpeed(String itemId, String blockId,
                java.util.Map<String, String> props) {
            return java.util.Optional.ofNullable(destroySpeed);
        }

        @Override
        public java.util.Optional<Boolean> canBeRepairedWith(String itemId, String materialId) {
            return java.util.Optional.ofNullable(repairable);
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.EnchantmentSpec>
                enchantmentSpec(String enchantmentId) {
            return java.util.Optional.ofNullable(enchantmentSpec);
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.EnchantmentRelations>
                enchantmentRelations(String enchantmentId) {
            return java.util.Optional.ofNullable(enchantmentRelations);
        }

        @Override
        public java.util.Optional<Boolean> enchantmentsCompatible(String firstId,
                String secondId) {
            var verdict = enchantmentCompatibility;
            return verdict == null ? java.util.Optional.empty()
                    : java.util.Optional.of(verdict.test(firstId, secondId));
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.FoodData> food(String itemId) {
            return java.util.Optional.ofNullable(food);
        }

        @Override
        public java.util.Optional<java.util.List<String>> possibleEnchantments(String itemId,
                boolean enchantingTable) {
            return java.util.Optional.ofNullable(enchantingTable
                    ? possibleEnchantmentsFromTable : possibleEnchantments);
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.ChatComponent>
                enchantmentLevelName(String enchantmentId, int level) {
            return java.util.Optional.ofNullable(enchantmentLevelName);
        }
    }

    /** Inventory port stub serving canned hotbar/main data and recording actions. */
    public static final class StubInventory implements ink.tuanzi.neomacros.port.InventoryPort {

        /** Canned slot data served to reads: slot index → stack data. */
        public final java.util.Map<Integer, ink.tuanzi.neomacros.port.model.ItemStackData> slots =
                new java.util.concurrent.ConcurrentHashMap<>();
        /** The canned selected hotbar slot ({@code null} = unknown). */
        public volatile Integer selectedSlot = 0;
        /** The canned cursor stack ({@code null} = empty cursor). */
        public volatile ink.tuanzi.neomacros.port.model.ItemStackData carried = null;
        /** The canned open container ({@code empty} = no container screen). */
        public volatile java.util.Optional<ContainerInfo> opened = java.util.Optional.empty();
        /** The canned raw menu object behind {@code raw} ({@code null} = none). */
        public volatile Object rawMenu = null;
        /** The canned menu slot under the mouse ({@code null} = none). */
        public volatile Integer underMouse = null;
        /** Canned screen-space slot corners: menu slot → top-left point. */
        public final java.util.Map<Integer, ink.tuanzi.neomacros.port.model.Pos2d> slotPositions =
                new java.util.concurrent.ConcurrentHashMap<>();
        /** Canned region names: menu slot → region. */
        public final java.util.Map<Integer, String> regions =
                new java.util.concurrent.ConcurrentHashMap<>();
        /** Canned player-menu stacks (result/craft/equipment rows). */
        public final java.util.Map<Integer, ink.tuanzi.neomacros.port.model.ItemStackData> playerMenu =
                new java.util.concurrent.ConcurrentHashMap<>();
        /** Canned menu-order stacks (container rows of the open menu). */
        public final java.util.Map<Integer, ink.tuanzi.neomacros.port.model.ItemStackData> menuSlots =
                new java.util.concurrent.ConcurrentHashMap<>();
        /** The canned open-menu state channel keyed by member name. */
        public final java.util.Map<String, Object> state =
                new java.util.concurrent.ConcurrentHashMap<>();
        /** The canned open recipe menu's grid width ({@code 0} = none). */
        public volatile int craftingW = 0;
        /** The canned open recipe menu's grid height ({@code 0} = none). */
        public volatile int craftingH = 0;
        /** The canned recipe-book visibility ({@code null} = no book screen). */
        public volatile Boolean recipeBook = null;
        /** The canned anvil rename-box text ({@code null} = no anvil screen). */
        public volatile String anvilName = null;
        /** The canned fuel-value table ({@code null} = the level hides it). */
        public volatile java.util.Map<String, Integer> fuelValues = null;
        /** The canned furnace fuel/smelt answers ({@code null} = undefined). */
        public volatile Boolean fuelAnswer = null;
        public volatile Boolean smeltableAnswer = null;
        /** The canned brewing recipe answers ({@code null} = undefined). */
        public volatile Boolean brewableAnswer = null;
        public volatile Boolean ingredientAnswer = null;
        public volatile Boolean recipeAnswer = null;
        /** The canned brewing preview result ({@code null} = undefined). */
        public volatile ink.tuanzi.neomacros.port.model.ItemStackData previewResult = null;
        /** The canned answers for the beacon/enchant write actions. */
        public volatile Boolean beaconSelectAnswer = Boolean.TRUE;
        public volatile Boolean beaconApplyAnswer = Boolean.TRUE;
        public volatile Boolean enchantAnswer = Boolean.TRUE;
        /** The canned creative tabs in display order. */
        public final List<InventoryPort.CreativeTab> creativeTabs =
                new CopyOnWriteArrayList<>();
        /** The canned creative item list (empty stacks allowed as air data). */
        public final List<ink.tuanzi.neomacros.port.model.ItemStackData> shownItems =
                new CopyOnWriteArrayList<>();
        /** The canned loom pattern offers, in selection order. */
        public final List<String> loomPatternIds = new CopyOnWriteArrayList<>();
        /** The canned saved-hotbar stacks (empty list = unset slot). */
        public final List<ink.tuanzi.neomacros.port.model.ItemStackData> savedHotbar =
                new CopyOnWriteArrayList<>();
        /** The canned loom pattern-selection answer. */
        public volatile Boolean loomSelectAnswer = Boolean.TRUE;
        final List<String> actions = new CopyOnWriteArrayList<>();

        @Override
        public java.util.Optional<ContainerInfo> openedContainer() {
            return opened;
        }

        @Override
        public java.util.Optional<Object> rawContainer() {
            return java.util.Optional.ofNullable(rawMenu);
        }

        @Override
        public java.util.Optional<Integer> slotUnderMouse() {
            return java.util.Optional.ofNullable(underMouse);
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.Pos2d> slotPos(int slot) {
            return java.util.Optional.ofNullable(slotPositions.get(slot));
        }

        @Override
        public java.util.Optional<String> regionOf(int slot) {
            return java.util.Optional.ofNullable(regions.get(slot));
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> open(String kind) {
            return record("open:" + kind);
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData> playerMenuSlot(int index) {
            return java.util.Optional.ofNullable(playerMenu.get(index));
        }

        @Override
        public int craftingWidth() {
            return craftingW;
        }

        @Override
        public int craftingHeight() {
            return craftingH;
        }

        @Override
        public Boolean recipeBookOpen() {
            return recipeBook;
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setRecipeBookOpen(boolean open) {
            return record("recipeBook:" + open);
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.ItemStackData> menuSlot(int index) {
            return Optional.ofNullable(menuSlots.get(index));
        }

        @Override
        public java.util.Map<String, Object> containerState() {
            return state;
        }

        @Override
        public java.util.Optional<String> anvilItemName() {
            return java.util.Optional.ofNullable(anvilName);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setAnvilItemName(String name) {
            return record("anvilName:" + name);
        }

        @Override
        public Boolean isFuel(ink.tuanzi.neomacros.port.model.ItemStackData stack) {
            return fuelAnswer;
        }

        @Override
        public Boolean isSmeltable(ink.tuanzi.neomacros.port.model.ItemStackData stack) {
            return smeltableAnswer;
        }

        @Override
        public java.util.Map<String, Integer> fuelValues() {
            return fuelValues == null ? java.util.Map.of() : fuelValues;
        }

        @Override
        public Boolean isBrewablePotion(ink.tuanzi.neomacros.port.model.ItemStackData potion) {
            return brewableAnswer;
        }

        @Override
        public Boolean isValidIngredient(ink.tuanzi.neomacros.port.model.ItemStackData ingredient) {
            return ingredientAnswer;
        }

        @Override
        public Boolean isValidRecipe(ink.tuanzi.neomacros.port.model.ItemStackData potion,
                ink.tuanzi.neomacros.port.model.ItemStackData ingredient) {
            return recipeAnswer;
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData> previewPotion(
                ink.tuanzi.neomacros.port.model.ItemStackData potion,
                ink.tuanzi.neomacros.port.model.ItemStackData ingredient) {
            return java.util.Optional.ofNullable(previewResult);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Boolean> selectBeaconPrimaryEffect(String id) {
            actions.add("beaconPrimary:" + id);
            return java.util.concurrent.CompletableFuture.completedFuture(beaconSelectAnswer);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Boolean> selectBeaconSecondaryEffect(String id) {
            actions.add("beaconSecondary:" + id);
            return java.util.concurrent.CompletableFuture.completedFuture(beaconSelectAnswer);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Boolean> applyBeaconEffects() {
            actions.add("beaconApply");
            return java.util.concurrent.CompletableFuture.completedFuture(beaconApplyAnswer);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Boolean> doEnchant(int index) {
            actions.add("enchant:" + index);
            return java.util.concurrent.CompletableFuture.completedFuture(enchantAnswer);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> selectStonecutterRecipe(int index) {
            return record("stonecutter:" + index);
        }

        @Override
        public List<InventoryPort.CreativeTab> creativeTabs() {
            return creativeTabs;
        }

        @Override
        public List<ink.tuanzi.neomacros.port.model.ItemStackData> creativeShownItems() {
            return shownItems;
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> creativeScrollBy(double amount) {
            return record("scrollBy:" + amount);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> creativeScrollTo(double position) {
            return record("scrollTo:" + position);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> creativeSearch(String text) {
            return record("search:" + text);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> creativeSelectTab(int index) {
            return record("selectTab:" + index);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> creativeDestroyHeld() {
            return record("destroyHeld");
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> creativeDestroyAll() {
            return record("destroyAll");
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> creativeSetCursor(
                ink.tuanzi.neomacros.port.model.ItemStackData stack) {
            return record("setCursor:"
                    + (stack == null ? "none" : stack.id()));
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> creativeSaveHotbar(int index) {
            return record("saveHotbar:" + index);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> creativeRestoreHotbar(int index) {
            return record("restoreHotbar:" + index);
        }

        @Override
        public List<ink.tuanzi.neomacros.port.model.ItemStackData> creativeSavedHotbar(
                int index) {
            return index == 0 ? savedHotbar : List.of();
        }

        @Override
        public List<String> loomPatterns() {
            return loomPatternIds;
        }

        @Override
        public java.util.concurrent.CompletableFuture<Boolean> loomSelectPattern(int index) {
            actions.add("loomPattern:" + index);
            return java.util.concurrent.CompletableFuture.completedFuture(loomSelectAnswer);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> selectVillagerTrade(int index) {
            return record("selectTrade:" + index);
        }

        /** Records one script-driven action. */
        private java.util.concurrent.CompletableFuture<Void> record(String what) {
            actions.add(what);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        /** The recorded script-driven action log (test assertions). */
        public List<String> actions() {
            return actions;
        }

        @Override
        public java.util.function.Supplier<java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData>> heldItem() {
            return () -> selectedSlot == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.ofNullable(slots.get(selectedSlot));
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData> slot(int index) {
            return java.util.Optional.ofNullable(slots.get(index));
        }

        @Override
        public java.util.function.Supplier<List<IndexedStack>> allItems() {
            return () -> slots.entrySet().stream()
                    .map(entry -> new IndexedStack(entry.getKey(), entry.getValue()))
                    .toList();
        }

        @Override
        public int countOf(String item) {
            int count = 0;
            for (ink.tuanzi.neomacros.port.model.ItemStackData stack : slots.values()) {
                if (stack.id().equals(item)) {
                    count += stack.count();
                }
            }
            return count;
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData> carriedItem() {
            return java.util.Optional.ofNullable(carried);
        }

        @Override
        public Integer selectedHotbarSlot() {
            return selectedSlot;
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setSelectedHotbarSlot(int index) {
            return record("selectHotbar:" + index);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> click(int slot, int button, boolean shift) {
            return record("click:" + slot + ":" + button + (shift ? ":shift" : ""));
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> pickupAll(int slot) {
            return record("pickupAll:" + slot);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> dragClick(List<Integer> slotList, int button) {
            return record("dragClick:" + slotList + ":" + button);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Integer> quickAll(int slot, int button) {
            actions.add("quickAll:" + slot + ":" + button);
            return java.util.concurrent.CompletableFuture.completedFuture(slots.size());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> closeAndDrop() {
            return record("closeAndDrop");
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> drop(int slot, boolean all) {
            return record("drop:" + slot + ":" + all);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> swapHotbar(int slot, int hotbarIndex) {
            return record("swapHotbar:" + slot + ":" + hotbarIndex);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> close() {
            return record("close");
        }

        /** Registered click interceptors (script-facing cancellable click). */
        final List<java.util.function.Function<
                ink.tuanzi.neomacros.port.InventoryPort.ClickSlotInfo, Boolean>> clickSinks =
                new CopyOnWriteArrayList<>();
        /** Registered drop interceptors. */
        final List<java.util.function.Function<
                ink.tuanzi.neomacros.port.InventoryPort.DropSlotInfo, Boolean>> dropSinks =
                new CopyOnWriteArrayList<>();
        /** Registered slot-sync observers. */
        final List<java.util.function.Consumer<
                ink.tuanzi.neomacros.port.InventoryPort.SlotUpdateInfo>> slotSinks =
                new CopyOnWriteArrayList<>();
        /** Registered cursor-sync observers. */
        final List<java.util.function.Consumer<
                ink.tuanzi.neomacros.port.InventoryPort.HeldUpdateInfo>> heldSinks =
                new CopyOnWriteArrayList<>();
        /** Registered pickup observers. */
        final List<java.util.function.Consumer<
                ink.tuanzi.neomacros.port.InventoryPort.ItemPickupInfo>> pickupSinks =
                new CopyOnWriteArrayList<>();

        @Override
        public void onClickSlot(java.util.function.Function<
                ink.tuanzi.neomacros.port.InventoryPort.ClickSlotInfo, Boolean> sink) {
            clickSinks.add(sink);
        }

        @Override
        public void onDropSlot(java.util.function.Function<
                ink.tuanzi.neomacros.port.InventoryPort.DropSlotInfo, Boolean> sink) {
            dropSinks.add(sink);
        }

        @Override
        public void onSlotUpdate(java.util.function.Consumer<
                ink.tuanzi.neomacros.port.InventoryPort.SlotUpdateInfo> sink) {
            slotSinks.add(sink);
        }

        @Override
        public void onHeldUpdate(java.util.function.Consumer<
                ink.tuanzi.neomacros.port.InventoryPort.HeldUpdateInfo> sink) {
            heldSinks.add(sink);
        }

        @Override
        public void onItemPickup(java.util.function.Consumer<
                ink.tuanzi.neomacros.port.InventoryPort.ItemPickupInfo> sink) {
            pickupSinks.add(sink);
        }

        /** Fires one click through the interceptors; returns whether it was cancelled. */
        public boolean fireClickSlot(ink.tuanzi.neomacros.port.InventoryPort.ClickSlotInfo click) {
            boolean canceled = false;
            for (var sink : clickSinks) {
                if (Boolean.TRUE.equals(sink.apply(click))) {
                    canceled = true;
                }
            }
            return canceled;
        }

        /** Fires one drop through the interceptors; returns whether it was cancelled. */
        public boolean fireDropSlot(ink.tuanzi.neomacros.port.InventoryPort.DropSlotInfo drop) {
            boolean canceled = false;
            for (var sink : dropSinks) {
                if (Boolean.TRUE.equals(sink.apply(drop))) {
                    canceled = true;
                }
            }
            return canceled;
        }

        /** Fires one slot sync at the observers. */
        public void fireSlotUpdate(ink.tuanzi.neomacros.port.InventoryPort.SlotUpdateInfo update) {
            for (var sink : slotSinks) {
                sink.accept(update);
            }
        }

        /** Fires one cursor sync at the observers. */
        public void fireHeldUpdate(ink.tuanzi.neomacros.port.InventoryPort.HeldUpdateInfo update) {
            for (var sink : heldSinks) {
                sink.accept(update);
            }
        }

        /** Fires one pickup at the observers. */
        public void fireItemPickup(ink.tuanzi.neomacros.port.InventoryPort.ItemPickupInfo pickup) {
            for (var sink : pickupSinks) {
                sink.accept(pickup);
            }
        }
    }

    private final StubRenderPort renderPort = new StubRenderPort();

    /** The recording HUD render port behind {@code neo.hud} textures. */
    public StubRenderPort renderPort() {
        return renderPort;
    }

    @Override
    public RenderPort render() {
        return renderPort;
    }

    /**
     * HUD render recorder: layer registrations, z changes, fired frames,
     * and texture uploads are all recorded for assertions. Firing a frame
     * replays every painter in {@code (z, id)} order into one
     * {@link RecordingDrawContext2d}, mirroring the resident-element
     * pipeline of the Fabric adapter.
     */
    public static final class StubRenderPort implements RenderPort {
        /** One recorded texture upload (a fresh pixel snapshot). */
        public record TextureUpload(String id, int width, int height, int[] argb) {
            /** Copies the buffer so later mutation cannot rewrite history. */
            public TextureUpload {
                argb = argb.clone();
            }

            /** Row-major ARGB pixel at {@code (x, y)}. */
            public int pixel(int x, int y) {
                return argb[y * width + x];
            }
        }

        /** One neutral primitive drawn during a fired frame. */
        public record Prim(String kind, int a, int b, int c, int d, int color,
                String text) {
        }

        /** Accumulates the primitives of one fired frame, in draw order. */
        public static final class RecordingDrawContext2d
                implements ink.tuanzi.neomacros.port.model.DrawContext2d {
            private final List<Prim> prims = new CopyOnWriteArrayList<>();

            @Override
            public void text(int x, int y, String text) {
                text(x, y, text, 0xFFFFFFFF);
            }

            @Override
            public void text(int x, int y, String text, int color) {
                prims.add(new Prim("text", x, y, 0, 0, color, text));
            }

            @Override
            public void rect(int x, int y, int width, int height, int color) {
                prims.add(new Prim("rect", x, y, width, height, color, null));
            }

            @Override
            public void outline(int x, int y, int width, int height, int color) {
                prims.add(new Prim("outline", x, y, width, height, color, null));
            }

            @Override
            public void sprite(int x, int y, int width, int height, String textureId) {
                prims.add(new Prim("sprite", x, y, width, height, 0, textureId));
            }

            /** The primitives drawn so far, in draw order. */
            public List<Prim> prims() {
                return List.copyOf(prims);
            }
        }

        private final java.util.Map<String, java.util.function.BiConsumer<Integer,
                ink.tuanzi.neomacros.port.model.DrawContext2d>> layers =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.Map<String, Integer> zs =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final List<TextureUpload> uploads = new CopyOnWriteArrayList<>();
        private final List<Prim> lastFrame = new CopyOnWriteArrayList<>();

        /** Every texture upload in call order. */
        public List<TextureUpload> uploads() {
            return List.copyOf(uploads);
        }

        /** Identifiers of layers currently registered, in render order. */
        public List<String> registeredLayers() {
            return layerIds().get();
        }

        /** The ordered port-call log: {@code add:id:z}, {@code setz:id:z}, {@code remove:id}. */
        public List<String> events() {
            return List.copyOf(events);
        }

        /** The primitives produced by the most recent {@link #fireFrame}. */
        public List<Prim> lastFrame() {
            return List.copyOf(lastFrame);
        }

        @Override
        public void addLayer(String id, int z, java.util.function.BiConsumer<Integer,
                ink.tuanzi.neomacros.port.model.DrawContext2d> painter) {
            layers.put(id, painter);
            zs.put(id, z);
            events.add("add:" + id + ":" + z);
        }

        @Override
        public void removeLayer(String id) {
            if (layers.remove(id) != null) {
                zs.remove(id);
                events.add("remove:" + id);
            }
        }

        @Override
        public void setLayerZ(String id, int z) {
            if (layers.containsKey(id)) {
                zs.put(id, z);
                events.add("setz:" + id + ":" + z);
            }
        }

        @Override
        public Supplier<List<String>> layerIds() {
            return () -> layers.keySet().stream()
                    .sorted(java.util.Comparator
                            .comparingInt((String id) -> zs.getOrDefault(id, 0))
                            .thenComparing(java.util.Comparator.naturalOrder()))
                    .toList();
        }

        /**
         * Fires one frame: every registered painter runs in
         * {@code (z, id)} order against a fresh recording context.
         *
         * @param tick tick count handed to the painters
         * @return the primitives drawn during this frame, in draw order
         */
        public List<Prim> fireFrame(int tick) {
            RecordingDrawContext2d context = new RecordingDrawContext2d();
            for (String id : layerIds().get()) {
                var painter = layers.get(id);
                if (painter != null) {
                    painter.accept(tick, context);
                }
            }
            lastFrame.clear();
            lastFrame.addAll(context.prims());
            return context.prims();
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> putTexture(
                String id, int width, int height, int[] argb) {
            uploads.add(new TextureUpload(id, width, height, argb));
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public Supplier<List<String>> textureIds() {
            return () -> uploads.stream().map(TextureUpload::id).distinct().toList();
        }
    }

    private final StubDraw3D draw3dPort = new StubDraw3D();

    @Override
    public Draw3DPort draw3d() {
        return draw3dPort;
    }

    /** World-space shape recorder for assertions. */
    public static final class StubDraw3D implements Draw3DPort {
        /** Separator between layer id and shape id in {@link #current} keys. */
        private static final String KEY_SEP = "\0";

        /** One recorded shape (immediate primitive face). */
        public record Shape(String kind, String layerId, Pos3d a, Pos3d b, int color) {
        }

        /** One recorded keyed shape write (G-033 {@code putShape}/{@code removeShape}). */
        public record KeyedShape(String op, String layerId, String shapeId, ShapeSpec spec) {
        }

        final List<Shape> shapes = new CopyOnWriteArrayList<>();
        final List<KeyedShape> keyed = new CopyOnWriteArrayList<>();
        /** Live keyed state, keyed {@code layerId + NUL + shapeId}. */
        final java.util.Map<String, ShapeSpec> current = new ConcurrentHashMap<>();

        private final java.util.concurrent.atomic.AtomicLong legacySeq =
                new java.util.concurrent.atomic.AtomicLong();

        @Override
        public void putShape(String layerId, String shapeId, ShapeSpec spec) {
            keyed.add(new KeyedShape("put", layerId, shapeId, spec));
            current.put(layerId + KEY_SEP + shapeId, spec);
        }

        @Override
        public void removeShape(String layerId, String shapeId) {
            keyed.add(new KeyedShape("remove", layerId, shapeId, null));
            current.remove(layerId + KEY_SEP + shapeId);
        }

        /** Every keyed write in call order, for order-sensitive assertions. */
        public List<KeyedShape> keyedShapes() {
            return List.copyOf(keyed);
        }

        /** The current spec of one keyed shape, or null when absent. */
        public ShapeSpec currentShape(String layerId, String shapeId) {
            return current.get(layerId + KEY_SEP + shapeId);
        }

        /** Live keyed shapes of one layer as {@code shapeId -> spec}. */
        public java.util.Map<String, ShapeSpec> liveShapes(String layerId) {
            String prefix = layerId + KEY_SEP;
            java.util.Map<String, ShapeSpec> result = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<String, ShapeSpec> entry : current.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    result.put(entry.getKey().substring(prefix.length()), entry.getValue());
                }
            }
            return result;
        }

        @Override
        public void drawBox(String layerId, Pos3d min, Pos3d max, int argbColor) {
            shapes.add(new Shape("box", layerId, min, max, argbColor));
            putShape(layerId, "r" + legacySeq.incrementAndGet(),
                    new ShapeSpec.Box(min, max, argbColor, 0, false, true));
        }

        @Override
        public void drawLine(String layerId, Pos3d from, Pos3d to, int argbColor) {
            shapes.add(new Shape("line", layerId, from, to, argbColor));
            putShape(layerId, "r" + legacySeq.incrementAndGet(),
                    new ShapeSpec.Line(from, to, argbColor, true));
        }

        @Override
        public void addTraceLine(String layerId, java.util.UUID entityUuid, int argbColor) {
            shapes.add(new Shape("trace", layerId,
                    new Pos3d(0, 0, 0), new Pos3d(0, 0, 0), argbColor));
            putShape(layerId, "r" + legacySeq.incrementAndGet(),
                    new ShapeSpec.Trace(entityUuid, argbColor));
        }

        @Override
        public void drawSurface(String layerId, java.util.List<Pos3d> points, int argbColor) {
            shapes.add(new Shape("surface", layerId,
                    points.isEmpty() ? new Pos3d(0, 0, 0) : points.get(0),
                    points.size() > 1 ? points.get(points.size() - 1) : new Pos3d(0, 0, 0),
                    argbColor));
            putShape(layerId, "r" + legacySeq.incrementAndGet(),
                    new ShapeSpec.Surface(java.util.List.copyOf(points), argbColor));
        }

        @Override
        public void clearLayer(String layerId) {
            shapes.removeIf(shape -> shape.layerId().equals(layerId));
            String prefix = layerId + KEY_SEP;
            current.keySet().removeIf(key -> key.startsWith(prefix));
            keyed.add(new KeyedShape("clear", layerId, null, null));
        }

        @Override
        public java.util.function.Supplier<List<String>> layerIds() {
            return () -> current.keySet().stream()
                    .map(key -> key.substring(0, key.indexOf(KEY_SEP.charAt(0))))
                    .distinct()
                    .toList();
        }
    }

    @Override
    public PlayerPort player() {
        return player;
    }

    /** Lifecycle sink fan-out with explicit test-side firing. */
    public static final class StubLifecycle implements LifecyclePort {
        final List<Consumer<Long>> tickSinks = new CopyOnWriteArrayList<>();
        final List<Runnable> startSinks = new CopyOnWriteArrayList<>();
        final List<Runnable> quitSinks = new CopyOnWriteArrayList<>();
        final List<Runnable> joinSinks = new CopyOnWriteArrayList<>();
        final List<Runnable> leaveSinks = new CopyOnWriteArrayList<>();
        final List<Consumer<ink.tuanzi.neomacros.port.model.ResourcePackLoadData>>
                resourcePackSinks = new CopyOnWriteArrayList<>();
        final AtomicBooleanHolder inWorld = new AtomicBooleanHolder();
        volatile java.util.Optional<LifecyclePort.ScreenInfo> screen =
                java.util.Optional.empty();

        /** Sets the canned open-screen snapshot (empty clears it). */
        public void setScreen(java.util.Optional<LifecyclePort.ScreenInfo> value) {
            this.screen = value;
        }

        @Override
        public java.util.Optional<ScreenInfo> screenInfo() {
            return screen;
        }

        /** Fires the tick sink with the given tick count. */
        public void fireTick(long tick) {
            for (Consumer<Long> sink : tickSinks) {
                sink.accept(tick);
            }
        }

        /** Fires the game-start sink. */
        public void fireGameStart() {
            for (Runnable sink : startSinks) {
                sink.run();
            }
        }

        /** Fires the game-quit sink. */
        public void fireGameQuit() {
            for (Runnable sink : quitSinks) {
                sink.run();
            }
        }

        /** Fires the world-join sink. */
        public void fireWorldJoin() {
            for (Runnable sink : joinSinks) {
                sink.run();
            }
        }

        /** Fires the world-leave sink. */
        public void fireWorldLeave() {
            for (Runnable sink : leaveSinks) {
                sink.run();
            }
        }

        /** Fires the resource-pack-loaded sink with the given payload. */
        public void fireResourcePackLoaded(boolean isGameStart, List<String> loadedPacks) {
            var data = new ink.tuanzi.neomacros.port.model.ResourcePackLoadData(
                    isGameStart, loadedPacks);
            for (Consumer<ink.tuanzi.neomacros.port.model.ResourcePackLoadData> sink
                    : resourcePackSinks) {
                sink.accept(data);
            }
        }

        /** Sets the in-world answer. */
        public void setInWorld(boolean value) {
            inWorld.value = value;
        }

        @Override
        public void onTick(Consumer<Long> sink) {
            tickSinks.add(sink);
        }

        @Override
        public void onResourcePackLoaded(
                Consumer<ink.tuanzi.neomacros.port.model.ResourcePackLoadData> sink) {
            resourcePackSinks.add(sink);
        }

        @Override
        public void onGameStart(Runnable sink) {
            startSinks.add(sink);
        }

        @Override
        public void onGameQuit(Runnable sink) {
            quitSinks.add(sink);
        }

        @Override
        public void onWorldJoin(Runnable sink) {
            joinSinks.add(sink);
        }

        @Override
        public void onWorldLeave(Runnable sink) {
            leaveSinks.add(sink);
        }

        @Override
        public Supplier<Boolean> inWorld() {
            return () -> inWorld.value;
        }
    }

    /** Chat output recording plus a fireable receive sink. */
    public static final class StubChat implements ChatPort {
        final List<String> output = new ArrayList<>();
        final List<Consumer<ChatComponent>> receiveSinks = new CopyOnWriteArrayList<>();
        final List<Function<ink.tuanzi.neomacros.port.model.TitleDisplayData,
                ink.tuanzi.neomacros.port.model.TextVerdict>> titleSinks = new CopyOnWriteArrayList<>();
        final List<Function<ink.tuanzi.neomacros.port.model.ActionbarDisplayData,
                ink.tuanzi.neomacros.port.model.TextVerdict>> actionbarSinks = new CopyOnWriteArrayList<>();
        final List<Function<String, ink.tuanzi.neomacros.port.model.ChatSendVerdict>> sendSinks =
                new CopyOnWriteArrayList<>();

        /** Delivers one displayed message to the receive sinks. */
        public void fireReceive(String text) {
            ChatComponent message = ChatComponent.plain(text);
            for (Consumer<ChatComponent> sink : receiveSinks) {
                sink.accept(message);
            }
        }

        /**
         * Fires one outgoing chat message through the send sinks; returns the
         * combined verdict (cancel OR across sinks, last replacement wins).
         */
        public ink.tuanzi.neomacros.port.model.ChatSendVerdict fireChatSend(String message) {
            boolean canceled = false;
            String replaced = null;
            for (Function<String, ink.tuanzi.neomacros.port.model.ChatSendVerdict> sink : sendSinks) {
                var verdict = sink.apply(message);
                if (verdict == null) {
                    continue;
                }
                canceled |= verdict.canceled();
                if (verdict.message() != null) {
                    replaced = verdict.message();
                }
            }
            return new ink.tuanzi.neomacros.port.model.ChatSendVerdict(canceled, replaced);
        }

        /**
         * Fires one title/subtitle display; returns the combined verdict
         * (cancel OR across sinks, last replacement text wins).
         */
        public ink.tuanzi.neomacros.port.model.TextVerdict fireTitle(String kind, String text) {
            ink.tuanzi.neomacros.port.model.TitleDisplayData display =
                    new ink.tuanzi.neomacros.port.model.TitleDisplayData(kind, ChatComponent.plain(text));
            boolean canceled = false;
            ChatComponent replaced = null;
            for (Function<ink.tuanzi.neomacros.port.model.TitleDisplayData,
                    ink.tuanzi.neomacros.port.model.TextVerdict> sink : titleSinks) {
                var verdict = sink.apply(display);
                if (verdict == null) {
                    continue;
                }
                canceled |= verdict.canceled();
                if (verdict.text() != null) {
                    replaced = verdict.text();
                }
            }
            return new ink.tuanzi.neomacros.port.model.TextVerdict(canceled, replaced);
        }

        /** Fires one action-bar display; returns the combined verdict. */
        public ink.tuanzi.neomacros.port.model.TextVerdict fireActionbar(String source, String text) {
            ink.tuanzi.neomacros.port.model.ActionbarDisplayData display =
                    new ink.tuanzi.neomacros.port.model.ActionbarDisplayData(ChatComponent.plain(text), source);
            boolean canceled = false;
            ChatComponent replaced = null;
            for (Function<ink.tuanzi.neomacros.port.model.ActionbarDisplayData,
                    ink.tuanzi.neomacros.port.model.TextVerdict> sink : actionbarSinks) {
                var verdict = sink.apply(display);
                if (verdict == null) {
                    continue;
                }
                canceled |= verdict.canceled();
                if (verdict.text() != null) {
                    replaced = verdict.text();
                }
            }
            return new ink.tuanzi.neomacros.port.model.TextVerdict(canceled, replaced);
        }

        /** Recorded output lines in order (prefixed by channel). */
        public List<String> output() {
            return List.copyOf(output);
        }

        @Override
        public void say(String message) {
            output.add("say:" + message);
        }

        @Override
        public void log(String message) {
            output.add("log:" + message);
        }

        @Override
        public void actionbar(String message) {
            output.add("bar:" + message);
        }

        @Override
        public void onChatReceive(Consumer<ChatComponent> sink) {
            receiveSinks.add(sink);
        }

        @Override
        public void onChatSend(Function<String,
                ink.tuanzi.neomacros.port.model.ChatSendVerdict> sink) {
            sendSinks.add(sink);
        }

        @Override
        public void onTitle(Function<ink.tuanzi.neomacros.port.model.TitleDisplayData,
                ink.tuanzi.neomacros.port.model.TextVerdict> sink) {
            titleSinks.add(sink);
        }

        @Override
        public void onActionbar(Function<ink.tuanzi.neomacros.port.model.ActionbarDisplayData,
                ink.tuanzi.neomacros.port.model.TextVerdict> sink) {
            actionbarSinks.add(sink);
        }
    }

    /** World data over canned state; tests mutate the fields directly. */
    public static final class StubWorld implements WorldPort {
        public long time;
        public String dimension = "";
        public boolean raining;
        BlockData blockAt;
        BlockInteraction raycastHit;
        String levelName = "";
        int moonPhase;
        TabListInfoData tabList;
        ScoreboardData scoreboard;
        TpsData tps;
        ChunkData chunk;
        public Pos3d respawnPos;
        public LightData light;
        /** Canned scoreboard teams backing the G-018B team faces. */
        public final List<TeamData> teams = new ArrayList<>();
        /** Canned local-player team (null = none). */
        public TeamData localTeam;
        /** Canned display-slot objectives keyed by slot index. */
        public final java.util.Map<Integer, ScoreboardObjectiveData> displayObjectives =
                new java.util.HashMap<>();
        /** Canned registry-level block type facts (null = unknown block). */
        public BlockTypeData blockType;
        /** Canned answer for containsAny queries (null = no world). */
        public Boolean chunkContainsAny;
        /** Canned answer for containsAll queries (null = no world). */
        public Boolean chunkContainsAll;
        /** Canned allowsSpawning answer (null = no world). */
        public Boolean allowsSpawning;
        /** Canned shouldSuffocate answer (null = no world). */
        public Boolean suffocates;
        /** Canned raw block entity object (null = none / no world). */
        public Object rawBlockEntity;
        public final java.util.List<Pos3d> foundBlocks = new ArrayList<>();
        /** Paths recorded by playSoundFile, in call order. */
        public final java.util.List<String> playedSoundFiles = new ArrayList<>();
        /** Volume recorded by the last playSoundFile call. */
        public double lastSoundFileVolume;
        final List<BossBarInfoData> bossBars = new ArrayList<>();
        final List<String> playedSounds = new ArrayList<>();
        final List<String> spawnedParticles = new ArrayList<>();

        /** Sets the canned block returned for any blockAt query. */
        public void setBlock(BlockData block) {
            this.blockAt = block;
        }

        /** Sets the canned raycast result (null = miss). */
        public void setRaycastHit(BlockInteraction hit) {
            this.raycastHit = hit;
        }

        /** Sets the canned chunk (null = no world). */
        public void setChunk(ChunkData data) {
            this.chunk = data;
        }

        /** Sets the canned block type facts (null = unknown block). */
        public void setBlockType(BlockTypeData data) {
            this.blockType = data;
        }

        /** Sets the canned containsAny/containsAll answers. */
        public void setChunkContains(Boolean any, Boolean all) {
            this.chunkContainsAny = any;
            this.chunkContainsAll = all;
        }

        /** Sets the canned allowsSpawning/shouldSuffocate answers. */
        public void setStateQueries(Boolean spawning, Boolean suffocating) {
            this.allowsSpawning = spawning;
            this.suffocates = suffocating;
        }

        /** Sets the canned raw block entity object (null = none). */
        public void setRawBlockEntity(Object entity) {
            this.rawBlockEntity = entity;
        }

        /** Sets the canned respawn position (null = no world). */
        public void setRespawnPos(Pos3d pos) {
            this.respawnPos = pos;
        }

        /** Sets the canned light levels (null = no world). */
        public void setLight(LightData data) {
            this.light = data;
        }

        /** Sets the canned tab list (null = none). */
        public void setTabList(TabListInfoData data) {
            this.tabList = data;
        }

        /** Adds one canned boss bar to the tracked list. */
        public void addBossBar(BossBarInfoData bar) {
            this.bossBars.add(bar);
        }

        /** Adds one canned scoreboard team. */
        public void addTeam(TeamData team) {
            this.teams.add(team);
        }

        /** Sets the canned local-player team (null = none). */
        public void setLocalTeam(TeamData team) {
            this.localTeam = team;
        }

        /** Cans one display-slot objective (null clears the slot). */
        public void setDisplayObjective(int slot, ScoreboardObjectiveData objective) {
            if (objective == null) {
                this.displayObjectives.remove(slot);
            } else {
                this.displayObjectives.put(slot, objective);
            }
        }

        @Override
        public Optional<BlockTypeData> blockType(String blockId) {
            return java.util.Optional.ofNullable(blockType);
        }

        @Override
        public Optional<ChunkData> chunkAt(Pos3d pos) {
            return java.util.Optional.ofNullable(chunk);
        }

        @Override
        public java.util.Optional<Boolean> allowsSpawningAt(Pos3d pos, String entityId) {
            return java.util.Optional.ofNullable(allowsSpawning);
        }

        @Override
        public java.util.Optional<Boolean> suffocatesAt(Pos3d pos) {
            return java.util.Optional.ofNullable(suffocates);
        }

        @Override
        public java.util.Optional<Boolean> chunkContains(int chunkX, int chunkZ,
                List<String> blockIds, boolean requireAll) {
            return java.util.Optional.ofNullable(
                    requireAll ? chunkContainsAll : chunkContainsAny);
        }

        @Override
        public java.util.Optional<Object> rawBlockEntityByPos(Pos3d pos) {
            return java.util.Optional.ofNullable(rawBlockEntity);
        }

        @Override
        public long time() {
            return time;
        }

        @Override
        public String dimension() {
            return dimension;
        }

        @Override
        public boolean isRaining() {
            return raining;
        }

        @Override
        public Optional<BlockData> blockAt(Pos3d pos) {
            return Optional.ofNullable(blockAt);
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.RayHit> raycast(Pos3d origin,
                Pos3d direction, ink.tuanzi.neomacros.port.model.RayOptions options) {
            return Optional.ofNullable(raycastHit).map(hit ->
                    new ink.tuanzi.neomacros.port.model.RayHit(hit, null, hit.pos(), 0));
        }

        @Override
        public String levelName() {
            return levelName;
        }

        @Override
        public int moonPhase() {
            return moonPhase;
        }

        @Override
        public Optional<TabListInfoData> tabList() {
            return Optional.ofNullable(tabList);
        }

        @Override
        public List<BossBarInfoData> bossBars() {
            return List.copyOf(bossBars);
        }

        @Override
        public List<TeamData> teams() {
            return List.copyOf(teams);
        }

        @Override
        public Optional<TeamData> localPlayerTeam() {
            return Optional.ofNullable(localTeam);
        }

        @Override
        public Optional<ScoreboardObjectiveData> displayObjective(int slot) {
            return Optional.ofNullable(displayObjectives.get(slot));
        }

        @Override
        public Optional<ScoreboardData> scoreboards() {
            return Optional.ofNullable(scoreboard);
        }

        @Override
        public Optional<TpsData> tps() {
            return Optional.ofNullable(tps);
        }

        @Override
        public Optional<Pos3d> respawnPos() {
            return Optional.ofNullable(respawnPos);
        }

        @Override
        public Optional<LightData> lightAt(Pos3d pos) {
            return Optional.ofNullable(light);
        }

        @Override
        public void playSound(String soundId, Pos3d pos, double volume, double pitch) {
            playedSounds.add(soundId);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> playSoundFile(String path,
                double volume) {
            playedSoundFiles.add(path);
            lastSoundFileVolume = volume;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void spawnParticle(String particleId, Pos3d pos, int count, Pos3d offset,
                double speed) {
            spawnedParticles.add(particleId);
        }

        /** Records the last findBlocksMatching request for scenario assertions. */
        public volatile java.util.Map<String, Object> lastScanRequest;
        /** Records every scan request in call order for scenario assertions. */
        public final java.util.List<java.util.Map<String, Object>> scanRequests =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public java.util.List<Pos3d> findBlocks(java.util.List<String> blockIds,
                Pos3d center, int range, int limit) {
            return findBlocksMatching(blockIds, null, null, center, range, limit, false);
        }

        @Override
        public java.util.List<Pos3d> findBlocksMatching(java.util.List<String> blockIds,
                String idPattern, java.util.Map<String, Object> stateFilters,
                Pos3d center, int range, int limit, boolean sphere) {
            java.util.Map<String, Object> request = new java.util.LinkedHashMap<>();
            request.put("types", blockIds == null ? java.util.List.of() : blockIds);
            request.put("idPattern", idPattern);
            request.put("states", stateFilters);
            request.put("center", center);
            request.put("range", range);
            request.put("limit", limit);
            request.put("sphere", sphere);
            lastScanRequest = request;
            scanRequests.add(request);
            return java.util.List.copyOf(foundBlocks);
        }

        @Override
        public java.util.concurrent.CompletableFuture<java.util.List<Pos3d>> scanBlocks(
                java.util.List<String> blockIds, String idPattern,
                java.util.Map<String, Object> stateFilters, Pos3d center,
                int range, int limit, boolean sphere) {
            java.util.Map<String, Object> request = new java.util.LinkedHashMap<>();
            request.put("types", blockIds == null ? java.util.List.of() : blockIds);
            request.put("idPattern", idPattern);
            request.put("states", stateFilters);
            request.put("center", center);
            request.put("range", range);
            request.put("limit", limit);
            request.put("sphere", sphere);
            request.put("async", true);
            lastScanRequest = request;
            scanRequests.add(request);
            return java.util.concurrent.CompletableFuture.completedFuture(
                    java.util.List.copyOf(foundBlocks));
        }
    }

    /** Entity queries over a canned snapshot list. */
    public static final class StubEntity implements EntityPort {
        final List<EntitySnapshot> entities = new ArrayList<>();
        public final java.util.Map<String, Object> entityState =
                new java.util.LinkedHashMap<>();
        public final java.util.Map<String, Object> livingExtras =
                new java.util.LinkedHashMap<>();
        public final java.util.Map<UUID, java.util.Map<String, Object>> species =
                new java.util.LinkedHashMap<>();
        final List<String> writes = new ArrayList<>();
        public volatile boolean canAttackResult;
        public volatile boolean canBeAffectedResult;
        public volatile boolean canSeeResult;
        public volatile boolean isFoodResult;
        public volatile boolean canBreedWithResult;
        public volatile String lastFoodId;
        public volatile boolean lastSimpleCast = true;

        /** Replaces the canned entity population. */
        public void setEntities(List<EntitySnapshot> snapshots) {
            entities.clear();
            entities.addAll(snapshots);
        }

        /** Replaces the base-entity state entries the port serves. */
        public void setEntityState(java.util.Map<String, Object> state) {
            entityState.clear();
            entityState.putAll(state);
        }

        /** Replaces the living-entity extras entries the port serves. */
        public void setLivingExtras(java.util.Map<String, Object> extras) {
            livingExtras.clear();
            livingExtras.putAll(extras);
        }

        /** Serves one entity's species state map (per UUID). */
        public void setSpeciesState(UUID uuid, java.util.Map<String, Object> state) {
            species.put(uuid, new java.util.LinkedHashMap<>(state));
        }

        /** The recorded write actions, oldest first. */
        public List<String> writes() {
            return List.copyOf(writes);
        }

        @Override
        public Optional<EntitySnapshot> byUuid(UUID uuid) {
            return entities.stream().filter(e -> e.uuid().equals(uuid)).findFirst();
        }

        @Override
        public java.util.Map<String, Object> entityState(UUID uuid) {
            return byUuid(uuid)
                    .<java.util.Map<String, Object>>map(
                            s -> new java.util.LinkedHashMap<>(entityState))
                    .orElse(java.util.Map.of());
        }

        @Override
        public java.util.Map<String, Object> speciesState(UUID uuid) {
            return byUuid(uuid)
                    .<java.util.Map<String, Object>>map(
                            s -> new java.util.LinkedHashMap<>(
                                    species.getOrDefault(uuid, java.util.Map.of())))
                    .orElse(java.util.Map.of());
        }

        @Override
        public java.util.Map<String, Object> livingExtras(UUID uuid) {
            return byUuid(uuid)
                    .<java.util.Map<String, Object>>map(
                            s -> new java.util.LinkedHashMap<>(livingExtras))
                    .orElse(java.util.Map.of());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setGlowing(UUID uuid, boolean glowing) {
            writes.add("setGlowing:" + glowing);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> resetGlowing(UUID uuid) {
            writes.add("resetGlowing");
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setGlowingColor(UUID uuid, int color) {
            writes.add("setGlowingColor:" + color);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> resetGlowingColor(UUID uuid) {
            writes.add("resetGlowingColor");
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setCustomName(UUID uuid, String name) {
            writes.add("setCustomName:" + name);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setCustomNameVisible(
                UUID uuid, boolean visible) {
            writes.add("setCustomNameVisible:" + visible);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean canAttack(UUID uuid, UUID target) {
            return canAttackResult;
        }

        @Override
        public boolean canBeAffected(UUID uuid, String effectId) {
            return canBeAffectedResult;
        }

        @Override
        public boolean canSee(UUID uuid, UUID target, boolean simpleCast) {
            lastSimpleCast = simpleCast;
            return canSeeResult;
        }

        @Override
        public boolean isFood(UUID uuid, String itemId) {
            lastFoodId = itemId;
            return isFoodResult;
        }

        @Override
        public boolean canBreedWith(UUID uuid, UUID other) {
            return canBreedWithResult;
        }

        @Override
        public List<EntitySnapshot> inRadius(Pos3d center, double range, Predicate<EntitySnapshot> filter) {
            double squared = range * range;
            List<EntitySnapshot> hits = new ArrayList<>();
            for (EntitySnapshot entity : entities) {
                double dx = entity.pos().x() - center.x();
                double dy = entity.pos().y() - center.y();
                double dz = entity.pos().z() - center.z();
                if (dx * dx + dy * dy + dz * dz <= squared && filter.test(entity)) {
                    hits.add(entity);
                }
            }
            return List.copyOf(hits);
        }

        @Override
        public List<EntitySnapshot> inBox(Pos3d min, Pos3d max, Predicate<EntitySnapshot> filter) {
            List<EntitySnapshot> hits = new ArrayList<>();
            for (EntitySnapshot entity : entities) {
                Pos3d pos = entity.pos();
                if (pos.x() < min.x() || pos.x() > max.x()
                        || pos.y() < min.y() || pos.y() > max.y()
                        || pos.z() < min.z() || pos.z() > max.z()) {
                    continue;
                }
                if (filter.test(entity)) {
                    hits.add(entity);
                }
            }
            return List.copyOf(hits);
        }
    }

    /** Local player state over one settable snapshot plus fireable interactions. */
    public static final class StubPlayer implements PlayerPort {
        volatile PlayerSnapshot self;
        volatile List<ink.tuanzi.neomacros.port.model.EffectSnapshot> effects = List.of();
        volatile int expLevel;
        volatile float expProgress;
        volatile int expTotal;
        volatile List<ink.tuanzi.neomacros.port.model.ItemStackData> armor = List.of();
        volatile UUID vehicle;
        final List<Function<BlockInteraction, Boolean>> attackBlockSinks = new CopyOnWriteArrayList<>();
        final List<Function<EntitySnapshot, Boolean>> attackEntitySinks = new CopyOnWriteArrayList<>();
        final List<Function<BlockInteraction, Boolean>> interactBlockSinks = new CopyOnWriteArrayList<>();
        final List<Function<EntitySnapshot, Boolean>> interactEntitySinks = new CopyOnWriteArrayList<>();
        final List<Function<ink.tuanzi.neomacros.port.model.SignEditData,
                ink.tuanzi.neomacros.port.model.SignEditVerdict>> signEditSinks = new CopyOnWriteArrayList<>();
        final List<String> actions = new java.util.concurrent.CopyOnWriteArrayList<>();

        /** Sets the local player snapshot (null = not in a world). */
        public void setSelf(PlayerSnapshot snapshot) {
            this.self = snapshot;
        }

        /** Records one script-driven action. */
        private java.util.concurrent.CompletableFuture<Void> record(String what) {
            actions.add(what);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        /** The recorded script-driven action log (test assertions). */
        public List<String> actions() {
            return actions;
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> attack(EntitySnapshot target) {
            return record("attack:" + target.uuid());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> useEntity(EntitySnapshot target,
                String hand) {
            return record("useEntity:" + target.uuid() + ":" + hand);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> useBlock(BlockInteraction hit,
                String hand) {
            return record("useBlock:" + hit.pos().x() + "," + hit.pos().y() + ","
                    + hit.pos().z() + ":" + hit.face() + ":" + hand);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> useItem(String hand) {
            return record("useItem:" + hand);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> attackBlock(BlockInteraction hit) {
            return record("attackBlock:" + hit.pos().x() + "," + hit.pos().y() + ","
                    + hit.pos().z() + ":" + hit.face());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> stopBreaking() {
            return record("stopBreaking");
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> swingHand(String hand) {
            return record("swing:" + hand);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> dropHeld(boolean all) {
            return record("drop:" + all);
        }

        // --- G-028 interaction override canned face: deterministic state
        // plus the recorded dispatch log the scenarios assert against.

        volatile boolean targetOverrideSet;
        volatile String targetOverrideKind = "block";
        volatile int overrideX;
        volatile int overrideY;
        volatile int overrideZ;
        volatile String overrideFace = "down";
        volatile boolean breakArmed;
        volatile boolean holdActive;
        volatile boolean rangeCheck = true;
        volatile boolean rangeAutoClear = true;
        volatile boolean airCheck;
        volatile boolean airAutoClear;
        volatile boolean shapeCheck = true;
        volatile boolean shapeAutoClear;
        volatile boolean targetInRangeAnswer = true;
        volatile ink.tuanzi.neomacros.port.model.BreakBlockResult breakOutcome =
                new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                        "SUCCESS", new Pos3d(1, 64, 1));
        volatile int holdRemainingAnswer;
        final List<Consumer<ink.tuanzi.neomacros.port.model.BreakBlockResult>>
                breakCallbacks = new CopyOnWriteArrayList<>();

        @Override
        public java.util.concurrent.CompletableFuture<Void> setTargetOverride(
                int x, int y, int z, String face) {
            actions.add("setTarget:" + x + "," + y + "," + z + ":" + face);
            targetOverrideSet = true;
            targetOverrideKind = "block";
            overrideX = x;
            overrideY = y;
            overrideZ = z;
            overrideFace = face;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setTargetEntityOverride(
                UUID entity) {
            actions.add("setTargetEntity:" + entity);
            targetOverrideSet = true;
            targetOverrideKind = "entity";
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setTargetMissedOverride() {
            actions.add("setTargetMissed");
            targetOverrideSet = true;
            targetOverrideKind = "miss";
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> clearTargetOverride() {
            actions.add("clearTarget");
            targetOverrideSet = false;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean hasTargetOverride() {
            return targetOverrideSet;
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setTargetRangeCheck(
                boolean enabled, boolean autoClear) {
            actions.add("rangeCheck:" + enabled + ":" + autoClear);
            rangeCheck = enabled;
            rangeAutoClear = autoClear;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setTargetAirCheck(
                boolean enabled, boolean autoClear) {
            actions.add("airCheck:" + enabled + ":" + autoClear);
            airCheck = enabled;
            airAutoClear = autoClear;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setTargetShapeCheck(
                boolean enabled, boolean autoClear) {
            actions.add("shapeCheck:" + enabled + ":" + autoClear);
            shapeCheck = enabled;
            shapeAutoClear = autoClear;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> resetTargetChecks() {
            actions.add("resetChecks");
            rangeCheck = true;
            rangeAutoClear = true;
            airCheck = false;
            airAutoClear = false;
            shapeCheck = true;
            shapeAutoClear = false;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean targetInRange(float tickDelta) {
            return targetOverrideSet && targetInRangeAnswer;
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.RayHit> target() {
            if (!targetOverrideSet || !"block".equals(targetOverrideKind)) {
                // Miss and entity overrides read as no pick in the stub;
                // without a stored override the stub world has no pick.
                return Optional.empty();
            }
            return Optional.of(new ink.tuanzi.neomacros.port.model.RayHit(
                    new BlockInteraction("minecraft:stone",
                            new Pos3d(overrideX, overrideY, overrideZ), overrideFace),
                    null,
                    new Pos3d(overrideX + 0.5, overrideY + 0.5, overrideZ + 0.5),
                    2.5));
        }

        @Override
        public java.util.concurrent.CompletableFuture<
                ink.tuanzi.neomacros.port.model.BreakBlockResult> breakBlock() {
            actions.add("breakBlock");
            return settleBreak(breakOutcome);
        }

        @Override
        public java.util.concurrent.CompletableFuture<
                ink.tuanzi.neomacros.port.model.BreakBlockResult> breakBlockAt(
                        int x, int y, int z) {
            actions.add("breakBlockAt:" + x + "," + y + "," + z);
            return settleBreak(breakOutcome);
        }

        /** Disarms and dispatches the canned outcome to queued callbacks. */
        private java.util.concurrent.CompletableFuture<
                ink.tuanzi.neomacros.port.model.BreakBlockResult> settleBreak(
                        ink.tuanzi.neomacros.port.model.BreakBlockResult outcome) {
            breakArmed = false;
            fireBreakCallbacks(outcome);
            return java.util.concurrent.CompletableFuture.completedFuture(outcome);
        }

        /** Runs (then clears) the queued break callbacks with one outcome. */
        private void fireBreakCallbacks(ink.tuanzi.neomacros.port.model.BreakBlockResult outcome) {
            if (breakCallbacks.isEmpty()) {
                return;
            }
            for (var callback : breakCallbacks) {
                try {
                    callback.accept(outcome);
                } catch (Throwable ignored) {
                    // one bad callback must not stop the others
                }
            }
            breakCallbacks.clear();
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setBreakOverride(
                boolean value, String reason) {
            actions.add("breakOverride:" + value + ":" + reason);
            breakArmed = value;
            if (!value) {
                fireBreakCallbacks(new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                        reason, null));
            }
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean hasBreakBlockOverride() {
            return breakArmed;
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> cancelBreakBlock() {
            actions.add("cancelBreak");
            breakArmed = false;
            fireBreakCallbacks(new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                    "CANCELLED", null));
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> reportBreakBlock(
                int x, int y, int z, boolean broken) {
            actions.add("reportBreak:" + x + "," + y + "," + z + ":" + broken);
            if (broken && breakArmed) {
                breakArmed = false;
                fireBreakCallbacks(new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                        "SUCCESS", new Pos3d(x, y, z)));
            }
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public void addBreakCallback(
                Consumer<ink.tuanzi.neomacros.port.model.BreakBlockResult> callback,
                boolean breaking) {
            actions.add("addBreakCallback:" + breaking);
            if (callback != null) {
                breakCallbacks.add(callback);
            }
            if (breaking) {
                breakArmed = true;
            }
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> setHoldInteract(boolean holding) {
            actions.add("holdInteract:" + holding);
            holdActive = holding;
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Integer> holdInteractFor(
                int ticks, boolean stopOnPause) {
            actions.add("holdInteractFor:" + ticks + ":" + stopOnPause);
            // The timed hold releases before the call resolves (parity).
            holdActive = false;
            return java.util.concurrent.CompletableFuture.completedFuture(holdRemainingAnswer);
        }

        @Override
        public boolean hasInteractOverride() {
            return holdActive;
        }

        /** Sets the active status effect list served to the wiring. */
        public void setEffects(List<ink.tuanzi.neomacros.port.model.EffectSnapshot> effects) {
            this.effects = List.copyOf(effects);
        }

        /** Sets the canned experience level, progress and total points. */
        public void setExp(int level, float progress, int total) {
            this.expLevel = level;
            this.expProgress = progress;
            this.expTotal = total;
        }

        /** Sets the four armor slots in feet, legs, chest, head order. */
        public void setArmor(List<ink.tuanzi.neomacros.port.model.ItemStackData> armor) {
            this.armor = List.copyOf(armor);
        }

        /** Sets the ridden entity UUID ({@code null} = on foot). */
        public void setVehicle(UUID vehicle) {
            this.vehicle = vehicle;
        }

        /** Fires the attack-block interaction; returns the cancel verdict. */
        public boolean fireAttackBlock(BlockInteraction hit) {
            return fireAll(attackBlockSinks, hit);
        }

        /** Fires the attack-entity interaction; returns the cancel verdict. */
        public boolean fireAttackEntity(EntitySnapshot target) {
            return fireAll(attackEntitySinks, target);
        }

        /** Fires the block-use interaction; returns the cancel verdict. */
        public boolean fireInteractBlock(BlockInteraction hit) {
            return fireAll(interactBlockSinks, hit);
        }

        /** Fires the entity-interaction; returns the cancel verdict. */
        public boolean fireInteractEntity(EntitySnapshot target) {
            return fireAll(interactEntitySinks, target);
        }

        /** Fires the sign-edit open; returns the combined verdict. */
        public ink.tuanzi.neomacros.port.model.SignEditVerdict fireSignEdit(
                ink.tuanzi.neomacros.port.model.SignEditData data) {
            boolean canceled = false;
            boolean closeScreen = false;
            List<String> replaced = null;
            for (Function<ink.tuanzi.neomacros.port.model.SignEditData,
                    ink.tuanzi.neomacros.port.model.SignEditVerdict> sink : signEditSinks) {
                var verdict = sink.apply(data);
                if (verdict == null) {
                    continue;
                }
                canceled |= verdict.canceled();
                closeScreen |= verdict.closeScreen();
                if (verdict.lines() != null) {
                    replaced = verdict.lines();
                }
            }
            return new ink.tuanzi.neomacros.port.model.SignEditVerdict(canceled, replaced, closeScreen);
        }

        @Override
        public Optional<PlayerSnapshot> self() {
            return Optional.ofNullable(self);
        }

        @Override
        public List<ink.tuanzi.neomacros.port.model.EffectSnapshot> activeEffects() {
            return effects;
        }

        @Override
        public int expLevel() {
            return expLevel;
        }

        @Override
        public float expProgress() {
            return expProgress;
        }

        @Override
        public int expTotal() {
            return expTotal;
        }

        @Override
        public List<ink.tuanzi.neomacros.port.model.ItemStackData> armorSlots() {
            return armor;
        }

        @Override
        public Optional<UUID> vehicleUuid() {
            return Optional.ofNullable(vehicle);
        }

        @Override
        public void onAttackBlock(Function<BlockInteraction, Boolean> sink) {
            attackBlockSinks.add(sink);
        }

        @Override
        public void onAttackEntity(Function<EntitySnapshot, Boolean> sink) {
            attackEntitySinks.add(sink);
        }

        @Override
        public void onInteractBlock(Function<BlockInteraction, Boolean> sink) {
            interactBlockSinks.add(sink);
        }

        @Override
        public void onInteractEntity(Function<EntitySnapshot, Boolean> sink) {
            interactEntitySinks.add(sink);
        }

        @Override
        public void onSignEdit(Function<ink.tuanzi.neomacros.port.model.SignEditData,
                ink.tuanzi.neomacros.port.model.SignEditVerdict> sink) {
            signEditSinks.add(sink);
        }

        // --- G-018 advancement/statistics canned face: deterministic reads
        // the progress/statistics scenarios assert against.

        volatile java.util.Map<String, Integer> statistics = java.util.Map.of();
        volatile java.util.Map<String, String> formattedStatistics = java.util.Map.of();
        volatile java.util.Map<String, String> statTypeNames = java.util.Map.of();
        volatile List<ink.tuanzi.neomacros.port.model.AdvancementData> advancements =
                List.of();
        volatile java.util.Map<String,
                ink.tuanzi.neomacros.port.model.AdvancementProgressData> progressById =
                java.util.Map.of();
        volatile java.util.Map<String,
                ink.tuanzi.neomacros.port.model.AdvancementDefinitionData> definitionById =
                java.util.Map.of();
        volatile java.util.Map<String,
                ink.tuanzi.neomacros.port.model.AdvancementProgressDetailData> detailById =
                java.util.Map.of();
        volatile java.util.Map<String, String> jsonById = java.util.Map.of();

        /** Sets the canned non-zero stat counters ({@code statTypeId:valueId -> value}). */
        public void setStatistics(java.util.Map<String, Integer> statistics) {
            this.statistics = statistics;
        }

        /** Sets the canned formatted counters (same keys as {@link #setStatistics}). */
        public void setFormattedStatistics(java.util.Map<String, String> formatted) {
            this.formattedStatistics = formatted;
        }

        /** Sets the canned stat-type display names ({@code statTypeId -> name}). */
        public void setStatTypeNames(java.util.Map<String, String> names) {
            this.statTypeNames = names;
        }

        /** Sets the canned client-known advancements (display entries). */
        public void setAdvancements(
                List<ink.tuanzi.neomacros.port.model.AdvancementData> advancements) {
            this.advancements = advancements;
        }

        /** Sets the canned progress entries by advancement id. */
        public void setAdvancementProgress(java.util.Map<String,
                ink.tuanzi.neomacros.port.model.AdvancementProgressData> progressById) {
            this.progressById = progressById;
        }

        /** Sets the canned definition data by advancement id. */
        public void setAdvancementDefinitions(java.util.Map<String,
                ink.tuanzi.neomacros.port.model.AdvancementDefinitionData> definitions) {
            this.definitionById = definitions;
        }

        /** Sets the canned progress detail (criterion dates + fraction) by id. */
        public void setAdvancementProgressDetails(java.util.Map<String,
                ink.tuanzi.neomacros.port.model.AdvancementProgressDetailData> details) {
            this.detailById = details;
        }

        /** Sets the canned advancement JSON by id. */
        public void setAdvancementJson(java.util.Map<String, String> jsonById) {
            this.jsonById = jsonById;
        }

        @Override
        public java.util.Map<String, Integer> statistics() {
            return statistics;
        }

        @Override
        public java.util.Map<String, String> formattedStatistics() {
            return formattedStatistics;
        }

        @Override
        public Optional<String> statTypeDisplayName(String statKey) {
            if (statKey == null || statKey.isEmpty()) {
                return Optional.empty();
            }
            int first = statKey.indexOf(':');
            int second = first < 0 ? -1 : statKey.indexOf(':', first + 1);
            String typeId = second < 0 ? statKey : statKey.substring(0, second);
            return Optional.ofNullable(statTypeNames.get(typeId));
        }

        @Override
        public List<ink.tuanzi.neomacros.port.model.AdvancementData> advancements() {
            return advancements;
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.AdvancementProgressData>
                advancementProgress(String id) {
            return Optional.ofNullable(progressById.get(id));
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.AdvancementDefinitionData>
                advancementDefinition(String id) {
            return Optional.ofNullable(definitionById.get(id));
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.AdvancementProgressDetailData>
                advancementProgressDetail(String id) {
            return Optional.ofNullable(detailById.get(id));
        }

        @Override
        public Optional<String> advancementJson(String id) {
            return Optional.ofNullable(jsonById.get(id));
        }

        /** Runs every sink; true when any sink cancels. */
        private static <T> boolean fireAll(List<Function<T, Boolean>> sinks, T payload) {
            boolean canceled = false;
            for (Function<T, Boolean> sink : sinks) {
                if (Boolean.TRUE.equals(sink.apply(payload))) {
                    canceled = true;
                }
            }
            return canceled;
        }
    }

    /**
     * The screen port stub: records every mutation for assertions, can
     * inject an open failure ({@link #failOpen}), and exposes synthetic
     * sink triggers (input/control/state/close) for end-to-end callback
     * chains — the recording pattern of {@code StubLifecycle#fireTick}.
     */
    public static final class StubScreen implements ink.tuanzi.neomacros.port.ScreenPort {

        /** When true, {@code open} answers null (the facade throws; the script catches). */
        public volatile boolean failOpen;
        /** Modifier read answers for {@code gui.shiftDown/ctrlDown/altDown}. */
        public volatile boolean shift;
        public volatile boolean control;
        public volatile boolean alt;

        private final java.util.concurrent.atomic.AtomicLong sequence =
                new java.util.concurrent.atomic.AtomicLong();
        private final List<String> actions = new CopyOnWriteArrayList<>();
        private final java.util.Map<Long, SessionSink> sinks =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.Map<Long, java.util.Map<String, ControlState>> states =
                new java.util.concurrent.ConcurrentHashMap<>();
        private volatile long active = -1L;
        private final List<String> drawn = new CopyOnWriteArrayList<>();

        /** The ordered action log ({@code open}/{@code close:1}/{@code write:1:dbg}...). */
        public List<String> actions() {
            return List.copyOf(actions);
        }

        /** Text/rect draws recorded by {@link #fireRender}, for assertions. */
        public List<String> drawings() {
            return List.copyOf(drawn);
        }

        @Override
        public Long open(java.util.Map<String, Object> normalizedSpec, SessionSink sink) {
            actions.add("open");
            if (failOpen) {
                return null;
            }
            long id = sequence.incrementAndGet();
            sinks.put(id, sink);
            java.util.Map<String, ControlState> seeded = new java.util.LinkedHashMap<>();
            Object controls = normalizedSpec.get("controls");
            if (controls instanceof java.util.List<?> entries) {
                for (Object entry : entries) {
                    if (entry instanceof java.util.Map<?, ?> map) {
                        ControlState state = stateOf(castMap(map));
                        seeded.put(state.id(), state);
                    }
                }
            }
            states.put(id, seeded);
            active = id;
            return id;
        }

        @Override
        public void close(long session) {
            actions.add("close:" + session);
            SessionSink sink = sinks.get(session);
            if (sink != null) {
                sink.closed();
            }
            if (active == session) {
                active = -1L;
            }
        }

        @Override
        public void reload(long session) {
            actions.add("reload:" + session);
        }

        @Override
        public void addControl(long session, java.util.Map<String, Object> control) {
            actions.add("add:" + session + ":" + control.get("id"));
            java.util.Map<String, ControlState> sessionStates = states.get(session);
            if (sessionStates != null) {
                ControlState state = stateOf(control);
                sessionStates.put(state.id(), state);
            }
        }

        @Override
        public void removeControl(long session, String id) {
            actions.add("remove:" + session + ":" + id);
            java.util.Map<String, ControlState> sessionStates = states.get(session);
            if (sessionStates != null) {
                sessionStates.remove(id);
            }
        }

        @Override
        public void writeControl(long session, String id,
                                 java.util.Map<String, Object> patch) {
            actions.add("write:" + session + ":" + id);
            java.util.Map<String, ControlState> sessionStates = states.get(session);
            if (sessionStates == null) {
                return;
            }
            ControlState state = sessionStates.get(id);
            if (state == null) {
                return;
            }
            sessionStates.put(id, new ControlState(state.id(), state.type(), state.x(),
                    state.y(), state.width(), state.height(),
                    patch.get("z") instanceof Number z ? z.intValue() : state.z(),
                    patch.get("label") instanceof String label ? label : state.label(),
                    patch.get("active") instanceof Boolean a ? a : state.active(),
                    patch.get("visible") instanceof Boolean v ? v : state.visible(),
                    patch.get("checked") instanceof Boolean c ? c : state.checked(),
                    patch.get("value") instanceof Number n ? n.doubleValue() : state.value(),
                    patch.get("text") instanceof String t ? t : state.text(),
                    patch.get("locked") instanceof Boolean l ? l : state.locked()));
        }

        @Override
        public Optional<ControlState> readControl(long session, String id) {
            java.util.Map<String, ControlState> sessionStates = states.get(session);
            return sessionStates == null ? Optional.empty()
                    : Optional.ofNullable(sessionStates.get(id));
        }

        @Override
        public boolean isDisplayed(long session) {
            return active == session && sinks.containsKey(session);
        }

        @Override
        public boolean shiftDown() {
            return shift;
        }

        @Override
        public boolean controlDown() {
            return control;
        }

        @Override
        public boolean altDown() {
            return alt;
        }

        // ---------------- synthetic triggers ----------------

        /** Fires one screen-level mouse press; answers the consumption verdict. */
        public boolean fireMouseDown(MouseInput input) {
            SessionSink sink = activeSink();
            return sink != null && sink.mouseDown(input);
        }

        /** Fires one screen-level mouse drag; answers the consumption verdict. */
        public boolean fireMouseDrag(MouseInput input) {
            SessionSink sink = activeSink();
            return sink != null && sink.mouseDrag(input);
        }

        /** Fires one screen-level mouse release; answers the consumption verdict. */
        public boolean fireMouseUp(MouseInput input) {
            SessionSink sink = activeSink();
            return sink != null && sink.mouseUp(input);
        }

        /** Fires one screen-level key press; answers the consumption verdict. */
        public boolean fireKeyPress(KeyInput input) {
            SessionSink sink = activeSink();
            return sink != null && sink.keyPress(input);
        }

        /** Fires one screen-level char typed; answers the consumption verdict. */
        public boolean fireCharTyped(CharInput input) {
            SessionSink sink = activeSink();
            return sink != null && sink.charTyped(input);
        }

        /** Fires the per-frame render callback through a recording canvas. */
        public void fireRender(double mouseX, double mouseY, float partialTick) {
            SessionSink sink = activeSink();
            if (sink == null) {
                return;
            }
            sink.render(new ink.tuanzi.neomacros.port.model.DrawContext2d() {
                @Override
                public void text(int x, int y, String text) {
                    drawn.add("text:" + x + "," + y + "," + text);
                }

                @Override
                public void text(int x, int y, String text, int color) {
                    drawn.add("text:" + x + "," + y + "," + text + ":" + color);
                }

                @Override
                public void rect(int x, int y, int width, int height, int color) {
                    drawn.add("rect:" + x + "," + y + "," + width + "," + height);
                }

                @Override
                public void outline(int x, int y, int width, int height, int color) {
                    drawn.add("outline:" + x + "," + y + "," + width + "," + height);
                }

                @Override
                public void sprite(int x, int y, int width, int height, String textureId) {
                    drawn.add("sprite:" + textureId);
                }
            }, mouseX, mouseY, partialTick);
        }

        /** Fires one control interaction ({@code click}/{@code change}). */
        public void fireControlEvent(String controlId, String event,
                                     java.util.Map<String, Object> payload) {
            SessionSink sink = activeSink();
            if (sink != null) {
                sink.controlEvent(controlId, event, payload);
            }
        }

        /** Pushes a live control state into the facade cache. */
        public void fireStateChanged(String controlId, ControlState state) {
            SessionSink sink = activeSink();
            if (sink != null) {
                sink.stateChanged(controlId, state);
            }
        }

        /** Fires the terminal close notification (replacement path). */
        public void fireClosed() {
            long session = active;
            SessionSink sink = activeSink();
            if (sink != null) {
                sink.closed();
            }
            if (session >= 0) {
                active = -1L;
            }
        }

        private SessionSink activeSink() {
            long session = active;
            return session >= 0 ? sinks.get(session) : null;
        }

        @SuppressWarnings("unchecked")
        private static java.util.Map<String, Object> castMap(java.util.Map<?, ?> map) {
            return (java.util.Map<String, Object>) map;
        }

        private static ControlState stateOf(java.util.Map<String, Object> control) {
            String id = control.get("id") instanceof String text ? text : "";
            String type = control.get("type") instanceof String text ? text : "";
            return new ControlState(id, type,
                    control.get("x") instanceof Number n ? n.intValue() : 0,
                    control.get("y") instanceof Number n ? n.intValue() : 0,
                    control.get("width") instanceof Number n ? n.intValue() : 0,
                    control.get("height") instanceof Number n ? n.intValue() : 0,
                    control.get("z") instanceof Number n ? n.intValue() : 0,
                    control.get("label") instanceof String text ? text : "",
                    !(control.get("active") instanceof Boolean a) || a,
                    !(control.get("visible") instanceof Boolean v) || v,
                    control.get("checked") instanceof Boolean c ? c : null,
                    control.get("value") instanceof Number n ? n.doubleValue() : null,
                    control.get("text") instanceof String text ? text : null,
                    control.get("locked") instanceof Boolean l ? l : null);
        }
    }

    private static final class AtomicBooleanHolder {
        boolean value;
    }
}
