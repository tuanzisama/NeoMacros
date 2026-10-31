package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.model.BlockInteraction;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.PlayerSnapshot;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.Rot2d;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;
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
