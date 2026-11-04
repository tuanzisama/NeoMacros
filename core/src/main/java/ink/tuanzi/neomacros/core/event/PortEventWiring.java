package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.event.def.chat.ChatReceiveEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameQuitEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameStartEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.WorldJoinEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.WorldLeaveEvent;
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
import ink.tuanzi.neomacros.core.event.def.player.PlayerGamemodeEvent;
import ink.tuanzi.neomacros.core.event.def.player.PlayerHeldItemEvent;
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

    private PortEventWiring() {
    }

    /** Wires every adapter port sink onto the runtime's event bus. */
    public static void wire(NeomacrosAdapter adapter, EventBus bus,
                            ModuleManager modules, TickClock clock) {
        SelfStateTracker tracker = new SelfStateTracker(bus);

        adapter.lifecycle().onTick(tick -> {
            clock.tick();
            bus.publish("tick", new TickEvent(tick));
            tracker.poll(adapter);
        });
        adapter.lifecycle().onGameStart(() -> {
            modules.loadPhase(ScriptMeta.Lifecycle.ALWAYS);
            bus.publish("game:start", new GameStartEvent());
        });
        adapter.lifecycle().onGameQuit(() -> {
            bus.publish("game:quit", new GameQuitEvent());
            modules.unloadPhase(ScriptMeta.Lifecycle.ALWAYS);
            ScriptApiEnvironment.closeStorages();
        });
        adapter.lifecycle().onWorldJoin(() -> {
            modules.loadPhase(ScriptMeta.Lifecycle.WORLD);
            bus.publish("world:join", new WorldJoinEvent());
        });
        adapter.lifecycle().onWorldLeave(() -> {
            tracker.emitLeave();
            bus.publish("world:leave", new WorldLeaveEvent());
            modules.unloadPhase(ScriptMeta.Lifecycle.WORLD);
        });
        adapter.chat().onChatReceive(message ->
                bus.publish("chat:receive", new ChatReceiveEvent(message)));

        adapter.player().onAttackBlock(block ->
                bus.publish("player:attackBlock", new PlayerAttackBlockEvent(block)).canceled());
        adapter.player().onAttackEntity(target ->
                bus.publish("player:attackEntity", new PlayerAttackEntityEvent(target)).canceled());
        adapter.player().onInteractBlock(block ->
                bus.publish("player:interactBlock", new PlayerInteractBlockEvent(block)).canceled());
        adapter.player().onInteractEntity(target ->
                bus.publish("player:interactEntity", new PlayerInteractEntityEvent(target)).canceled());

        adapter.input().onKeyPress(edge ->
                bus.publish("key:press", new KeyPressEvent(
                        edge.keyName(), edge.keyCode(),
                        edge.down() ? "press" : "release", List.of())).canceled());
        adapter.input().onScroll(amount ->
                bus.publish("input:wheel", new InputWheelEvent(amount)));

        adapter.inventory().onOpened(info ->
                bus.publish("inventory:open", java.util.Map.of(
                        "title", info.title(), "kind", info.kind(), "size", info.size())));
        adapter.inventory().onUpdated(slot ->
                bus.publish("inventory:update", java.util.Map.of("mode", "slot", "slot", slot)));

        // Poll-based derived events (weather, exp, heldItem, gamemode)
        // are wired in the SelfStateTracker's tick check.
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
        private String lastScreenTitle;

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
            emitScreen(adapter);
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

        /** Publishes screen open/close transitions (tick-granular). */
        void emitScreen(NeomacrosAdapter adapter) {
            boolean open = adapter.lifecycle().isScreenOpen();
            if (open == lastScreenOpen) {
                return;
            }
            if (open) {
                String title = adapter.lifecycle().screenTitle().orElse("");
                bus.publish("screen:open", new ScreenOpenEvent(title, "screen"));
            } else {
                bus.publish("screen:close",
                        new ScreenCloseEvent(lastScreenTitle != null ? lastScreenTitle : ""));
            }
            lastScreenTitle = open ? adapter.lifecycle().screenTitle().orElse(null) : null;
            lastScreenOpen = open;
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
