package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.runtime.ScriptEngine;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.TickClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the shared adapter-to-bus event wiring: lifecycle publishes,
 * chat receive, cancellable interaction verdicts, and the per-tick derived
 * player/world event family.
 */
class PortEventWiringTest {

    @TempDir
    Path dir;

    private static final EventCatalog catalog = new EventCatalog();

    static {
        catalog.registerAll(ProductionEventSet.records());
    }

    private record Published(String name, Object payload) {
    }

    /** Builds a real module manager over a no-op engine (no scripts to load). */
    private static ModuleManager silentModules(ScriptRuntime runtime, Path dir) {
        return new ModuleManager(runtime, new ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry("26.3"),
                ink.tuanzi.neomacros.core.api.bindgen.PermissionView.ALLOW_ALL, dir, m -> { });
    }

    private static ScriptRuntime runtime(NeomacrosAdapter adapter, EventBus bus) {
        ScriptEngine engine = new ScriptEngine() {
            @Override
            public EngineSession openSession(ScriptContext context, java.nio.file.Path moduleRoot) {
                return new EngineSession() {
                    @Override
                    public void installBindings(java.util.Map<String, Object> bindings) {
                    }

                    @Override
                    public void importModule(String moduleName, Path sourceFile) {
                    }

                    @Override
                    public void close() {
                    }
                };
            }

            @Override
            public void clearModuleCache() {
            }

            @Override
            public void close() {
            }
        };
        var pool = new ink.tuanzi.neomacros.core.thread.ScriptThreadPool(2);
        return new ScriptRuntime(engine, adapter, catalog, bus, pool, 500);
    }

    @Test
    void joinVitalsDeathAndLeaveFlowFromTicks() {
        var published = new ArrayList<Published>();
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var clock = new TickClock();
        var runtime = runtime(adapter, bus);
        var modules = silentModules(runtime, dir.resolve("scripts-a"));
        PortEventWiring.wire(adapter, bus, modules, clock);
        recordAll(published, bus, catalog);

        adapter.playerPort.self = snapshot(1.0);
        adapter.worldPort.time = 100;
        adapter.worldPort.dimension = "minecraft:overworld";
        fireTicks(adapter, clock, 1);

        assertEquals("player:join", published.get(1).name());
        assertEquals("player:spawn", published.get(2).name());
        assertTrue(published.stream().anyMatch(p -> p.name().equals("tick")));
        assertEquals(1, clock.currentTick());

        // Vitals: damage and hunger change in one tick.
        published.clear();
        adapter.playerPort.self = new PlayerSnapshot(UUID.randomUUID(), "tester",
                new Pos3d(0, 64, 0), new Rot2d(0, 0), 0.5, 18, 300);
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().anyMatch(p -> p.name().equals("player:damage")),
                "events were: " + names(published));
        assertTrue(published.stream().anyMatch(p -> p.name().equals("player:health")));
        assertTrue(published.stream().anyMatch(p -> p.name().equals("player:hunger")));

