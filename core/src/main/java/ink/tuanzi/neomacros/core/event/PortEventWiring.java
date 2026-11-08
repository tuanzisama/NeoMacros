package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.event.def.chat.ChatReceiveEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameQuitEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameStartEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.WorldJoinEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.WorldLeaveEvent;
import ink.tuanzi.neomacros.core.event.def.movement.PlayerGlidingEvent;
import ink.tuanzi.neomacros.core.event.def.movement.PlayerSneakEvent;
import ink.tuanzi.neomacros.core.event.def.movement.PlayerSprintEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerAirEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerAttackBlockEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerAttackEntityEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerDamageEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerDeathEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerDimensionChangeEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerHealEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerHealthEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerHungerEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerInteractBlockEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerInteractEntityEvent;
import ink.tuanzi.neomacros.core.event.def.input.InputWheelEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerExpEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerArmorEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerGamemodeEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerHeldItemEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerRidingEvent;
import ink.tuanzi.neomacros.core.event.def.screen.ScreenChangeEvent;
import ink.tuanzi.neomacros.core.event.def.screen.ScreenCloseEvent;
import ink.tuanzi.neomacros.core.event.def.screen.ScreenOpenEvent;
import ink.tuanzi.neomacros.core.event.def.world.WorldWeatherEvent;
import ink.tuanzi.neomacros.core.event.def.input.KeyPressEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerJoinEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerLeaveEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerRespawnEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerSpawnEvent;
import ink.tuanzi.neomacros.core.event.def.tick.TickEvent;
import ink.tuanzi.neomacros.core.event.def.world.WorldDimensionChangeEvent;
import ink.tuanzi.neomacros.core.event.def.world.WorldTimeEvent;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.module.ScriptMeta;
import ink.tuanzi.neomacros.core.thread.TickClock;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;

import java.util.List;

/**
 * The one production event wiring from adapter port sinks onto the event
 * bus: lifecycle sources, chat receive, cancellable player interactions,
 * and the per-tick self-state tracker that derives the {@code player:*}
 * vitals and dimension/time events. Both the fabric bootstrap and the
 * application-level E2E harness run through this class so the semantics
 * cannot drift between the two environments.
 */
public final class PortEventWiring {

    /** Builds the script block handle for one neutral interaction hit. */
    private static ink.tuanzi.neomacros.core.api.types.Block blockHandle(
            ink.tuanzi.neomacros.port.model.BlockInteraction hit) {
        return new ink.tuanzi.neomacros.core.api.types.Block(
                new ink.tuanzi.neomacros.core.api.types.BlockPos(
                        Math.floor(hit.pos().x()), Math.floor(hit.pos().y()),
                        Math.floor(hit.pos().z())));
    }

    private PortEventWiring() {
    }

    /** One lifecycle body on the module runner, or inline when it is null. */
    private static void phase(java.util.concurrent.Executor moduleRunner, Runnable body) {
        if (moduleRunner == null) {
            body.run();
        } else {
            moduleRunner.execute(body);
        }
    }

    /** Wires every adapter port sink onto the runtime's event bus. */
    public static void wire(NeomacrosAdapter adapter, EventBus bus,
                            ModuleManager modules, TickClock clock) {
        wire(adapter, bus, modules, clock, null);
    }

