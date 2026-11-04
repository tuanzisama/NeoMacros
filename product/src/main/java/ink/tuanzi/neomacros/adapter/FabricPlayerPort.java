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
            //? if <26.1 {
            /* Minecraft.getInstance().gameMode.attack(player, entity);
            player.swing(InteractionHand.MAIN_HAND); */
            //?}
            //? if >=26.1 {
            throw new UnsupportedOperationException(
                    "attack action pending 26.x interaction API research");
            //?}
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> useEntity(EntitySnapshot target) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            var entity = findEntity(target.uuid());
            if (player == null || entity == null) {
                throw new IllegalStateException("target entity is not loaded: " + target.uuid());
            }
            //? if <26.1 {
            /* Minecraft.getInstance().gameMode.interact(player, entity,
                    InteractionHand.MAIN_HAND); */
            //?}
            //? if >=26.1 {
            throw new UnsupportedOperationException(
                    "useEntity action pending 26.x interaction API research");
            //?}
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> useBlock(BlockInteraction hit) {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                throw new IllegalStateException("no local player");
            }
            var pos = new BlockPos((int) Math.floor(hit.pos().x()),
                    (int) Math.floor(hit.pos().y()), (int) Math.floor(hit.pos().z()));
            var hitResult = new net.minecraft.world.phys.BlockHitResult(
                    net.minecraft.world.phys.Vec3.atCenterOf(pos),
                    net.minecraft.core.Direction.UP, pos, false);
            //? if <26.1 {
            /* Minecraft.getInstance().gameMode.useItemOn(player,
                    InteractionHand.MAIN_HAND, hitResult); */
            //?}
            //? if >=26.1 {
            throw new UnsupportedOperationException(
                    "useBlock action pending 26.x interaction API research");
            //?}
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> swingHand() {
        return dispatch(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            //? if <26.1 {
            /* player.swing(InteractionHand.MAIN_HAND); */
            //?}
            //? if >=26.1 {
            throw new UnsupportedOperationException(
                    "swingHand action pending 26.x interaction API research");
            //?}
        });
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
            throw new UnsupportedOperationException(
                    "dropHeld action pending 26.x interaction API research");
            //?}
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
                verdict(shouldFail(attackBlockSinks, blockHit(world, pos))));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                verdict(shouldFail(attackEntitySinks, FabricEntityPort.snapshotOf(entity))));
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                verdict(shouldFail(interactBlockSinks, blockHit(world, hitResult.getBlockPos()))));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                verdict(shouldFail(interactEntitySinks, FabricEntityPort.snapshotOf(entity))));
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
                    player.getAirSupply()));
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

    /** Builds the neutral block hit for one world position. */
    private static BlockInteraction blockHit(net.minecraft.world.level.Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return new BlockInteraction(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                new Pos3d(pos.getX(), pos.getY(), pos.getZ()));
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
}
