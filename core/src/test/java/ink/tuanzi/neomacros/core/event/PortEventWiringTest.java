package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.InventoryPort.ClickSlotInfo;
import ink.tuanzi.neomacros.port.InventoryPort.ContainerInfo;
import ink.tuanzi.neomacros.port.InventoryPort.DropSlotInfo;
import ink.tuanzi.neomacros.port.InventoryPort.HeldUpdateInfo;
import ink.tuanzi.neomacros.port.InventoryPort.ItemPickupInfo;
import ink.tuanzi.neomacros.port.InventoryPort.SlotUpdateInfo;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        var healthPayload = (ink.tuanzi.neomacros.core.event.def.player.PlayerHealthEvent)
                published.stream().filter(p -> p.name().equals("player:health"))
                        .findFirst().orElseThrow().payload();
        assertEquals(-0.5, healthPayload.change(), 0.0001,
                "player:health carries the signed delta against the previous sample");
        assertTrue(published.stream().anyMatch(p -> p.name().equals("player:hunger")));

        // Death edge: the payload names the death block and the inventory snapshot.
        published.clear();
        adapter.inventoryItems = List.of(new InventoryPort.IndexedStack(3,
                new ink.tuanzi.neomacros.port.model.ItemStackData(
                        "minecraft:diamond_sword", 1, "Sword", 0, 1561, List.of(), List.of(),
                        java.util.Map.of())));
        adapter.playerPort.self = snapshot(0.0);
        fireTicks(adapter, clock, 1);
        var death = published.stream()
                .filter(p -> p.name().equals("player:death")).findFirst().orElseThrow();
        var deathPayload = (ink.tuanzi.neomacros.core.event.def.player.PlayerDeathEvent)
                death.payload();
        assertEquals(64.0, deathPayload.deathPos().getY(), 0.0001);
        assertEquals(1, deathPayload.inventory().size());
        assertEquals("minecraft:diamond_sword", deathPayload.inventory().get(0).id());

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
    void titleDisplayPublishesAndCarriesTextVerdict() {
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-title")), new TickClock());
        var published = new ArrayList<Published>();
        recordAll(published, bus, catalog);

        bus.onSync("chat:title", (payload, flag) ->
                ((ink.tuanzi.neomacros.core.event.EventPayloadEnvelope) payload).hooks()
                        .write("text", "replaced"));
        var verdict = adapter.chatPort.fireTitle("subtitle", "original");
        assertFalse(verdict.canceled());
        assertEquals("replaced", verdict.text().text());
        assertEquals("chat:title", published.get(0).name());
        // Async observers see the final mutated state (sequential visibility).
        @SuppressWarnings("unchecked")
        var state = (java.util.Map<String, Object>) published.get(0).payload();
        assertEquals("subtitle", state.get("kind"));
        assertEquals("replaced", state.get("text"));
        runtime.close();
    }

    @Test
    void actionbarVerdictCancelsAndKeepsSource() {
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-bar")), new TickClock());
        var published = new ArrayList<Published>();
        recordAll(published, bus, catalog);

        bus.onSync("chat:actionbar", (payload, flag) -> flag.cancel());
        var verdict = adapter.chatPort.fireActionbar("local", "hello");
        assertTrue(verdict.canceled());
        assertEquals("chat:actionbar", published.get(0).name());
        var payload = (ink.tuanzi.neomacros.core.event.def.chat.ChatActionbarEvent) published.get(0).payload();
        assertEquals("local", payload.source());
        assertEquals("hello", payload.text().text());
        runtime.close();
    }

    @Test
    void signEditVerdictReplacesLinesAndClosesScreen() {
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-sign")), new TickClock());
        var published = new ArrayList<Published>();
        recordAll(published, bus, catalog);

        bus.onSync("screen:signEdit", (payload, flag) -> {
            var envelope = (ink.tuanzi.neomacros.core.event.EventPayloadEnvelope) payload;
            envelope.hooks().write("lines", java.util.List.of("a", "b", "", ""));
            envelope.hooks().write("closeScreen", true);
        });
        var verdict = adapter.playerPort.fireSignEdit(new ink.tuanzi.neomacros.port.model.SignEditData(
                1, 64, 2, true, java.util.List.of("", "", "", "")));
        assertFalse(verdict.canceled());
        assertTrue(verdict.closeScreen());
        assertEquals(java.util.List.of("a", "b", "", ""), verdict.lines());
        assertEquals("screen:signEdit", published.get(0).name());
        // Async observers see the final mutated state (sequential visibility).
        @SuppressWarnings("unchecked")
        var state = (java.util.Map<String, Object>) published.get(0).payload();
        assertEquals(1, state.get("x"));
        assertEquals(Boolean.TRUE, state.get("front"));
        assertEquals(java.util.List.of("a", "b", "", ""), state.get("lines"));
        var signPos = (ink.tuanzi.neomacros.core.api.types.BlockPos) state.get("pos");
        assertEquals(1.0, signPos.getX(), 0.0001);
        assertEquals(64.0, signPos.getY(), 0.0001);
        assertEquals(2.0, signPos.getZ(), 0.0001);
        runtime.close();
    }

    @Test
    void signEditWithoutSubscriberStaysPassThrough() {
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-sign2")), new TickClock());

        var verdict = adapter.playerPort.fireSignEdit(new ink.tuanzi.neomacros.port.model.SignEditData(
                0, 0, 0, false, java.util.List.of("keep", "", "", "")));
        assertFalse(verdict.canceled());
        assertFalse(verdict.closeScreen());
        assertEquals(null, verdict.lines());
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
        assertEquals(13.0f, damagePayload.health(), 0.001f,
                "entity:damage carries the post-damage health");

        // Health recovery fires entity:heal; the local player never appears.
        published.clear();
        adapter.entityPort.nearby = List.of(entity(zombie, 15.0));
        fireTicks(adapter, clock, 1);
        var heal = published.stream()
                .filter(p -> p.name().equals("entity:heal")).findFirst().orElseThrow();
        var healPayload = (ink.tuanzi.neomacros.core.event.def.entity.EntityHealEvent)
                heal.payload();
        assertEquals(zombie, healPayload.entity().uuid());
        assertEquals(15.0f, healPayload.health(), 0.001f,
                "entity:heal carries the post-heal health");

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
        assertEquals(null, gainPayload.previousEffect(),
                "a gain has no previous effect snapshot");

        // An amplifier change fires added=true with the new amplifier.
        published.clear();
        adapter.playerPort.effects = List.of(effect("minecraft:speed", 1));
        fireTicks(adapter, clock, 1);
        assertEquals(1, published.stream().filter(p -> p.name().equals("player:effect")).count());
        var upgrade = (ink.tuanzi.neomacros.core.event.def.player.PlayerEffectEvent)
                published.stream().filter(p -> p.name().equals("player:effect"))
                        .findFirst().orElseThrow().payload();
        assertEquals(0, upgrade.previousEffect().amplifier(),
                "the upgrade reports the replaced amplifier");

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
        assertEquals(1, lossPayload.previousEffect().amplifier(),
                "the removal reports the removed effect snapshot");

        // No change publishes nothing.
        published.clear();
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().noneMatch(p -> p.name().equals("player:effect")),
                "events were: " + names(published));
        runtime.close();
    }

    @Test
    void expHeldItemArmorAndRidingCarryTheirNewFields() {
        var published = new ArrayList<Published>();
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var clock = new TickClock();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-l")), clock);
        recordAll(published, bus, catalog);

        // The first sample only establishes the baselines.
        adapter.playerPort.self = snapshot(1.0);
        adapter.held = Optional.of(stack("minecraft:stick", 0));
        adapter.offhand = stack("minecraft:torch", 0);
        adapter.playerPort.armor = List.of(stack("minecraft:iron_boots", 0),
                stack("minecraft:air", 0), stack("minecraft:iron_chestplate", 0),
                stack("minecraft:air", 0));
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().noneMatch(p -> p.name().equals("player:armor")
                        || p.name().equals("player:heldItem")
                        || p.name().equals("player:exp")),
                "the first sample publishes no change edge: " + names(published));

        // Experience carries the total and the previous triple.
        adapter.playerPort.expLevel = 5;
        adapter.playerPort.expProgress = 0.25f;
        adapter.playerPort.expTotal = 120;
        fireTicks(adapter, clock, 1);
        var exp = (ink.tuanzi.neomacros.core.event.def.player.PlayerExpEvent)
                lastPayload(published, "player:exp");
        assertEquals(5, exp.level());
        assertEquals(0.25f, exp.progress(), 0.0001f);
        assertEquals(120, exp.total());
        assertEquals(5, exp.change());
        assertEquals(0, exp.prevLevel());
        assertEquals(0.0f, exp.prevProgress(), 0.0001f);
        assertEquals(0, exp.prevTotal());

        // Armor reports the changed slot with both stacks around the change.
        adapter.playerPort.armor = List.of(stack("minecraft:iron_boots", 0),
                stack("minecraft:air", 0), stack("minecraft:diamond_chestplate", 0),
                stack("minecraft:air", 0));
        fireTicks(adapter, clock, 1);
        var armor = (ink.tuanzi.neomacros.core.event.def.player.PlayerArmorEvent)
                lastPayload(published, "player:armor");
        assertEquals("chest", armor.slot());
        assertEquals("minecraft:diamond_chestplate", armor.item().id());
        assertEquals("minecraft:iron_chestplate", armor.oldItem().id());
        assertEquals(1, published.stream().filter(p -> p.name().equals("player:armor")).count());

        // The main hand and the off hand are tracked separately.
        adapter.held = Optional.of(stack("minecraft:diamond_sword", 0));
        fireTicks(adapter, clock, 1);
        var mainHand = (ink.tuanzi.neomacros.core.event.def.player.PlayerHeldItemEvent)
                lastPayload(published, "player:heldItem");
        assertFalse(mainHand.offHand());
        assertEquals("minecraft:stick", mainHand.previous().id());
        assertEquals("minecraft:diamond_sword", mainHand.current().id());

        adapter.offhand = stack("minecraft:shield", 0);
        fireTicks(adapter, clock, 1);
        var offHand = (ink.tuanzi.neomacros.core.event.def.player.PlayerHeldItemEvent)
                lastPayload(published, "player:heldItem");
        assertTrue(offHand.offHand());
        assertEquals("minecraft:torch", offHand.previous().id());
        assertEquals("minecraft:shield", offHand.current().id());

        // Riding carries the vehicle and the vehicle left behind.
        var mount = UUID.randomUUID();
        adapter.entityPort.nearby = List.of(entity(mount, 20.0));
        adapter.playerPort.vehicle = mount;
        fireTicks(adapter, clock, 1);
        var mountEvent = (ink.tuanzi.neomacros.core.event.def.player.PlayerRidingEvent)
                lastPayload(published, "player:riding");
        assertEquals(mount, mountEvent.vehicle().uuid());
        assertEquals(null, mountEvent.previousVehicle());

        adapter.playerPort.vehicle = null;
        fireTicks(adapter, clock, 1);
        var dismountEvent = (ink.tuanzi.neomacros.core.event.def.player.PlayerRidingEvent)
                lastPayload(published, "player:riding");
        assertEquals(null, dismountEvent.vehicle());
        assertEquals(mount, dismountEvent.previousVehicle().uuid());
        runtime.close();
    }

    /** The payload of the most recently published event with that name. */
    private static Object lastPayload(List<Published> published, String name) {
        List<Published> matches = published.stream()
                .filter(p -> p.name().equals(name)).toList();
        if (matches.isEmpty()) {
            throw new AssertionError("no " + name + " event published");
        }
        return matches.get(matches.size() - 1).payload();
    }

    private static ink.tuanzi.neomacros.port.model.ItemStackData stack(String id, int damage) {
        return new ink.tuanzi.neomacros.port.model.ItemStackData(id, 1, id, damage, 0,
                List.of(), List.of(), java.util.Map.of());
    }

    @Test
    void screenAndContainerTransitionsPublish() {
        var published = new ArrayList<Published>();
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var clock = new TickClock();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-f")), clock);
        recordAll(published, bus, catalog);

        adapter.playerPort.self = snapshot(1.0);
        fireTicks(adapter, clock, 1);

        // A screen opens: screen:open and screen:change(open) fire together.
        published.clear();
        adapter.lifecyclePort.screen = java.util.Optional.of(
                new LifecyclePort.ScreenInfo("ChatScreen", "chat", "Chat"));
        fireTicks(adapter, clock, 1);
        var open = published.stream()
                .filter(p -> p.name().equals("screen:open")).findFirst().orElseThrow();
        assertEquals("ChatScreen",
                ((ink.tuanzi.neomacros.core.event.def.screen.ScreenOpenEvent) open.payload()).screen());
        assertTrue(published.stream().anyMatch(p -> p.name().equals("screen:change")
                && "open".equals(((ink.tuanzi.neomacros.core.event.def.screen.ScreenChangeEvent) p.payload()).phase())),
                "events were: " + names(published));

        // A container opens on top: inventory:open carries kind, menu id,
        // and the MC class name of the opening screen.
        published.clear();
        adapter.lifecyclePort.screen = java.util.Optional.of(
                new LifecyclePort.ScreenInfo("GenericContainerScreen", "chest", "Chest"));
        adapter.container = java.util.Optional.of(new ContainerInfo("Chest", "chest", 27, 1));
        fireTicks(adapter, clock, 1);
        var containerOpen = published.stream()
                .filter(p -> p.name().equals("inventory:open")).findFirst().orElseThrow();
        var openPayload = (ink.tuanzi.neomacros.core.event.def.inventory.InventoryOpenEvent) containerOpen.payload();
        assertEquals("chest", openPayload.kind());
        assertEquals(1, openPayload.containerId());
        assertEquals("GenericContainerScreen", openPayload.screen());

        // The container closes: inventory:close keeps the last kind.
        published.clear();
        adapter.container = java.util.Optional.empty();
        fireTicks(adapter, clock, 1);
        var containerClose = published.stream()
                .filter(p -> p.name().equals("inventory:close")).findFirst().orElseThrow();
        assertEquals("chest",
                ((ink.tuanzi.neomacros.core.event.def.inventory.InventoryCloseEvent) containerClose.payload()).kind());

        // The screen closes: screen:close and screen:change(close) fire.
        published.clear();
        adapter.lifecyclePort.screen = java.util.Optional.empty();
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().anyMatch(p -> p.name().equals("screen:close")),
                "events were: " + names(published));
        assertTrue(published.stream().anyMatch(p -> p.name().equals("screen:change")
                && "close".equals(((ink.tuanzi.neomacros.core.event.def.screen.ScreenChangeEvent) p.payload()).phase())),
                "events were: " + names(published));
        runtime.close();
    }

    @Test
    void inventoryClickInterceptsAndCarriesTheVerdict() {
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var clock = new TickClock();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-g")), clock);

        bus.onSync("inventory:click", (payload, flag) -> {
            var state = (java.util.Map<?, ?>) ((EventPayloadEnvelope) payload).payload();
            if (state.get("slot").equals(5) && "throw".equals(state.get("mode"))) {
                flag.cancel();
            }
        });

        boolean canceled = adapter.inventorySinks.fireClick(new ClickSlotInfo(
                1, "chest", 5, 1, "throw", false));
        assertTrue(canceled, "a matching click should be cancelled");

        boolean passes = adapter.inventorySinks.fireClick(new ClickSlotInfo(
                1, "chest", 6, 0, "pickup", false));
        assertFalse(passes, "a non-matching click stays confirmed");
        runtime.close();
    }

    @Test
    void inventoryDropInterceptsAndCarriesTheVerdict() {
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var clock = new TickClock();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-h")), clock);

        bus.onSync("inventory:dropSlot", (payload, flag) -> {
            var state = (java.util.Map<?, ?>) ((EventPayloadEnvelope) payload).payload();
            if (Boolean.TRUE.equals(state.get("all")) && state.get("slot").equals(40)) {
                flag.cancel();
            }
        });

        assertTrue(adapter.inventorySinks.fireDrop(new DropSlotInfo("player", 40, true)));
        assertFalse(adapter.inventorySinks.fireDrop(new DropSlotInfo("player", 40, false)));
        runtime.close();
    }

    @Test
    void inventorySyncsAndPickupPublishTheirPayloads() {
        var published = new ArrayList<Published>();
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var clock = new TickClock();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-i")), clock);
        recordAll(published, bus, catalog);

        var oldStack = new ink.tuanzi.neomacros.port.model.ItemStackData(
                "minecraft:diamond_pickaxe", 1, "Pickaxe", 10, 1561, List.of(), List.of(),
                java.util.Map.of());
        var newStack = new ink.tuanzi.neomacros.port.model.ItemStackData(
                "minecraft:diamond_pickaxe", 1, "Pickaxe", 11, 1561, List.of(), List.of(),
                java.util.Map.of());
        adapter.lifecyclePort.screen = java.util.Optional.of(
                new LifecyclePort.ScreenInfo("GenericContainerScreen", "chest", "Chest"));
        adapter.inventorySinks.fireSlot(new SlotUpdateInfo(1, "chest", 3, false, oldStack, newStack));
        adapter.inventorySinks.fireHeld(new HeldUpdateInfo(1, "chest", newStack));
        adapter.inventorySinks.firePickup(new ItemPickupInfo(newStack, 3));
        // A sync with no screen open reports no screen class.
        adapter.lifecyclePort.screen = java.util.Optional.empty();
        adapter.inventorySinks.fireSlot(new SlotUpdateInfo(1, "chest", 4, false, oldStack, newStack));

        var slotUpdate = published.stream()
                .filter(p -> p.name().equals("inventory:update")).toList();
        assertEquals(3, slotUpdate.size(),
                "slot + held + screenless slot updates; events were: " + names(published));
        var slotPayload = (ink.tuanzi.neomacros.core.event.def.inventory.InventoryUpdateEvent)
                slotUpdate.get(0).payload();
        assertEquals("slot", slotPayload.mode());
        assertEquals(10, slotPayload.oldStack().damage());
        assertEquals(11, slotPayload.newStack().damage());
        assertEquals("GenericContainerScreen", slotPayload.screen());
        var heldPayload = (ink.tuanzi.neomacros.core.event.def.inventory.InventoryUpdateEvent)
                slotUpdate.get(1).payload();
        assertEquals("held", heldPayload.mode());
        assertEquals(-1, heldPayload.slot());
        assertEquals("GenericContainerScreen", heldPayload.screen());
        var screenlessPayload = (ink.tuanzi.neomacros.core.event.def.inventory.InventoryUpdateEvent)
                slotUpdate.get(2).payload();
        assertEquals("slot", screenlessPayload.mode());
        assertEquals(null, screenlessPayload.screen(),
                "no open screen reads as a null (undefined to scripts) screen field");

        var pickup = published.stream().filter(p -> p.name().equals("item:pickup"))
                .findFirst().orElseThrow();
        assertEquals(3, ((ink.tuanzi.neomacros.core.event.def.inventory.ItemPickupEvent)
                pickup.payload()).amount());
        runtime.close();
    }

    @Test
    void cancelledContainerOpenClosesAndKeepsBookkeepingClean() {
        var published = new ArrayList<Published>();
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var clock = new TickClock();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-j")), clock);
        recordAll(published, bus, catalog);
        adapter.playerPort.self = snapshot(20.0);
        fireTicks(adapter, clock, 1);

        bus.onSync("inventory:open", (payload, flag) -> {
            var state = (java.util.Map<?, ?>) ((EventPayloadEnvelope) payload).payload();
            if ("chest".equals(state.get("kind"))) {
                flag.cancel();
            }
        });
        published.clear();
        adapter.container = java.util.Optional.of(new ContainerInfo("Chest", "chest", 27, 1));
        fireTicks(adapter, clock, 1);

        assertTrue(adapter.inventorySinks.actions.contains("close"),
                "a cancelled open closes the container; actions: " + adapter.inventorySinks.actions);
        assertTrue(published.stream().noneMatch(p -> p.name().equals("inventory:close")),
                "no unmatched close follows; events were: " + names(published));

        // The next tick with the container gone emits nothing (bookkeeping cleared).
        published.clear();
        adapter.container = java.util.Optional.empty();
        fireTicks(adapter, clock, 1);
        assertTrue(published.stream().noneMatch(p -> p.name().startsWith("inventory:")),
                "events were: " + names(published));
        runtime.close();
    }

    @Test
    void itemDamagePublishesOnDurabilityOnlyChanges() {
        var published = new ArrayList<Published>();
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = new StubAdapter();
        var clock = new TickClock();
        var runtime = runtime(adapter, bus);
        PortEventWiring.wire(adapter, bus, silentModules(runtime, dir.resolve("scripts-k")), clock);
        recordAll(published, bus, catalog);
        adapter.playerPort.self = snapshot(20.0);

        var held = new ink.tuanzi.neomacros.port.model.ItemStackData(
                "minecraft:diamond_pickaxe", 1, "Pickaxe", 0, 1561, List.of(), List.of(),
                java.util.Map.of());
        adapter.held = java.util.Optional.of(held);
        fireTicks(adapter, clock, 1);
        published.clear();

        adapter.held = java.util.Optional.of(new ink.tuanzi.neomacros.port.model.ItemStackData(
                "minecraft:diamond_pickaxe", 1, "Pickaxe", 2, 1561, List.of(), List.of(),
                java.util.Map.of()));
        fireTicks(adapter, clock, 1);
        var damage = published.stream().filter(p -> p.name().equals("item:damage"))
                .findFirst().orElseThrow();
        var payload = (ink.tuanzi.neomacros.core.event.def.inventory.ItemDamageEvent)
                damage.payload();
        assertEquals("main", payload.slot());
        assertEquals(2, payload.damage());
        runtime.close();
    }

    private static EntitySnapshot entity(UUID uuid, double health) {        return new EntitySnapshot(uuid, "minecraft:zombie", "Zombie",
                new Pos3d(1, 64, 1), health);
    }

    private static ink.tuanzi.neomacros.port.model.EffectSnapshot effect(String id, int amplifier) {
        return new ink.tuanzi.neomacros.port.model.EffectSnapshot(id, amplifier, 200);
    }

    private static void recordAll(ArrayList<Published> sink, EventBus bus, EventCatalog catalog) {
        for (var descriptor : catalog.all()) {
            String name = descriptor.name();
            bus.on(name, payload -> sink.add(new Published(name, payload instanceof ink.tuanzi.neomacros.core.event.EventPayloadEnvelope envelope ? envelope.payload() : payload)));
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
        final StubChat chatPort = new StubChat();
        final StubEntity entityPort = new StubEntity();
        final InventorySinks inventorySinks = new InventorySinks();
        volatile java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData> held =
                java.util.Optional.empty();
        volatile ink.tuanzi.neomacros.port.model.ItemStackData offhand;
        volatile List<InventoryPort.IndexedStack> inventoryItems = List.of();
        volatile java.util.Optional<ink.tuanzi.neomacros.port.InventoryPort.ContainerInfo> container =
                java.util.Optional.empty();

        @Override
        public LifecyclePort lifecycle() {
            return lifecyclePort;
        }

        @Override
        public ChatPort chat() {
            return chatPort;
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
                public java.util.Optional<ContainerInfo> openedContainer() {
                    return container;
                }

                @Override
                public java.util.function.Supplier<java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData>> heldItem() {
                    return () -> held;
                }

                @Override
                public java.util.Optional<ink.tuanzi.neomacros.port.model.ItemStackData> slot(int index) {
                    return index == 40 ? java.util.Optional.ofNullable(offhand)
                            : java.util.Optional.empty();
                }

                @Override
                public java.util.function.Supplier<List<IndexedStack>> allItems() {
                    return () -> inventoryItems;
                }

                @Override
                public int countOf(String item) {
                    return 0;
                }

                @Override
                public void onClickSlot(java.util.function.Function<ClickSlotInfo, Boolean> sink) {
                    inventorySinks.click.add(sink);
                }

                @Override
                public void onDropSlot(java.util.function.Function<DropSlotInfo, Boolean> sink) {
                    inventorySinks.drop.add(sink);
                }

                @Override
                public void onSlotUpdate(java.util.function.Consumer<SlotUpdateInfo> sink) {
                    inventorySinks.slot.add(sink);
                }

                @Override
                public void onHeldUpdate(java.util.function.Consumer<HeldUpdateInfo> sink) {
                    inventorySinks.held.add(sink);
                }

                @Override
                public void onItemPickup(java.util.function.Consumer<ItemPickupInfo> sink) {
                    inventorySinks.pickup.add(sink);
                }

                @Override
                public java.util.concurrent.CompletableFuture<Void> close() {
                    inventorySinks.actions.add("close");
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
            };
        }

        /** Stable sink collectors the tests fire through. */
        static final class InventorySinks {
            final List<java.util.function.Function<ClickSlotInfo, Boolean>> click =
                    new java.util.ArrayList<>();
            final List<java.util.function.Function<DropSlotInfo, Boolean>> drop =
                    new java.util.ArrayList<>();
            final List<java.util.function.Consumer<SlotUpdateInfo>> slot =
                    new java.util.ArrayList<>();
            final List<java.util.function.Consumer<HeldUpdateInfo>> held =
                    new java.util.ArrayList<>();
            final List<java.util.function.Consumer<ItemPickupInfo>> pickup =
                    new java.util.ArrayList<>();
            final List<String> actions = new java.util.ArrayList<>();

            boolean fireClick(ClickSlotInfo info) {
                boolean canceled = false;
                for (var sink : click) {
                    if (Boolean.TRUE.equals(sink.apply(info))) {
                        canceled = true;
                    }
                }
                return canceled;
            }

            boolean fireDrop(DropSlotInfo info) {
                boolean canceled = false;
                for (var sink : drop) {
                    if (Boolean.TRUE.equals(sink.apply(info))) {
                        canceled = true;
                    }
                }
                return canceled;
            }

            void fireSlot(SlotUpdateInfo info) {
                for (var sink : slot) {
                    sink.accept(info);
                }
            }

            void fireHeld(HeldUpdateInfo info) {
                for (var sink : held) {
                    sink.accept(info);
                }
            }

            void firePickup(ItemPickupInfo info) {
                for (var sink : pickup) {
                    sink.accept(info);
                }
            }
        }

        @Override
        public ink.tuanzi.neomacros.port.ClientPort client() {
            return new ink.tuanzi.neomacros.port.ClientPort() {
                public int fps() { return 60; }

                public String gameVersion() { return "26.3-stub"; }

                public Object minecraftInstance() { return null; }
            };
        }

        @Override
        public ink.tuanzi.neomacros.port.DialogPort dialog() {
            return new ink.tuanzi.neomacros.port.DialogPort() {
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
        volatile java.util.Optional<ScreenInfo> screen = java.util.Optional.empty();

        void fireTick(long tick) {
            ticks.forEach(s -> s.accept(tick));
        }

        void fireWorldLeave() {
            leaves.forEach(Runnable::run);
        }

        @Override
        public java.util.Optional<ScreenInfo> screenInfo() {
            return screen;
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
        final List<Function<ink.tuanzi.neomacros.port.model.TitleDisplayData,
                ink.tuanzi.neomacros.port.model.TextVerdict>> titleSinks = new CopyOnWriteArrayList<>();
        final List<Function<ink.tuanzi.neomacros.port.model.ActionbarDisplayData,
                ink.tuanzi.neomacros.port.model.TextVerdict>> actionbarSinks = new CopyOnWriteArrayList<>();

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

        /** Fires one title/subtitle display; returns the combined verdict. */
        ink.tuanzi.neomacros.port.model.TextVerdict fireTitle(String kind, String text) {
            var display = new ink.tuanzi.neomacros.port.model.TitleDisplayData(
                    kind, ChatComponent.plain(text));
            boolean canceled = false;
            ChatComponent replaced = null;
            for (var sink : titleSinks) {
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
        ink.tuanzi.neomacros.port.model.TextVerdict fireActionbar(String source, String text) {
            var display = new ink.tuanzi.neomacros.port.model.ActionbarDisplayData(
                    ChatComponent.plain(text), source);
            boolean canceled = false;
            ChatComponent replaced = null;
            for (var sink : actionbarSinks) {
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
        public java.util.concurrent.CompletableFuture<Void> useEntity(EntitySnapshot target,
                String hand) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> useBlock(BlockInteraction hit,
                String hand) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> useItem(String hand) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> attackBlock(BlockInteraction hit) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> stopBreaking() {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> swingHand(String hand) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> dropHeld(boolean all) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        volatile PlayerSnapshot self;
        volatile List<ink.tuanzi.neomacros.port.model.EffectSnapshot> effects = List.of();
        volatile int expLevel;
        volatile float expProgress;
        volatile int expTotal;
        volatile List<ink.tuanzi.neomacros.port.model.ItemStackData> armor = List.of();
        volatile UUID vehicle;
        final List<Function<BlockInteraction, Boolean>> attackBlocks = new CopyOnWriteArrayList<>();
        final List<Function<EntitySnapshot, Boolean>> attackEntities = new CopyOnWriteArrayList<>();
        final List<Function<BlockInteraction, Boolean>> interactBlocks = new CopyOnWriteArrayList<>();
        final List<Function<EntitySnapshot, Boolean>> interactEntities = new CopyOnWriteArrayList<>();
        final List<Function<ink.tuanzi.neomacros.port.model.SignEditData,
                ink.tuanzi.neomacros.port.model.SignEditVerdict>> signEditSinks = new CopyOnWriteArrayList<>();

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

        @Override
        public void onSignEdit(Function<ink.tuanzi.neomacros.port.model.SignEditData,
                ink.tuanzi.neomacros.port.model.SignEditVerdict> sink) {
            signEditSinks.add(sink);
        }

        boolean fireAttackBlock(BlockInteraction hit) {
            return attackBlocks.stream().anyMatch(s -> Boolean.TRUE.equals(s.apply(hit)));
        }

        /** Fires the sign-edit open; returns the combined verdict. */
        ink.tuanzi.neomacros.port.model.SignEditVerdict fireSignEdit(
                ink.tuanzi.neomacros.port.model.SignEditData data) {
            boolean canceled = false;
            boolean closeScreen = false;
            List<String> replaced = null;
            for (var sink : signEditSinks) {
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
    }
}
