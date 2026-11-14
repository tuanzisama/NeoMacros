package ink.tuanzi.neomacros.fabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import ink.tuanzi.neomacros.adapter.FabricCommandPort;
import ink.tuanzi.neomacros.adapter.MainThread;
import ink.tuanzi.neomacros.adapter.NeomacrosAdapterImpl;
import ink.tuanzi.neomacros.core.NeoMacros;
import ink.tuanzi.neomacros.core.api.ChatApi;
import ink.tuanzi.neomacros.core.api.ThreadApi;
import ink.tuanzi.neomacros.core.api.CommandApi;
import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.EventsApi;
import ink.tuanzi.neomacros.core.api.FsApi;
import ink.tuanzi.neomacros.core.api.InputApi;
import ink.tuanzi.neomacros.core.api.NetApi;
import ink.tuanzi.neomacros.core.api.PerformanceApi;
import ink.tuanzi.neomacros.core.api.player.PlayerApi;
import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.StorageApi;
import ink.tuanzi.neomacros.core.api.world.WorldApi;
import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.config.ConfigManager;
import ink.tuanzi.neomacros.core.config.CoreOptions;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.event.PortEventWiring;
import ink.tuanzi.neomacros.core.event.ProductionEventSet;
import ink.tuanzi.neomacros.core.event.SyncWatchdog;
import ink.tuanzi.neomacros.core.module.LoadedModule;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.module.ScriptEntry;
import ink.tuanzi.neomacros.core.module.manifest.ScriptCategory;
import ink.tuanzi.neomacros.core.runtime.ScriptEngine;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.security.TrustManager;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.engine.GraalJsEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Fabric client entrypoint: assembles the script runtime for this node
 * (engine, adapter ports, event framework, threading), prepares the macros
 * folder layout with first-install examples, wires manifest key bindings,
 * and registers the root {@code /neomacros} client command.
 */
