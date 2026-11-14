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
import ink.tuanzi.neomacros.core.api.types.Component;
import ink.tuanzi.neomacros.core.api.types.TextComponent;
import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.core.api.types.ItemStackBuilder;
import ink.tuanzi.neomacros.core.api.types.Enchantment;
import ink.tuanzi.neomacros.core.api.types.Pos2D;
import ink.tuanzi.neomacros.core.api.types.Pos3D;
import ink.tuanzi.neomacros.core.api.types.RayHit;
import ink.tuanzi.neomacros.core.runtime.TimerHandle;

import java.util.List;

/**
 * The explicit-triple gate's scan surface: every registered API module class, every
 * {@code @ApiClass} script type, and the timer handle. Event payload records
 * join through their own list. Keep in sync with production registration
 * (the module class list and the guest-type table).
 */
final class ApiCompletenessSurface {

    private ApiCompletenessSurface() {
    }

    //** API module classes ({@code @ApiModule}). */
    static final List<Class<?>> MODULE_CLASSES = List.of(
            EventsApi.class, ThreadApi.class, ChatApi.class, StorageApi.class,
            PerformanceApi.class,
            WorldApi.class, EntityApi.class, PlayerApi.class, ClientApi.class,
            JavaApi.class, ReflectApi.class, FsApi.class, NetApi.class,
            InputApi.class, CommandApi.class, HudApi.class, RenderApi.class);

