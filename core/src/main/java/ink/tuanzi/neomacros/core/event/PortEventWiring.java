package ink.tuanzi.neomacros.core.event;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.types.TextComponent;
import ink.tuanzi.neomacros.core.event.def.chat.ChatActionbarEvent;
import ink.tuanzi.neomacros.core.event.def.chat.ChatReceiveEvent;
import ink.tuanzi.neomacros.core.event.def.chat.ChatSendEvent;
import ink.tuanzi.neomacros.core.event.def.chat.ChatTitleEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameQuitEvent;
import ink.tuanzi.neomacros.core.event.def.lifecycle.GameResourcePackEvent;
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
import ink.tuanzi.neomacros.core.event.def.screen.ScreenSignEditEvent;
import ink.tuanzi.neomacros.core.event.def.world.WorldWeatherEvent;
import ink.tuanzi.neomacros.core.event.def.input.InputKeyPressEvent;
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

    /**
     * Reads the replacement text ({@code {text: "..."}} or a TextComponent)
     * from one sync verdict; null keeps the original text.
     */
    private static ink.tuanzi.neomacros.port.model.ChatComponent replacementText(Object verdict) {
        if (!(verdict instanceof java.util.Map<?, ?> map)) {
            return null;
        }
        Object text = map.get("text");
        if (text instanceof String plain) {
            return ink.tuanzi.neomacros.port.model.ChatComponent.plain(plain);
        }
        if (text instanceof TextComponent component) {
            return component.toChatModel();
        }
        return null;
    }

    /**
     * Reads the sign-edit verdict ({@code {lines: [...], closeScreen}}) from
     * one sync verdict; null lines keep the original text.
     */
    private static ink.tuanzi.neomacros.port.model.SignEditVerdict signEditVerdict(
            boolean canceled, Object verdict) {
        if (!(verdict instanceof java.util.Map<?, ?> map)) {
            return new ink.tuanzi.neomacros.port.model.SignEditVerdict(canceled, null, false);
        }
        java.util.List<String> lines = null;
        if (map.get("lines") instanceof java.util.List<?> raw) {
            java.util.List<String> parsed = new java.util.ArrayList<>(raw.size());
            for (Object line : raw) {
                parsed.add(line == null ? "" : String.valueOf(line));
            }
            lines = parsed;
        }
        boolean closeScreen = truthy(map.get("closeScreen"));
        return new ink.tuanzi.neomacros.port.model.SignEditVerdict(canceled, lines, closeScreen);
    }

    /** JS-style truthiness for structured verdict fields. */
    private static boolean truthy(Object value) {
        if (value == null || Boolean.FALSE.equals(value)) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            return !text.isEmpty();
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        return true;
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
        // A resource reload completes after game:start in production (the
        // fabric client-started hook runs while the boot loading overlay is
        // still up), so the startup pack list already has subscribers.
        adapter.lifecycle().onResourcePackLoaded(data ->
                bus.publish("game:resourcePack", new GameResourcePackEvent(
                        data.isGameStart(), data.loadedPacks())));
        adapter.chat().onChatReceive(message ->
                bus.publish("chat:receive", new ChatReceiveEvent(message)));
        adapter.chat().onChatSend(message -> {
            var result = bus.publish("chat:send", new ChatSendEvent(message));
            // A committed {message} rewrite replaces the text on the wire.
            String rewritten = result.verdict() instanceof java.util.Map<?, ?> map
                    && map.get("message") instanceof String text ? text : null;
            return new ink.tuanzi.neomacros.port.model.ChatSendVerdict(
                    result.canceled(), rewritten);
        });
        adapter.chat().onTitle(display -> {
            var result = bus.publish("chat:title",
                    new ChatTitleEvent(display.kind(), display.text()));
            return new ink.tuanzi.neomacros.port.model.TextVerdict(
                    result.canceled(), replacementText(result.verdict()));
        });
        adapter.chat().onActionbar(display -> {
            var result = bus.publish("chat:actionbar",
                    new ChatActionbarEvent(display.text(), display.source()));
            return new ink.tuanzi.neomacros.port.model.TextVerdict(
                    result.canceled(), replacementText(result.verdict()));
        });
        adapter.player().onSignEdit(data -> {
            var result = bus.publish("screen:signEdit",
                    new ScreenSignEditEvent(data.x(), data.y(), data.z(),
                            data.front(), data.lines(), false,
                            new ink.tuanzi.neomacros.core.api.types.BlockPos(
                                    data.x(), data.y(), data.z())));
            return signEditVerdict(result.canceled(), result.verdict());
        });

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
                bus.publish("input:keyPress", new InputKeyPressEvent(
                        edge.keyName(), edge.keyCode(),
                        edge.down() ? "press" : "release", List.of())).canceled());
        adapter.input().onScroll(amount ->
                bus.publish("input:wheel", new InputWheelEvent(amount)));

        // Inventory event family: the click and drop sources are pre-action
        // and cancellable (a cancelled click/drop never sends its packet);
        // the slot/cursor syncs and pickups are observations. The sync
        // payloads carry the open screen's MC class name alongside the menu
        // identity (kind/containerId), captured on the publishing thread.
        adapter.inventory().onClickSlot(click ->
                bus.publish("inventory:click",
                        new ink.tuanzi.neomacros.core.event.def.inventory.InventoryClickEvent(
                                click.containerId(), click.kind(), click.slot(),
                                click.button(), click.mode(), click.shift())).canceled());
        adapter.inventory().onDropSlot(drop ->
                bus.publish("inventory:dropSlot",
                        new ink.tuanzi.neomacros.core.event.def.inventory.InventoryDropSlotEvent(
                                drop.kind(), drop.slot(), drop.all())).canceled());
        adapter.inventory().onSlotUpdate(update ->
                bus.publish("inventory:update",
                        new ink.tuanzi.neomacros.core.event.def.inventory.InventoryUpdateEvent(
                                update.full() ? "all" : "slot", update.containerId(),
                                update.kind(), update.slot(),
                                update.oldStack(), update.newStack(),
                                currentScreenClass(adapter))));
        adapter.inventory().onHeldUpdate(update ->
                bus.publish("inventory:update",
                        new ink.tuanzi.neomacros.core.event.def.inventory.InventoryUpdateEvent(
                                "held", update.containerId(), update.kind(), -1,
                                null, update.stack(), currentScreenClass(adapter))));
        adapter.inventory().onItemPickup(pickup ->
                bus.publish("item:pickup",
                        new ink.tuanzi.neomacros.core.event.def.inventory.ItemPickupEvent(
                                pickup.item(), pickup.amount())));

        // SelfStateTracker's tick check; the packet-driven inventory
        // update/click sources ride the fabric hubs in the bootstrap.
    }

    /**
     * The MC class name of the currently open screen, or {@code null} when
     * no screen snapshot is available.
     *
     * @zh 当前打开屏幕的 MC 类名；无屏幕快照时为 null。
     */
    private static String currentScreenClass(NeomacrosAdapter adapter) {
        return adapter.lifecycle().screenInfo()
                .map(info -> info.screenClass())
                .orElse(null);
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

        /** Player-inventory slot index of the off hand (vanilla order). */
        private static final int OFFHAND_SLOT = 40;

        private final EventBus bus;
        private boolean seen;
        private boolean dead;
        private PlayerSnapshot last;
        private String lastDimension;
        private long lastTime;
        private boolean timeInitialized;
        private final java.util.Map<java.util.UUID, Double> entityHealth = new java.util.HashMap<>();
        private final java.util.Map<java.util.UUID, String> lastCustomNames = new java.util.HashMap<>();
        private final java.util.Map<String, ink.tuanzi.neomacros.port.model.EffectSnapshot>
                lastEffects = new java.util.HashMap<>();
        private String lastGamemode;
        private int lastExpLevel = -1;
        private float lastExpProgress;
        private int lastExpTotal;
        private boolean lastRaining;
        private boolean weatherInitialized;
        private String lastHeldItemId;
        private ink.tuanzi.neomacros.port.model.ItemStackData lastHeldStack;
        private boolean heldSampled;
        private String lastOffhandItemId;
        private ink.tuanzi.neomacros.port.model.ItemStackData lastOffhandStack;
        private boolean offhandSampled;
        private List<ink.tuanzi.neomacros.port.model.ItemStackData> lastArmor;
        private boolean lastScreenOpen;
        private String lastScreenClass;
        private String lastScreenTitle;
        private String lastContainerKind;
        private int lastContainerId;
        private String lastContainerTitle;
        private String dismissedContainerKey;
        private String lastDialogKind;
        private java.util.UUID lastVehicle;
        private String lastArmorSignature = "";
        private final java.util.Map<String, String> lastDurabilityIds = new java.util.HashMap<>();
        private final java.util.Map<String, Integer> lastDurabilityValues =
                new java.util.HashMap<>();

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
                emitVitals(adapter, now);
            }
            last = now;
            emitWorld(adapter, now);
            List<EntitySnapshot> nearby = adapter.entity().inRadius(now.pos(),
                    ENTITY_HEALTH_RANGE,
                    entity -> !entity.uuid().equals(now.uuid()));
            emitEntityHealth(nearby);
            emitEntityNameChanges(adapter, now, nearby);
            emitDerived(adapter, now);
            emitRiding(adapter);
            emitArmor(adapter);
            emitDurability(adapter);
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
            lastCustomNames.clear();
            lastEffects.clear();
            lastVehicle = null;
            lastArmor = null;
            lastDurabilityIds.clear();
            lastDurabilityValues.clear();
            lastScreenOpen = false;
            lastScreenClass = null;
            lastScreenTitle = null;
            lastContainerKind = null;
            lastContainerId = 0;
            lastContainerTitle = null;
            dismissedContainerKey = null;
            lastDialogKind = null;
        }

        /** Diffs the mount state and publishes player:riding edges. */
        private void emitRiding(NeomacrosAdapter adapter) {
            java.util.UUID vehicle = adapter.player().vehicleUuid().orElse(null);
            if (java.util.Objects.equals(vehicle, lastVehicle)) {
                return;
            }
            EntitySnapshot previous = lastVehicle == null
                    ? null : adapter.entity().byUuid(lastVehicle).orElse(null);
            lastVehicle = vehicle;
            EntitySnapshot snapshot = vehicle == null
                    ? null : adapter.entity().byUuid(vehicle).orElse(null);
            bus.publish("player:riding", new PlayerRidingEvent(snapshot, previous));
        }

        /**
         * Diffs the four armor slots and publishes one player:armor event per
         * slot whose stack identity (id + count) moved; the first sample only
         * establishes the baseline. Durability-only moves stay with
         * item:damage.
         */
        private void emitArmor(NeomacrosAdapter adapter) {
            List<ink.tuanzi.neomacros.port.model.ItemStackData> armor =
                    adapter.player().armorSlots();
            List<ink.tuanzi.neomacros.port.model.ItemStackData> previous = lastArmor;
            lastArmor = armor;
            if (previous == null) {
                return;
            }
            String[] labels = {"feet", "legs", "chest", "head"};
            for (int i = 0; i < labels.length && i < armor.size(); i++) {
                ink.tuanzi.neomacros.port.model.ItemStackData stack = armor.get(i);
                ink.tuanzi.neomacros.port.model.ItemStackData before =
                        i < previous.size() ? previous.get(i) : null;
                if (sameStackIdentity(stack, before)) {
                    continue;
                }
                bus.publish("player:armor", new PlayerArmorEvent(armor, labels[i], stack, before));
            }
        }

        /** Whether two stacks carry the same item id and count (damage ignored). */
        private static boolean sameStackIdentity(
                ink.tuanzi.neomacros.port.model.ItemStackData a,
                ink.tuanzi.neomacros.port.model.ItemStackData b) {
            if (a == null || b == null) {
                return a == b;
            }
            return a.id().equals(b.id()) && a.count() == b.count();
        }

        /**
         * Diffs the held stack and the four armor slots on the durability
         * axis: the same item id with a different damage value publishes
         * item:damage (current durability, not the delta). Other stack
         * changes stay with player:heldItem / player:armor.
         */
        private void emitDurability(NeomacrosAdapter adapter) {
            java.util.Map<String, ink.tuanzi.neomacros.port.model.ItemStackData> slots =
                    new java.util.LinkedHashMap<>();
            adapter.inventory().heldItem().get()
                    .ifPresent(stack -> slots.put("main", stack));
            java.util.List<ink.tuanzi.neomacros.port.model.ItemStackData> armor =
                    adapter.player().armorSlots();
            String[] labels = {"feet", "legs", "chest", "head"};
            for (int i = 0; i < labels.length && i < armor.size(); i++) {
                if (armor.get(i) != null) {
                    slots.put(labels[i], armor.get(i));
                }
            }
            for (java.util.Map.Entry<String, ink.tuanzi.neomacros.port.model.ItemStackData> entry
                    : slots.entrySet()) {
                String slot = entry.getKey();
                var stack = entry.getValue();
                Integer previousDamage = lastDurabilityValues.get(slot);
                String previousId = lastDurabilityIds.get(slot);
                lastDurabilityIds.put(slot, stack.id());
                lastDurabilityValues.put(slot, stack.damage());
                if (previousDamage == null || previousId == null) {
                    continue;
                }
                if (previousId.equals(stack.id()) && previousDamage != stack.damage()
                        && stack.maxDamage() > 0 && !stack.id().equals("minecraft:air")) {
                    bus.publish("item:damage", new ink.tuanzi.neomacros.core.event.def.inventory.ItemDamageEvent(
                            slot, stack, stack.damage()));
                }
            }
        }

        /**
         * Diffs the health of the given nearby entities against the previous
         * tick and publishes entity:damage / entity:heal. Entities leaving
         * the radius are forgotten so re-entering them does not replay a
         * stale delta.
         */
        private void emitEntityHealth(List<EntitySnapshot> nearby) {
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
                                    entity, (float) -delta, (float) entity.health()));
                } else {
                    bus.publish("entity:heal",
                            new ink.tuanzi.neomacros.core.event.def.entity.EntityHealEvent(
                                    entity, (float) delta, (float) entity.health()));
                }
            }
            entityHealth.keySet().removeIf(uuid -> !present.contains(uuid));
        }

        /**
         * Diffs the custom name of the local player and every nearby entity
         * against the previous tick and publishes entity:customName only on
         * actual changes; the first sample per entity is a baseline and
         * entities leaving the radius are forgotten so re-entry cannot
         * replay a stale name. Diff gating is the high-traffic suppression:
         * no-op sync echoes never publish, and the per-entity rate is
         * bounded by the observed rename rate.
         */
        private void emitEntityNameChanges(NeomacrosAdapter adapter, PlayerSnapshot self,
                List<EntitySnapshot> nearby) {
            java.util.Set<java.util.UUID> present = new java.util.HashSet<>();
            present.add(self.uuid());
            diffCustomName(adapter, self.uuid());
            for (EntitySnapshot entity : nearby) {
                present.add(entity.uuid());
                diffCustomName(adapter, entity.uuid());
            }
            lastCustomNames.keySet().removeIf(uuid -> !present.contains(uuid));
        }

        /** Publishes one entity's custom-name edge when the value actually moved. */
        private void diffCustomName(NeomacrosAdapter adapter, java.util.UUID uuid) {
            Object value = adapter.entity().entityState(uuid).get("customName");
            String current = value instanceof String text ? text : null;
            if (!lastCustomNames.containsKey(uuid)) {
                lastCustomNames.put(uuid, current);
                return;
            }
            String previous = lastCustomNames.put(uuid, current);
            if (java.util.Objects.equals(previous, current)) {
                return;
            }
            bus.publish("entity:customName",
                    new ink.tuanzi.neomacros.core.event.def.entity.EntityCustomNameEvent(
                            uuid, previous, current));
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
         * container switches emit close then open. A cancelled open closes
         * the container right away (the server stays authoritative) and
         * records nothing, so no unmatched close follows.
         */
        void emitContainer(NeomacrosAdapter adapter) {
            var info = adapter.inventory().openedContainer().orElse(null);
            String kind = info == null ? null : info.kind();
            int containerId = info == null ? 0 : info.containerId();
            if (info == null) {
                dismissedContainerKey = null;
            } else if (java.util.Objects.equals(dismissedContainerKey, kind + ":" + containerId)) {
                // This container was cancelled once already; keep ignoring it
                // until the open identity changes or it closes.
                return;
            }
            if (java.util.Objects.equals(kind, lastContainerKind) && containerId == lastContainerId) {
                return;
            }
            if (lastContainerKind != null) {
                bus.publish("inventory:close", new ink.tuanzi.neomacros.core.event.def.inventory.InventoryCloseEvent(
                        lastContainerKind, lastContainerTitle != null ? lastContainerTitle : ""));
            }
            if (info != null) {
                var result = bus.publish("inventory:open",
                        new ink.tuanzi.neomacros.core.event.def.inventory.InventoryOpenEvent(
                                info.title(), info.kind(), info.size(), info.containerId(),
                                currentScreenClass(adapter)));
                if (result.canceled()) {
                    try {
                        adapter.inventory().close();
                    } catch (UnsupportedOperationException ignored) {
                        // Ports without inventory actions cannot close; the
                        // cancel then only skips the event bookkeeping.
                    }
                    dismissedContainerKey = kind + ":" + containerId;
                    lastContainerKind = null;
                    lastContainerId = 0;
                    lastContainerTitle = null;
                    return;
                }
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
            float expProgress = adapter.player().expProgress();
            int expTotal = adapter.player().expTotal();
            if (lastExpLevel >= 0 && (expLevel != lastExpLevel || expTotal != lastExpTotal)) {
                bus.publish("player:exp", new PlayerExpEvent(expLevel, expProgress,
                        expLevel - lastExpLevel, expTotal, lastExpProgress, lastExpTotal,
                        lastExpLevel));
            }
            lastExpLevel = expLevel;
            lastExpProgress = expProgress;
            lastExpTotal = expTotal;

            var held = adapter.inventory().heldItem().get();
            String heldId = held.map(ink.tuanzi.neomacros.port.model.ItemStackData::id).orElse(null);
            if (heldSampled && !java.util.Objects.equals(lastHeldItemId, heldId)) {
                bus.publish("player:heldItem",
                        new PlayerHeldItemEvent(lastHeldStack, held.orElse(null), false));
            }
            heldSampled = true;
            lastHeldItemId = heldId;
            lastHeldStack = held.orElse(null);

            var offhand = adapter.inventory().slot(OFFHAND_SLOT);
            String offhandId = offhand
                    .map(ink.tuanzi.neomacros.port.model.ItemStackData::id).orElse(null);
            if (offhandSampled && !java.util.Objects.equals(lastOffhandItemId, offhandId)) {
                bus.publish("player:heldItem",
                        new PlayerHeldItemEvent(lastOffhandStack, offhand.orElse(null), true));
            }
            offhandSampled = true;
            lastOffhandItemId = offhandId;
            lastOffhandStack = offhand.orElse(null);
        }

        /**
         * Diffs the player's active status effects against the previous tick
         * and publishes player:effect for additions, removals, and
         * amplifier changes; each event carries the replaced effect snapshot.
         */
        private void emitEffectChanges(NeomacrosAdapter adapter) {
            java.util.Set<String> active = new java.util.HashSet<>();
            for (ink.tuanzi.neomacros.port.model.EffectSnapshot effect : adapter.player().activeEffects()) {
                active.add(effect.effectId());
                ink.tuanzi.neomacros.port.model.EffectSnapshot previous =
                        lastEffects.put(effect.effectId(), effect);
                if (previous == null || previous.amplifier() != effect.amplifier()) {
                    bus.publish("player:effect",
                            new ink.tuanzi.neomacros.core.event.def.player.PlayerEffectEvent(
                                    effect.effectId(), true, effect.amplifier(), previous));
                }
            }
            lastEffects.entrySet().removeIf(entry -> {
                if (active.contains(entry.getKey())) {
                    return false;
                }
                ink.tuanzi.neomacros.port.model.EffectSnapshot removed = entry.getValue();
                bus.publish("player:effect",
                        new ink.tuanzi.neomacros.core.event.def.player.PlayerEffectEvent(
                                removed.effectId(), false, removed.amplifier(), removed));
                return true;
            });
        }

        /** Emits health/hunger/air deltas and death/respawn edges. */
        private void emitVitals(NeomacrosAdapter adapter, PlayerSnapshot now) {
            double health = now.health();
            double previous = last.health();
            if (health != previous) {
                if (health < previous) {
                    bus.publish("player:damage", new PlayerDamageEvent(previous - health, health));
                } else {
                    bus.publish("player:heal", new PlayerHealEvent(health - previous, health));
                }
                bus.publish("player:health", new PlayerHealthEvent(health, health - previous));
            }
            if (health <= 0 && !dead && previous > 0) {
                dead = true;
                bus.publish("player:death", new PlayerDeathEvent(entityOf(now),
                        deathBlockPos(now), inventorySnapshot(adapter)));
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

        /** The block-grid position of the observed death sample. */
        private static ink.tuanzi.neomacros.core.api.types.BlockPos deathBlockPos(
                PlayerSnapshot snapshot) {
            return new ink.tuanzi.neomacros.core.api.types.BlockPos(
                    snapshot.pos().x(), snapshot.pos().y(), snapshot.pos().z());
        }

        /** The non-empty inventory stacks at the death sample, ordered by slot. */
        private static List<ink.tuanzi.neomacros.port.model.ItemStackData> inventorySnapshot(
                NeomacrosAdapter adapter) {
            return adapter.inventory().allItems().get().stream()
                    .sorted(java.util.Comparator.comparingInt(
                            ink.tuanzi.neomacros.port.InventoryPort.IndexedStack::slot))
                    .map(ink.tuanzi.neomacros.port.InventoryPort.IndexedStack::stack)
                    .toList();
        }
    }
}
