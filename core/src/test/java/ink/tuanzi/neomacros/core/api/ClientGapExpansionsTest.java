package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.client.ClientApi;
import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.ClientPort;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.InputPort;
import ink.tuanzi.neomacros.port.InventoryPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.ModData;
import ink.tuanzi.neomacros.port.model.OptionsData;
import ink.tuanzi.neomacros.port.model.Pos2d;
import ink.tuanzi.neomacros.port.model.ServerInfoData;
import ink.tuanzi.neomacros.port.model.WindowData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client gap expansions: connection lifecycle futures, mod listing,
 * window/mouse/clipboard surfaces, server info, option slice, and registry
 * queries, driven through the API over a canned port.
 */
class ClientGapExpansionsTest {

    private CannedClient client;
    private ClientApi api;

    @BeforeEach
    void install() {
        client = new CannedClient();
        ScriptApiEnvironment.install(null, new SinglePortAdapter(client));
        api = new ClientApi();
    }

    @AfterEach
    void uninstall() {
        ScriptApiEnvironment.install(null, null);
    }

    @Test
    void lifecycleFuturesDelegateToPort() throws Exception {
        assertTrue(api.connect("localhost:25565").get(1, java.util.concurrent.TimeUnit.SECONDS)
                == null);
        assertTrue(api.disconnect().get(1, java.util.concurrent.TimeUnit.SECONDS) == null);
        assertTrue(api.loadWorld("New World").get(1, java.util.concurrent.TimeUnit.SECONDS)
                == null);
        assertEquals("localhost:25565", client.lastAddress);
        assertTrue(client.disconnected);
        assertEquals("New World", client.lastWorld);
    }

    @Test
    void unsupportedLifecycleRejects() {
        client.failLifecycle = true;
        assertRejects(api.connect("x"));
        assertRejects(api.disconnect());
        assertRejects(api.loadWorld("x"));
    }

    private static void assertRejects(CompletableFuture<Void> future) {
        try {
            future.get(1, java.util.concurrent.TimeUnit.SECONDS);
            throw new AssertionError("expected rejection");
        } catch (java.util.concurrent.ExecutionException expected) {
            // rejected as designed
        } catch (Exception e) {
            throw new AssertionError("unexpected failure", e);
        }
    }

    @Test
    void modsRenderShape() {
        List<ModData> mods = api.getMods();
        assertEquals(2, mods.size());
        assertEquals("neomacros", mods.get(0).id());
        assertEquals("1.0.0", mods.get(0).version());
        assertEquals(List.of("author"), mods.get(0).authors());
        assertTrue(api.isModLoaded("neomacros"));
        assertFalse(api.isModLoaded("absent"));
        ModData one = api.getMod("other");
        assertEquals("other", one.id());
        assertNull(api.getMod("absent"));
    }

    @Test
    void pingAndServerInfoFollowConnectionState() {
        assertNull(api.getPing());
        assertNull(api.getServerInfo());
        client.connected = true;
        assertEquals(42, api.getPing());
        assertEquals("Server", api.getServerInfo().name());
        assertEquals("host:25565", api.getServerInfo().address());
    }

    @Test
    void windowMouseAndOptionsRenderShape() {
        WindowData window = api.getWindow();
        assertEquals(1920, window.width());
        assertEquals(1080, window.height());
        assertEquals(640, window.scaledWidth());
        assertEquals(360, window.scaledHeight());
        ClientApi.MousePos mouse = api.getMouse();
        assertEquals(12.5, mouse.x());
        assertEquals(3.25, mouse.y());
        OptionsData options = api.getOptions();
        assertEquals(90, options.fov());
        assertEquals(1.0, options.gamma());
        assertEquals(2, options.guiScale());
        assertEquals(12, options.renderDistance());
    }

    @Test
    void clipboardRoundTripAndFlags() {
        api.setClipboard("hello");
        assertEquals("hello", api.getClipboard());
        assertTrue(api.isDevEnv());
        client.devEnv = false;
        assertFalse(api.isDevEnv());
    }

    @Test
    void registryQueriesOverCannedKinds() {
        assertEquals(List.of("minecraft:stone", "minecraft:dirt"),
                api.registryIds("block"));
        assertEquals("minecraft:stone", api.registryLookup("block", "stone"));
        assertNull(api.registryLookup("block", "missing"));
        try {
            api.registryIds("nope");
            throw new AssertionError("expected rejection");
        } catch (IllegalArgumentException expected) {
            // unknown kind as designed
        }
    }

    /** Client port with canned fields the tests mutate directly. */
    static final class CannedClient implements ClientPort {
        boolean connected;
        boolean devEnv = true;
        boolean failLifecycle;
        String lastAddress;
        String lastWorld;
        boolean disconnected;
        String clipboardValue;
        final List<ModData> modList = List.of(
                new ModData("neomacros", "NeoMacros", "1.0.0", List.of("author")),
                new ModData("other", "Other", "0.2.0", List.of()));

        @Override
        public int fps() {
            return 60;
        }

        @Override
        public String gameVersion() {
            return "26.3";
        }

        @Override
        public Object minecraftInstance() {
            return null;
        }

        @Override
        public CompletableFuture<Void> connect(String address) {
            if (failLifecycle) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
            lastAddress = address;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> disconnect() {
            if (failLifecycle) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
            disconnected = true;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> loadWorld(String name) {
            if (failLifecycle) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
            lastWorld = name;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.Optional<Integer> ping() {
            return connected ? java.util.Optional.of(42) : java.util.Optional.empty();
        }

        @Override
        public List<ModData> mods() {
            return modList;
        }

        @Override
        public boolean isModLoaded(String id) {
            return modList.stream().anyMatch(mod -> mod.id().equals(id));
        }

        @Override
        public boolean devEnvironment() {
            return devEnv;
        }

        @Override
        public String clipboard() {
            return clipboardValue;
        }

        @Override
        public void setClipboard(String value) {
            clipboardValue = value;
        }

        @Override
        public java.util.Optional<WindowData> window() {
            return java.util.Optional.of(new WindowData(1920, 1080, 640, 360));
        }

        @Override
        public Pos2d mouse() {
            return new Pos2d(12.5, 3.25);
        }

        @Override
        public java.util.Optional<ServerInfoData> serverInfo() {
            return connected
                    ? java.util.Optional.of(new ServerInfoData("Server", "host:25565"))
                    : java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<OptionsData> options() {
            return java.util.Optional.of(new OptionsData(90, 1.0, 2, 12));
        }

        @Override
        public List<String> registryIds(String kind) {
            if ("block".equals(kind)) {
                return List.of("minecraft:stone", "minecraft:dirt");
            }
            throw new IllegalArgumentException("unknown registry kind: " + kind);
        }

        @Override
        public java.util.Optional<String> registryLookup(String kind, String id) {
            if ("block".equals(kind) && "stone".equals(id)) {
                return java.util.Optional.of("minecraft:stone");
            }
            return java.util.Optional.empty();
        }
    }

    /** Adapter that only serves the client port; other ports are unused here. */
    record SinglePortAdapter(ClientPort client) implements NeomacrosAdapter {
        @Override
        public LifecyclePort lifecycle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatPort chat() {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorldPort world() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityPort entity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlayerPort player() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputPort input() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InventoryPort inventory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ink.tuanzi.neomacros.port.DialogPort dialog() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPort render() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Draw3DPort draw3d() {
            throw new UnsupportedOperationException();
        }
    }
}
