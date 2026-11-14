package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.LifecyclePort;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lifecycle sources backed by the Fabric client lifecycle, tick, and play
 * connection events. Sinks run on the game main thread, in registration
 * order; a sink that throws is logged and skipped so it can neither break
 * the remaining sinks nor crash the game's own tick/shutdown path.
 */
public final class FabricLifecyclePort implements LifecyclePort {

    private static final Logger LOGGER = LoggerFactory.getLogger(FabricLifecyclePort.class);

    private final List<Consumer<Long>> tickSinks = new CopyOnWriteArrayList<>();
    private final List<Runnable> gameStartSinks = new CopyOnWriteArrayList<>();
    private final List<Runnable> gameQuitSinks = new CopyOnWriteArrayList<>();
    private final List<Runnable> worldJoinSinks = new CopyOnWriteArrayList<>();
    private final List<Runnable> worldLeaveSinks = new CopyOnWriteArrayList<>();
    private final List<Consumer<ink.tuanzi.neomacros.port.model.ResourcePackLoadData>>
            resourcePackSinks = new CopyOnWriteArrayList<>();
    private final AtomicLong tickCount = new AtomicLong();
    /** Marks the first observed client resource reload as the boot load. */
    private final java.util.concurrent.atomic.AtomicBoolean firstResourceReload =
            new java.util.concurrent.atomic.AtomicBoolean(true);

    /** Creates the port and hooks the Fabric client events feeding the sinks. */
    public FabricLifecyclePort() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long tick = tickCount.incrementAndGet();
            for (Consumer<Long> sink : tickSinks) {
                runSafely("tick", () -> sink.accept(tick));
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            for (Runnable sink : gameStartSinks) {
                runSafely("game-start", sink);
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            for (Runnable sink : gameQuitSinks) {
                runSafely("game-quit", sink);
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            for (Runnable sink : worldJoinSinks) {
                runSafely("world-join", sink);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            for (Runnable sink : worldLeaveSinks) {
                runSafely("world-leave", sink);
            }
            TabListTracker.clear();
            BossbarHub.clear();
        });
        registerResourcePackListener();
    }

    /**
     * Hooks the fabric client-resource reload listener: it runs on the
     * reload's apply stage (the game thread for client reloads) once every
     * pack has landed, which is the point the loading overlay begins fading
     * out. The first observed reload since construction is the boot load.
     */
    @SuppressWarnings("deprecation") // the v0 helper is the only registration API on every node
    private void registerResourcePackListener() {
        net.fabricmc.fabric.api.resource.ResourceManagerHelper
                .get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloadListener(new PackLoadedListener());
    }

    /** One completed client resource reload forwarded to the port sinks. */
    private final class PackLoadedListener implements
            net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener,
            net.minecraft.server.packs.resources.ResourceManagerReloadListener {

        @Override
        public net.minecraft.resources.Identifier getFabricId() {
            return net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    "neomacros", "resource_pack_loaded");
        }

        @Override
        public void onResourceManagerReload(
                net.minecraft.server.packs.resources.ResourceManager manager) {
            boolean gameStart = firstResourceReload.getAndSet(false);
            List<String> packs;
            try {
                packs = List.copyOf(Minecraft.getInstance().getResourcePackRepository()
                        .getSelectedIds());
            } catch (RuntimeException e) {
                LOGGER.warn("neomacros: resource pack list unavailable on reload", e);
                packs = List.of();
            }
            var data = new ink.tuanzi.neomacros.port.model.ResourcePackLoadData(
                    gameStart, packs);
            for (Consumer<ink.tuanzi.neomacros.port.model.ResourcePackLoadData> sink
                    : resourcePackSinks) {
                runSafely("resource-pack-loaded", () -> sink.accept(data));
            }
        }
    }

    /** Runs one sink, downgrading any failure to a log record. */
    private static void runSafely(String source, Runnable sink) {
        try {
            sink.run();
        } catch (Throwable t) {
            LOGGER.error("NeoMacros lifecycle sink failed during {}; continuing", source, t);
        }
    }

    @Override
    public void onTick(Consumer<Long> sink) {
        tickSinks.add(sink);
    }

    @Override
    public void onGameStart(Runnable sink) {
        gameStartSinks.add(sink);
    }

    @Override
    public void onGameQuit(Runnable sink) {
        gameQuitSinks.add(sink);
    }

    @Override
    public void onWorldJoin(Runnable sink) {
        worldJoinSinks.add(sink);
    }

    @Override
    public void onWorldLeave(Runnable sink) {
        worldLeaveSinks.add(sink);
    }

    @Override
    public void onResourcePackLoaded(
            Consumer<ink.tuanzi.neomacros.port.model.ResourcePackLoadData> sink) {
        resourcePackSinks.add(sink);
    }

    @Override
    public void requestShutdown() {
        Minecraft.getInstance().stop();
    }