    /**
     * Wires every adapter port sink onto the runtime's event bus, running
     * the lifecycle phase loads on {@code moduleRunner} when given (loading
     * blocks its caller until every script compiles, so production hops it
     * off the game thread; the lifecycle event publishes chain after their
     * phase loads so freshly subscribed scripts still receive them). Null
     * keeps the synchronous inline behavior.
     */
    public static void wire(NeomacrosAdapter adapter, EventBus bus,
                            ModuleManager modules, TickClock clock,
                            java.util.concurrent.Executor moduleRunner) {
        SelfStateTracker tracker = new SelfStateTracker(bus);

        adapter.lifecycle().onTick(tick -> {
            clock.tick();
            bus.publish("tick", new TickEvent(tick));
            tracker.poll(adapter);
        });
        adapter.lifecycle().onGameStart(() ->
                phase(moduleRunner, () -> {
                    modules.loadPhase(ScriptMeta.Lifecycle.ALWAYS);
                    bus.publish("game:start", new GameStartEvent());
                }));
        // Quit stays synchronous even with a module runner: the process is
        // exiting, and a background unload could be cut off before storage
        // flushes. The one-off pause is invisible here.
        adapter.lifecycle().onGameQuit(() -> {
            bus.publish("game:quit", new GameQuitEvent());
            modules.unloadPhase(ScriptMeta.Lifecycle.ALWAYS);
            ScriptApiEnvironment.closeStorages();
        });
        adapter.lifecycle().onWorldJoin(() ->
                phase(moduleRunner, () -> {
                    modules.loadPhase(ScriptMeta.Lifecycle.WORLD);
                    bus.publish("world:join", new WorldJoinEvent(
                            ScriptApiEnvironment.adapter().client().serverInfo()
                                    .map(info -> info.address()).orElse(null)));
                }));
        adapter.lifecycle().onWorldLeave(() -> {
            tracker.emitLeave();
            bus.publish("world:leave", new WorldLeaveEvent());
            phase(moduleRunner, () -> modules.unloadPhase(ScriptMeta.Lifecycle.WORLD));
        });
        adapter.chat().onChatReceive(message ->
                bus.publish("chat:receive", new ChatReceiveEvent(message)));

        adapter.player().onAttackBlock(hit ->
                bus.publish("player:attackBlock", new PlayerAttackBlockEvent(
                        blockHandle(hit), hit.face())).canceled());
        adapter.player().onAttackEntity(target ->
                bus.publish("player:attackEntity", new PlayerAttackEntityEvent(target)).canceled());
        adapter.player().onInteractBlock(hit ->
                bus.publish("player:interactBlock", new PlayerInteractBlockEvent(
                        blockHandle(hit), hit.face())).canceled());
        adapter.player().onInteractEntity(target ->
                bus.publish("player:interactEntity", new PlayerInteractEntityEvent(target)).canceled());

        adapter.input().onKeyPress(edge ->
                bus.publish("key:press", new KeyPressEvent(
                        edge.keyName(), edge.keyCode(),
                        edge.down() ? "press" : "release", List.of())).canceled());
        adapter.input().onScroll(amount ->
                bus.publish("input:wheel", new InputWheelEvent(amount)));

        // Poll-based derived events (weather, exp, heldItem, gamemode,
        // screen/inventory/dialog transitions) are wired in the
        // SelfStateTracker's tick check; the packet-driven inventory
        // update/click sources ride the fabric hubs in the bootstrap.
    }

    /**
     * Derives the local player event family from per-tick snapshots: enter /
     * leave world, spawn / death / respawn edges, vitals deltas, status
     * effect changes, the nearby-entity health diff, and the dimension pair
     * plus world time jumps from the world port. All queries run on the tick
     * thread (the game main thread in production).
     */
    private static final class SelfStateTracker {

        /** Radius around the player polled for entity health changes. */
        private static final double ENTITY_HEALTH_RANGE = 32.0;

        private final EventBus bus;
        private boolean seen;
        private boolean dead;
        private PlayerSnapshot last;
        private String lastDimension;
        private long lastTime;
        private boolean timeInitialized;
        private final java.util.Map<java.util.UUID, Double> entityHealth = new java.util.HashMap<>();
        private final java.util.Map<String, Integer> effectAmplifiers = new java.util.HashMap<>();
        private String lastGamemode;
        private int lastExpLevel = -1;
        private boolean lastRaining;
        private boolean weatherInitialized;
        private String lastHeldItemId;
        private boolean lastScreenOpen;
        private String lastScreenClass;
        private String lastScreenTitle;
        private String lastContainerKind;
        private int lastContainerId;
        private String lastContainerTitle;
        private String lastDialogKind;
        private java.util.UUID lastVehicle;
        private String lastArmorSignature = "";

        SelfStateTracker(EventBus bus) {
            this.bus = bus;
        }