    /** Script-facing class types ({@code @ApiClass}). */
    static final List<Class<?>> CLASSES = List.of(
            EntityApi.Entity.class, EntityApi.ItemEntity.class, EntityApi.LivingEntity.class,
            PlayerApi.Player.class, PlayerApi.LocalPlayer.class,
            ClientApi.Options.class, ClientApi.VideoOptions.class,
            ClientApi.SkinOptions.class, ClientApi.MusicOptions.class,
            ClientApi.ControlOptions.class, ClientApi.ChatOptions.class,
            ClientApi.AccessibilityOptions.class,
            PlayerApi.Inventory.class, PlayerApi.PlayerInventory.class,
            PlayerApi.Interactions.class, PlayerApi.BreakBlockResult.class,
            Pos2D.class, Pos3D.class, BlockPos.class, ChunkPos.class,
            Block.class, BlockState.class, RayHit.class,
            ItemStack.class, ItemStackBuilder.class, Enchantment.class,
            ink.tuanzi.neomacros.core.api.types.Image.class,
            ink.tuanzi.neomacros.core.api.types.PacketBuffer.class,
            FsApi.FileHandle.class, NetApi.Ws.class,
            SpeciesEntities.MobEntity.class, SpeciesEntities.AnimalEntity.class, SpeciesEntities.TamableAnimalEntity.class,
            SpeciesEntities.SheepEntity.class, SpeciesEntities.WolfEntity.class, SpeciesEntities.CatEntity.class,
            SpeciesEntities.VillagerEntity.class, SpeciesEntities.CreeperEntity.class, SpeciesEntities.ZombieEntity.class,
            SpeciesEntities.PrimedTntEntity.class, SpeciesEntities.BatEntity.class, SpeciesEntities.MushroomCowEntity.class,
            SpeciesEntities.AbstractHorseEntity.class, SpeciesEntities.AbstractChestedHorseEntity.class,
            SpeciesEntities.DonkeyEntity.class, SpeciesEntities.EndermanEntity.class, SpeciesEntities.HorseEntity.class,
            SpeciesEntities.IronGolemEntity.class, SpeciesEntities.LlamaEntity.class, SpeciesEntities.PhantomEntity.class,
            SpeciesEntities.SpiderEntity.class,
            SpeciesEntities.ChickenEntity.class, SpeciesEntities.GhastEntity.class,
            SpeciesEntities.OcelotEntity.class, SpeciesEntities.RabbitEntity.class,
            SpeciesEntities.StriderEntity.class, SpeciesEntities.WitherBossEntity.class,
            SpeciesEntities.AxolotlEntity.class, SpeciesEntities.BeeEntity.class,
            SpeciesEntities.FoxEntity.class, SpeciesEntities.FrogEntity.class,
            SpeciesEntities.GoatEntity.class, SpeciesEntities.PandaEntity.class,
            SpeciesEntities.ParrotEntity.class, SpeciesEntities.PolarBearEntity.class,
            SpeciesEntities.ShulkerEntity.class, SpeciesEntities.SnowGolemEntity.class,
            SpeciesEntities.TurtleEntity.class, SpeciesEntities.VexEntity.class,
            SpeciesEntities.WitchEntity.class, SpeciesEntities.DolphinEntity.class,
            SpeciesEntities.WardenEntity.class, SpeciesEntities.DrownedEntity.class,
            SpeciesEntities.ZombieVillagerEntity.class, SpeciesEntities.ZombifiedPiglinEntity.class,
            SpeciesEntities.AllayEntity.class, SpeciesEntities.CowEntity.class,
            SpeciesEntities.ArmadilloEntity.class, SpeciesEntities.CamelEntity.class,
            SpeciesEntities.SnifferEntity.class,
            SpeciesEntities.AbstractFishEntity.class, SpeciesEntities.AbstractSchoolingFishEntity.class,
            SpeciesEntities.SalmonEntity.class, SpeciesEntities.CodEntity.class,
            SpeciesEntities.PufferfishEntity.class, SpeciesEntities.TropicalFishEntity.class,
            SpeciesEntities.PiglinEntity.class, SpeciesEntities.PiglinBruteEntity.class,
            SpeciesEntities.PillagerEntity.class, SpeciesEntities.EvokerEntity.class,
            SpeciesEntities.WanderingTraderEntity.class, SpeciesEntities.EnderDragonEntity.class,
            SpeciesEntities.BreezeEntity.class, SpeciesEntities.CreakingEntity.class,
            SpeciesEntities.ArmorStandEntity.class, SpeciesEntities.FallingBlockEntity.class,
            SpeciesEntities.ItemFrameEntity.class, SpeciesEntities.AreaEffectCloudEntity.class,
            Chunk.class,
            PlayerApi.FurnaceInventory.class,
            PlayerApi.ContainerInventory.class, PlayerApi.RecipeInventory.class,
            PlayerApi.CraftingInventory.class,
            PlayerApi.AnvilInventory.class, PlayerApi.EnchantInventory.class,
            PlayerApi.GrindstoneInventory.class, PlayerApi.StonecutterInventory.class,
            PlayerApi.SmithingInventory.class, PlayerApi.CartographyInventory.class,
            PlayerApi.BeaconInventory.class, PlayerApi.BrewingStandInventory.class,
            PlayerApi.VillagerInventory.class,
            PlayerApi.TradeOffer.class, PlayerApi.HorseInventory.class,
            PlayerApi.LoomInventory.class, PlayerApi.CreativeInventory.class,
            PlayerApi.Abilities.class,
            Component.class, TextComponent.class,
            ink.tuanzi.neomacros.core.api.SuggestionsBuilder.class,
            PlayerApi.Screen.class, PlayerApi.InventoryScreen.class,
            PlayerApi.ContainerScreen.class, PlayerApi.ChatScreen.class,
            PlayerApi.SignScreen.class, PlayerApi.BookEditScreen.class,
            PlayerApi.BookViewScreen.class, PlayerApi.DialogScreen.class,
            PlayerApi.TitleScreen.class, PlayerApi.PauseScreen.class,
            PlayerApi.DeathScreen.class, PlayerApi.CreativeScreen.class,
            PlayerApi.ProgressScreen.class, PlayerApi.DisconnectedScreen.class,
            PlayerApi.WinScreen.class, PlayerApi.ConfirmScreen.class,
            PlayerApi.CommandBlockScreen.class, PlayerApi.StructureBlockScreen.class,
            PlayerApi.JigsawScreen.class,
            PlayerApi.DialogHandle.class, PlayerApi.DialogAction.class,
            PlayerApi.DialogInput.class,
            TimerHandle.class);

    /** Event payload records ({@code @ApiEvent}); wired in the bootstraps. */
    static final List<Class<?>> EVENTS =
            ink.tuanzi.neomacros.core.event.ProductionEventSet.records();
}
