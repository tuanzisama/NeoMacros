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
import ink.tuanzi.neomacros.port.model.BossBarSnapshot;
import ink.tuanzi.neomacros.port.model.ChunkData;
import ink.tuanzi.neomacros.port.model.LightData;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ScoreboardData;
import ink.tuanzi.neomacros.port.model.TabListData;
import ink.tuanzi.neomacros.port.model.TpsData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    /** The test-facing input port: fires key edges and wheel events. */
    public StubInput inputPort() {
        return input;
    }

    /** Input port recording key-press sinks and firing wheel events. */
    public static final class StubInput implements ink.tuanzi.neomacros.port.InputPort {

        private volatile java.util.function.Function<ink.tuanzi.neomacros.port.model.KeyPressEdge, Boolean>
                keyPressSink;
        private volatile java.util.function.Consumer<Runnable> loopDispatcher = Runnable::run;
        private final List<java.util.function.Consumer<Double>> scrollSinks = new java.util.concurrent.CopyOnWriteArrayList<>();

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

    private final ink.tuanzi.neomacros.port.ClientPort clientPort =
            new ink.tuanzi.neomacros.port.ClientPort() {
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
            };

    @Override
    public EntityPort entity() {
        return entity;
    }

    @Override
    public ink.tuanzi.neomacros.port.ClientPort client() {
        return clientPort;
    }

    public ink.tuanzi.neomacros.port.InputPort input() {
        return input;
    }

    @Override
    public ink.tuanzi.neomacros.port.InventoryPort inventory() {
        return inventory;
    }

    @Override
    public ink.tuanzi.neomacros.port.DialogPort dialog() {
        return new ink.tuanzi.neomacros.port.DialogPort() {
        };
    }

    /** The test-facing inventory port: reads canned data, records actions. */
    public StubInventory inventoryPort() {
        return inventory;
    }

    private final StubInventory inventory = new StubInventory();

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
        final List<String> actions = new CopyOnWriteArrayList<>();

        @Override
        public java.util.Optional<ContainerInfo> openedContainer() {
            return opened;
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

    @Override
    public RenderPort render() {
        throw new UnsupportedPortFeature("render");
    }

    private final StubDraw3D draw3dPort = new StubDraw3D();

    @Override
    public Draw3DPort draw3d() {
        return draw3dPort;
    }

    /** World-space shape recorder for assertions. */
    public static final class StubDraw3D implements Draw3DPort {
        /** One recorded shape. */
        public record Shape(String kind, String layerId, Pos3d a, Pos3d b, int color) {
        }

        final List<Shape> shapes = new CopyOnWriteArrayList<>();

        @Override
        public void drawBox(String layerId, Pos3d min, Pos3d max, int argbColor) {
            shapes.add(new Shape("box", layerId, min, max, argbColor));
        }

        @Override
        public void drawLine(String layerId, Pos3d from, Pos3d to, int argbColor) {
            shapes.add(new Shape("line", layerId, from, to, argbColor));
        }

        @Override
        public void addTraceLine(String layerId, java.util.UUID entityUuid, int argbColor) {
            shapes.add(new Shape("trace", layerId,
                    new Pos3d(0, 0, 0), new Pos3d(0, 0, 0), argbColor));
        }

        @Override
        public void drawSurface(String layerId, java.util.List<Pos3d> points, int argbColor) {
            shapes.add(new Shape("surface", layerId,
                    points.isEmpty() ? new Pos3d(0, 0, 0) : points.get(0),
                    points.size() > 1 ? points.get(points.size() - 1) : new Pos3d(0, 0, 0),
                    argbColor));
        }

        @Override
        public void clearLayer(String layerId) {
            shapes.removeIf(shape -> shape.layerId().equals(layerId));
        }

        @Override
        public java.util.function.Supplier<List<String>> layerIds() {
            return () -> shapes.stream().map(Shape::layerId).distinct().toList();
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

        /** Sets the in-world answer. */
        public void setInWorld(boolean value) {
            inWorld.value = value;
        }

        @Override
        public void onTick(Consumer<Long> sink) {
            tickSinks.add(sink);
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
        TabListData tabList;
        ScoreboardData scoreboard;
        TpsData tps;
        ChunkData chunk;
        public Pos3d respawnPos;
        public LightData light;
        public final java.util.List<Pos3d> foundBlocks = new ArrayList<>();
        /** Paths recorded by playSoundFile, in call order. */
        public final java.util.List<String> playedSoundFiles = new ArrayList<>();
        /** Volume recorded by the last playSoundFile call. */
        public double lastSoundFileVolume;
        final List<BossBarSnapshot> bossBars = new ArrayList<>();
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

        /** Sets the canned respawn position (null = no world). */
        public void setRespawnPos(Pos3d pos) {
            this.respawnPos = pos;
        }

        /** Sets the canned light levels (null = no world). */
        public void setLight(LightData data) {
            this.light = data;
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
        public Optional<TabListData> tabList() {
            return Optional.ofNullable(tabList);
        }

        @Override
        public List<BossBarSnapshot> bossBars() {
            return List.copyOf(bossBars);
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

        /** Replaces the canned entity population. */
        public void setEntities(List<EntitySnapshot> snapshots) {
            entities.clear();
            entities.addAll(snapshots);
        }

        @Override
        public Optional<EntitySnapshot> byUuid(UUID uuid) {
            return entities.stream().filter(e -> e.uuid().equals(uuid)).findFirst();
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

        /** Sets the active status effect list served to the wiring. */
        public void setEffects(List<ink.tuanzi.neomacros.port.model.EffectSnapshot> effects) {
            this.effects = List.copyOf(effects);
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

    private static final class AtomicBooleanHolder {
        boolean value;
    }
}
