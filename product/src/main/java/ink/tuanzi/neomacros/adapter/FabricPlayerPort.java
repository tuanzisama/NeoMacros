package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.EffectSnapshot;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Local player state service backed by the vanilla client player, plus the
 * interaction event sources wired to the Fabric unified player interaction
 * callbacks (fired on the client for local actions). State queries marshal
 * onto the game main thread; the interaction callbacks already run there.
 * Every registered sink sees each interaction; a sink returning {@code true}
 * fails the game action.
 */
public final class FabricPlayerPort implements PlayerPort {

    private final List<Function<BlockInteraction, Boolean>> attackBlockSinks = new CopyOnWriteArrayList<>();
    private final List<Function<EntitySnapshot, Boolean>> attackEntitySinks = new CopyOnWriteArrayList<>();
    private final List<Function<BlockInteraction, Boolean>> interactBlockSinks = new CopyOnWriteArrayList<>();
    private final List<Function<EntitySnapshot, Boolean>> interactEntitySinks = new CopyOnWriteArrayList<>();

    // --- Script-driven actions (main-thread dispatched) ---

    @Override
    public java.util.concurrent.CompletableFuture<Void> attack(EntitySnapshot target) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            var entity = findEntity(target.uuid());
            if (player == null || entity == null) {
                throw new IllegalStateException("target entity is not loaded: " + target.uuid());
            }
            Minecraft.getInstance().gameMode.attack(player, entity);
            //? if <26.3 {
            /* player.swing(InteractionHand.MAIN_HAND); */
            //?}
            //? if >=26.3 {
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            //?}
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> useEntity(EntitySnapshot target,
            String hand) {
        return dispatch(() -> useEntityNow(target, hand));
    }

