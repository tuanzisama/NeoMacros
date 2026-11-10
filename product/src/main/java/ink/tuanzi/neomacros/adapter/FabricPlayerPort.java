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
        return dispatch(() -> {
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
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> useBlock(BlockInteraction hit,
            String hand) {
        return dispatch(() -> {
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
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> useItem(String hand) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            var gameMode = Minecraft.getInstance().gameMode;
            if (player == null || gameMode == null) {
                throw new IllegalStateException("no local player");
            }
            var used = gameMode.useItem(player, parseHand(hand));
            if (used.consumesAction()) {
                swing(player, parseHand(hand));
            }
        });
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
        return MainThread.run(() -> {
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
        });
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
        return MainThread.run(() -> {
            var connection = Minecraft.getInstance().getConnection();
            Identifier parsed = id == null ? null : Identifier.tryParse(id);
            if (connection == null || parsed == null) {
                return java.util.Optional
                        .<ink.tuanzi.neomacros.port.model.AdvancementProgressData>empty();
            }
            var clientAdvancements = connection.getAdvancements();
            var accessor = (ink.tuanzi.neomacros.adapter.mixin.ClientAdvancementsAccessor)
                    clientAdvancements;
            var node = accessor.neomacros$tree().get(parsed);
            var progress = node == null ? null
                    : accessor.neomacros$progress().get(node.holder());
            if (progress == null) {
                return java.util.Optional
                        .<ink.tuanzi.neomacros.port.model.AdvancementProgressData>empty();
            }
            return java.util.Optional.of(new ink.tuanzi.neomacros.port.model.AdvancementProgressData(
                    progress.isDone(),
                    copyCriteria(progress.getCompletedCriteria()),
                    copyCriteria(progress.getRemainingCriteria())));
        });
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
}