        // Death edge.
        published.clear();
        adapter.playerPort.self = snapshot(0.0);
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().anyMatch(p -> p.name().equals("player:death")),
                "events were: " + names(published));

        // Respawn edge.
        published.clear();
        adapter.playerPort.self = snapshot(1.0);
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().anyMatch(p -> p.name().equals("player:respawn")),
                "events were: " + names(published));

        // Leaving the world emits player:leave through the world-leave sink.
        published.clear();
        adapter.playerPort.self = null;
        adapter.lifecyclePort.fireWorldLeave();
        assertTrue(published.stream().anyMatch(p -> p.name().equals("player:leave")),
                "events were: " + names(published));
        assertTrue(published.stream().anyMatch(p -> p.name().equals("world:leave")));
        runtime.close();
    }

    @Test
    void dimensionChangeAndTimeJumpPublish() {
        var published = new ArrayList<Published>();
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var clock = new TickClock();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-b")), clock);
        recordAll(published, bus, catalog);

        adapter.playerPort.self = snapshot(1.0);
        adapter.worldPort.dimension = "minecraft:overworld";
        adapter.worldPort.time = 1000;
        fireTicks(adapter, clock, 1);

        published.clear();
        adapter.worldPort.dimension = "minecraft:the_nether";
        adapter.worldPort.time = 1500;
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().anyMatch(p -> p.name().equals("player:dimensionChange")),
                "events were: " + names(published));
        assertTrue(published.stream().anyMatch(p -> p.name().equals("world:dimensionChange")),
                "events were: " + names(published));
        // The dimension switch re-bases the time tracker: no jump on this tick.
        assertTrue(published.stream().noneMatch(p -> p.name().equals("world:time")),
                "events were: " + names(published));

        // A discontinuous jump afterwards publishes world:time.
        published.clear();
        adapter.worldPort.time = 2000;
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().anyMatch(p -> p.name().equals("world:time")),
                "events were: " + names(published));

        // Normal one-tick advance never publishes world:time.
        published.clear();
        adapter.worldPort.time = 2001;
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().noneMatch(p -> p.name().equals("world:time")),
                "events were: " + names(published));
        runtime.close();
    }

    @Test
    void interactionSinkPublishesAndReturnsVerdict() {
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-c")), new TickClock());

        var subscription = bus.onSync("player:attackBlock", (payload, flag) -> flag.cancel());
        try {
            boolean canceled = adapter.playerPort.fireAttackBlock(
                    new BlockInteraction("minecraft:stone", new Pos3d(1, 2, 3)));
            assertTrue(canceled, "cancellation verdict must reach the adapter");
        } finally {
            subscription.deactivate();
        }

        // With no sync subscriber the verdict stays uncanceled.
        boolean canceled2 = adapter.playerPort.fireAttackBlock(
                new BlockInteraction("minecraft:dirt", new Pos3d(4, 5, 6)));
        assertEquals(false, canceled2);
        runtime.close();
    }

    @Test
    void entityHealthDiffPublishesDamageAndHeal() {
        var published = new ArrayList<Published>();
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var clock = new TickClock();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-d")), clock);
        recordAll(published, bus, catalog);

        adapter.playerPort.self = snapshot(1.0);
        var zombie = java.util.UUID.randomUUID();
        adapter.entityPort.nearby = List.of(entity(zombie, 20.0));
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().noneMatch(p -> p.name().startsWith("entity:")),
                "first sight must not fire a delta: " + names(published));

        // Health drop fires entity:damage with the positive damage amount.
        published.clear();
        adapter.entityPort.nearby = List.of(entity(zombie, 13.0));
        fireTicks(adapter, clock, 1);
        var damage = published.stream()
                .filter(p -> p.name().equals("entity:damage")).findFirst().orElseThrow();
        var damagePayload = (ink.tuanzi.neomacros.core.event.def.entity.EntityDamageEvent) damage.payload();
        assertEquals(zombie, damagePayload.entity().uuid());
        assertEquals(7.0f, damagePayload.amount(), 0.001f);

        // Health recovery fires entity:heal; the local player never appears.
        published.clear();
        adapter.entityPort.nearby = List.of(entity(zombie, 15.0));
        fireTicks(adapter, clock, 1);
        var heal = published.stream()
                .filter(p -> p.name().equals("entity:heal")).findFirst().orElseThrow();
        assertEquals(zombie,
                ((ink.tuanzi.neomacros.core.event.def.entity.EntityHealEvent) heal.payload())
                        .entity().uuid());

        // The same health twice publishes nothing.
        published.clear();
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().noneMatch(p -> p.name().startsWith("entity:")),
                "events were: " + names(published));

        // Leaving the radius clears tracking; returning replays no delta.
        published.clear();
        adapter.entityPort.nearby = List.of();
        fireTicks(adapter, clock, 1);
        adapter.entityPort.nearby = List.of(entity(zombie, 15.0));
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().noneMatch(p -> p.name().startsWith("entity:")),
                "events were: " + names(published));
        runtime.close();
    }

    @Test
    void effectChangesPublishAddRemoveAndAmplifier() {
        var published = new ArrayList<Published>();
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var clock = new TickClock();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-e")), clock);
        recordAll(published, bus, catalog);

        adapter.playerPort.self = snapshot(1.0);
        fireTicks(adapter, clock, 1);

        // Gaining an effect fires added=true with its amplifier.
        published.clear();
        adapter.playerPort.effects = List.of(effect("minecraft:speed", 0));
        fireTicks(adapter, clock, 1);
        var gain = published.stream()
                .filter(p -> p.name().equals("player:effect")).findFirst().orElseThrow();
        var gainPayload = (ink.tuanzi.neomacros.core.event.def.player.PlayerEffectEvent) gain.payload();
        assertEquals("minecraft:speed", gainPayload.effectId());
        assertTrue(gainPayload.added());
        assertEquals(0, gainPayload.amplifier());

        // An amplifier change fires added=true with the new amplifier.
        published.clear();
        adapter.playerPort.effects = List.of(effect("minecraft:speed", 1));
        fireTicks(adapter, clock, 1);
        assertEquals(1, published.stream().filter(p -> p.name().equals("player:effect")).count());

        // Losing the effect fires added=false with the last known amplifier.
        published.clear();
        adapter.playerPort.effects = List.of();
        fireTicks(adapter, clock, 1);
        var loss = published.stream()
                .filter(p -> p.name().equals("player:effect")).findFirst().orElseThrow();
        var lossPayload = (ink.tuanzi.neomacros.core.event.def.player.PlayerEffectEvent) loss.payload();
        assertEquals("minecraft:speed", lossPayload.effectId());
        assertEquals(false, lossPayload.added());
        assertEquals(1, lossPayload.amplifier());

        // No change publishes nothing.
        published.clear();
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().noneMatch(p -> p.name().equals("player:effect")),
                "events were: " + names(published));
        runtime.close();
    }

    private static EntitySnapshot entity(UUID uuid, double health) {
        return new EntitySnapshot(uuid, "minecraft:zombie", "Zombie",
                new Pos3d(1, 64, 1), health);
    }

    private static ink.tuanzi.neomacros.port.model.EffectSnapshot effect(String id, int amplifier) {
        return new ink.tuanzi.neomacros.port.model.EffectSnapshot(id, amplifier, 200);
    }

    private static void recordAll(ArrayList<Published> sink, EventBus bus, EventCatalog catalog) {
        for (var descriptor : catalog.all()) {
            String name = descriptor.name();
            bus.on(name, payload -> sink.add(new Published(name, payload)));
        }
    }

    private static List<String> names(ArrayList<Published> published) {
        return published.stream().map(Published::name).toList();
    }

    private static void fireTicks(StubAdapter adapter, TickClock clock, int count) {
        for (int i = 0; i < count; i++) {
            adapter.lifecyclePort.fireTick(clock.currentTick() + 1);
        }
    }

    private static PlayerSnapshot snapshot(double health) {
        return new PlayerSnapshot(UUID.randomUUID(), "tester",
                new Pos3d(0, 64, 0), new Rot2d(0, 0), health, 20, 300);
    }

    /** Minimal adapter double exposing mutable canned state. */
    private static final class StubAdapter implements NeomacrosAdapter {
        final StubLifecycle lifecyclePort = new StubLifecycle();
        final StubWorld worldPort = new StubWorld();
        final StubPlayer playerPort = new StubPlayer();
        final StubEntity entityPort = new StubEntity();

        @Override
        public LifecyclePort lifecycle() {
            return lifecyclePort;
        }

        @Override
        public ChatPort chat() {
            return new StubChat();
        }

        @Override
        public WorldPort world() {
            return worldPort;
        }

        @Override
        public EntityPort entity() {
            return entityPort;
        }

        @Override
        public ink.tuanzi.neomacros.port.InventoryPort inventory() {
            return new ink.tuanzi.neomacros.port.InventoryPort() {
                @Override
                public java.util.function.Supplier<java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData>> heldItem() {
                    return java.util.Optional::empty;
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
            };
        }

        @Override

        public ink.tuanzi.neomacros.port.InputPort input() {
            // No-op: the key-press/wheel wiring registers sinks only.
            return new ink.tuanzi.neomacros.port.InputPort() {
                @Override
                public Object registerKeybind(String keyName, java.util.function.Consumer<Integer> sink) {
                    return null;
                }

                @Override
                public void registerKeyPress(String keyName, Runnable sink) {
                }

                @Override
                public void unregister(Object handle) {
                }

                @Override
                public void registerScriptBinding(String scriptName, String keyName,
                        java.util.function.Consumer<Integer> sink) {
                }

                @Override
                public void unregisterScriptBinding(String scriptName) {
                }

                @Override
                public void onScroll(java.util.function.Consumer<Double> sink) {
                }
            };
        }

        @Override
        public ink.tuanzi.neomacros.port.RenderPort render() {
            throw new UnsupportedOperationException("render");
        }

        @Override
        public ink.tuanzi.neomacros.port.Draw3DPort draw3d() {
            throw new UnsupportedOperationException("draw3d");
        }

        @Override
        public PlayerPort player() {
            return playerPort;
        }
    }

    private static final class StubLifecycle implements LifecyclePort {
        final List<Consumer<Long>> ticks = new CopyOnWriteArrayList<>();
        final List<Runnable> leaves = new CopyOnWriteArrayList<>();

        void fireTick(long tick) {
            ticks.forEach(s -> s.accept(tick));
        }

        void fireWorldLeave() {
            leaves.forEach(Runnable::run);
        }

        @Override
        public void onTick(Consumer<Long> sink) {
            ticks.add(sink);
        }

        @Override
        public void onGameStart(Runnable sink) {
        }

        @Override
        public void onGameQuit(Runnable sink) {
        }

        @Override
        public void onWorldJoin(Runnable sink) {
        }

        @Override
        public void onWorldLeave(Runnable sink) {
            leaves.add(sink);
        }

        @Override
        public Supplier<Boolean> inWorld() {
            return () -> false;
        }
    }

    private static final class StubChat implements ChatPort {
        @Override
        public void say(String message) {
        }

        @Override
        public void log(String message) {
        }

        @Override
        public void actionbar(String message) {
        }

        @Override
        public void onChatReceive(Consumer<ChatComponent> sink) {
        }
    }

    private static final class StubWorld implements WorldPort {
        long time;
        String dimension = "";

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
            return false;
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.BlockData> blockAt(Pos3d pos) {
            return Optional.empty();
        }

        @Override
        public Optional<ink.tuanzi.neomacros.port.model.RayHit> raycast(Pos3d origin,
                Pos3d direction, ink.tuanzi.neomacros.port.model.RayOptions options) {
            return Optional.empty();
        }
    }

    private static final class StubEntity implements EntityPort {
        volatile List<EntitySnapshot> nearby = List.of();

        @Override
        public Optional<EntitySnapshot> byUuid(UUID uuid) {
            return nearby.stream().filter(e -> e.uuid().equals(uuid)).findFirst();
        }

        @Override
        public List<EntitySnapshot> inRadius(Pos3d center, double range, java.util.function.Predicate<EntitySnapshot> filter) {
            return nearby.stream().filter(filter).toList();
        }
    }

    private static final class StubPlayer implements PlayerPort {
        @Override
        public java.util.concurrent.CompletableFuture<Void> attack(EntitySnapshot target) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> useEntity(EntitySnapshot target) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> useBlock(BlockInteraction hit) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> swingHand() {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> dropHeld(boolean all) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        volatile PlayerSnapshot self;
        volatile List<ink.tuanzi.neomacros.port.model.EffectSnapshot> effects = List.of();
        final List<Function<BlockInteraction, Boolean>> attackBlocks = new CopyOnWriteArrayList<>();
        final List<Function<EntitySnapshot, Boolean>> attackEntities = new CopyOnWriteArrayList<>();
        final List<Function<BlockInteraction, Boolean>> interactBlocks = new CopyOnWriteArrayList<>();
        final List<Function<EntitySnapshot, Boolean>> interactEntities = new CopyOnWriteArrayList<>();

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
            attackBlocks.add(sink);
        }

        @Override
        public void onAttackEntity(Function<EntitySnapshot, Boolean> sink) {
            attackEntities.add(sink);
        }

        @Override
        public void onInteractBlock(Function<BlockInteraction, Boolean> sink) {
            interactBlocks.add(sink);
        }

        @Override
        public void onInteractEntity(Function<EntitySnapshot, Boolean> sink) {
            interactEntities.add(sink);
        }

        boolean fireAttackBlock(BlockInteraction hit) {
            return attackBlocks.stream().anyMatch(s -> Boolean.TRUE.equals(s.apply(hit)));
        }
    }
}
