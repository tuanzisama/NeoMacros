package ink.tuanzi.neomacros.core.api.bindgen;

import ink.tuanzi.neomacros.core.api.ChatApi;
import ink.tuanzi.neomacros.core.api.CommandApi;
import ink.tuanzi.neomacros.core.api.EventsApi;
import ink.tuanzi.neomacros.core.api.FsApi;
import ink.tuanzi.neomacros.core.api.HudApi;
import ink.tuanzi.neomacros.core.api.InputApi;
import ink.tuanzi.neomacros.core.api.NetApi;
import ink.tuanzi.neomacros.core.api.PerformanceApi;
import ink.tuanzi.neomacros.core.api.StorageApi;
import ink.tuanzi.neomacros.core.api.ThreadApi;
import ink.tuanzi.neomacros.core.api.client.ClientApi;
import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.entity.SpeciesEntities;
import ink.tuanzi.neomacros.core.api.java.JavaApi;
import ink.tuanzi.neomacros.core.api.player.PlayerApi;
import ink.tuanzi.neomacros.core.api.reflect.ReflectApi;
import ink.tuanzi.neomacros.core.api.render.RenderApi;
import ink.tuanzi.neomacros.core.api.world.WorldApi;
import ink.tuanzi.neomacros.core.api.types.Block;
import ink.tuanzi.neomacros.core.api.types.BlockPos;
import ink.tuanzi.neomacros.core.api.types.BlockState;
import ink.tuanzi.neomacros.core.api.types.ChunkPos;
import ink.tuanzi.neomacros.core.api.types.Chunk;
import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.core.api.types.ItemStackBuilder;
import ink.tuanzi.neomacros.core.api.types.Pos2D;
import ink.tuanzi.neomacros.core.api.types.Pos3D;
import ink.tuanzi.neomacros.core.api.types.RayHit;
import ink.tuanzi.neomacros.core.runtime.TimerHandle;

import java.util.List;

/**
 * The explicit-triple gate's scan surface: every registered facade, every
 * {@code @ApiClass} script type, and the timer handle. Event payload records
 * join through their own list. Keep in sync with production registration
 * (the facade registry list and the guest-type table).
 */
final class ApiCompletenessSurface {

    private ApiCompletenessSurface() {
    }

    /** Facade classes ({@code @ApiModule}). */
    static final List<Class<?>> FACADES = List.of(
            EventsApi.class, ThreadApi.class, ChatApi.class, StorageApi.class,
            PerformanceApi.class,
            WorldApi.class, EntityApi.class, PlayerApi.class, ClientApi.class,
            JavaApi.class, ReflectApi.class, FsApi.class, NetApi.class,
            InputApi.class, CommandApi.class, HudApi.class, RenderApi.class,
            ink.tuanzi.neomacros.core.api.screen.ScreenApi.class);

    /** Script-facing class types ({@code @ApiClass}). */
    static final List<Class<?>> CLASSES = List.of(
            EntityApi.Entity.class, EntityApi.ItemEntity.class, EntityApi.LivingEntity.class,
            PlayerApi.Player.class, PlayerApi.LocalPlayer.class,
            PlayerApi.Inventory.class, PlayerApi.PlayerInventory.class,
            Pos2D.class, Pos3D.class, BlockPos.class, ChunkPos.class,
            Block.class, BlockState.class, RayHit.class,
            ItemStack.class, ItemStackBuilder.class,
            ink.tuanzi.neomacros.core.api.types.Image.class,
            FsApi.FileHandle.class, NetApi.Ws.class,
            SpeciesEntities.Mob.class, SpeciesEntities.Animal.class, SpeciesEntities.TamableAnimal.class,
            SpeciesEntities.Sheep.class, SpeciesEntities.Wolf.class, SpeciesEntities.Cat.class,
            SpeciesEntities.Villager.class, SpeciesEntities.Creeper.class, SpeciesEntities.Zombie.class,
            SpeciesEntities.PrimedTnt.class, SpeciesEntities.Bat.class, SpeciesEntities.MushroomCow.class,
            SpeciesEntities.AbstractHorse.class, SpeciesEntities.AbstractChestedHorse.class,
            SpeciesEntities.Donkey.class, SpeciesEntities.Enderman.class, SpeciesEntities.Horse.class,
            SpeciesEntities.IronGolem.class, SpeciesEntities.Llama.class, SpeciesEntities.Phantom.class,
            SpeciesEntities.Spider.class,
            Chunk.class,
            PlayerApi.FurnaceInventory.class,
            TimerHandle.class);

    /** Event payload records ({@code @ApiEvent}); wired in the bootstraps. */
    static final List<Class<?>> EVENTS =
            ink.tuanzi.neomacros.core.event.ProductionEventSet.records();
}
