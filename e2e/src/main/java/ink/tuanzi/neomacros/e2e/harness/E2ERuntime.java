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
import ink.tuanzi.neomacros.engine.ModuleResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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
        // Script-facing event classes (BaseEvent chain) for instanceof dispatch.
        registry.registerEventType("BaseEvent", false);
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
        registry.registerGuestType("Inventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.Inventory.class);
        registry.registerGuestType("PlayerInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.PlayerInventory.class);
        registry.registerGuestType("ItemEntity", EntityApi.ItemEntity.class);
        registry.registerGuestType("LivingEntity", EntityApi.LivingEntity.class);
                registry.registerGuestType("VillagerInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.VillagerInventory.class);
                registry.registerGuestType("TradeOffer", ink.tuanzi.neomacros.core.api.player.PlayerApi.TradeOffer.class);
                registry.registerGuestType("HorseInventory", ink.tuanzi.neomacros.core.api.player.PlayerApi.HorseInventory.class);
                registry.registerGuestType("Abilities", ink.tuanzi.neomacros.core.api.player.PlayerApi.Abilities.class);
        registry.registerGuestType("Axolotl", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Axolotl.class);
        registry.registerGuestType("Bee", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Bee.class);
        registry.registerGuestType("Fox", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Fox.class);
        registry.registerGuestType("Frog", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Frog.class);
        registry.registerGuestType("Goat", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Goat.class);
        registry.registerGuestType("Panda", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Panda.class);
        registry.registerGuestType("Parrot", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Parrot.class);
        registry.registerGuestType("PolarBear", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PolarBear.class);
        registry.registerGuestType("Shulker", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Shulker.class);
        registry.registerGuestType("SnowGolem", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.SnowGolem.class);
        registry.registerGuestType("Turtle", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Turtle.class);
        registry.registerGuestType("Vex", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Vex.class);
        registry.registerGuestType("Witch", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Witch.class);
        registry.registerGuestType("Dolphin", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Dolphin.class);
        registry.registerGuestType("Warden", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Warden.class);
        registry.registerGuestType("Drowned", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Drowned.class);
        registry.registerGuestType("ZombieVillager", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ZombieVillager.class);
        registry.registerGuestType("ZombifiedPiglin", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.ZombifiedPiglin.class);
        registry.registerGuestType("Allay", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Allay.class);
        registry.registerGuestType("Cow", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Cow.class);
        registry.registerGuestType("Armadillo", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Armadillo.class);
        registry.registerGuestType("Camel", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Camel.class);
        registry.registerGuestType("Sniffer", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Sniffer.class);
        registry.registerGuestType("AbstractFish", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractFish.class);
        registry.registerGuestType("AbstractSchoolingFish", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractSchoolingFish.class);
        registry.registerGuestType("Salmon", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Salmon.class);
        registry.registerGuestType("Cod", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Cod.class);
        registry.registerGuestType("Pufferfish", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Pufferfish.class);
        registry.registerGuestType("TropicalFish", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.TropicalFish.class);
        registry.registerGuestType("Piglin", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Piglin.class);
        registry.registerGuestType("PiglinBrute", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PiglinBrute.class);
        registry.registerGuestType("Pillager", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Pillager.class);
        registry.registerGuestType("Evoker", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Evoker.class);
        registry.registerGuestType("WanderingTrader", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.WanderingTrader.class);
        registry.registerGuestType("EnderDragon", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.EnderDragon.class);
        registry.registerGuestType("Breeze", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Breeze.class);
        registry.registerGuestType("Creaking", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Creaking.class);
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
                registry.registerGuestType("WitherBoss", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.WitherBoss.class);
                registry.registerGuestType("Strider", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Strider.class);
                registry.registerGuestType("Rabbit", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Rabbit.class);
                registry.registerGuestType("Ocelot", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Ocelot.class);
                registry.registerGuestType("Ghast", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Ghast.class);
                registry.registerGuestType("Chicken", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Chicken.class);
                registry.registerGuestType("Spider", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Spider.class);
                registry.registerGuestType("Phantom", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Phantom.class);
                registry.registerGuestType("Llama", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Llama.class);
                registry.registerGuestType("IronGolem", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.IronGolem.class);
                registry.registerGuestType("Horse", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Horse.class);
                registry.registerGuestType("Enderman", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Enderman.class);
                registry.registerGuestType("Donkey", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Donkey.class);
                registry.registerGuestType("AbstractChestedHorse", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractChestedHorse.class);
                registry.registerGuestType("AbstractHorse", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.AbstractHorse.class);
                registry.registerGuestType("MushroomCow", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.MushroomCow.class);
                registry.registerGuestType("Bat", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Bat.class);
                registry.registerGuestType("PrimedTnt", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.PrimedTnt.class);
                registry.registerGuestType("Zombie", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Zombie.class);
                registry.registerGuestType("Creeper", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Creeper.class);
                registry.registerGuestType("Villager", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Villager.class);
                registry.registerGuestType("Cat", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Cat.class);
                registry.registerGuestType("Wolf", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Wolf.class);
                registry.registerGuestType("Sheep", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Sheep.class);
                registry.registerGuestType("TamableAnimal", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.TamableAnimal.class);
                registry.registerGuestType("Animal", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Animal.class);
                registry.registerGuestType("Mob", ink.tuanzi.neomacros.core.api.entity.SpeciesEntities.Mob.class);
                registry.registerGuestType("Ws", ink.tuanzi.neomacros.core.api.NetApi.Ws.class);
                registry.registerGuestType("FileHandle",
                        ink.tuanzi.neomacros.core.api.FsApi.FileHandle.class);
                registry.registerGuestType("Image", ink.tuanzi.neomacros.core.api.types.Image.class);
        registry.promoteRootMembers("thread", List.of("sleep", "ticks", "nextTick", "runOnMain"));
        ScriptApiEnvironment.install(bus, adapter);
        ScriptApiEnvironment.installStorage(storageDir);

        ScriptEngine engine = new GraalJsEngine(new ModuleResolver() {
            @Override
            public Optional<String> virtualSource(String specifier) {
                return registry.virtualSourceFor(specifier, PermissionView.ALLOW_ALL);
            }

            @Override
            public Optional<Path> fileFor(String specifier) {
                return Optional.empty();
            }
        });
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