    @Override
    public Supplier<Boolean> inWorld() {
        return () -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.level != null;
        };
    }

    /** The current screen accessor moved onto Gui in 26.2 (same guard as the key hook). */
    static net.minecraft.client.gui.screens.Screen currentScreen() {
        //? if >=26.2 {
        return Minecraft.getInstance().gui.screen();
        //?} else {
        /*
        return Minecraft.getInstance().screen;
        */
        //?}
    }

    @Override
    public boolean isScreenOpen() {
        return currentScreen() != null;
    }

    @Override
    public java.util.Optional<String> screenTitle() {
        var screen = currentScreen();
        if (screen == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(screen.getTitle() == null
                ? null : screen.getTitle().getString());
    }

    @Override
    public java.util.Optional<ScreenInfo> screenInfo() {
        var screen = currentScreen();
        if (screen == null) {
            return java.util.Optional.empty();
        }
        String title = screen.getTitle() != null ? screen.getTitle().getString() : "";
        return java.util.Optional.of(new ScreenInfo(
                screen.getClass().getSimpleName(), screenKind(screen), title));
    }

    /** Maps one open screen to its kind label (specific classes first, then containers). */
    private static String screenKind(net.minecraft.client.gui.screens.Screen screen) {
        if (screen instanceof ink.tuanzi.neomacros.fabric.ScriptScreen) {
            return "script";
        }
        if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
            return "inventory";
        }
        if (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
            return "creative";
        }
        if (screen instanceof net.minecraft.client.gui.screens.ChatScreen
                || screen instanceof net.minecraft.client.gui.screens.InBedChatScreen) {
            return "chat";
        }
        if (screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            return "title";
        }
        if (screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
            return "pause";
        }
        if (screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
            return "death";
        }
        if (screen instanceof net.minecraft.client.gui.screens.WinScreen) {
            return "wingame";
        }
        if (screen instanceof net.minecraft.client.gui.screens.ProgressScreen
                || screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
                //? if <26.1 {
                /*|| screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen*/
                //?}
                || screen instanceof net.minecraft.client.gui.screens.GenericMessageScreen) {
            return "progress";
        }
        if (screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) {
            return "disconnected";
        }
        if (screen instanceof net.minecraft.client.gui.screens.ConnectScreen) {
            return "connecting";
        }
        if (screen instanceof net.minecraft.client.gui.screens.ConfirmScreen
                || screen instanceof net.minecraft.client.gui.screens.AlertScreen) {
            return "confirm";
        }
        if (screen instanceof net.minecraft.client.gui.screens.dialog.DialogScreen<?>) {
            return "dialog";
        }
        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen) {
            return "sign";
        }
        if (screen instanceof net.minecraft.client.gui.screens.inventory.BookEditScreen) {
            return "bookedit";
        }
        if (screen instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen) {
            return "bookview";
        }
        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen) {
            return "commandblock";
        }
        if (screen instanceof net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen) {
            return "structureblock";
        }
        if (screen instanceof net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen) {
            return "jigsaw";
        }
        if (screen instanceof net.minecraft.client.gui.screens.inventory.HorseInventoryScreen) {
            return "mount";
        }
        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
            var menu = ((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) screen).getMenu();
            return menu != null ? FabricInventoryPort.menuKind(menu) : "container";
        }
        return "screen";
    }

    @Override
    public java.util.Optional<String> chatText() {
        var screen = currentScreen();
        if (screen instanceof net.minecraft.client.gui.screens.ChatScreen chat) {
            var input = ((ink.tuanzi.neomacros.adapter.mixin.ChatScreenAccessor) chat).neomacros$input();
            return java.util.Optional.ofNullable(input != null ? input.getValue() : null);
        }
        return java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<String> signText() {
        var screen = currentScreen();
        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen sign) {
            // The editor's own line buffer rather than the block entity: it
            // is the face under edit (front or back), it tracks typing, and
            // it reflects write-face writes made before the editor closes
            // (the block entity only catches up once vanilla submits the
            // buffer on close).
            String[] messages =
                    ((ink.tuanzi.neomacros.adapter.mixin.AbstractSignEditScreenAccessor) sign)
                            .neomacros$messages();
            if (messages == null) {
                return java.util.Optional.empty();
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < messages.length; i++) {
                if (i > 0) {
                    text.append('\n');
                }
                text.append(messages[i] != null ? messages[i] : "");
            }
            return java.util.Optional.of(text.toString());
        }
        return java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<String> bookEditPage() {
        var screen = currentScreen();
        if (screen instanceof net.minecraft.client.gui.screens.inventory.BookEditScreen book) {
            var pages = ((ink.tuanzi.neomacros.adapter.mixin.BookEditScreenAccessor) book).neomacros$pages();
            int page = ((ink.tuanzi.neomacros.adapter.mixin.BookEditScreenAccessor) book).neomacros$currentPage();
            if (pages == null || page < 0 || page >= pages.size()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.ofNullable(pages.get(page));
        }
        return java.util.Optional.empty();
    }

    @Override
    public void closeScreen() {
        MainThread.run(() -> Minecraft.getInstance().gui.setScreen(null));
    }
}