public final class NeoMacrosFabric implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(NeoMacros.MOD_ID);

    private static ScriptRuntime runtime;
    private static ConfigManager configManager;
    private static CoreOptions options;
    private static Path configDir;
    private static Path macrosDir;
    private static ModuleManager modules;
    private static NeomacrosAdapterImpl adapter;
    private static TrustManager trustManager;
    private static java.util.concurrent.ConcurrentHashMap<String, String> pendingTrusts;
    private static boolean quitOnDone;
    private static ink.tuanzi.neomacros.core.task.TaskRegistry tasks;
    private static ink.tuanzi.neomacros.fabric.web.WebService webService;
    private static ink.tuanzi.neomacros.fabric.mcp.McpService mcpService;
    private static ink.tuanzi.neomacros.fabric.web.WebRuntime webRuntime;
    private static ink.tuanzi.neomacros.fabric.update.UpdateChecker updateChecker;

    /**
     * The version string this install actually runs (channel-suffixed, e.g.
     * {@code 0.1.0-dev.123+abc1234}), resolved from the fabric.mod.json
     * metadata the build stamps; {@link NeoMacros#VERSION} stays the plain
     * baseline fallback.
     */
    private static String runtimeVersion = NeoMacros.VERSION;

    @Override
    public void onInitializeClient() {
        LOGGER.info("NeoMacros bootstrap");
        runtimeVersion = FabricLoader.getInstance()
                .getModContainer(NeoMacros.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(NeoMacros.VERSION);
        configDir = FabricLoader.getInstance().getConfigDir().resolve(NeoMacros.CONFIG_DIR_NAME);
        // Carry a pre-rename options.json over so player settings survive.
        Path configFile = configDir.resolve("config.json");
        Path legacyConfigFile = configDir.resolve("options.json");
        if (!Files.exists(configFile) && Files.exists(legacyConfigFile)) {
            try {
                Files.move(legacyConfigFile, configFile);
                LOGGER.info("neomacros: migrated options.json to config.json");
            } catch (IOException e) {
                LOGGER.warn("neomacros: cannot migrate options.json to config.json: {}",
                        e.getMessage());
            }
        }
        configManager = new ConfigManager(configFile,
                message -> LOGGER.warn("neomacros config: {}", message));
        options = new CoreOptions();
        configManager.register(options);
        configManager.load();
        configManager.createIfMissing();
        // The packet hub reads packetIgnore off this instance on every fire,
        // so config reloads take effect without re-binding (G-002 §3).
        ink.tuanzi.neomacros.adapter.PacketHub.configure(options);

        macrosDir = options.macrosDir.isEmpty()
                ? configDir.resolve("macros") : Path.of(options.macrosDir);
        boolean seeded = ExampleSeeder.ensureLayout(macrosDir,
                message -> LOGGER.info("neomacros: {}", message));
        if (!seeded) {
            noteLegacyScriptsDir();
        }

        adapter = new NeomacrosAdapterImpl();
        var catalog = new EventCatalog();
        // Explicit registration: the classpath scanner cannot see mod classes
        // in nested-jar deployments (java.class.path is not mod-resolved), so
        // the event list comes from the shared production set. Keep in sync
        // with core/event/def.
        catalog.registerAll(ProductionEventSet.records());
        var pool = new ScriptThreadPool(Runtime.getRuntime().availableProcessors());
        var bus = new EventBus(catalog, pool.workers());
        // Behind the packet hub's hasDispatchTarget fast path (G-002 §3).
        ink.tuanzi.neomacros.adapter.PacketHub.bind(bus);
        var registry = new BindingRegistry(mcVersion());

        var ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "neomacros-io");
            thread.setDaemon(true);
            return thread;
        });
        registry.registerAll(java.util.List.of(
                EventsApi.class, ThreadApi.class, ChatApi.class, StorageApi.class,
                PerformanceApi.class,
                WorldApi.class, EntityApi.class, PlayerApi.class,
                ink.tuanzi.neomacros.core.api.client.ClientApi.class));
        registry.registerInstance(new ink.tuanzi.neomacros.core.api.reflect.ReflectApi(
                ink.tuanzi.neomacros.adapter.FabricClassResolver::resolve));
        registry.registerInstance(
                new ink.tuanzi.neomacros.core.api.java.JavaApi(
                        ink.tuanzi.neomacros.adapter.FabricClassResolver::resolve,
                        NeoMacrosFabric::wrapRaw));
        
        registry.registerGuestType("Entity", EntityApi.Entity.class);
        registry.registerGuestType("Player", PlayerApi.Player.class);
        registry.registerGuestType("LocalPlayer", PlayerApi.LocalPlayer.class);
        registry.registerGuestType("Options",
                ink.tuanzi.neomacros.core.api.client.ClientApi.Options.class);
        registry.registerGuestType("VideoOptions",
                ink.tuanzi.neomacros.core.api.client.ClientApi.VideoOptions.class);
        registry.registerGuestType("SkinOptions",
                ink.tuanzi.neomacros.core.api.client.ClientApi.SkinOptions.class);
        registry.registerGuestType("MusicOptions",
                ink.tuanzi.neomacros.core.api.client.ClientApi.MusicOptions.class);
        registry.registerGuestType("ControlOptions",
                ink.tuanzi.neomacros.core.api.client.ClientApi.ControlOptions.class);
        registry.registerGuestType("ChatOptions",
                ink.tuanzi.neomacros.core.api.client.ClientApi.ChatOptions.class);
        registry.registerGuestType("AccessibilityOptions",
                ink.tuanzi.neomacros.core.api.client.ClientApi.AccessibilityOptions.class);
        for (var descriptor : catalog.all()) {
            registry.registerEventType(descriptor.name(), descriptor.cancellable(), descriptor.direction());
        }

        registry.registerGuestType("Pos2D", ink.tuanzi.neomacros.core.api.types.Pos2D.class);
        registry.registerGuestType("Pos3D", ink.tuanzi.neomacros.core.api.types.Pos3D.class);
        registry.registerGuestType("ChunkPos", ink.tuanzi.neomacros.core.api.types.ChunkPos.class);
        registry.registerGuestType("BlockPos", ink.tuanzi.neomacros.core.api.types.BlockPos.class);
        registry.registerGuestType("Block", ink.tuanzi.neomacros.core.api.types.Block.class);
        registry.registerGuestType("BlockState", ink.tuanzi.neomacros.core.api.types.BlockState.class);
        registry.registerGuestType("RayHit", ink.tuanzi.neomacros.core.api.types.RayHit.class);
        registry.registerGuestType("ItemStack", ink.tuanzi.neomacros.core.api.types.ItemStack.class);
        registry.registerGuestType("ItemStackBuilder", ink.tuanzi.neomacros.core.api.types.ItemStackBuilder.class);
        registry.registerGuestType("Enchantment", ink.tuanzi.neomacros.core.api.types.Enchantment.class);
        registry.registerGuestType("Image", ink.tuanzi.neomacros.core.api.types.Image.class);
        registry.registerGuestType("CustomImage", ink.tuanzi.neomacros.core.api.types.CustomImage.class);
        registry.registerGuestType("HudLayer", ink.tuanzi.neomacros.core.api.render.HudLayer.class);
        registry.registerGuestType("Draw2DElement", ink.tuanzi.neomacros.core.api.render.components.Draw2DElement.class);
        registry.registerGuestType("Draw2DText", ink.tuanzi.neomacros.core.api.render.components.Draw2DText.class);
        registry.registerGuestType("Draw2DRect", ink.tuanzi.neomacros.core.api.render.components.Draw2DRect.class);
        registry.registerGuestType("Draw2DLine", ink.tuanzi.neomacros.core.api.render.components.Draw2DLine.class);
        registry.registerGuestType("Draw3D", ink.tuanzi.neomacros.core.api.render.Draw3D.class);
        registry.registerGuestType("Box", ink.tuanzi.neomacros.core.api.render.components3d.Box.class);
        registry.registerGuestType("Line3D", ink.tuanzi.neomacros.core.api.render.components3d.Line3D.class);
        registry.registerGuestType("RenderElement3D",
                ink.tuanzi.neomacros.core.api.render.components3d.RenderElement3D.class);
        registry.registerGuestType("Surface", ink.tuanzi.neomacros.core.api.render.components3d.Surface.class);
        registry.registerGuestType("TraceLine", ink.tuanzi.neomacros.core.api.render.components3d.TraceLine.class);
        registry.registerGuestType("EntityTraceLine",
                ink.tuanzi.neomacros.core.api.render.components3d.EntityTraceLine.class);
        registry.registerGuestType("PacketBuffer",
                ink.tuanzi.neomacros.core.api.types.PacketBuffer.class);
        registry.registerGuestType("FileHandle",
                ink.tuanzi.neomacros.core.api.FsApi.FileHandle.class);
        registry.registerGuestType("Ws", ink.tuanzi.neomacros.core.api.NetApi.Ws.class);
        registry.registerGuestType("MobEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.MobEntity.class);
        registry.registerGuestType("AnimalEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AnimalEntity.class);
        registry.registerGuestType("TamableAnimalEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.TamableAnimalEntity.class);
        registry.registerGuestType("SheepEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.SheepEntity.class);
        registry.registerGuestType("WolfEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.WolfEntity.class);
        registry.registerGuestType("CatEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.CatEntity.class);
        registry.registerGuestType("VillagerEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.VillagerEntity.class);
        registry.registerGuestType("CreeperEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.CreeperEntity.class);
        registry.registerGuestType("ZombieEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ZombieEntity.class);
        registry.registerGuestType("PrimedTntEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PrimedTntEntity.class);
        registry.registerGuestType("BatEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.BatEntity.class);
        registry.registerGuestType("MushroomCowEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.MushroomCowEntity.class);
        registry.registerGuestType("AbstractHorseEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractHorseEntity.class);
        registry.registerGuestType("AbstractChestedHorseEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractChestedHorseEntity.class);
        registry.registerGuestType("DonkeyEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.DonkeyEntity.class);
        registry.registerGuestType("EndermanEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.EndermanEntity.class);
        registry.registerGuestType("HorseEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.HorseEntity.class);
        registry.registerGuestType("IronGolemEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.IronGolemEntity.class);
        registry.registerGuestType("LlamaEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.LlamaEntity.class);
        registry.registerGuestType("PhantomEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PhantomEntity.class);
        registry.registerGuestType("SpiderEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.SpiderEntity.class);
        registry.registerGuestType("ChickenEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ChickenEntity.class);
        registry.registerGuestType("GhastEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.GhastEntity.class);
        registry.registerGuestType("OcelotEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.OcelotEntity.class);
        registry.registerGuestType("RabbitEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.RabbitEntity.class);
        registry.registerGuestType("StriderEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.StriderEntity.class);
        registry.registerGuestType("WitherBossEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.WitherBossEntity.class);
        registry.registerGuestType("Chunk", ink.tuanzi.neomacros.core.api.types.Chunk.class);
        registry.registerGuestType("FurnaceInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.FurnaceInventory.class);
        registry.registerGuestType("ContainerInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.ContainerInventory.class);
        registry.registerGuestType("RecipeInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.RecipeInventory.class);
        registry.registerGuestType("CraftingInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.CraftingInventory.class);
        registry.registerGuestType("AnvilInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.AnvilInventory.class);
        registry.registerGuestType("EnchantInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.EnchantInventory.class);
        registry.registerGuestType("GrindstoneInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.GrindstoneInventory.class);
        registry.registerGuestType("StonecutterInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.StonecutterInventory.class);
        registry.registerGuestType("SmithingInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.SmithingInventory.class);
        registry.registerGuestType("CartographyInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.CartographyInventory.class);
        registry.registerGuestType("BeaconInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.BeaconInventory.class);
        registry.registerGuestType("BrewingStandInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.BrewingStandInventory.class);
        registry.registerGuestType("VillagerInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.VillagerInventory.class);
        registry.registerGuestType("TradeOffer", ink.tuanzi.neomacros.core.api.player.PlayerApi.TradeOffer.class);
        registry.registerGuestType("HorseInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.HorseInventory.class);
        registry.registerGuestType("LoomInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.LoomInventory.class);
        registry.registerGuestType("CreativeInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.CreativeInventory.class);
        registry.registerGuestType("Abilities", ink.tuanzi.neomacros.core.api.player.PlayerApi.Abilities.class);
        registry.registerGuestType("AxolotlEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AxolotlEntity.class);
        registry.registerGuestType("BeeEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.BeeEntity.class);
        registry.registerGuestType("FoxEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.FoxEntity.class);
        registry.registerGuestType("FrogEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.FrogEntity.class);
        registry.registerGuestType("GoatEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.GoatEntity.class);
        registry.registerGuestType("PandaEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PandaEntity.class);
        registry.registerGuestType("ParrotEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ParrotEntity.class);
        registry.registerGuestType("PolarBearEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PolarBearEntity.class);
        registry.registerGuestType("ShulkerEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ShulkerEntity.class);
        registry.registerGuestType("SnowGolemEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.SnowGolemEntity.class);
        registry.registerGuestType("TurtleEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.TurtleEntity.class);
        registry.registerGuestType("VexEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.VexEntity.class);
        registry.registerGuestType("WitchEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.WitchEntity.class);
        registry.registerGuestType("DolphinEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.DolphinEntity.class);
        registry.registerGuestType("WardenEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.WardenEntity.class);
        registry.registerGuestType("DrownedEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.DrownedEntity.class);
        registry.registerGuestType("ZombieVillagerEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ZombieVillagerEntity.class);
        registry.registerGuestType("ZombifiedPiglinEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ZombifiedPiglinEntity.class);
        registry.registerGuestType("AllayEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AllayEntity.class);
        registry.registerGuestType("CowEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.CowEntity.class);
        registry.registerGuestType("ArmadilloEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ArmadilloEntity.class);
        registry.registerGuestType("CamelEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.CamelEntity.class);
        registry.registerGuestType("SnifferEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.SnifferEntity.class);
        registry.registerGuestType("AbstractFishEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractFishEntity.class);
        registry.registerGuestType("AbstractSchoolingFishEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractSchoolingFishEntity.class);
        registry.registerGuestType("SalmonEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.SalmonEntity.class);
        registry.registerGuestType("CodEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.CodEntity.class);
        registry.registerGuestType("PufferfishEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PufferfishEntity.class);
        registry.registerGuestType("TropicalFishEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.TropicalFishEntity.class);
        registry.registerGuestType("PiglinEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PiglinEntity.class);
        registry.registerGuestType("PiglinBruteEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PiglinBruteEntity.class);
        registry.registerGuestType("PillagerEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PillagerEntity.class);
        registry.registerGuestType("EvokerEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.EvokerEntity.class);
        registry.registerGuestType("WanderingTraderEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.WanderingTraderEntity.class);
        registry.registerGuestType("EnderDragonEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.EnderDragonEntity.class);
        registry.registerGuestType("BreezeEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.BreezeEntity.class);
        registry.registerGuestType("CreakingEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.CreakingEntity.class);
        registry.registerGuestType("ArmorStandEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ArmorStandEntity.class);
        registry.registerGuestType("FallingBlockEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.FallingBlockEntity.class);
        registry.registerGuestType("ItemFrameEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ItemFrameEntity.class);
        registry.registerGuestType("AreaEffectCloudEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AreaEffectCloudEntity.class);
        registry.registerGuestType("ProjectileEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ProjectileEntity.class);
        registry.registerGuestType("AbstractArrowEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractArrowEntity.class);
        registry.registerGuestType("ArrowEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ArrowEntity.class);
        registry.registerGuestType("ThrownTridentEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ThrownTridentEntity.class);
        registry.registerGuestType("WitherSkullEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.WitherSkullEntity.class);
        registry.registerGuestType("FishingHookEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.FishingHookEntity.class);
        registry.registerGuestType("BoatEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.BoatEntity.class);
        registry.registerGuestType("ChestBoatEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ChestBoatEntity.class);
        registry.registerGuestType("AbstractMinecartEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractMinecartEntity.class);
        registry.registerGuestType("MinecartFurnaceEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.MinecartFurnaceEntity.class);
        registry.registerGuestType("MinecartTntEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.MinecartTntEntity.class);
        registry.registerGuestType("EndCrystalEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.EndCrystalEntity.class);
        registry.registerGuestType("InteractionEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.InteractionEntity.class);
        registry.registerGuestType("HangingEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.HangingEntity.class);
        registry.registerGuestType("PaintingEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PaintingEntity.class);
        registry.registerGuestType("DisplayEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.DisplayEntity.class);
        registry.registerGuestType("BlockDisplayEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.BlockDisplayEntity.class);
        registry.registerGuestType("ItemDisplayEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ItemDisplayEntity.class);
        registry.registerGuestType("TextDisplayEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.TextDisplayEntity.class);
        registry.registerGuestType("SlimeEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.SlimeEntity.class);
        registry.registerGuestType("GuardianEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.GuardianEntity.class);
        registry.registerGuestType("AbstractIllagerEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractIllagerEntity.class);
        registry.registerGuestType("SpellcasterIllagerEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.SpellcasterIllagerEntity.class);
        registry.registerGuestType("VindicatorEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.VindicatorEntity.class);
        registry.registerGuestType("PigEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PigEntity.class);
        registry.registerGuestType("Component", ink.tuanzi.neomacros.core.api.types.Component.class);
        registry.registerGuestType("TextComponent", ink.tuanzi.neomacros.core.api.types.TextComponent.class);
        registry.registerGuestType("Inventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.Inventory.class);
        registry.registerGuestType("PlayerInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.PlayerInventory.class);
        registry.registerGuestType("Screen", ink.tuanzi.neomacros.core.api.player.PlayerApi.Screen.class);
        registry.registerGuestType("BuiltScreen", ink.tuanzi.neomacros.core.api.render.screen.BuiltScreen.class);
        registry.registerGuestType("InventoryScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.InventoryScreen.class);
        registry.registerGuestType("ContainerScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.ContainerScreen.class);
        registry.registerGuestType("ChatScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.ChatScreen.class);
        registry.registerGuestType("SignScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.SignScreen.class);
        registry.registerGuestType("BookEditScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.BookEditScreen.class);
        registry.registerGuestType("BookViewScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.BookViewScreen.class);
        registry.registerGuestType("DialogScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.DialogScreen.class);
        registry.registerGuestType("TitleScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.TitleScreen.class);
        registry.registerGuestType("PauseScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.PauseScreen.class);
        registry.registerGuestType("DeathScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.DeathScreen.class);
        registry.registerGuestType("CreativeScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.CreativeScreen.class);
        registry.registerGuestType("ProgressScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.ProgressScreen.class);
        registry.registerGuestType("DisconnectedScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.DisconnectedScreen.class);
        registry.registerGuestType("WinScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.WinScreen.class);
        registry.registerGuestType("ConfirmScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.ConfirmScreen.class);
        registry.registerGuestType("CommandBlockScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.CommandBlockScreen.class);
        registry.registerGuestType("StructureBlockScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.StructureBlockScreen.class);
        registry.registerGuestType("JigsawScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.JigsawScreen.class);
        registry.registerGuestType("DialogHandle", ink.tuanzi.neomacros.core.api.player.PlayerApi.DialogHandle.class);
        registry.registerGuestType("ItemEntity", EntityApi.ItemEntity.class);
        registry.registerGuestType("LivingEntity", EntityApi.LivingEntity.class);
        registry.registerInstance(new FsApi(new GameDirFsBridge(), ioExecutor));
        ink.tuanzi.neomacros.core.api.types.CustomImage.installRoot(new ConfigDirFsBridge());
        registry.registerInstance(new NetApi(ioExecutor));
        registry.registerInstance(new ink.tuanzi.neomacros.core.api.HudApi(
                adapter.render(), Minecraft.getInstance()::execute, () -> {
                    var window = Minecraft.getInstance().getWindow();
                    if (window == null) {
                        return new ink.tuanzi.neomacros.port.model.WindowData(0, 0, 0, 0);
                    }
                    return new ink.tuanzi.neomacros.port.model.WindowData(
                            window.getWidth(), window.getHeight(),
                            window.getGuiScaledWidth(), window.getGuiScaledHeight());
                }));
        registry.registerInstance(new ink.tuanzi.neomacros.core.api.render.RenderApi(
                adapter.draw3d()));
        var inputDelayer = new java.util.concurrent.ScheduledThreadPoolExecutor(1, run -> {
            Thread thread = new Thread(run, "neomacros-input-delay");
            thread.setDaemon(true);
            return thread;
        });
        var inputApi = new InputApi(new InputApi.InputBridge() {
            @Override
            public Object registerKeybind(String keyName, java.util.function.Consumer<Integer> sink) {
                return adapter.input().registerKeybind(keyName, sink);
            }

            @Override
            public Object registerKeyPress(String keyName, Runnable sink) {
                adapter.input().registerKeyPress(keyName, sink);
                return keyName;
            }

            @Override
            public void onScroll(java.util.function.DoubleConsumer sink) {
                adapter.input().onScroll(sink::accept);
            }

            @Override
            public void unregister(Object handle) {
                adapter.input().unregister(handle);
            }

            @Override
            public void injectKey(String keyName, boolean down) {
                adapter.input().injectKey(keyName, down);
            }

            @Override
            public void injectBind(String bindName, boolean down) {
                adapter.input().injectBind(bindName, down);
            }

            @Override
            public boolean bindDown(String bindName) {
                return adapter.input().bindDown(bindName);
            }

            @Override
            public java.util.List<ink.tuanzi.neomacros.port.model.KeybindData> keybinds() {
                return adapter.input().keybinds();
            }

            @Override
            public void setBind(String bindName, String keyName) {
                adapter.input().setBind(bindName, keyName);
            }
        }, inputDelayer);
        registry.registerInstance(inputApi);
        registry.promoteRootMembers("thread", java.util.List.of("sleep", "ticks", "nextTick", "runOnMain"));
        ScriptApiEnvironment.install(bus, adapter);
        ScriptApiEnvironment.installStorage(configDir.resolve("storage"));
        // Script console: MC components render through this node's real API,
        // and the plain-text mirror into the logging tree is always on (the
        // web service adds its ring/SSE dispatcher per start).
        ink.tuanzi.neomacros.core.console.ScriptConsole.installComponentView(
                ink.tuanzi.neomacros.adapter.ComponentViews::toChatView);
        ink.tuanzi.neomacros.adapter.ConsoleMirror.install();

        var commandPort = new FabricCommandPort();
        var commandApi = new CommandApi(commandPort);
        registry.registerInstance(commandApi);

        ScriptEngine engine = new GraalJsEngine();
        runtime = new ScriptRuntime(engine, adapter, catalog, bus, pool, options.maxSyncTime);
        var extraIgnores = new java.util.ArrayList<>(EventBus.DEFAULT_WILDCARD_IGNORES);
        extraIgnores.addAll(options.wildcardIgnoresExtra);
        runtime.eventBus().setWildcardIgnores(extraIgnores);

        trustManager = new TrustManager(options);
        // Pending first-load trust requests by script name; /neomacros trust
        // <name> resolves the grant through this map.
        pendingTrusts = new java.util.concurrent.ConcurrentHashMap<>();
        trustManager.onPrompt(prompt -> {
            String name = prompt.scriptName() != null ? prompt.scriptName() : prompt.contentHash();
            pendingTrusts.put(name, prompt.contentHash());
            // The category tag rides on the label so the line reads
            // "name (category)"; absent when only the hash is known.
            String label = Msg.name(name) + entry(name)
                    .map(e -> Msg.dim(UiText.tr("neomacros.ui.category."
                            + e.category().dirName())))
                    .orElse("");
            String caps = String.join(", ", prompt.requested());
            adapter.chat().logClickable(
                    Msg.line(UiText.tr("neomacros.chat.trust_prompt",
                            label, Msg.num(caps))),
                    "/neomacros trust " + name);
        });

        var viewResolver = new java.util.function.BiFunction<String, List<String>, PermissionView>() {
            @Override
            public PermissionView apply(String scriptName, List<String> declared) {
                if (declared == null || declared.isEmpty()) {
                    return PermissionView.ALLOW_ALL;
                }
                java.nio.file.Path source = modules.entries().stream()
                        .filter(entry -> entry.name().equals(scriptName))
                        .map(ScriptEntry::entryFile)
                        .findFirst()
                        .orElse(null);
                if (source == null || !Files.isRegularFile(source)) {
                    return PermissionView.DENY_ALL;
                }
                try {
                    String hash = TrustManager.hashOf(Files.readString(source));
                    return trustManager.viewFor(scriptName, hash, declared);
                } catch (IOException e) {
                    LOGGER.warn("neomacros: cannot hash {} for trust: {}", scriptName, e.getMessage());
                    return PermissionView.DENY_ALL;
                }
            }
        };

        modules = new ModuleManager(runtime, registry, viewResolver, macrosDir,
                message -> LOGGER.warn("neomacros scripts: {}", message));
        modules.withEventCatalog(catalog);
        // Task manager: one task per macro activation, enriched with session
        // metadata captured on the main thread at start.
        tasks = new ink.tuanzi.neomacros.core.task.TaskRegistry();
        tasks.metadataProvider(NeoMacrosFabric::captureTaskMetadata);
        modules.withTaskRegistry(tasks);
        // An explicit enable/disable (panel or command) always wins; without
        // one, the manifest's config.service.autostart is the initial default
        // (absent or null = true).
        modules.withAutoloadFilter(name -> {
            Boolean saved = options.autoload.get(name);
            if (saved != null) {
                return saved;
            }
            return modules.entries().stream()
                    .filter(entry -> entry.name().equals(name))
                    .findFirst()
                    .map(entry -> {
                        var service = entry.manifest().configOrEmpty().service();
                        return service == null || service.autostart() == null || service.autostart();
                    })
                    .orElse(true);
        });
        // Key-mapping registration splices vanilla options state: module
        // loads run on the module worker, so the MC-facing hooks hop back to
        // the game thread.
        modules.onLoad(module -> MainThread.run(() -> registerScriptBinding(module)));
        modules.onLoad(module ->
                PerformanceApi.touch(module.meta().name()));
        modules.onUnload(module -> MainThread.run(() ->
                adapter.input().unregisterScriptBinding(module.meta().name())));
        modules.onUnload(module ->
                PerformanceApi.drop(module.meta().name()));

        // Web panel subsystem: state backend over the module manager, device
        // registry beside options.json, and the service mapping core.web. The
        // events module carries the panel broadcasts to whatever hub the
        // current service start owns.
        var panelEvents = new ink.tuanzi.neomacros.fabric.web.sse.PanelEvents();
        webRuntime = new ink.tuanzi.neomacros.fabric.web.WebRuntime(
                modules, options, configManager, macrosDir, runtime.mainThread(), panelEvents,
                runtimeVersion, mcVersion(), tasks);
        modules.onLoadError(webRuntime::moduleLoadError);
        modules.onLoad(webRuntime::moduleLoaded);
        modules.onUnload(webRuntime::moduleUnloaded);
        modules.onFolderChange(webRuntime::folderChanged);
        var devices = new ink.tuanzi.neomacros.fabric.web.auth.DeviceRegistry(
                configDir.resolve("web-devices.json"),
                message -> LOGGER.warn("neomacros web-devices: {}", message));
        var pairingStore = new ink.tuanzi.neomacros.fabric.web.pair.PairingService(
                configDir.resolve("web-pairing.json"), System::currentTimeMillis,
                () -> options.web.pairingTokenTtlMinutes * 60_000L,
                message -> LOGGER.warn("neomacros web-pairing: {}", message));
        var pairingRequests = new ink.tuanzi.neomacros.fabric.web.pair.PairingRequests(
                System::currentTimeMillis);
        webService = new ink.tuanzi.neomacros.fabric.web.WebService(
                webRuntime, webRuntime, devices, pairingStore, pairingRequests,
                () -> options.web.listen, () -> options.web.port,
                () -> options.web.panelUrl, () -> options.web.allowedOrigins, panelEvents,
                webRuntime.memoryCenter());
        // Tokenless pairing prompts: each new request renders one chat line
        // with clickable approve/trust/deny commands (the trust-prompt idiom).
        webService.onPairRequest(request ->
                sendPairRequestCard(request));
        webRuntime.serverInfo(() -> new ink.tuanzi.neomacros.fabric.web.v1.ServerInfo(
                webService.port(), webService.status().uptimeMs(), options.web.listen));
        // Update checker: compares the running build's channel against the
        // remote manifest (self-hosted, GitHub fallback). Notices fan out to
        // the in-game card and the panel SSE stream; dedupe and ignores
        // persist in the update config section.
        updateChecker = new ink.tuanzi.neomacros.fabric.update.UpdateChecker(
                options.update, runtimeVersion,
                notice -> presentUpdateNotice(notice, panelEvents));
        updateChecker.start();
        // The MCP agent server reads the same rings' data through its own
        // instances and shares the backend/command registry; a generated
        // token lands in the config with everything else.
        mcpService = new ink.tuanzi.neomacros.fabric.mcp.McpService(
                webRuntime, webRuntime, adapter, commandPort,
                () -> options.mcp, () -> runtimeVersion,
                token -> {
                    options.mcp.token = token;
                    configManager.save();
                });
        // Error presentation chain: script failures become a throttled
        // clickable chat line pointing at the recent-errors viewer screen.
        var errorPresenter = new ink.tuanzi.neomacros.core.runtime.ErrorPresenter(
                line -> adapter.chat().logClickable(
                        Msg.line(UiText.tr("neomacros.chat.error_line", Msg.err(line))),
                        "/neomacros errors"),
                3000);
        runtime.setErrorHandler(error -> {
            webRuntime.recordRuntimeError(error);
            errorPresenter.record(error);
        });

        quitOnDone = Boolean.getBoolean("neomacros.e2e.quitOnDone");

        PortEventWiring.wire(adapter, bus, modules, runtime.tickClock(), ModuleWorker::execute);
        // Fabric-local extras ride as additional sinks: the scripts-loaded log,
        // the config-driven script file watch, and the E2E quit-on-done poll.
        adapter.lifecycle().onGameStart(() ->
                LOGGER.info("NeoMacros: {} scripts loaded", modules.loaded().size()));
        adapter.lifecycle().onGameQuit(() -> {
            updateChecker.stop();
            mcpService.stop();
            webService.stop();
            webService.saveDevices();
            configManager.save();
        });
        adapter.lifecycle().onTick(tick -> {
            if (options.scriptWatch && tick % options.scriptWatchInterval == 0) {
                // The scan walks every watched folder and a change reloads
                // scripts; neither belongs on the game thread.
                ModuleWorker.execute(modules::checkForChanges);
            }
        });
        // The persisted intent (last start/stop) or autoStart brings the web
        // service up with the client; a bind failure stays a log line here.
        if (options.web.autoStart || options.web.enabled) {
            try {
                int port = webService.start();
                LOGGER.info("neomacros: web panel auto-started on port {}", port);
            } catch (java.io.IOException e) {
                LOGGER.warn("neomacros: web panel auto-start failed: {}", e.getMessage());
            }
        }
        if (options.mcp.autoStart || options.mcp.enabled) {
            try {
                int port = mcpService.start();
                LOGGER.info("neomacros: mcp server auto-started on port {}", port);
            } catch (java.io.IOException e) {
                LOGGER.warn("neomacros: mcp server auto-start failed: {}", e.getMessage());
            }
        }
        if (quitOnDone) {
            adapter.lifecycle().onTick(tick -> {
                if (tick % 100 == 0 && tick > 0
                        && Files.exists(configDir.resolve("storage").resolve("e2e-suite.json"))) {
                    LOGGER.info("NeoMacros E2E results present; shutting down client");
                    mcpService.stop();
                    webService.stop();
                    webService.saveDevices();
                    configManager.save();
                    adapter.lifecycle().requestShutdown();
                }
            });
        }
        // Hub→Bus wiring: adapter mixin event sources → EventBus.publish.
        ink.tuanzi.neomacros.adapter.EntityLifecycleHub.onLoad((uuid, typeId) ->
                bus.publish("entity:load", new ink.tuanzi.neomacros.core.event.def.entity.EntityLoadEvent(
                        new ink.tuanzi.neomacros.port.model.EntitySnapshot(
                                java.util.UUID.fromString(uuid), typeId, typeId,
                                new ink.tuanzi.neomacros.port.model.Pos3d(0, 64, 0), 20))));
        // Unload sinks receive the uuid resolved from the tracking id; an
        // entity never seen loading (stale login-replaced entries) has none
        // and cannot form a valid snapshot, so the event is skipped.
        ink.tuanzi.neomacros.adapter.EntityLifecycleHub.onUnload(uuid -> {
            if (uuid == null) {
                return;
            }
            bus.publish("entity:unload", new ink.tuanzi.neomacros.core.event.def.entity.EntityUnloadEvent(
                    new ink.tuanzi.neomacros.port.model.EntitySnapshot(
                            java.util.UUID.fromString(uuid), "", "",
                            new ink.tuanzi.neomacros.port.model.Pos3d(0, 0, 0), 0)));
        });
        ink.tuanzi.neomacros.adapter.SoundEventHub.register(data ->
                bus.publish("world:sound", new ink.tuanzi.neomacros.core.event.def.world.WorldSoundEvent(
                        data.soundId(),
                        new ink.tuanzi.neomacros.core.api.types.Pos3D(data.x(), data.y(), data.z()),
                        data.volume(), data.pitch())));
        ink.tuanzi.neomacros.adapter.WorldEventHub.onBlockUpdate(data ->
                bus.publish("world:blockUpdate",
                        new ink.tuanzi.neomacros.core.event.def.world.WorldBlockUpdateEvent(
                                new ink.tuanzi.neomacros.core.api.types.BlockPos(
                                        data.x(), data.y(), data.z()),
                                data.blockId())));
        ink.tuanzi.neomacros.adapter.ChunkEventHub.onLoad((chunkX, chunkZ) ->
                bus.publish("world:chunkLoad",
                        new ink.tuanzi.neomacros.core.event.def.world.WorldChunkLoadEvent(
                                new ink.tuanzi.neomacros.core.api.types.ChunkPos(chunkX, chunkZ))));
        ink.tuanzi.neomacros.adapter.ChunkEventHub.onUnload((chunkX, chunkZ) ->
                bus.publish("world:chunkUnload",
                        new ink.tuanzi.neomacros.core.event.def.world.WorldChunkUnloadEvent(
                                new ink.tuanzi.neomacros.core.api.types.ChunkPos(chunkX, chunkZ))));
        ink.tuanzi.neomacros.adapter.BossbarHub.register(data ->
                bus.publish("world:bossbar",
                        new ink.tuanzi.neomacros.core.event.def.world.WorldBossbarEvent(
                                data.uuid(), data.action(), data.name(), data.progress())));

        // The inventory event family is wired through the port sinks in
        // PortEventWiring, so the e2e harness observes the same publishes.

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            runtime.mainThread().runQueuedTasks();
            ink.tuanzi.neomacros.adapter.BlockScanScheduler.tick();
        });
        registerCommands(trustManager, errorPresenter, pendingTrusts, updateChecker, adapter.chat());
        preRegisterScriptBindings();
        // Raw keyboard source: the cancellable path fans the edge out to the
        // key:press event (sync subscribers may cancel, which also swallows
        // the vanilla update) and then to the keydown/keyup macro dispatch.
        ink.tuanzi.neomacros.adapter.KeyEdgeHub.registerCancellable(raw -> {
            boolean canceled = adapter.input().fireKeyPress(new ink.tuanzi.neomacros.port.model.KeyPressEdge(
                    raw.keyName(), raw.keyCode(), raw.down()));
            if (!canceled) {
                dispatchRawKey(raw);
            }
            return canceled;
        });
    }

    /**
     * Registers one key mapping per discovered keypress macro during
     * client init, while fabric api still accepts registrations. When the
     * script actually loads, the binding hook reuses the existing mapping
     * and only re-attaches its sink.
     */
    private static void preRegisterScriptBindings() {
        for (ScriptEntry entry : modules.discover()) {
            if (entry.category() != ScriptCategory.KEYPRESS
                    || !options.autoload.getOrDefault(entry.name(), true)) {
                continue;
            }
            String key = entry.effectiveKey(options.keybinds);
            if (key.isEmpty()) {
                continue;
            }
            String name = entry.name();
            adapter.input().registerScriptBinding(name, key,
                    clicks -> triggerKeypress(name, key, clicks));
        }
    }

    /** Registers (or re-registers) one loaded keypress script's key binding. */
    private static void registerScriptBinding(LoadedModule module) {
        if (module.entry().category() != ScriptCategory.KEYPRESS) {
            return;
        }
        String key = module.entry().effectiveKey(options.keybinds);
        if (key.isEmpty()) {
            return;
        }
        String name = module.meta().name();
        adapter.input().registerScriptBinding(name, key,
                clicks -> triggerKeypress(name, key, clicks));
    }

    /**
     * Delivers one raw key edge to matching keydown/keyup macros: the edge,
     * effective binding, and required modifiers must all match.
     */
    private static void dispatchRawKey(ink.tuanzi.neomacros.adapter.KeyEdgeHub.RawKey event) {
        String edge = event.down() ? "keydown" : "keyup";
        String keyName = event.keyName();
        List<String> modifiers = polledModifiers();
        for (LoadedModule module : modules.loaded()) {
            if (module.entry().category() != ScriptCategory.KEYPRESS) {
                continue;
            }
            var keypress = module.entry().manifest().configOrEmpty().keypress();
            if (keypress == null || !keypress.onOrDefault().equals(edge)) {
                continue;
            }
            String effective = module.entry().effectiveKey(options.keybinds);
            if (effective.isEmpty() || !effective.equals(keyName)) {
                continue;
            }
            if (!holdsAllModifiers(modifiers, keypress.modifiersOrEmpty())) {
                continue;
            }
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("event", edge);
            payload.put("key", keyName);
            payload.put("code", event.keyCode());
            payload.put("clicks", 1);
            payload.put("modifiers", modifiers);
            modules.trigger(module.meta().name(), payload);
        }
    }

    /** Delivers one polled press-edge keypress trigger with fresh modifiers. */
    private static void triggerKeypress(String name, String key, int clicks) {
        LoadedModule module = modules.loaded(name).orElse(null);
        if (module == null) {
            return;
        }
        var keypress = module.entry().manifest().configOrEmpty().keypress();
        List<String> modifiers = polledModifiers();
        if (keypress != null && !holdsAllModifiers(modifiers, keypress.modifiersOrEmpty())) {
            return;
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("event", "keypress");
        payload.put("key", key);
        payload.put("code", com.mojang.blaze3d.platform.InputConstants.getKey(key).getValue());
        payload.put("clicks", clicks);
        payload.put("modifiers", modifiers);
        modules.trigger(name, payload);
    }

    /**
     * Modifier names currently held, polled at dispatch time (the key-state
     * hook carries no modifier mask). The isKeyDown signature changed
     * per input back end: SDL scancode on 26.3, window+key on older nodes.
     */
    private static List<String> polledModifiers() {
        List<String> out = new java.util.ArrayList<>();
        if (keyDown("key.keyboard.left.control") || keyDown("key.keyboard.right.control")) {
            out.add("ctrl");
        }
        if (keyDown("key.keyboard.left.shift") || keyDown("key.keyboard.right.shift")) {
            out.add("shift");
        }
        if (keyDown("key.keyboard.left.alt") || keyDown("key.keyboard.right.alt")) {
            out.add("alt");
        }
        return out;
    }

    /** Whether one named key is currently held down. */
    private static boolean keyDown(String keyName) {
        int value = com.mojang.blaze3d.platform.InputConstants.getKey(keyName).getValue();
        //? if >=26.3 {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(value);
        //?} elif >=26.1 {
        /*
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow(), value);
        */
        //?} else {
        /*
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), value);
        */
        //?}
    }

    /** Lenient modifier matching: every required key must be held; extras are fine. */
    private static boolean holdsAllModifiers(List<String> held, List<String> required) {
        for (String modifier : required) {
            if (!held.contains(modifier)) {
                return false;
            }
        }
        return true;
    }

    /** Captures the session metadata a new task row shows (main-thread marshalled). */
    private static ink.tuanzi.neomacros.core.task.TaskRegistry.Metadata captureTaskMetadata() {
        var captured = ink.tuanzi.neomacros.adapter.MainThread.run(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            //? if >=26.2 {
            boolean singleplayer = mc.hasSingleplayerServer();
            //?} else {
            /*
            boolean singleplayer = mc.isSingleplayer();
            */
            //?}
            String server = singleplayer ? "Integrated" : "";
            var connection = mc.getConnection();
            if (!singleplayer && connection != null && connection.getServerData() != null) {
                server = connection.getServerData().ip;
            }
            String uuid = mc.player != null ? mc.player.getUUID().toString() : "";
            String player = mc.player != null ? mc.player.getName().getString() : "";
            String gameType = singleplayer ? "single" : (server.isEmpty() ? "" : "multi");
            return new ink.tuanzi.neomacros.core.task.TaskRegistry.Metadata(
                    uuid, player, gameType, server, mcVersion());
        });
        return captured != null ? captured
                : ink.tuanzi.neomacros.core.task.TaskRegistry.Metadata.EMPTY;
    }

    /** Logs a one-time notice when the legacy flat scripts directory still holds scripts. */
    private static void noteLegacyScriptsDir() {
        Path legacy = configDir.resolve("scripts");
        if (!Files.isDirectory(legacy)) {
            return;
        }
        long count;
        try (Stream<Path> files = Files.list(legacy)) {
            count = files.filter(p -> p.getFileName().toString().endsWith(".js")).count();
        } catch (IOException e) {
            return;
        }
        if (count > 0) {
            LOGGER.warn("neomacros: {} flat script(s) remain in the legacy folder {} and are no"
                    + " longer loaded; move each one into macros/<category>/<name>/ with a"
                    + " neomacro.yml manifest", count, legacy);
        }
    }

    /** The running Minecraft version (node trimming key for ApiMC ranges). */
    /** Maps one raw host object onto its registered script handle, or null. */
    private static Object wrapRaw(Object raw) {
        if (raw instanceof net.minecraft.client.player.LocalPlayer local) {
            return new PlayerApi.LocalPlayer(local.getUUID());
        }
        if (raw instanceof net.minecraft.world.entity.player.Player remote) {
            return new PlayerApi.Player(remote.getUUID());
        }
        if (raw instanceof net.minecraft.world.entity.item.ItemEntity item) {
            return new EntityApi.ItemEntity(item.getUUID());
        }
        if (raw instanceof net.minecraft.world.entity.LivingEntity living) {
            return new EntityApi.LivingEntity(living.getUUID());
        }
        if (raw instanceof net.minecraft.world.entity.Entity entity) {
            return new EntityApi.Entity(entity.getUUID());
        }
        if (raw instanceof net.minecraft.world.level.block.state.BlockState state) {
            java.util.Map<String, String> props = new java.util.LinkedHashMap<>();
            state.getProperties().forEach(property ->
                    props.put(property.getName(), String.valueOf(state.getValue(property))));
            return new ink.tuanzi.neomacros.core.api.types.BlockState(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(state.getBlock()).toString(), props);
        }
        return null;
    }

    private static String mcVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("26.3");
    }

    // ---- command helpers -------------------------------------------------

    /**
     * Suggests script names across every category from the cached discovery
     * result: suggestions run on the game thread on every keystroke, so this
     * must not rescan the macros root (the module worker refreshes the cache
     * every watch interval).
     */
    private static final SuggestionProvider<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>
            SUGGEST_ANY = (ctx, builder) -> {
        modules.entries().forEach(e -> builder.suggest(e.name()));
        return builder.buildFuture();
    };

    /** Suggests the category directory names. */
    private static final SuggestionProvider<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>
            SUGGEST_CATEGORY = (ctx, builder) -> {
        for (ScriptCategory category : ScriptCategory.values()) {
            builder.suggest(category.dirName());
        }
        return builder.buildFuture();
    };

    /** The discovered entry with the given script name, if any. */
    private static Optional<ScriptEntry> entry(String name) {
        return modules.discover().stream()
                .filter(e -> e.name().equals(name))
                .findFirst();
    }

    private static String string(CommandContext<?> ctx, String arg) {
        return StringArgumentType.getString(ctx, arg);
    }

    /**
     * One chat line: the lang key resolved with tagged accents. Output goes
     * through the mod chat port rather than FabricClientCommandSource
     * sendFeedback, so the channel is the same one the trust and pairing
     * cards use and survives the 1.21.11 chat split on any fabric-api build.
     */
    private static void feedback(CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx,
                                 String key, Object... args) {
        adapter.chat().log(Msg.line(UiText.tr(key, args)));
    }

    /** One chat line from a pre-resolved (already colored) message. */
    private static void feedbackText(CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx,
                                     String message) {
        adapter.chat().log(Msg.line(message));
    }

    /** Registers the root {@code /neomacros} command, its subcommands and the {@code /nm} alias. */
    private static void registerCommands(TrustManager trustManager,
            ink.tuanzi.neomacros.core.runtime.ErrorPresenter errorPresenter,
            java.util.concurrent.ConcurrentHashMap<String, String> pendingTrusts,
            ink.tuanzi.neomacros.fabric.update.UpdateChecker updateChecker,
            ink.tuanzi.neomacros.port.ChatPort chat) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = dispatcher.register(literal("neomacros")
                    .executes(ctx -> {
                        feedbackText(ctx, Msg.dim(UiText.tr("neomacros.cmd.help")));
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(literal("status").executes(ctx -> {
                        feedback(ctx, "neomacros.cmd.status",
                                Msg.name(runtimeVersion),
                                Msg.num(runtime.eventCatalog().all().size()),
                                Msg.num(modules.loaded().size()),
                                Msg.num(modules.errors().size()),
                                Msg.num(runtime.tickClock().currentTick()));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(UpdateCommands.subtree(updateChecker, chat))
                    .then(literal("list")
                            .executes(ctx -> {
                                // The digest needs a fresh discovery (a walk of
                                // every macro folder); the worker takes the scan.
                                ModuleWorker.execute(() ->
                                        feedbackText(ctx, describeScripts(null)));
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(argument("category", StringArgumentType.word())
                                    .suggests(SUGGEST_CATEGORY)
                                    .executes(ctx -> {
                                        ScriptCategory category = ScriptCategory
                                                .fromDirName(string(ctx, "category"));
                                        if (category == null) {
                                            feedback(ctx, "neomacros.cmd.unknown_category",
                                                    Msg.name(string(ctx, "category")));
                                            return 0;
                                        }
                                        ModuleWorker.execute(() ->
                                                feedbackText(ctx, describeScripts(category)));
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(literal("reload")
                            .executes(ctx -> {
                                // Reload blocks until every script recompiles; the
                                // worker takes it so the game keeps rendering.
                                feedbackText(ctx, Msg.dim(UiText.tr("neomacros.cmd.reloading")));
                                ModuleWorker.execute(() -> {
                                    modules.reloadAll();
                                    feedback(ctx, "neomacros.cmd.reloaded",
                                            Msg.num(modules.loaded().size()));
                                });
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(argument("script", StringArgumentType.word())
                                    .suggests(SUGGEST_ANY)
                                    .executes(ctx -> {
                                        String name = string(ctx, "script");
                                        ModuleWorker.execute(() -> {
                                            if (entry(name).isEmpty()) {
                                                feedback(ctx, "neomacros.cmd.unknown_script",
                                                        Msg.name(name));
                                                return;
                                            }
                                            modules.reload(name);
                                            feedback(ctx, "neomacros.cmd.reloaded_one",
                                                    Msg.name(name));
                                        });
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(literal("enable")
                            .then(argument("script", StringArgumentType.word())
                                    .suggests(SUGGEST_ANY)
                                    .executes(ctx -> {
                                        String name = string(ctx, "script");
                                        // Validation, the config write, and the load
                                        // all run on the worker: nothing on the game
                                        // thread beyond parsing the command itself.
                                        ModuleWorker.execute(() -> {
                                            if (entry(name).isEmpty()) {
                                                feedback(ctx, "neomacros.cmd.unknown_script",
                                                        Msg.name(name));
                                                return;
                                            }
                                            options.autoload = mutableAutoload();
                                            options.autoload.put(name, true);
                                            configManager.save();
                                            if (modules.loaded(name).isEmpty()) {
                                                modules.loadByName(name);
                                            }
                                            feedback(ctx, "neomacros.cmd.enabled", Msg.name(name));
                                        });
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(literal("disable")
                            .then(argument("script", StringArgumentType.word())
                                    .suggests(SUGGEST_ANY)
                                    .executes(ctx -> {
                                        String name = string(ctx, "script");
                                        ModuleWorker.execute(() -> {
                                            if (entry(name).isEmpty()) {
                                                feedback(ctx, "neomacros.cmd.unknown_script",
                                                        Msg.name(name));
                                                return;
                                            }
                                            options.autoload = mutableAutoload();
                                            options.autoload.put(name, false);
                                            configManager.save();
                                            modules.unload(name);
                                            feedback(ctx, "neomacros.cmd.disabled", Msg.name(name));
                                        });
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(literal("trust")
                            .then(argument("script", StringArgumentType.greedyString())
                                    .suggests((ctx, builder) -> {
                                        pendingTrusts.keySet().forEach(builder::suggest);
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        String name = string(ctx, "script");
                                        String hash = pendingTrusts.get(name);
                                        if (hash == null) {
                                            feedback(ctx, "neomacros.cmd.no_pending_trust_from",
                                                    Msg.name(name));
                                            return 0;
                                        }
                                        trustManager.trust(hash);
                                        pendingTrusts.remove(name);
                                        // The grant and its apply ride the module
                                        // worker together: a script that prompted
                                        // was loaded under the denying view, so the
                                        // reload brings the granted capabilities
                                        // live without a manual reload command.
                                        ModuleWorker.execute(() -> {
                                            configManager.save();
                                            boolean live = modules.loaded(name).isPresent();
                                            if (live) {
                                                modules.reload(name);
                                            }
                                            feedback(ctx, live
                                                    ? "neomacros.cmd.trusted_reloaded"
                                                    : "neomacros.cmd.trusted_apply",
                                                    Msg.name(name));
                                        });
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .then(literal("errors").executes(ctx -> {
                        var recent = errorPresenter.recent();
                        var lines = new java.util.ArrayList<String>(recent);
                        for (ink.tuanzi.neomacros.core.module.ScriptLoadError error : modules.errors()) {
                            lines.add(UiText.tr("neomacros.cmd.invalid_folder",
                                    Msg.err(error.folder().getFileName()),
                                    Msg.err(error.message())));
                        }
                        ScriptViewerScreen.open(UiText.tr("neomacros.cmd.recent_errors"),
                                lines.isEmpty() ? UiText.tr("neomacros.cmd.no_recent_errors")
                                        : String.join("\n\n", lines));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(WebCommands.subtree(webContext()))
                    .then(McpCommands.subtree(mcpContext())));
            dispatcher.register(literal("nm").redirect(root));
        });
    }

    /** The web commands' collaborators over the live runtime. */
    private static WebCommands.Context webContext() {
        WebCommands.LinkBuilder links = () -> {
            if (!webService.running()) {
                throw new IllegalStateException("the web service is not running");
            }
            String panel = options.web.panelUrl == null || options.web.panelUrl.isBlank()
                    ? "https://neomacro-editor.tuanzi.ink" : options.web.panelUrl.strip();
            while (panel.endsWith("/")) {
                panel = panel.substring(0, panel.length() - 1);
            }
            int hash = panel.indexOf('#');
            if (hash >= 0) {
                panel = panel.substring(0, hash);
            }
            // Query-parameter deep link: the panel reads search params and
            // clears them with history.replaceState. The long-lived one-time
            // token plus the loopback/Origin gates are the security boundary.
            return new StringBuilder(panel)
                    .append("/?host=").append(deepLinkHost()).append(':')
                    .append(webService.port())
                    .append("&view=editor&pair=")
                    .append(webService.newPairingToken())
                    .toString();
        };
        return new WebCommands.Context(webService, links, adapter.chat());
    }

    /** The mcp commands' collaborators over the live runtime. */
    private static McpCommands.Context mcpContext() {
        return new McpCommands.Context(mcpService, adapter.chat());
    }

    /**
     * The host the browser should use in the deep link, derived from the
     * listen scope: the loopback literals pass through (IPv6 bracketed),
     * and a wildcard bind falls back to loopback — always reachable when
     * the game is local.
     */
    private static String deepLinkHost() {
        String listen = options.web.listen == null ? "" : options.web.listen.strip();
        return switch (listen) {
            case "::1" -> "[::1]";
            default -> "127.0.0.1";
        };
    }

    /**
     * Fan-out for an update notice: the editor stream hears every finding,
     * while the in-game toast plus chat card fires once per
     * {@code channel@version} and never for one the player ignored.
     * Arrives on the checker thread.
     */
    private static void presentUpdateNotice(
            ink.tuanzi.neomacros.fabric.update.UpdateNotice notice,
            ink.tuanzi.neomacros.fabric.web.sse.PanelEvents panelEvents) {
        String key = notice.key();
        if (key.equals(options.update.ignoredVersion)) {
            return;
        }
        if (options.update.notifyEditor) {
            panelEvents.broadcast("update:available", new com.google.gson.Gson().toJson(
                    java.util.Map.of(
                            "channel", notice.channel(),
                            "current", notice.currentVersion(),
                            "latest", notice.latestVersion(),
                            "notes", notice.notes() == null ? "" : notice.notes(),
                            "date", notice.date() == null ? "" : notice.date())));
        }
        if (!options.update.notifyInGame || key.equals(options.update.lastNotified)) {
            return;
        }
        options.update.lastNotified = key;
        configManager.save();
        MainThread.run(() -> {
            adapter.chat().toast(
                    UiText.tr("neomacros.chat.update.toast_title"),
                    UiText.tr("neomacros.chat.update.toast_body",
                            Msg.name(notice.latestVersion()), Msg.dim(notice.channel())));
            adapter.chat().log(Msg.line(UiText.tr("neomacros.chat.update.available",
                    Msg.name(notice.latestVersion()), Msg.dim(notice.channel()))));
            if (notice.notes() != null && !notice.notes().isBlank()) {
                adapter.chat().logLink(
                        Msg.line(UiText.tr("neomacros.chat.update.notes")), notice.notes());
            }
            adapter.chat().logClickable(
                    Msg.line(Msg.dim(UiText.tr("neomacros.chat.update.ignore"))),
                    "/neomacros update ignore");
        });
    }

    /**
     * Renders the in-game decision card for one tokenless pairing request:
     * a header line plus three clickable commands (the trust-prompt
     * idiom). Arrives on an HTTP thread; the chat port marshals to main.
     */
    private static void sendPairRequestCard(
            ink.tuanzi.neomacros.fabric.web.pair.PairingRequests.Request request) {
        String label = request.label() == null || request.label().isBlank()
                ? "?" : request.label();
        String source = request.source() == null ? "" : request.source().replaceFirst("^/", "");
        adapter.chat().log(Msg.line(UiText.tr("neomacros.chat.pair_request",
                Msg.name(label), Msg.num(source))));
        String id = request.id();
        adapter.chat().logClickable(
                Msg.line(Msg.ok(UiText.tr("neomacros.chat.pair_request_approve"))),
                "/neomacros editor approve " + id);
        adapter.chat().logClickable(
                Msg.line(Msg.num(UiText.tr("neomacros.chat.pair_request_trust"))),
                "/neomacros editor trust-host " + id);
        adapter.chat().logClickable(
                Msg.line(Msg.err(UiText.tr("neomacros.chat.pair_request_deny"))),
                "/neomacros editor deny " + id);
    }

    /**
     * A config map accepting mutation: the defaults are immutable and the
     * swap-in-place keeps concurrent readers on the game thread safe.
     */
    private static Map<String, Boolean> mutableAutoload() {
        return options.autoload instanceof java.util.LinkedHashMap<String, Boolean>
                ? options.autoload : new java.util.LinkedHashMap<>(options.autoload);
    }

    /** One-line digest of the discovered scripts, optionally per category. */
    private static String describeScripts(ScriptCategory category) {
        modules.discover();
        var lines = new java.util.ArrayList<String>();
        for (ScriptCategory cat : ScriptCategory.values()) {
            if (category != null && cat != category) {
                continue;
            }
            List<ScriptEntry> entries = modules.entries().stream()
                    .filter(e -> e.category() == cat)
                    .toList();
            lines.add("§f[" + cat.dirName() + "]§r");
            for (ScriptEntry entry : entries) {
                String state = entryState(entry.name());
                String key = entry.category() == ScriptCategory.KEYPRESS
                        ? entry.effectiveKey(options.keybinds) : "";
                lines.add("  " + Msg.name(entry.name()) + " "
                        + Msg.dim(UiText.tr("neomacros.ui.state.version",
                                entry.manifest().version(), entry.manifest().author()))
                        + (key.isEmpty() ? "" : "  " + Msg.num(key))
                        + "  " + state);
            }
            if (entries.isEmpty()) {
                lines.add("  " + Msg.dim(UiText.tr("neomacros.cmd.none")));
            }
        }
        for (ink.tuanzi.neomacros.core.module.ScriptLoadError error : modules.errors()) {
            lines.add("  " + UiText.tr("neomacros.cmd.invalid_folder",
                    Msg.err(error.folder().getFileName()), Msg.err(error.message())));
        }
        return String.join("\n", lines);
    }

    /** The localized run-state word for one script (loaded/stopped/disabled). */
    private static String entryState(String name) {
        if (!options.autoload.getOrDefault(name, true)) {
            return "§8" + UiText.tr("neomacros.ui.state.disabled") + "§r";
        }
        return modules.loaded(name).isPresent()
                ? Msg.ok(UiText.tr("neomacros.ui.state.loaded"))
                : Msg.dim(UiText.tr("neomacros.ui.state.stopped"));
    }

    /** Config bridge for the optional Cloth settings screen. */
    static CoreOptions configOptions() {
        return options;
    }

    /** Saves the config; used as the Cloth screen save hook. */
    static void saveConfig() {
        configManager.save();
    }

    /**
     * Applies changed web settings to a running panel by restarting it;
     * a bind failure (e.g. the new port is taken) stays a log line and the
     * panel stays stopped.
     */
    static void restartWebIfRunning() {
        if (webService == null || !webService.running()) {
            return;
        }
        webService.stop();
        try {
            webService.start();
        } catch (java.io.IOException e) {
            LOGGER.warn("neomacros: web panel restart failed: {}", e.getMessage());
        }
    }

    /**
     * Applies changed mcp settings to a running server by restarting it;
     * a bind failure (e.g. the new port is taken) stays a log line and the
     * server stays stopped.
     */
    static void restartMcpIfRunning() {
        if (mcpService == null || !mcpService.running()) {
            return;
        }
        mcpService.stop();
        try {
            mcpService.start();
        } catch (java.io.IOException e) {
            LOGGER.warn("neomacros: mcp server restart failed: {}", e.getMessage());
        }
    }

    /**
     * Sandbox boundary for the fs API: game-dir relative, traversal denied.
     */
    private record GameDirFsBridge() implements ink.tuanzi.neomacros.core.api.FsApi.FsBridge {
        @Override
        public Path resolve(String path) {
            Path gameDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
            Path resolved = gameDir.toAbsolutePath().resolve(path).normalize();
            if (!resolved.startsWith(gameDir.toAbsolutePath())) {
                throw new java.lang.SecurityException("path escapes the game directory: " + path);
            }
            return resolved;
        }
    }

    /**
     * Sandbox boundary for the CustomImage file faces: config-dir relative
     * (the analog of the JsMacros config folder), traversal denied.
     */
    private record ConfigDirFsBridge() implements ink.tuanzi.neomacros.core.api.FsApi.FsBridge {
        @Override
        public Path resolve(String path) {
            Path root = configDir.toAbsolutePath().normalize();
            Path resolved = root.resolve(path).normalize();
            if (!resolved.startsWith(root)) {
                throw new java.lang.SecurityException("path escapes the config directory: " + path);
            }
            return resolved;
        }
    }
}
