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
import ink.tuanzi.neomacros.port.model.ChatComponent;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;

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

    @Override
    public EntityPort entity() {
        return entity;
    }

    @Override
    public ink.tuanzi.neomacros.port.InputPort input() {
        return input;
    }

    @Override
    public ink.tuanzi.neomacros.port.InventoryPort inventory() {
        return inventory;
    }

    /** The test-facing inventory port: reads canned data, records actions. */
    public ink.tuanzi.neomacros.port.InventoryPort inventoryPort() {
        return inventory;
    }

    private final StubInventory inventory = new StubInventory();

    /** Inventory port stub serving canned hotbar/main data. */
    public static final class StubInventory implements ink.tuanzi.neomacros.port.InventoryPort {
        @Override
        public java.util.function.Supplier<java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData>> heldItem() {
            return () -> java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData> slot(int index) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.function.Supplier<List<IndexedStack>> allItems() {
            return List::of;
        }

        @Override
        public int countOf(String item) {
            return 0;
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

        /** Delivers one displayed message to the receive sinks. */
        public void fireReceive(String text) {
            ChatComponent message = ChatComponent.plain(text);
            for (Consumer<ChatComponent> sink : receiveSinks) {
                sink.accept(message);
            }
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
    }

    /** World data over canned state; tests mutate the fields directly. */
    public static final class StubWorld implements WorldPort {
        long time;
        String dimension = "";
        boolean raining;
        BlockData blockAt;
        BlockInteraction raycastHit;

        /** Sets the canned block returned for any blockAt query. */
        public void setBlock(BlockData block) {
            this.blockAt = block;
        }

        /** Sets the canned raycast result (null = miss). */
        public void setRaycastHit(BlockInteraction hit) {
            this.raycastHit = hit;
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
                    new ink.tuanzi.neomacros.port.model.RayHit(
                            new BlockData(hit.id(), hit.pos()), null, hit.pos(), 0));
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
    }

    /** Local player state over one settable snapshot plus fireable interactions. */
    public static final class StubPlayer implements PlayerPort {
        volatile PlayerSnapshot self;
        volatile List<ink.tuanzi.neomacros.port.model.EffectSnapshot> effects = List.of();
        final List<Function<BlockInteraction, Boolean>> attackBlockSinks = new CopyOnWriteArrayList<>();
        final List<Function<EntitySnapshot, Boolean>> attackEntitySinks = new CopyOnWriteArrayList<>();
        final List<Function<BlockInteraction, Boolean>> interactBlockSinks = new CopyOnWriteArrayList<>();
        final List<Function<EntitySnapshot, Boolean>> interactEntitySinks = new CopyOnWriteArrayList<>();
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
        public java.util.concurrent.CompletableFuture<Void> useEntity(EntitySnapshot target) {
            return record("useEntity:" + target.uuid());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> useBlock(BlockInteraction hit) {
            return record("useBlock:" + hit.pos().x() + "," + hit.pos().y() + "," + hit.pos().z());
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> swingHand() {
            return record("swing");
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