        /** Polls one tick's worth of state transitions. */
        void poll(NeomacrosAdapter adapter) {
            java.util.Optional<PlayerSnapshot> self = adapter.player().self();
            if (self.isEmpty()) {
                if (seen) {
                    emitLeave();
                }
                return;
            }
            PlayerSnapshot now = self.get();
            if (!seen) {
                seen = true;
                dead = false;
                bus.publish("player:join", new PlayerJoinEvent(entityOf(now)));
                bus.publish("player:spawn", new PlayerSpawnEvent(entityOf(now)));
            } else {
                emitVitals(now);
            }
            last = now;
            emitWorld(adapter, now);
            emitEntityHealth(adapter, now);
            emitDerived(adapter, now);
            emitRiding(adapter);
            emitArmor(adapter);
            emitScreen(adapter);
            emitContainer(adapter);
            emitDialog(adapter);
            emitEffectChanges(adapter);
        }

        /** Emits player:leave from the last known snapshot, if any. */
        void emitLeave() {
            if (seen) {
                bus.publish("player:leave", new PlayerLeaveEvent(last.uuid(), last.name()));
            }
            seen = false;
            dead = false;
            last = null;
            lastDimension = null;
            timeInitialized = false;
            entityHealth.clear();
            effectAmplifiers.clear();
            lastVehicle = null;
            lastArmorSignature = "";
            lastScreenOpen = false;
            lastScreenClass = null;
            lastScreenTitle = null;
            lastContainerKind = null;
            lastContainerId = 0;
            lastContainerTitle = null;
            lastDialogKind = null;
        }

        /** Diffs the mount state and publishes player:riding edges. */
        private void emitRiding(NeomacrosAdapter adapter) {
            java.util.UUID vehicle = adapter.player().vehicleUuid().orElse(null);
            if (java.util.Objects.equals(vehicle, lastVehicle)) {
                return;
            }
            lastVehicle = vehicle;
            EntitySnapshot snapshot = vehicle == null
                    ? null : adapter.entity().byUuid(vehicle).orElse(null);
            bus.publish("player:riding", new PlayerRidingEvent(snapshot));
        }

        /** Diffs the four armor slots and publishes player:armor. */
        private void emitArmor(NeomacrosAdapter adapter) {
            List<ink.tuanzi.neomacros.port.model.ItemStackData> armor =
                    adapter.player().armorSlots();
            StringBuilder signature = new StringBuilder();
            for (ink.tuanzi.neomacros.port.model.ItemStackData stack : armor) {
                signature.append(stack.id()).append('x').append(stack.count()).append(',');
            }
            if (signature.toString().equals(lastArmorSignature)) {
                return;
            }
            lastArmorSignature = signature.toString();
            bus.publish("player:armor", new PlayerArmorEvent(armor));
        }

        /**
         * Diffs nearby entity health against the previous tick and publishes
         * entity:damage / entity:heal. Entities leaving the radius are
         * forgotten so re-entering them does not replay a stale delta.
         */
        private void emitEntityHealth(NeomacrosAdapter adapter, PlayerSnapshot self) {
            List<EntitySnapshot> nearby = adapter.entity().inRadius(
                    self.pos(), ENTITY_HEALTH_RANGE,
                    entity -> !entity.uuid().equals(self.uuid()));
            java.util.Set<java.util.UUID> present = new java.util.HashSet<>();
            for (EntitySnapshot entity : nearby) {
                present.add(entity.uuid());
                Double previous = entityHealth.put(entity.uuid(), entity.health());
                if (previous == null || previous == entity.health()) {
                    continue;
                }
                double delta = entity.health() - previous;
                if (delta < 0) {
                    bus.publish("entity:damage",
                            new ink.tuanzi.neomacros.core.event.def.entity.EntityDamageEvent(
                                    entity, (float) -delta));
                } else {
                    bus.publish("entity:heal",
                            new ink.tuanzi.neomacros.core.event.def.entity.EntityHealEvent(
                                    entity, (float) delta));
                }
            }
            entityHealth.keySet().removeIf(uuid -> !present.contains(uuid));
        }

