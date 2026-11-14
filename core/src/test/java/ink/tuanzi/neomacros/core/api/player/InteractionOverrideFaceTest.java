package ink.tuanzi.neomacros.core.api.player;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.types.BlockPos;
import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.DialogPort;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.RayHit;
import ink.tuanzi.neomacros.port.model.Rot2d;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interaction override face over a canned player port: the target
 * override writes and checks, the break override with its outcome mapping
 * and one-shot callbacks, the held-use override, the crosshair-driven
 * {@code interact()} dispatch and the {@code reset()} chain — plus the
 * {@code interactions} entry point's world-presence semantics.
 */
class InteractionOverrideFaceTest {

    private CannedPlayer port;
    private PlayerApi.LocalPlayer local;
    private PlayerApi.Interactions interactions;

    @BeforeEach
    void install() {
        port = new CannedPlayer();
        ScriptApiEnvironment.install(null, new PlayerEntityAdapter(port));
        port.self = new PlayerSnapshot(UUID.randomUUID(), "tester",
                new Pos3d(0, 64, 0), new Rot2d(0, 0), 20, 20, 300);
        local = new PlayerApi.LocalPlayer(port.self.uuid());
        interactions = local.getInteractions();
    }

    @AfterEach
    void uninstall() throws Exception {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void entryPointTracksWorldPresence() {
        assertNotNull(interactions);

        port.self = null;
        assertNull(local.getInteractions(), "no world means no interactions handle");

        port.self = new PlayerSnapshot(UUID.randomUUID(), "tester",
                new Pos3d(0, 64, 0), new Rot2d(0, 0), 20, 20, 300);
        assertNotNull(local.getInteractions());
    }

    @Test
    void targetOverrideWritesClearsAndFaceDefaults() throws Exception {
        assertFalse(interactions.isTargetOverride());

        interactions.setTarget(new BlockPos(1, 64, 1), "up").get(1, TimeUnit.SECONDS);
        assertTrue(interactions.isTargetOverride());
        assertEquals(List.of("setTarget:1,64,1:up"), port.actions);

        // The omitted face defaults to down.
        interactions.setTarget(new BlockPos(2, 64, 2), null).get(1, TimeUnit.SECONDS);
        assertEquals("setTarget:2,64,2:down", port.actions.get(port.actions.size() - 1));

        // An unknown face throws synchronously before any write.
        assertThrows(IllegalArgumentException.class,
                () -> interactions.setTarget(new BlockPos(3, 64, 3), "sideways"));

        // A null position clears the override (Target.setTargetBlock parity).
        interactions.setTarget(null, null).get(1, TimeUnit.SECONDS);
        assertFalse(interactions.isTargetOverride());
        assertEquals("clearTarget", port.actions.get(port.actions.size() - 1));

        interactions.setTargetMissed().get(1, TimeUnit.SECONDS);
        assertTrue(interactions.isTargetOverride());

        interactions.clearTarget().get(1, TimeUnit.SECONDS);
        assertFalse(interactions.isTargetOverride());
    }

    @Test
    void targetEntityRequiresALoadedEntity() throws Exception {
        UUID gone = UUID.randomUUID();
        var failed = interactions.setTargetEntity(new EntityApi.Entity(gone));
        assertTrue(failed.isCompletedExceptionally(), "unloaded entity must fail");

        UUID loaded = UUID.randomUUID();
        port.entity = new EntitySnapshot(loaded, "minecraft:zombie", "Zombie",
                new Pos3d(4, 64, 4), 20);
        interactions.setTargetEntity(new EntityApi.Entity(loaded))
                .get(1, TimeUnit.SECONDS);
        assertTrue(interactions.isTargetOverride());
        assertEquals("setTargetEntity:" + loaded,
                port.actions.get(port.actions.size() - 1));
    }

    @Test
    void checksConfigureQueryAndReset() throws Exception {
        interactions.setRangeCheck(false, false).get(1, TimeUnit.SECONDS);
        interactions.setAirCheck(true, true).get(1, TimeUnit.SECONDS);
        interactions.setShapeCheck(false, true).get(1, TimeUnit.SECONDS);
        assertEquals(List.of("rangeCheck:false:false", "airCheck:true:true",
                "shapeCheck:false:true"), port.actions);

        // In-range is a pure query over the stored override.
        assertFalse(interactions.isTargetInRange(0.5));
        interactions.setTarget(new BlockPos(1, 64, 1), null).get(1, TimeUnit.SECONDS);
        assertTrue(interactions.isTargetInRange(null));

        interactions.resetChecks().get(1, TimeUnit.SECONDS);
        assertTrue(port.rangeCheck);
        assertTrue(port.rangeAutoClear);
        assertFalse(port.airCheck);
        assertFalse(port.airAutoClear);
        assertTrue(port.shapeCheck);
        assertFalse(port.shapeAutoClear);
        assertEquals("resetChecks", port.actions.get(port.actions.size() - 1));
    }

    @Test
    void breakBlockResolvesWithTheMappedOutcome() throws Exception {
        port.breakOutcome = new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                "SUCCESS", new Pos3d(5, 60, 5));
        var success = interactions.breakBlock(new BlockPos(5, 60, 5))
                .get(1, TimeUnit.SECONDS);
        assertEquals("SUCCESS", success.reason());
        assertEquals(5, success.pos().getIntX());
        assertEquals(60, success.pos().getIntY());
        assertEquals(5, success.pos().getIntZ());
        assertEquals("breakBlockAt:5,60,5", port.actions.get(port.actions.size() - 1));

        port.breakOutcome = new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                "CANCELLED", null);
        var cancelled = interactions.breakBlock(null).get(1, TimeUnit.SECONDS);
        assertEquals("CANCELLED", cancelled.reason());
        assertNull(cancelled.pos());
        assertEquals("breakBlock", port.actions.get(port.actions.size() - 1));
    }

    @Test
    void breakOverrideArmingCancelAndOneShotCallbacks() throws Exception {
        List<String> received = new CopyOnWriteArrayList<>();
        interactions.addBreakCallback(args -> received.add(
                args[0] + "@" + (args[1] == null ? "-"
                        : args[1] + "," + args[2])),
                true);
        assertEquals("addBreakCallback:true", port.actions.get(port.actions.size() - 1));
        assertTrue(interactions.isBreakOverride(), "breaking=true arms the override");

        // A queued callback fires once with the settling outcome.
        interactions.cancelBreak().get(1, TimeUnit.SECONDS);
        assertEquals(List.of("CANCELLED@-"), received);
        assertFalse(interactions.isBreakOverride());

        // A second settle must not re-fire the cleared queue.
        received.clear();
        interactions.setBreakOverride(false, "RESET").get(1, TimeUnit.SECONDS);
        assertTrue(received.isEmpty(), "the one-shot queue was already cleared");
        assertEquals("breakOverride:false:RESET",
                port.actions.get(port.actions.size() - 1));

        // onBreakBlock reports a client destruction call.
        interactions.onBreakBlock(new BlockPos(2, 64, 2), true)
                .get(1, TimeUnit.SECONDS);
        assertEquals("reportBreak:2,64,2:true",
                port.actions.get(port.actions.size() - 1));
        assertFalse(interactions.isBreakOverride());

        // A null position is rejected before any write.
        var failed = interactions.onBreakBlock(null, true);
        assertTrue(failed.isCompletedExceptionally());
    }

    @Test
    void holdInteractLifecycleAndTimedHold() throws Exception {
        assertFalse(interactions.isInteractOverride());

        interactions.holdInteract(true, null).get(1, TimeUnit.SECONDS);
        assertTrue(interactions.isInteractOverride());
        assertEquals("holdInteract:true", port.actions.get(port.actions.size() - 1));

        interactions.holdInteract(false, null).get(1, TimeUnit.SECONDS);
        assertFalse(interactions.isInteractOverride());

        // The timed hold resolves with the ticks that did not elapse and
        // releases the override before it resolves.
        port.holdRemainingAnswer = 3;
        assertEquals(3, interactions.holdInteractFor(10, false)
                .get(1, TimeUnit.SECONDS).intValue());
        assertEquals("holdInteractFor:10:false",
                port.actions.get(port.actions.size() - 1));
        assertFalse(interactions.isInteractOverride());

        // stopOnPause defaults to true.
        interactions.holdInteractFor(5, null).get(1, TimeUnit.SECONDS);
        assertEquals("holdInteractFor:5:true",
                port.actions.get(port.actions.size() - 1));
    }

    @Test
    void interactDispatchesFromTheEffectivePick() throws Exception {
        // No override (and no pick): the held item is used in the air.
        interactions.interact(null).get(1, TimeUnit.SECONDS);
        assertEquals("useItem:main", port.actions.get(port.actions.size() - 1));

        // A block override routes to the block-use action on the named face.
        interactions.setTarget(new BlockPos(1, 64, 1), "up").get(1, TimeUnit.SECONDS);
        interactions.interact("off").get(1, TimeUnit.SECONDS);
        assertEquals("useBlock:1,64,1:up:off",
                port.actions.get(port.actions.size() - 1));

        // An entity override routes to the entity-use action.
        UUID loaded = UUID.randomUUID();
        port.entity = new EntitySnapshot(loaded, "minecraft:zombie", "Zombie",
                new Pos3d(4, 64, 4), 20);
        interactions.setTargetEntity(new EntityApi.Entity(loaded))
                .get(1, TimeUnit.SECONDS);
        interactions.interact(null).get(1, TimeUnit.SECONDS);
        assertEquals("useEntity:" + loaded + ":main",
                port.actions.get(port.actions.size() - 1));

        // A stored miss falls back to the held item.
        interactions.setTargetMissed().get(1, TimeUnit.SECONDS);
        interactions.interact(null).get(1, TimeUnit.SECONDS);
        assertEquals("useItem:main", port.actions.get(port.actions.size() - 1));
    }

    @Test
    void resetRestoresEveryOverrideInOrder() throws Exception {
        interactions.setTarget(new BlockPos(1, 64, 1), null).get(1, TimeUnit.SECONDS);
        interactions.setBreakOverride(true, null).get(1, TimeUnit.SECONDS);
        interactions.holdInteract(true, null).get(1, TimeUnit.SECONDS);
        port.actions.clear();

        interactions.reset().get(1, TimeUnit.SECONDS);

        assertEquals(List.of("resetChecks", "clearTarget",
                "breakOverride:false:RESET", "holdInteract:false"), port.actions);
        assertFalse(interactions.isTargetOverride());
        assertFalse(interactions.isBreakOverride());
        assertFalse(interactions.isInteractOverride());
    }

    /** Canned player port: recorded actions plus deterministic answers. */
    private static final class CannedPlayer implements PlayerPort {
        volatile PlayerSnapshot self;
        volatile EntitySnapshot entity;
        volatile boolean rangeCheck = true;
        volatile boolean rangeAutoClear = true;
        volatile boolean airCheck;
        volatile boolean airAutoClear;
        volatile boolean shapeCheck = true;
        volatile boolean shapeAutoClear;
        volatile boolean targetSet;
        volatile String targetKind = "block";
        volatile int targetX;
        volatile int targetY;
        volatile int targetZ;
        volatile String targetFace = "down";
        volatile boolean breakArmed;
        volatile boolean holdActive;
        volatile ink.tuanzi.neomacros.port.model.BreakBlockResult breakOutcome =
                new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                        "SUCCESS", new Pos3d(1, 64, 1));
        volatile int holdRemainingAnswer;
        final List<String> actions = new CopyOnWriteArrayList<>();
        final List<Consumer<ink.tuanzi.neomacros.port.model.BreakBlockResult>>
                breakCallbacks = new CopyOnWriteArrayList<>();

        @Override
        public Optional<PlayerSnapshot> self() {
            return Optional.ofNullable(self);
        }

        @Override
        public List<ink.tuanzi.neomacros.port.model.EffectSnapshot> activeEffects() {
            return List.of();
        }

        @Override
        public void onAttackBlock(
                java.util.function.Function<BlockInteraction, Boolean> sink) {
        }

        @Override
        public void onAttackEntity(
                java.util.function.Function<EntitySnapshot, Boolean> sink) {
        }

        @Override
        public void onInteractBlock(
                java.util.function.Function<BlockInteraction, Boolean> sink) {
        }

        @Override
        public void onInteractEntity(
                java.util.function.Function<EntitySnapshot, Boolean> sink) {
        }

        private CompletableFuture<Void> record(String what) {
            actions.add(what);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> useEntity(EntitySnapshot target, String hand) {
            return record("useEntity:" + target.uuid() + ":" + hand);
        }

        @Override
        public CompletableFuture<Void> useBlock(BlockInteraction hit, String hand) {
            return record("useBlock:" + (int) hit.pos().x() + "," + (int) hit.pos().y()
                    + "," + (int) hit.pos().z() + ":" + hit.face() + ":" + hand);
        }

        @Override
        public CompletableFuture<Void> useItem(String hand) {
            return record("useItem:" + hand);
        }

        @Override
        public CompletableFuture<Void> attack(EntitySnapshot target) {
            return record("attack:" + target.uuid());
        }

        @Override
        public CompletableFuture<Void> attackBlock(BlockInteraction hit) {
            return record("attackBlock");
        }

        @Override
        public CompletableFuture<Void> stopBreaking() {
            return record("stopBreaking");
        }

        @Override
        public CompletableFuture<Void> swingHand(String hand) {
            return record("swing:" + hand);
        }

        @Override
        public CompletableFuture<Void> dropHeld(boolean all) {
            return record("drop:" + all);
        }

        @Override
        public CompletableFuture<Void> setTargetOverride(int x, int y, int z, String face) {
            actions.add("setTarget:" + x + "," + y + "," + z + ":" + face);
            targetSet = true;
            targetKind = "block";
            targetX = x;
            targetY = y;
            targetZ = z;
            targetFace = face;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setTargetEntityOverride(UUID uuid) {
            actions.add("setTargetEntity:" + uuid);
            targetSet = true;
            targetKind = "entity";
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setTargetMissedOverride() {
            actions.add("setTargetMissed");
            targetSet = true;
            targetKind = "miss";
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> clearTargetOverride() {
            actions.add("clearTarget");
            targetSet = false;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean hasTargetOverride() {
            return targetSet;
        }

        @Override
        public CompletableFuture<Void> setTargetRangeCheck(boolean enabled, boolean autoClear) {
            actions.add("rangeCheck:" + enabled + ":" + autoClear);
            rangeCheck = enabled;
            rangeAutoClear = autoClear;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setTargetAirCheck(boolean enabled, boolean autoClear) {
            actions.add("airCheck:" + enabled + ":" + autoClear);
            airCheck = enabled;
            airAutoClear = autoClear;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> setTargetShapeCheck(boolean enabled, boolean autoClear) {
            actions.add("shapeCheck:" + enabled + ":" + autoClear);
            shapeCheck = enabled;
            shapeAutoClear = autoClear;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> resetTargetChecks() {
            actions.add("resetChecks");
            rangeCheck = true;
            rangeAutoClear = true;
            airCheck = false;
            airAutoClear = false;
            shapeCheck = true;
            shapeAutoClear = false;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean targetInRange(float tickDelta) {
            return targetSet;
        }

        @Override
        public Optional<RayHit> target() {
            if (!targetSet) {
                return Optional.empty();
            }
            if ("miss".equals(targetKind)) {
                return Optional.empty();
            }
            if ("entity".equals(targetKind)) {
                return Optional.of(new RayHit(null, entity,
                        new Pos3d(targetX, targetY, targetZ), 2.5));
            }
            return Optional.of(new RayHit(
                    new BlockInteraction("minecraft:stone",
                            new Pos3d(targetX, targetY, targetZ), targetFace),
                    null,
                    new Pos3d(targetX + 0.5, targetY + 0.5, targetZ + 0.5),
                    2.5));
        }

        @Override
        public CompletableFuture<ink.tuanzi.neomacros.port.model.BreakBlockResult>
                breakBlock() {
            actions.add("breakBlock");
            return settle(breakOutcome);
        }

        @Override
        public CompletableFuture<ink.tuanzi.neomacros.port.model.BreakBlockResult>
                breakBlockAt(int x, int y, int z) {
            actions.add("breakBlockAt:" + x + "," + y + "," + z);
            return settle(breakOutcome);
        }

        private CompletableFuture<ink.tuanzi.neomacros.port.model.BreakBlockResult> settle(
                ink.tuanzi.neomacros.port.model.BreakBlockResult outcome) {
            breakArmed = false;
            fireCallbacks(outcome);
            return CompletableFuture.completedFuture(outcome);
        }

        private void fireCallbacks(ink.tuanzi.neomacros.port.model.BreakBlockResult outcome) {
            for (var callback : breakCallbacks) {
                callback.accept(outcome);
            }
            breakCallbacks.clear();
        }

        @Override
        public CompletableFuture<Void> setBreakOverride(boolean value, String reason) {
            actions.add("breakOverride:" + value + ":" + reason);
            breakArmed = value;
            if (!value) {
                fireCallbacks(new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                        reason, null));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean hasBreakBlockOverride() {
            return breakArmed;
        }

        @Override
        public CompletableFuture<Void> cancelBreakBlock() {
            actions.add("cancelBreak");
            breakArmed = false;
            fireCallbacks(new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                    "CANCELLED", null));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> reportBreakBlock(int x, int y, int z,
                boolean broken) {
            actions.add("reportBreak:" + x + "," + y + "," + z + ":" + broken);
            if (broken && breakArmed) {
                breakArmed = false;
                fireCallbacks(new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                        "SUCCESS", new Pos3d(x, y, z)));
            }
            return CompletableFuture.completedFuture(null);
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
        public CompletableFuture<Void> setHoldInteract(boolean holding) {
            actions.add("holdInteract:" + holding);
            holdActive = holding;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Integer> holdInteractFor(int ticks, boolean stopOnPause) {
            actions.add("holdInteractFor:" + ticks + ":" + stopOnPause);
            holdActive = false;
            return CompletableFuture.completedFuture(holdRemainingAnswer);
        }

        @Override
        public boolean hasInteractOverride() {
            return holdActive;
        }
    }

    /** Minimal entity port serving the one canned snapshot. */
    private static final class CannedEntities implements EntityPort {
        private final CannedPlayer player;

        CannedEntities(CannedPlayer player) {
            this.player = player;
        }

        @Override
        public Optional<EntitySnapshot> byUuid(UUID uuid) {
            return player.entity != null && uuid.equals(player.entity.uuid())
                    ? Optional.of(player.entity)
                    : Optional.empty();
        }

        @Override
        public List<EntitySnapshot> inRadius(Pos3d center, double range,
                java.util.function.Predicate<EntitySnapshot> filter) {
            return List.of();
        }
    }

    /** Adapter exposing only the canned player and entity ports. */
    private static final class PlayerEntityAdapter implements NeomacrosAdapter {
        private final CannedPlayer player;

        PlayerEntityAdapter(CannedPlayer player) {
            this.player = player;
        }

        @Override
        public LifecyclePort lifecycle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorldPort world() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityPort entity() {
            return new CannedEntities(player);
        }

        @Override
        public PlayerPort player() {
            return player;
        }

        @Override
        public ink.tuanzi.neomacros.port.InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InventoryPort inventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClientPort client() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPort render() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DialogPort dialog() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Draw3DPort draw3d() {
            throw new UnsupportedOperationException();
        }
    }
}