    /** Runs one entity use on the caller (main) thread. */
    private void useEntityNow(EntitySnapshot target, String hand) {
        var player = Minecraft.getInstance().player;
        var entity = findEntity(target.uuid());
        if (player == null || entity == null) {
            throw new IllegalStateException("target entity is not loaded: " + target.uuid());
        }
        //? if <26.1 {
        /* Minecraft.getInstance().gameMode.interact(player, entity,
                parseHand(hand)); */
        //?}
        //? if >=26.1 {
        Minecraft.getInstance().gameMode.interact(player, entity,
                new net.minecraft.world.phys.EntityHitResult(entity), parseHand(hand));
        //?}
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> useBlock(BlockInteraction hit,
            String hand) {
        return dispatch(() -> useBlockNow(hit, hand));
    }

    /** Runs one block use on the caller (main) thread. */
    private void useBlockNow(BlockInteraction hit, String hand) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            throw new IllegalStateException("no local player");
        }
        var pos = new BlockPos((int) Math.floor(hit.pos().x()),
                (int) Math.floor(hit.pos().y()), (int) Math.floor(hit.pos().z()));
        var hitResult = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos),
                parseFace(hit.face()), pos, false);
        //? if <26.1 {
        /* Minecraft.getInstance().gameMode.useItemOn(player,
                parseHand(hand), hitResult); */
        //?}
        //? if >=26.1 {
        Minecraft.getInstance().gameMode.useItemOn(player,
                parseHand(hand), hitResult);
        //?}
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> useItem(String hand) {
        return dispatch(() -> useItemNow(hand));
    }

    /** Runs one held-item use on the caller (main) thread. */
    private void useItemNow(String hand) {
        var player = Minecraft.getInstance().player;
        var gameMode = Minecraft.getInstance().gameMode;
        if (player == null || gameMode == null) {
            throw new IllegalStateException("no local player");
        }
        var used = gameMode.useItem(player, parseHand(hand));
        if (used.consumesAction()) {
            swing(player, parseHand(hand));
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> attackBlock(BlockInteraction hit) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            var gameMode = Minecraft.getInstance().gameMode;
            if (player == null || gameMode == null) {
                throw new IllegalStateException("no local player");
            }
            var pos = new BlockPos((int) Math.floor(hit.pos().x()),
                    (int) Math.floor(hit.pos().y()), (int) Math.floor(hit.pos().z()));
            swing(player, parseHand("main"));
            gameMode.startDestroyBlock(pos, parseFace(hit.face()));
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> stopBreaking() {
        return dispatch(() -> {
            var gameMode = Minecraft.getInstance().gameMode;
            if (gameMode != null) {
                gameMode.stopDestroyBlock();
            }
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> swingHand(String hand) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            swing(player, parseHand(hand));
        });
    }

    /** Swings one hand across the version-specific swing signatures. */
    private static void swing(net.minecraft.client.player.LocalPlayer player,
            InteractionHand hand) {
        //? if <26.3 {
        /* player.swing(hand); */
        //?}
        //? if >=26.3 {
        player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        //?}
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> dropHeld(boolean all) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            //? if <26.1 {
            /* var stack = player.getInventory().getItem(
                    player.getInventory().getSelectedSlot());
            if (!stack.isEmpty()) {
                player.drop(stack.copyWithCount(all ? stack.getCount() : 1), true);
                player.getInventory().setItem(
                        player.getInventory().getSelectedSlot(),
                        all ? net.minecraft.world.item.ItemStack.EMPTY
                            : stack.copyWithCount(stack.getCount() - 1));
            } */
            //?}
            //? if >=26.1 {
            Minecraft.getInstance().gameMode.dropItem(player, all);
            //?}
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> setFlying(boolean value) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                throw new IllegalStateException("no local player");
            }
            player.getAbilities().flying = value;
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> setFlySpeed(float value) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                throw new IllegalStateException("no local player");
            }
            player.getAbilities().setFlyingSpeed(value);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> setWalkingSpeed(float value) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                throw new IllegalStateException("no local player");
            }
            player.getAbilities().setWalkingSpeed(value);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> look(float yaw, float pitch) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                throw new IllegalStateException("no local player");
            }
            player.setYRot(yaw);
            player.setXRot(pitch);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> lookAt(EntitySnapshot target) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            var entity = findEntity(target.uuid());
            if (player == null || entity == null) {
                throw new IllegalStateException("target entity is not loaded: " + target.uuid());
            }
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    entity.getEyePosition());
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> teleport(double x, double y, double z) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                throw new IllegalStateException("no local player");
            }
            player.setPos(x, y, z);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> setVelocity(double x, double y, double z) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                throw new IllegalStateException("no local player");
            }
            player.setDeltaMovement(x, y, z);
        });
    }

    @Override
    public float itemCooldownPercent(String itemId) {
        Float percent = MainThread.run(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return 0f;
            }
            var id = net.minecraft.resources.Identifier.parse(itemId);
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id);
            if (item.isEmpty()) {
                return 0f;
            }
            return player.getCooldowns().getCooldownPercent(
                    new net.minecraft.world.item.ItemStack(item.get()), 0f);
        });
        return percent != null ? percent : 0f;
    }

    @Override
    public java.util.Optional<java.util.UUID> vehicleUuid() {
        return MainThread.run(() -> {
            var player = Minecraft.getInstance().player;
            var vehicle = player != null ? player.getVehicle() : null;
            return vehicle != null
                    ? java.util.Optional.of(vehicle.getUUID())
                    : java.util.Optional.<java.util.UUID>empty();
        });
    }

    @Override
    public java.util.List<ink.tuanzi.neomacros.port.model.ItemStackData> armorSlots() {
        return MainThread.run(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return java.util.List
                        .<ink.tuanzi.neomacros.port.model.ItemStackData>of();
            }
            java.util.List<ink.tuanzi.neomacros.port.model.ItemStackData> slots =
                    new java.util.ArrayList<>(4);
            for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
                    net.minecraft.world.entity.EquipmentSlot.FEET,
                    net.minecraft.world.entity.EquipmentSlot.LEGS,
                    net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.HEAD}) {
                slots.add(FabricInventoryPort.stackDataOf(player.getItemBySlot(slot)));
            }
            return java.util.List.copyOf(slots);
        });
    }

    /** Finds one loaded entity by UUID. */
    private net.minecraft.world.entity.Entity findEntity(java.util.UUID uuid) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        for (var entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) {
                return entity;
            }
        }
        return null;
    }

    /** Runs one game action on the main thread; failures fail the future. */
    private java.util.concurrent.CompletableFuture<Void> dispatch(Runnable action) {
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

    /** Creates the port and hooks the Fabric client interaction callbacks. */
    public FabricPlayerPort() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                verdict(shouldFail(attackBlockSinks,
                        blockHit(world, pos, direction.getName()))));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                verdict(shouldFail(attackEntitySinks, FabricEntityPort.snapshotOf(entity))));
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                verdict(shouldFail(interactBlockSinks,
                        blockHit(world, hitResult.getBlockPos(),
                                hitResult.getDirection().getName()))));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                verdict(shouldFail(interactEntitySinks, FabricEntityPort.snapshotOf(entity))));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
                .END_CLIENT_TICK.register(client -> tickInteractionOverrides());
    }

    @Override
    public int expTotal() {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null ? player.totalExperience : 0;
    }

    @Override
    public Optional<PlayerSnapshot> self() {
        return MainThread.run(() -> {
            LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) {
                return Optional.empty();
            }
            return Optional.of(new PlayerSnapshot(
                    player.getUUID(),
                    player.getName().getString(),
                    new Pos3d(player.getX(), player.getY(), player.getZ()),
                    new Rot2d(player.getYRot(), player.getXRot()),
                    player.getHealth(),
                    player.getFoodData().getFoodLevel(),
                    player.getAirSupply(),
                    player.getFoodData().getSaturationLevel(),
                    player.isShiftKeyDown(),
                    player.isSprinting(),
                    player.isFallFlying()));
        });
    }

    @Override
    public List<EffectSnapshot> activeEffects() {
        return MainThread.run(() -> {
            LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) {
                return List.of();
            }
            return player.getActiveEffectsMap().values().stream()
                    .map(instance -> new EffectSnapshot(
                            BuiltInRegistries.MOB_EFFECT.getKey(
                                    instance.getEffect().value()).toString(),
                            instance.getAmplifier(),
                            instance.getDuration()))
                    .toList();
        });
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
    public void onSignEdit(java.util.function.Function<ink.tuanzi.neomacros.port.model.SignEditData,
            ink.tuanzi.neomacros.port.model.SignEditVerdict> sink) {
        SignEditHub.register(sink);
    }

    /** Builds the neutral block hit for one world position. */
    private static BlockInteraction blockHit(net.minecraft.world.level.Level world, BlockPos pos,
            String face) {
        BlockState state = world.getBlockState(pos);
        return new BlockInteraction(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                new Pos3d(pos.getX(), pos.getY(), pos.getZ()), face);
    }

    /** Runs every sink of one interaction; true when any sink fails it. */
    private static <T> boolean shouldFail(List<Function<T, Boolean>> sinks, T payload) {
        boolean failed = false;
        for (Function<T, Boolean> sink : sinks) {
            if (Boolean.TRUE.equals(sink.apply(payload))) {
                failed = true;
            }
        }
        return failed;
    }

    /** Maps a cancel verdict onto the Fabric interaction result. */
    private static InteractionResult verdict(boolean canceled) {
        return canceled ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    @Override
    public java.util.Optional<Object> rawSelf() {
        return java.util.Optional.ofNullable(Minecraft.getInstance().player);
    }

    @Override
    public java.util.Map<String, Integer> statistics() {
        return MainThread.run(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return java.util.Map.of();
            }
            var counter = player.getStats();
            java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
            for (net.minecraft.stats.StatType<?> type : BuiltInRegistries.STAT_TYPE) {
                collectStats(counter, type, out);
            }
            return out;
        });
    }

    /** Collects the non-zero values of one stat type into the map. */
    @SuppressWarnings("unchecked")
    private static void collectStats(net.minecraft.stats.StatsCounter counter,
            net.minecraft.stats.StatType<?> type, java.util.Map<String, Integer> out) {
        var registry = (net.minecraft.core.Registry<Object>) type.getRegistry();
        String typeId = BuiltInRegistries.STAT_TYPE.getKey(type).toString();
        for (Object value : registry) {
            int count = counter.getValue(
                    (net.minecraft.stats.StatType<Object>) type, value);
            if (count != 0) {
                out.put(typeId + ":" + registry.getKey(value), count);
            }
        }
    }

    @Override
    public java.util.Map<String, String> formattedStatistics() {
        java.util.Map<String, String> result = MainThread.run(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return java.util.Map.<String, String>of();
            }
            var counter = player.getStats();
            java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
            for (net.minecraft.stats.StatType<?> type : BuiltInRegistries.STAT_TYPE) {
                collectFormattedStats(counter, type, out);
            }
            return out;
        });
        return result != null ? result : java.util.Map.of();
    }

    /** Collects the formatted non-zero values of one stat type. */
    @SuppressWarnings("unchecked")
    private static void collectFormattedStats(net.minecraft.stats.StatsCounter counter,
            net.minecraft.stats.StatType<?> type, java.util.Map<String, String> out) {
        var registry = (net.minecraft.core.Registry<Object>) type.getRegistry();
        String typeId = BuiltInRegistries.STAT_TYPE.getKey(type).toString();
        for (Object value : registry) {
            int count = counter.getValue(
                    (net.minecraft.stats.StatType<Object>) type, value);
            if (count != 0) {
                var stat = ((net.minecraft.stats.StatType<Object>) type).get(value);
                out.put(typeId + ":" + registry.getKey(value), stat.format(count));
            }
        }
    }

    @Override
    public java.util.Optional<String> statTypeDisplayName(String statKey) {
        java.util.Optional<String> result = MainThread.run(() -> {
            if (statKey == null || statKey.isEmpty()) {
                return java.util.Optional.<String>empty();
            }
            int first = statKey.indexOf(':');
            int second = first < 0 ? -1 : statKey.indexOf(':', first + 1);
            String typeId = second < 0 ? statKey : statKey.substring(0, second);
            var parsed = net.minecraft.resources.Identifier.tryParse(typeId);
            if (parsed == null) {
                return java.util.Optional.<String>empty();
            }
            var type = BuiltInRegistries.STAT_TYPE.getValue(parsed);
            if (type == null) {
                return java.util.Optional.<String>empty();
            }
            return java.util.Optional.of(type.getDisplayName().getString());
        });
        return result != null ? result : java.util.Optional.empty();
    }

    @Override
    public double blockInteractionRange() {
        Double range = MainThread.run(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null ? player.blockInteractionRange() : 0;
        });
        return range != null ? range : 0;
    }

    @Override
    public double entityInteractionRange() {
        Double range = MainThread.run(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null ? player.entityInteractionRange() : 0;
        });
        return range != null ? range : 0;
    }

    @Override
    public java.util.Optional<ink.tuanzi.neomacros.port.model.RayHit> target() {
        return MainThread.run(this::evaluateTargetRead);
    }

    /**
     * Reads the effective pick on the main thread: the stored target
     * override when one exists (running the configured checks, which may
     * auto-clear it), otherwise the real crosshair raycast. Checks never
     * run against the vanilla pick, so the game itself keeps acting on
     * what the player actually aims at.
     */
    private java.util.Optional<ink.tuanzi.neomacros.port.model.RayHit> evaluateTargetRead() {
        var override = targetOverride;
        if (override == null) {
            return realPick();
        }
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if ("miss".equals(override.kind())) {
            return java.util.Optional.empty();
        }
        if ("entity".equals(override.kind())) {
            var entity = findEntity(override.entity());
            if (entity == null) {
                // A dead or unloaded override target is invalid: drop it.
                setTargetOverrideNow(null);
                return java.util.Optional.empty();
            }
            if (player == null) {
                return java.util.Optional.empty();
            }
            var eye = player.getEyePosition();
            var picked = entity.pick(player.entityInteractionRange() + 1.0, 0.0f, false);
            var location = picked.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY
                    ? picked.getLocation()
                    : entity.position();
            if (targetCheckRange
                    && eye.distanceTo(location) >= player.entityInteractionRange()) {
                if (targetClearIfOutOfRange) {
                    setTargetOverrideNow(null);
                }
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new ink.tuanzi.neomacros.port.model.RayHit(
                    null, FabricEntityPort.snapshotOf(entity), toPos3d(location),
                    eye.distanceTo(location)));
        }
        // Block override: air, shape and range checks against the stored pos.
        var level = mc.level;
        if (level == null || player == null) {
            return java.util.Optional.empty();
        }
        var pos = new BlockPos(override.x(), override.y(), override.z());
        var state = level.getBlockState(pos);
        if (targetCheckAir && state.isAir()) {
            if (targetClearIfIsAir) {
                setTargetOverrideNow(null);
            }
            return java.util.Optional.empty();
        }
        if (targetCheckShape && !state.isAir()
                && state.getCollisionShape(level, pos).isEmpty()) {
            if (targetClearIfEmptyShape) {
                setTargetOverrideNow(null);
            }
            return java.util.Optional.empty();
        }
        var eye = player.getEyePosition();
        if (targetCheckRange) {
            // The block passes when the nearest point of its unit cube is
            // strictly inside the block interaction reach (JsMacros rule).
            double closestX = Math.min(Math.max(eye.x, pos.getX()), pos.getX() + 1.0);
            double closestY = Math.min(Math.max(eye.y, pos.getY()), pos.getY() + 1.0);
            double closestZ = Math.min(Math.max(eye.z, pos.getZ()), pos.getZ() + 1.0);
            double reach = eye.distanceTo(new net.minecraft.world.phys.Vec3(
                    closestX, closestY, closestZ));
            if (reach >= player.blockInteractionRange()) {
                if (targetClearIfOutOfRange) {
                    setTargetOverrideNow(null);
                }
                return java.util.Optional.empty();
            }
        }
        var center = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        return java.util.Optional.of(new ink.tuanzi.neomacros.port.model.RayHit(
                blockHit(level, pos, override.face()), null, toPos3d(center),
                eye.distanceTo(center)));
    }

    /** The real crosshair raycast (the vanilla pick, never overridden). */
    private java.util.Optional<ink.tuanzi.neomacros.port.model.RayHit> realPick() {
        LocalPlayer player = Minecraft.getInstance().player;
        var hitResult = Minecraft.getInstance().hitResult;
        if (player == null || hitResult == null
                || hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return java.util.Optional.<ink.tuanzi.neomacros.port.model.RayHit>empty();
        }
        double distance = player.getEyePosition().distanceTo(hitResult.getLocation());
        if (hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            var world = Minecraft.getInstance().level;
            if (world == null) {
                return java.util.Optional.<ink.tuanzi.neomacros.port.model.RayHit>empty();
            }
            var block = blockHit(world, blockHit.getBlockPos(),
                    blockHit.getDirection().getName());
            return java.util.Optional.of(new ink.tuanzi.neomacros.port.model.RayHit(
                    block, null, toPos3d(blockHit.getLocation()), distance));
        }
        if (hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit) {
            return java.util.Optional.of(new ink.tuanzi.neomacros.port.model.RayHit(
                    null, FabricEntityPort.snapshotOf(entityHit.getEntity()),
                    toPos3d(entityHit.getLocation()), distance));
        }
        return java.util.Optional.<ink.tuanzi.neomacros.port.model.RayHit>empty();
    }

    @Override
    public java.util.List<ink.tuanzi.neomacros.port.model.AdvancementData> advancements() {
        return MainThread.run(() -> {
            var connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return java.util.List.<ink.tuanzi.neomacros.port.model.AdvancementData>of();
            }
            var clientAdvancements = connection.getAdvancements();
            var tree = ((ink.tuanzi.neomacros.adapter.mixin.ClientAdvancementsAccessor)
                    clientAdvancements).neomacros$tree();
            java.util.List<ink.tuanzi.neomacros.port.model.AdvancementData> out =
                    new java.util.ArrayList<>();
            for (net.minecraft.advancements.AdvancementNode node : tree.nodes()) {
                var display = node.advancement().display().orElse(null);
                if (display == null) {
                    continue;
                }
                //? if <26.3 {
                /* out.add(new ink.tuanzi.neomacros.port.model.AdvancementData(
                        node.holder().id().toString(),
                        node.advancement().parent().map(Object::toString).orElse(null),
                        display.getTitle().getString(),
                        display.getDescription().getString(),
                        display.getType().getSerializedName(),
                        display.isHidden())); */
                //?}
                //? if >=26.3 {
                out.add(new ink.tuanzi.neomacros.port.model.AdvancementData(
                        node.holder().id().toString(),
                        node.advancement().parent().map(Object::toString).orElse(null),
                        display.title().getString(),
                        display.description().getString(),
                        display.type().getSerializedName(),
                        display.hidden()));
                //?}
            }
            return out;
        });
    }

    @Override
    public java.util.Optional<ink.tuanzi.neomacros.port.model.AdvancementProgressData>
            advancementProgress(String id) {
        java.util.Optional<ink.tuanzi.neomacros.port.model.AdvancementProgressData> result =
                MainThread.run(() -> {
                    var progress = advancementProgressOf(id);
                    if (progress == null) {
                        return java.util.Optional
                                .<ink.tuanzi.neomacros.port.model.AdvancementProgressData>empty();
                    }
                    return java.util.Optional.of(
                            new ink.tuanzi.neomacros.port.model.AdvancementProgressData(
                                    progress.isDone(),
                                    copyCriteria(progress.getCompletedCriteria()),
                                    copyCriteria(progress.getRemainingCriteria())));
                });
        return result != null ? result : java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<ink.tuanzi.neomacros.port.model.AdvancementDefinitionData>
            advancementDefinition(String id) {
        java.util.Optional<ink.tuanzi.neomacros.port.model.AdvancementDefinitionData> result =
                MainThread.run(() -> {
                    var node = advancementNodeOf(id);
                    if (node == null) {
                        return java.util.Optional
                                .<ink.tuanzi.neomacros.port.model.AdvancementDefinitionData>empty();
                    }
                    var advancement = node.advancement();
                    var rewards = advancement.rewards();
                    java.util.List<String> loot = new java.util.ArrayList<>();
                    //? if <26.3 {
                    /* for (var lootKey : rewards.loot()) {
                        loot.add(rewardKeyId(lootKey));
                    } */
                    //?}
                    //? if >=26.3 {
                    for (var holder : rewards.loot()) {
                        holder.unwrapKey().ifPresent(key -> loot.add(rewardKeyId(key)));
                    }
                    //?}
                    java.util.List<String> recipes = new java.util.ArrayList<>();
                    for (var recipeKey : rewards.recipes()) {
                        recipes.add(rewardKeyId(recipeKey));
                    }
                    return java.util.Optional.of(
                            new ink.tuanzi.neomacros.port.model.AdvancementDefinitionData(
                                    node.holder().id().toString(),
                                    advancement.requirements().requirements(),
                                    rewards.experience(),
                                    java.util.List.copyOf(loot),
                                    java.util.List.copyOf(recipes)));
                });
        return result != null ? result : java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<ink.tuanzi.neomacros.port.model.AdvancementProgressDetailData>
            advancementProgressDetail(String id) {
        java.util.Optional<ink.tuanzi.neomacros.port.model.AdvancementProgressDetailData> result =
                MainThread.run(() -> {
                    var progress = advancementProgressOf(id);
                    if (progress == null) {
                        return java.util.Optional
                                .<ink.tuanzi.neomacros.port.model.AdvancementProgressDetailData>
                                empty();
                    }
                    java.util.Map<String, Long> dates = new java.util.LinkedHashMap<>();
                    for (String criterion : progress.getCompletedCriteria()) {
                        var criterionProgress = progress.getCriterion(criterion);
                        if (criterionProgress == null || !criterionProgress.isDone()) {
                            continue;
                        }
                        java.time.Instant obtained = criterionProgress.getObtained();
                        if (obtained != null) {
                            dates.put(criterion, obtained.toEpochMilli());
                        }
                    }
                    var text = progress.getProgressText();
                    return java.util.Optional.of(
                            new ink.tuanzi.neomacros.port.model.AdvancementProgressDetailData(
                                    dates, text == null ? null : text.getString()));
                });
        return result != null ? result : java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<String> advancementJson(String id) {
        java.util.Optional<String> result = MainThread.run(() -> {
            var node = advancementNodeOf(id);
            if (node == null) {
                return java.util.Optional.<String>empty();
            }
            // Plain JsonOps is enough for encoding: every registry reference
            // (items, loot tables, recipes, triggers) serializes to its
            // namespaced id on the encode path without a registry lookup.
            var encoded = net.minecraft.advancements.Advancement.CODEC.encodeStart(
                    com.mojang.serialization.JsonOps.INSTANCE, node.advancement());
            return encoded.result().map(com.google.gson.JsonElement::toString);
        });
        return result != null ? result : java.util.Optional.empty();
    }

    /**
     * Resolves one advancement tree node by id over the client connection, or
     * null when there is no connection or the id is unknown.
     */
    private static net.minecraft.advancements.AdvancementNode advancementNodeOf(String id) {
        var connection = Minecraft.getInstance().getConnection();
        Identifier parsed = id == null ? null : Identifier.tryParse(id);
        if (connection == null || parsed == null) {
            return null;
        }
        var clientAdvancements = connection.getAdvancements();
        var accessor = (ink.tuanzi.neomacros.adapter.mixin.ClientAdvancementsAccessor)
                clientAdvancements;
        return accessor.neomacros$tree().get(parsed);
    }

    /** Resolves one client-known progress entry by id, or null when absent. */
    private static net.minecraft.advancements.AdvancementProgress advancementProgressOf(
            String id) {
        var connection = Minecraft.getInstance().getConnection();
        Identifier parsed = id == null ? null : Identifier.tryParse(id);
        if (connection == null || parsed == null) {
            return null;
        }
        var clientAdvancements = connection.getAdvancements();
        var accessor = (ink.tuanzi.neomacros.adapter.mixin.ClientAdvancementsAccessor)
                clientAdvancements;
        var node = accessor.neomacros$tree().get(parsed);
        return node == null ? null : accessor.neomacros$progress().get(node.holder());
    }

    /**
     * The namespaced id of one advancement-reward resource key (loot table or
     * recipe); the accessor renamed with the 1.21.11 identifier pivot.
     */
    private static String rewardKeyId(net.minecraft.resources.ResourceKey<?> key) {
        //? if <1.21.11 {
        /* return key.location().toString(); */
        //?}
        //? if >=1.21.11 {
        return key.identifier().toString();
        //?}
    }

    /** Copies one criteria iterable into an ordered list. */
    private static java.util.List<String> copyCriteria(Iterable<String> criteria) {
        java.util.List<String> out = new java.util.ArrayList<>();
        criteria.forEach(out::add);
        return java.util.List.copyOf(out);
    }

    /** Maps the script hand name onto the interaction hand. */
    private static InteractionHand parseHand(String hand) {
        if (hand == null || "main".equals(hand)) {
            return InteractionHand.MAIN_HAND;
        }
        if ("off".equals(hand)) {
            return InteractionHand.OFF_HAND;
        }
        throw new IllegalArgumentException("hand must be 'main' or 'off': " + hand);
    }

    /** Maps the script face name onto the block face; unknown faces throw. */
    private static net.minecraft.core.Direction parseFace(String face) {
        net.minecraft.core.Direction direction =
                face == null ? null : net.minecraft.core.Direction.byName(face);
        if (direction == null) {
            throw new IllegalArgumentException("unknown face: " + face);
        }
        return direction;
    }

    /** Lifts a world position into the neutral position type. */
    private static ink.tuanzi.neomacros.port.model.Pos3d toPos3d(net.minecraft.world.phys.Vec3 pos) {
        return new ink.tuanzi.neomacros.port.model.Pos3d(pos.x, pos.y, pos.z);
    }

    @Override
    public boolean isBreakingBlock() {
        Boolean breaking = MainThread.run(() -> {
            var gameMode = Minecraft.getInstance().gameMode;
            return gameMode != null && gameMode.isDestroying();
        });
        return Boolean.TRUE.equals(breaking);
    }

    // --- Interaction override face (G-028): a script-set crosshair target,
    // the break-outcome override and the held-use override. All three are
    // client-side script state over the local player's interaction loop.
    // The target override rewrites what target() reports (and therefore
    // what the crosshair-driven NeoMacros actions act on); it never
    // rewrites the vanilla crosshair, so it grants no server reach. Writes
    // marshal onto the game main thread and settle once applied.

    /** Stored crosshair target override: a block, an entity, or a fixed miss. */
    private record TargetOverride(String kind, int x, int y, int z, String face,
            java.util.UUID entity) {
        static TargetOverride block(int x, int y, int z, String face) {
            return new TargetOverride("block", x, y, z, face, null);
        }

        static TargetOverride entity(java.util.UUID uuid) {
            return new TargetOverride("entity", 0, 0, 0, null, uuid);
        }

        static TargetOverride miss() {
            return new TargetOverride("miss", 0, 0, 0, null, null);
        }
    }

    private volatile TargetOverride targetOverride;
    private volatile boolean targetCheckRange = true;
    private volatile boolean targetClearIfOutOfRange = true;
    private volatile boolean targetCheckAir = false;
    private volatile boolean targetClearIfIsAir = false;
    private volatile boolean targetCheckShape = true;
    private volatile boolean targetClearIfEmptyShape = false;

    private volatile boolean breakArmed;
    private volatile boolean breakTracked;
    private volatile int breakX;
    private volatile int breakY;
    private volatile int breakZ;
    /** Whether the tracked position follows the real pick (look-away ends it). */
    private volatile boolean breakCrosshairDriven;
    private volatile String breakFace = "down";
    /** The block state recorded when the tracked position was set (main thread). */
    private BlockState breakStartState;
    private volatile CompletableFuture<ink.tuanzi.neomacros.port.model.BreakBlockResult>
            breakFuture;
    private final List<java.util.function.Consumer<ink.tuanzi.neomacros.port.model.BreakBlockResult>>
            breakCallbacks = new CopyOnWriteArrayList<>();

    private volatile boolean holdActive;
    /** Ticks until the next held-use issue (main thread). */
    private int holdUseDelay;
    /** Remaining ticks of a timed hold, -1 when none (main thread). */
    private int holdForRemaining = -1;
    private boolean holdForStopOnPause = true;
    private CompletableFuture<Integer> holdForFuture;

    @Override
    public CompletableFuture<Void> setTargetOverride(int x, int y, int z, String face) {
        return dispatch(() -> setTargetOverrideNow(TargetOverride.block(x, y, z, face)));
    }

    @Override
    public CompletableFuture<Void> setTargetEntityOverride(java.util.UUID entity) {
        return dispatch(() -> {
            if (findEntity(entity) == null) {
                throw new IllegalStateException("target entity is not loaded: " + entity);
            }
            setTargetOverrideNow(TargetOverride.entity(entity));
        });
    }

    @Override
    public CompletableFuture<Void> setTargetMissedOverride() {
        return dispatch(() -> setTargetOverrideNow(TargetOverride.miss()));
    }

    @Override
    public CompletableFuture<Void> clearTargetOverride() {
        return dispatch(() -> setTargetOverrideNow(null));
    }

    @Override
    public boolean hasTargetOverride() {
        // A stored read never revalidates (JsMacros Target.hasOverride).
        return targetOverride != null;
    }

    @Override
    public CompletableFuture<Void> setTargetRangeCheck(boolean enabled, boolean autoClear) {
        return dispatch(() -> {
            targetCheckRange = enabled;
            targetClearIfOutOfRange = autoClear;
        });
    }

    @Override
    public CompletableFuture<Void> setTargetAirCheck(boolean enabled, boolean autoClear) {
        return dispatch(() -> {
            targetCheckAir = enabled;
            targetClearIfIsAir = autoClear;
        });
    }

    @Override
    public CompletableFuture<Void> setTargetShapeCheck(boolean enabled, boolean autoClear) {
        return dispatch(() -> {
            targetCheckShape = enabled;
            targetClearIfEmptyShape = autoClear;
        });
    }

    @Override
    public CompletableFuture<Void> resetTargetChecks() {
        return dispatch(() -> {
            targetCheckRange = true;
            targetClearIfOutOfRange = true;
            targetCheckAir = false;
            targetClearIfIsAir = false;
            targetCheckShape = true;
            targetClearIfEmptyShape = false;
        });
    }

    @Override
    public boolean targetInRange(float tickDelta) {
        Boolean inRange = MainThread.run(() -> {
            var override = targetOverride;
            if (override == null) {
                return false;
            }
            if ("miss".equals(override.kind())) {
                return true;
            }
            var mc = Minecraft.getInstance();
            var player = mc.player;
            if (player == null) {
                return false;
            }
            var eye = player.getEyePosition();
            if ("entity".equals(override.kind())) {
                var entity = findEntity(override.entity());
                if (entity == null) {
                    return false;
                }
                var picked =
                        entity.pick(player.entityInteractionRange() + 1.0, tickDelta, false);
                var location = picked.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY
                        ? picked.getLocation()
                        : entity.position();
                // Query only: it never clears the override.
                return eye.distanceTo(location) < player.entityInteractionRange();
            }
            var level = mc.level;
            if (level == null) {
                return false;
            }
            var pos = new BlockPos(override.x(), override.y(), override.z());
            double closestX = Math.min(Math.max(eye.x, pos.getX()), pos.getX() + 1.0);
            double closestY = Math.min(Math.max(eye.y, pos.getY()), pos.getY() + 1.0);
            double closestZ = Math.min(Math.max(eye.z, pos.getZ()), pos.getZ() + 1.0);
            return eye.distanceTo(new net.minecraft.world.phys.Vec3(
                    closestX, closestY, closestZ)) < player.blockInteractionRange();
        });
        return Boolean.TRUE.equals(inRange);
    }

    @Override
    public CompletableFuture<ink.tuanzi.neomacros.port.model.BreakBlockResult> breakBlock() {
        CompletableFuture<ink.tuanzi.neomacros.port.model.BreakBlockResult> future =
                new CompletableFuture<>();
        dispatch(() -> {
            boolean pinned = targetOverride != null;
            var hit = evaluateTargetRead().orElse(null);
            if (hit == null || hit.block() == null) {
                future.complete(new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                        "NO_TARGET", null));
                return;
            }
            var bp = hit.block().pos();
            startBreakAt((int) Math.floor(bp.x()), (int) Math.floor(bp.y()),
                    (int) Math.floor(bp.z()), !pinned, future);
        });
        return future;
    }

    @Override
    public CompletableFuture<ink.tuanzi.neomacros.port.model.BreakBlockResult>
            breakBlockAt(int x, int y, int z) {
        CompletableFuture<ink.tuanzi.neomacros.port.model.BreakBlockResult> future =
                new CompletableFuture<>();
        // An explicit position is crosshair-independent: the pick is
        // neither consulted nor moved (only target writes can end it).
        dispatch(() -> startBreakAt(x, y, z, false, future));
        return future;
    }

    /** Arms the break override at the position and starts the destroy path. */
    private void startBreakAt(int x, int y, int z, boolean crosshairDriven,
            CompletableFuture<ink.tuanzi.neomacros.port.model.BreakBlockResult> future) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        var gameMode = mc.gameMode;
        if (level == null || gameMode == null) {
            future.complete(new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                    "NOT_BREAKING", null));
            return;
        }
        var pos = new BlockPos(x, y, z);
        var state = level.getBlockState(pos);
        if (state.isAir()) {
            future.complete(new ink.tuanzi.neomacros.port.model.BreakBlockResult(
                    "IS_AIR", null));
            return;
        }
        // A new break supersedes any pending one.
        if (breakFuture != null) {
            settleBreak("TARGET_CHANGE", null);
        }
        breakArmed = true;
        breakTracked = true;
        breakX = x;
        breakY = y;
        breakZ = z;
        breakStartState = state;
        breakCrosshairDriven = crosshairDriven;
        var override = targetOverride;
        breakFace = override != null && "block".equals(override.kind())
                && override.x() == x && override.y() == y && override.z() == z
                ? override.face() : "down";
        breakFuture = future;
        var player = mc.player;
        if (player != null) {
            swing(player, InteractionHand.MAIN_HAND);
        }
        if (!gameMode.startDestroyBlock(pos, parseFace(breakFace))) {
            settleBreak("NOT_BREAKING", null);
            return;
        }
        // Instant-break modes destroy synchronously: settle right away.
        if (!level.getBlockState(pos).equals(state)) {
            settleBreak("SUCCESS", new Pos3d(x, y, z));
        }
    }

    @Override
    public CompletableFuture<Void> setBreakOverride(boolean value, String reason) {
        return dispatch(() -> {
            if (value) {
                breakArmed = true;
                breakTracked = false;
                breakStartState = null;
                breakCrosshairDriven = false;
                return;
            }
            settleBreak(reason, null);
        });
    }

    @Override
    public boolean hasBreakBlockOverride() {
        Boolean active = MainThread.run(() -> {
            if (!breakArmed) {
                return false;
            }
            var mc = Minecraft.getInstance();
            if (mc.level == null || mc.gameMode == null) {
                settleBreak("NOT_BREAKING", null);
                return false;
            }
            if (!breakTracked) {
                return true;
            }
            var state = mc.level.getBlockState(new BlockPos(breakX, breakY, breakZ));
            if (breakStartState != null && !state.equals(breakStartState)) {
                settleBreak("SUCCESS", new Pos3d(breakX, breakY, breakZ));
                return false;
            }
            if (state.isAir()) {
                settleBreak("IS_AIR", null);
                return false;
            }
            return true;
        });
        return Boolean.TRUE.equals(active);
    }

    @Override
    public CompletableFuture<Void> cancelBreakBlock() {
        return dispatch(() -> settleBreak("CANCELLED", null));
    }

    @Override
    public CompletableFuture<Void> reportBreakBlock(int x, int y, int z, boolean broken) {
        return dispatch(() -> {
            if (broken && breakArmed) {
                settleBreak("SUCCESS", new Pos3d(x, y, z));
            }
        });
    }

    @Override
    public void addBreakCallback(
            java.util.function.Consumer<ink.tuanzi.neomacros.port.model.BreakBlockResult>
                    callback,
            boolean breaking) {
        dispatch(() -> {
            if (callback != null) {
                breakCallbacks.add(callback);
            }
            if (breaking) {
                breakArmed = true;
                breakTracked = false;
                breakStartState = null;
                breakCrosshairDriven = false;
            }
        });
    }

    /**
     * Ends the break override episode: disarms it, stops client-side
     * breaking when the attack key is up, completes the pending break and
     * dispatches (then clears) the queued outcome callbacks.
     */
    private void settleBreak(String reason, Pos3d position) {
        boolean wasArmed = breakArmed;
        breakArmed = false;
        breakTracked = false;
        breakStartState = null;
        breakCrosshairDriven = false;
        var future = breakFuture;
        breakFuture = null;
        if (wasArmed && !"SUCCESS".equals(reason)) {
            var mc = Minecraft.getInstance();
            var gameMode = mc.gameMode;
            if (gameMode != null && !mc.options.keyAttack.isDown()) {
                gameMode.stopDestroyBlock();
            }
        }
        var result = new ink.tuanzi.neomacros.port.model.BreakBlockResult(reason, position);
        if (future != null) {
            future.complete(result);
        }
        if (!breakCallbacks.isEmpty()) {
            for (var callback : breakCallbacks) {
                try {
                    callback.accept(result);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            breakCallbacks.clear();
        }
    }

    /** Stores (or clears) the target override and revalidates a pending break. */
    private void setTargetOverrideNow(TargetOverride next) {
        var previous = targetOverride;
        targetOverride = next;
        if (!breakArmed || !breakTracked) {
            return;
        }
        if (previous != null && next == null) {
            // Clearing a stored pin ends a pending break (JsMacros parity).
            settleBreak("TARGET_LOST", null);
            return;
        }
        if (next == null) {
            return;
        }
        if ("block".equals(next.kind())) {
            if (next.x() == breakX && next.y() == breakY && next.z() == breakZ) {
                return;
            }
            settleBreak("TARGET_CHANGE", null);
            return;
        }
        settleBreak("TARGET_LOST", null);
    }

    @Override
    public CompletableFuture<Void> setHoldInteract(boolean holding) {
        return dispatch(() -> {
            if (holding) {
                holdActive = true;
                holdUseDelay = 0;
                issueUseNow();
                return;
            }
            releaseHold();
        });
    }

    @Override
    public CompletableFuture<Integer> holdInteractFor(int ticks, boolean stopOnPause) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        dispatch(() -> {
            if (holdForRemaining >= 0 && holdForFuture != null
                    && !holdForFuture.isDone()) {
                holdForFuture.complete(holdForRemaining);
            }
            holdForFuture = null;
            holdForRemaining = -1;
            if (ticks <= 0) {
                future.complete(0);
                return;
            }
            holdActive = true;
            holdUseDelay = 0;
            issueUseNow();
            holdForRemaining = ticks;
            holdForStopOnPause = stopOnPause;
            holdForFuture = future;
        });
        return future;
    }

    @Override
    public boolean hasInteractOverride() {
        // The flag only: it does not query the physical use key.
        return holdActive;
    }

    /** Releases the held-use override (deferring the item release check). */
    private void releaseHold() {
        if (!holdActive) {
            return;
        }
        holdActive = false;
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (gameMode != null && player != null && player.isUsingItem()
                && !mc.options.keyUse.isDown()) {
            gameMode.releaseUsingItem(player);
        }
    }

    /** Issues one crosshair-driven use action (the held-use loop's step). */
    private void issueUseNow() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null) {
            return;
        }
        if (gameMode.isDestroying() || player.isHandsBusy()) {
            return;
        }
        var hit = evaluateTargetRead().orElse(null);
        if (hit != null && hit.block() != null) {
            useBlockNow(hit.block(), "main");
        } else if (hit != null && hit.entity() != null) {
            useEntityNow(hit.entity(), "main");
        } else {
            useItemNow("main");
        }
    }

    /** Drives the break and held-use overrides once per client tick. */
    private void tickInteractionOverrides() {
        try {
            var mc = Minecraft.getInstance();
            tickBreakOverride(mc);
            tickHoldOverride(mc);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void tickBreakOverride(Minecraft mc) {
        if (!breakArmed) {
            return;
        }
        var level = mc.level;
        var gameMode = mc.gameMode;
        if (level == null || gameMode == null) {
            settleBreak("NOT_BREAKING", null);
            return;
        }
        if (!breakTracked) {
            // Adopt the first valid block target; never starts a break.
            var hit = evaluateTargetRead().orElse(null);
            if (hit != null && hit.block() != null) {
                var bp = hit.block().pos();
                var pos = new BlockPos((int) Math.floor(bp.x()),
                        (int) Math.floor(bp.y()), (int) Math.floor(bp.z()));
                var state = level.getBlockState(pos);
                if (!state.isAir()) {
                    breakTracked = true;
                    breakX = pos.getX();
                    breakY = pos.getY();
                    breakZ = pos.getZ();
                    breakStartState = state;
                    breakFace = hit.block().face();
                    breakCrosshairDriven = targetOverride == null;
                }
            }
            return;
        }
        var pos = new BlockPos(breakX, breakY, breakZ);
        var state = level.getBlockState(pos);
        if (breakStartState != null && !state.equals(breakStartState)) {
            settleBreak("SUCCESS", new Pos3d(breakX, breakY, breakZ));
            return;
        }
        if (state.isAir()) {
            settleBreak("IS_AIR", null);
            return;
        }
        if (breakCrosshairDriven && targetOverride == null) {
            // No pin stored: looking away from the tracked block ends it.
            var hit = realPick().orElse(null);
            if (hit == null || hit.block() == null) {
                settleBreak("TARGET_LOST", null);
                return;
            }
            var bp = hit.block().pos();
            if ((int) Math.floor(bp.x()) != breakX
                    || (int) Math.floor(bp.y()) != breakY
                    || (int) Math.floor(bp.z()) != breakZ) {
                settleBreak("TARGET_CHANGE", null);
                return;
            }
        }
        if (breakFuture != null && !gameMode.continueDestroyBlock(pos, parseFace(breakFace))) {
            var after = level.getBlockState(pos);
            if (breakStartState != null && !after.equals(breakStartState)) {
                settleBreak("SUCCESS", new Pos3d(breakX, breakY, breakZ));
            } else {
                settleBreak("NOT_BREAKING", null);
            }
        }
    }

    private void tickHoldOverride(Minecraft mc) {
        if (holdActive && mc.options.keyUse.isDown()) {
            // The physical use key takes over: drop the override flag.
            holdActive = false;
        }
        if (holdActive && holdUseDelay > 0) {
            holdUseDelay--;
        } else if (holdActive) {
            var player = mc.player;
            var gameMode = mc.gameMode;
            if (player != null && gameMode != null && !player.isUsingItem()
                    && !player.isHandsBusy() && !gameMode.isDestroying()) {
                issueUseNow();
                holdUseDelay = 4; // mirrors the vanilla right-click delay
            }
        }
        if (holdForRemaining < 0) {
            return;
        }
        if (!holdActive) {
            finishHoldFor();
            return;
        }
        boolean paused = mc.isPaused();
        if (paused && holdForStopOnPause) {
            releaseHold();
            finishHoldFor();
            return;
        }
        if (paused) {
            return; // paused ticks never count
        }
        holdForRemaining--;
        if (holdForRemaining <= 0) {
            releaseHold();
            finishHoldFor();
        }
    }

    private void finishHoldFor() {
        int remaining = Math.max(holdForRemaining, 0);
        holdForRemaining = -1;
        var future = holdForFuture;
        holdForFuture = null;
        if (future != null) {
            future.complete(remaining);
        }
    }

    // --- Sign editor write face and panorama capture (G-037) ---

    @Override
    public boolean writeSign(List<String> lines) {
        Boolean open = MainThread.run(() -> {
            var screen = FabricLifecyclePort.currentScreen();
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
                    editor)) {
                return false;
            }
            String[] messages = ((ink.tuanzi.neomacros.adapter.mixin.AbstractSignEditScreenAccessor)
                    editor).neomacros$messages();
            if (messages != null && lines != null) {
                for (int i = 0; i < lines.size() && i < messages.length; i++) {
                    String line = lines.get(i);
                    if (line != null) {
                        messages[i] = line;
                    }
                }
            }
            // The buffer is the editor's live text: it renders from it and
            // submits it to the server when the editor closes (vanilla
            // AbstractSignEditScreen.removed), so nothing else is needed.
            return true;
        });
        if (open == null) {
            throw new IllegalStateException(
                    "sign editor write never reached the game main thread");
        }
        return open;
    }

    @Override
    public CompletableFuture<String> takePanorama(String folder) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                var root = ink.tuanzi.neomacros.fabric.NeoMacrosFabric.macrosDir();
                if (root == null) {
                    throw new IllegalStateException("the macro folder is not initialized");
                }
                java.nio.file.Path base = root.toAbsolutePath().normalize();
                java.nio.file.Path target = base.resolve(folder).normalize();
                if (!target.startsWith(base)) {
                    throw new SecurityException(
                            "path escapes the macro folder: " + folder);
                }
                // The capture routine writes panorama_0..5.png into this
                // directory; vanilla assumes it exists, so create it here.
                java.nio.file.Path screenshots = target.resolve("screenshots");
                java.nio.file.Files.createDirectories(screenshots);

                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.level == null) {
                    throw new IllegalStateException(
                            "panorama capture requires an in-world player");
                }
                // Renders the six faces on this (main) thread, resizes the
                // framebuffer to the routine's fixed 4096x4096 and queues the
                // six PNG writes; the message names the target directory.
                var message = mc.grabPanoramixScreenshot(screenshots.toFile());
                future.complete(message != null ? message.getString()
                        : screenshots.toString());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }
}