        /**
         * Publishes screen transitions (tick-granular): the generic
         * screen:change on every identity switch, plus the open/close edges
         * enriched with the screen's MC class name.
         */
        void emitScreen(NeomacrosAdapter adapter) {
            var info = adapter.lifecycle().screenInfo().orElse(null);
            String screenClass = info == null ? null : info.screenClass();
            String title = info == null ? null : info.title();
            boolean open = info != null;
            if (!java.util.Objects.equals(screenClass, lastScreenClass)) {
                String phase = open && lastScreenOpen ? "switch" : (open ? "open" : "close");
                bus.publish("screen:change", new ScreenChangeEvent(phase, screenClass,
                        info != null ? info.kind() : "",
                        info != null ? info.title()
                                : (lastScreenTitle != null ? lastScreenTitle : "")));
            }
            if (open != lastScreenOpen) {
                if (open) {
                    bus.publish("screen:open", new ScreenOpenEvent(info.title(), info.kind(), info.screenClass()));
                } else {
                    bus.publish("screen:close", new ScreenCloseEvent(
                            lastScreenTitle != null ? lastScreenTitle : "",
                            lastScreenClass != null ? lastScreenClass : ""));
                }
            }
            lastScreenOpen = open;
            lastScreenClass = screenClass;
            lastScreenTitle = title;
        }

        /**
         * Publishes inventory:open/close from the open container diff (the
         * player inventory screen included, kind {@code player}); direct
         * container switches emit close then open.
         */
        void emitContainer(NeomacrosAdapter adapter) {
            var info = adapter.inventory().openedContainer().orElse(null);
            String kind = info == null ? null : info.kind();
            int containerId = info == null ? 0 : info.containerId();
            if (java.util.Objects.equals(kind, lastContainerKind) && containerId == lastContainerId) {
                return;
            }
            if (lastContainerKind != null) {
                bus.publish("inventory:close", new ink.tuanzi.neomacros.core.event.def.inventory.InventoryCloseEvent(
                        lastContainerKind, lastContainerTitle != null ? lastContainerTitle : ""));
            }
            if (info != null) {
                bus.publish("inventory:open", new ink.tuanzi.neomacros.core.event.def.inventory.InventoryOpenEvent(
                        info.title(), info.kind(), info.size(), info.containerId()));
            }
            lastContainerKind = kind;
            lastContainerId = containerId;
            lastContainerTitle = info != null ? info.title() : null;
        }

        /** Publishes dialog:open/close from the current dialog diff. */
        void emitDialog(NeomacrosAdapter adapter) {
            var info = adapter.dialog().currentDialog().orElse(null);
            String kind = info == null ? null : info.kind();
            if (java.util.Objects.equals(kind, lastDialogKind)) {
                return;
            }
            if (info != null) {
                java.util.List<ink.tuanzi.neomacros.core.api.player.PlayerApi.DialogAction> actions =
                        new java.util.ArrayList<>();
                for (ink.tuanzi.neomacros.port.DialogPort.DialogActionInfo action : info.actions()) {
                    actions.add(new ink.tuanzi.neomacros.core.api.player.PlayerApi.DialogAction(
                            action.index(), action.label(), action.id()));
                }
                bus.publish("dialog:open", new ink.tuanzi.neomacros.core.event.def.dialog.DialogOpenEvent(
                        info.kind(), info.title(), info.canCloseWithEscape(), actions));
            } else if (lastDialogKind != null) {
                bus.publish("dialog:close", new ink.tuanzi.neomacros.core.event.def.dialog.DialogCloseEvent(
                        lastDialogKind));
            }
            lastDialogKind = kind;
        }

        /** Publishes weather, gamemode, exp, and held item changes. */
        void emitDerived(NeomacrosAdapter adapter, PlayerSnapshot now) {
            var world = adapter.world();
            boolean raining = world.isRaining();
            boolean thundering = world.isThundering();
            if (!weatherInitialized) {
                lastRaining = raining;
                weatherInitialized = true;
            } else if (raining != lastRaining || thundering != (lastRaining && world.isThundering())) {
                bus.publish("world:weather", new WorldWeatherEvent(raining, thundering));
                lastRaining = raining;
            }

            String gamemode = adapter.player().gamemode();
            if (lastGamemode == null) {
                lastGamemode = gamemode;
            } else if (!lastGamemode.equals(gamemode)) {
                bus.publish("player:gamemode", new PlayerGamemodeEvent(lastGamemode, gamemode));
                lastGamemode = gamemode;
            }

            int expLevel = adapter.player().expLevel();
            if (lastExpLevel >= 0 && expLevel != lastExpLevel) {
                bus.publish("player:exp", new PlayerExpEvent(expLevel,
                        adapter.player().expProgress(), expLevel - lastExpLevel));
            }
            lastExpLevel = expLevel;

            var held = adapter.inventory().heldItem().get();
            String heldId = held.map(ink.tuanzi.neomacros.port.model.ItemStackData::id).orElse(null);
            if (lastHeldItemId != null && !lastHeldItemId.equals(heldId)) {
                bus.publish("player:heldItem", new PlayerHeldItemEvent(null, held.orElse(null)));
            }
            lastHeldItemId = heldId;
        }

