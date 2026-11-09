package ink.tuanzi.neomacros.e2e.harness;

import ink.tuanzi.neomacros.core.api.ChatApi;
import ink.tuanzi.neomacros.core.api.ThreadApi;
import ink.tuanzi.neomacros.core.api.EventsApi;
import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.StorageApi;
import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.player.PlayerApi;
import ink.tuanzi.neomacros.core.api.world.WorldApi;
import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.event.PortEventWiring;
import ink.tuanzi.neomacros.core.event.ProductionEventSet;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.module.ScriptMeta;
import ink.tuanzi.neomacros.core.runtime.ScriptEngine;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.engine.GraalJsEngine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Application-level E2E harness: assembles the real engine, the real event
 * framework, and the real module manager over a stub adapter, injects the
 * {@code __test} bridge, and exposes event injection plus await helpers.
 * Scenarios load fixture scripts exactly the way production bootstrap does.
 */
public final class E2ERuntime implements AutoCloseable {

    private final StubAdapter adapter;
    private final ScriptRuntime runtime;
    private final BindingRegistry registry;
    private final ModuleManager modules;
    private final TestBridge bridge;
    private final EventCatalog catalog;

    private E2ERuntime(StubAdapter adapter, ScriptRuntime runtime, BindingRegistry registry,
                       ModuleManager modules, TestBridge bridge, EventCatalog catalog) {
        this.adapter = adapter;
        this.runtime = runtime;
        this.registry = registry;
        this.modules = modules;
        this.bridge = bridge;
        this.catalog = catalog;
    }