        /**
         * Diffs the player's active status effects against the previous tick
         * and publishes player:effect for additions, removals, and
         * amplifier changes.
         */
        private void emitEffectChanges(NeomacrosAdapter adapter) {
            java.util.Set<String> active = new java.util.HashSet<>();
            for (ink.tuanzi.neomacros.port.model.EffectSnapshot effect : adapter.player().activeEffects()) {
                active.add(effect.effectId());
                Integer previous = effectAmplifiers.put(effect.effectId(), effect.amplifier());
                if (previous == null || previous != effect.amplifier()) {
                    bus.publish("player:effect",
                            new ink.tuanzi.neomacros.core.event.def.player.PlayerEffectEvent(
                                    effect.effectId(), true, effect.amplifier()));
                }
            }
            effectAmplifiers.entrySet().removeIf(entry -> {
                if (active.contains(entry.getKey())) {
                    return false;
                }
                bus.publish("player:effect",
                        new ink.tuanzi.neomacros.core.event.def.player.PlayerEffectEvent(
                                entry.getKey(), false, entry.getValue()));
                return true;
            });
        }

        /** Emits health/hunger/air deltas and death/respawn edges. */
        private void emitVitals(PlayerSnapshot now) {
            double health = now.health();
            double previous = last.health();
            if (health != previous) {
                if (health < previous) {
                    bus.publish("player:damage", new PlayerDamageEvent(previous - health, health));
                } else {
                    bus.publish("player:heal", new PlayerHealEvent(health - previous, health));
                }
                bus.publish("player:health", new PlayerHealthEvent(health));
            }
            if (health <= 0 && !dead && previous > 0) {
                dead = true;
                bus.publish("player:death", new PlayerDeathEvent(entityOf(now)));
            } else if (health > 0 && dead) {
                dead = false;
                bus.publish("player:respawn", new PlayerRespawnEvent(entityOf(now)));
            }
            if (now.hunger() != last.hunger()) {
                bus.publish("player:hunger", new PlayerHungerEvent(now.hunger()));
            }
            if (now.air() != last.air()) {
                bus.publish("player:air", new PlayerAirEvent(now.air()));
            }
            if (now.sneaking() != last.sneaking()) {
                bus.publish("player:sneak", new PlayerSneakEvent(now.sneaking()));
            }
            if (now.sprinting() != last.sprinting()) {
                bus.publish("player:sprint", new PlayerSprintEvent(now.sprinting()));
            }
            if (now.gliding() != last.gliding()) {
                bus.publish("player:gliding", new PlayerGlidingEvent(now.gliding()));
            }
        }

        /** Emits dimension changes and world time jumps while in a world. */
        private void emitWorld(NeomacrosAdapter adapter, PlayerSnapshot now) {
            String dimension = adapter.world().dimension();
            if (dimension == null || dimension.isEmpty()) {
                return;
            }
            if (lastDimension == null) {
                lastDimension = dimension;
            } else if (!lastDimension.equals(dimension)) {
                bus.publish("player:dimensionChange",
                        new PlayerDimensionChangeEvent(lastDimension, dimension));
                bus.publish("world:dimensionChange",
                        new WorldDimensionChangeEvent(lastDimension, dimension));
                lastDimension = dimension;
                timeInitialized = false;
            }
            long time = adapter.world().time();
            if (!timeInitialized) {
                lastTime = time;
                timeInitialized = true;
            } else if (Math.abs(time - lastTime) > 1) {
                bus.publish("world:time", new WorldTimeEvent(time));
                lastTime = time;
            } else {
                lastTime = time;
            }
        }

        /** Lifts a player snapshot into the entity snapshot shape. */
        private static EntitySnapshot entityOf(PlayerSnapshot snapshot) {
            return new EntitySnapshot(snapshot.uuid(), "minecraft:player", snapshot.name(),
                    snapshot.pos(), snapshot.health());
        }
    }
}