    /**
     * Creates a harness over one scripts directory (fixtures are written
     * there by the scenario) and one storage directory.
     */
    public static E2ERuntime create(Path scriptsDir, Path storageDir) {
        var adapter = new StubAdapter();
        var catalog = new EventCatalog();
        catalog.registerAll(ProductionEventSet.records());
        var pool = new ScriptThreadPool(2);
        // Synchronous executor: publishes dispatch inline, keeping scenarios deterministic.
        var bus = new EventBus(catalog, Runnable::run);
        var registry = new BindingRegistry("26.3");
        var bridge = new TestBridge();
        registry.registerAll(List.of(EventsApi.class, ThreadApi.class, ChatApi.class,
                StorageApi.class, ink.tuanzi.neomacros.core.api.PerformanceApi.class,
                TestBridge.class, WorldApi.class, EntityApi.class, PlayerApi.class,
                ink.tuanzi.neomacros.core.api.java.JavaApi.class,
                ink.tuanzi.neomacros.core.api.client.ClientApi.class));
        registry.registerInstance(new ink.tuanzi.neomacros.core.api.reflect.ReflectApi());
        registry.registerGuestType("Entity", EntityApi.Entity.class);
        registry.registerGuestType("Player", PlayerApi.Player.class);
        registry.registerGuestType("LocalPlayer", PlayerApi.LocalPlayer.class);
        for (var descriptor : catalog.all()) {
            registry.registerEventType(descriptor.name(), descriptor.cancellable());
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
        registry.registerGuestType("Component", ink.tuanzi.neomacros.core.api.types.Component.class);
        registry.registerGuestType("TextComponent", ink.tuanzi.neomacros.core.api.types.TextComponent.class);
        registry.registerGuestType("Inventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.Inventory.class);
        registry.registerGuestType("PlayerInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.PlayerInventory.class);
        registry.registerGuestType("Screen", ink.tuanzi.neomacros.core.api.player.PlayerApi.Screen.class);
        registry.registerGuestType("InventoryScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.InventoryScreen.class);
        registry.registerGuestType("ContainerScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.ContainerScreen.class);
        registry.registerGuestType("ChatScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.ChatScreen.class);
        registry.registerGuestType("DialogScreen", ink.tuanzi.neomacros.core.api.player.PlayerApi.DialogScreen.class);
        registry.registerGuestType("DialogHandle", ink.tuanzi.neomacros.core.api.player.PlayerApi.DialogHandle.class);
        registry.registerGuestType("ItemEntity", EntityApi.ItemEntity.class);
        registry.registerGuestType("LivingEntity", EntityApi.LivingEntity.class);
                registry.registerGuestType("VillagerInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.VillagerInventory.class);
                registry.registerGuestType("TradeOffer", ink.tuanzi.neomacros.core.api.player.PlayerApi.TradeOffer.class);
                registry.registerGuestType("HorseInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.HorseInventory.class);
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
                registry.registerGuestType("BrewingStandInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.BrewingStandInventory.class);
                registry.registerGuestType("BeaconInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.BeaconInventory.class);
                registry.registerGuestType("CartographyInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.CartographyInventory.class);
                registry.registerGuestType("SmithingInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.SmithingInventory.class);
                registry.registerGuestType("StonecutterInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.StonecutterInventory.class);
                registry.registerGuestType("GrindstoneInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.GrindstoneInventory.class);
                registry.registerGuestType("EnchantInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.EnchantInventory.class);
                registry.registerGuestType("AnvilInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.AnvilInventory.class);
                registry.registerGuestType("CraftingInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.CraftingInventory.class);
                registry.registerGuestType("ContainerInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.ContainerInventory.class);
                registry.registerGuestType("FurnaceInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.FurnaceInventory.class);
                registry.registerGuestType("Chunk", ink.tuanzi.neomacros.core.api.types.Chunk.class);
                registry.registerGuestType("WitherBossEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.WitherBossEntity.class);
                registry.registerGuestType("StriderEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.StriderEntity.class);
                registry.registerGuestType("RabbitEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.RabbitEntity.class);
                registry.registerGuestType("OcelotEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.OcelotEntity.class);
                registry.registerGuestType("GhastEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.GhastEntity.class);
                registry.registerGuestType("ChickenEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ChickenEntity.class);
                registry.registerGuestType("SpiderEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.SpiderEntity.class);
                registry.registerGuestType("PhantomEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PhantomEntity.class);
                registry.registerGuestType("LlamaEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.LlamaEntity.class);
                registry.registerGuestType("IronGolemEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.IronGolemEntity.class);
                registry.registerGuestType("HorseEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.HorseEntity.class);
                registry.registerGuestType("EndermanEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.EndermanEntity.class);
                registry.registerGuestType("DonkeyEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.DonkeyEntity.class);
                registry.registerGuestType("AbstractChestedHorseEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractChestedHorseEntity.class);
                registry.registerGuestType("AbstractHorseEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractHorseEntity.class);
                registry.registerGuestType("MushroomCowEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.MushroomCowEntity.class);
                registry.registerGuestType("BatEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.BatEntity.class);
                registry.registerGuestType("PrimedTntEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PrimedTntEntity.class);
                registry.registerGuestType("ZombieEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ZombieEntity.class);
                registry.registerGuestType("CreeperEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.CreeperEntity.class);
                registry.registerGuestType("VillagerEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.VillagerEntity.class);
                registry.registerGuestType("CatEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.CatEntity.class);
                registry.registerGuestType("WolfEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.WolfEntity.class);
                registry.registerGuestType("SheepEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.SheepEntity.class);
                registry.registerGuestType("TamableAnimalEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.TamableAnimalEntity.class);
                registry.registerGuestType("AnimalEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AnimalEntity.class);
                registry.registerGuestType("MobEntity", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.MobEntity.class);
                registry.registerGuestType("Ws", ink.tuanzi.neomacros.core.api.NetApi.Ws.class);
                registry.registerGuestType("FileHandle",
                        ink.tuanzi.neomacros.core.api.FsApi.FileHandle.class);
                registry.registerGuestType("Image", ink.tuanzi.neomacros.core.api.types.Image.class);
        registry.promoteRootMembers("thread", List.of("sleep", "ticks", "nextTick", "runOnMain"));
        ScriptApiEnvironment.install(bus, adapter);
        ScriptApiEnvironment.installStorage(storageDir);

        ScriptEngine engine = new GraalJsEngine();
        var runtime = new ScriptRuntime(engine, adapter, catalog, bus, pool, 500);
        // Script-side failures must surface in test output, never vanish.
        StringBuilder errors = new StringBuilder();
        runtime.setErrorHandler(error -> {
            errors.append(error).append(System.lineSeparator());
            log("SCRIPT-ERROR " + error);
            StringBuilder trace = new StringBuilder();
            for (Throwable t = error; t != null; t = t.getCause()) {
                trace.append("  caused by ").append(t).append('\n');
                for (StackTraceElement frame : t.getStackTrace()) {
                    trace.append("    at ").append(frame).append('\n');
                    if (trace.length() > 4000) {
                        break;
                    }
                }
            }
            log(trace.toString());
        });
        var modules = new ModuleManager(runtime, registry, PermissionView.ALLOW_ALL,
                scriptsDir, m -> log("WARN " + m));

        PortEventWiring.wire(adapter, bus, modules, runtime.tickClock());

        // The stub input's fire helpers dispatch onto the first context's
        // loop so GraalJS never sees cross-thread context access.
        modules.onLoad(module ->
                adapter.inputPort().setLoopDispatcher(
                        action -> module.context().loop().execute(action)));

        var harness = new E2ERuntime(adapter, runtime, registry, modules,
                (TestBridge) registry.instance("__test"), catalog);
        harness.scriptErrors = errors;
        return harness;
    }

    /** The stub adapter (event injection + output assertions). */
    public StubAdapter adapter() {
        return adapter;
    }

    /** Script-side errors captured since creation (test diagnostics). */
    public String scriptErrors() {
        StringBuilder captured = scriptErrors;
        return captured == null ? "" : captured.toString();
    }

    private volatile StringBuilder scriptErrors;

    /** The event bus (publish helper for tests). */
    public EventBus eventBus() {
        return runtime.eventBus();
    }

    /** The test bridge (case reports). */
    public TestBridge bridge() {
        return bridge;
    }

    /** The event catalog. */
    public EventCatalog eventCatalog() {
        return catalog;
    }

    /**
     * Loads one fixture through the real module manager. The path may be the
     * entry file itself or the fixture's legacy flat name; it resolves by
     * entry file first, then by manifest name.
     */
    public void loadScript(Path file) throws Exception {
        log("LOAD " + file + " exists=" + java.nio.file.Files.exists(file));
        Path target = file.toAbsolutePath().normalize();
        String name = file.getFileName().toString().replaceAll("\\.js$", "");
        var entry = modules.discover().stream()
                .filter(e -> e.entryFile().toAbsolutePath().normalize().equals(target)
                        || e.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no discovered script entry for " + file));
        var loaded = modules.load(entry);
        log("LOADED " + loaded.meta().name() + " lifecycle=" + loaded.meta().lifecycle());
    }

    private static void log(String line) {
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("build/e2e-harness.log"),
                    line + "\n", java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    /**
     * Waits until every loaded context's loop has drained its queue plus a
     * grace period for scheduler-driven promise resolution.
     */
    public void awaitSettle() throws InterruptedException {
        for (var module : modules.loaded()) {
            try {
                module.context().loop().awaitIdle(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Thread.sleep(50);
    }

    /** Loads every discovered fixture whose lifecycle matches the phase. */
    public void loadPhase(ScriptMeta.Lifecycle phase) {
        modules.loadPhase(phase);
    }

    /** The module manager (reload/unload scenarios). */
    public ModuleManager modules() {
        return modules;
    }

    @Override
    public void close() {
        ScriptApiEnvironment.closeStorages();
        modules.close();
        runtime.close();
    }

}
