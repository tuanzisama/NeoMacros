package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.api.ChatApi;
import ink.tuanzi.neomacros.core.api.ThreadApi;
import ink.tuanzi.neomacros.core.api.EventsApi;
import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.StorageApi;
import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.runtime.ScriptError;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.port.ChatPort;
import ink.tuanzi.neomacros.port.EntityPort;
import ink.tuanzi.neomacros.port.LifecyclePort;
import ink.tuanzi.neomacros.port.NeomacrosAdapter;
import ink.tuanzi.neomacros.port.PlayerPort;
import ink.tuanzi.neomacros.port.WorldPort;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full script loading chain against the real engine: annotated facades, the
 * virtual {@code @neomacros/*} module sources, event subscription, the clock
 * primitives, and the chat facade — the same path production bootstrap runs.
 */
class ScriptLoadIntegrationTest {

    @TempDir
    Path scriptsDir;

    private ScriptThreadPool pool;
    private ScriptRuntime runtime;
    private GraalJsEngine engine;
    private ScriptContext context;
    private BindingRegistry registry;
    private final List<String> chatLines = new ArrayList<>();

    /** Test adapter: chat output recorded, unimplemented ports rejected. */
    static NeomacrosAdapter adapterOf(List<String> chatLines) {
        return new NeomacrosAdapter() {
            @Override
            public LifecyclePort lifecycle() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChatPort chat() {
                return new ChatPort() {
                    @Override
                    public void say(String message) {
                        chatLines.add("say:" + message);
                    }

                    @Override
                    public void log(String message) {
                        chatLines.add("log:" + message);
                    }

                    @Override
                    public void actionbar(String message) {
                        chatLines.add("bar:" + message);
                    }

                    @Override
                    public void onChatReceive(java.util.function.Consumer<ChatComponent> sink) {
                    }
                };
            }

            @Override
            public WorldPort world() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ink.tuanzi.neomacros.port.ClientPort client() {
                throw new UnsupportedOperationException();
            }

            @Override
            public EntityPort entity() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ink.tuanzi.neomacros.port.InventoryPort inventory() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ink.tuanzi.neomacros.port.InputPort input() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ink.tuanzi.neomacros.port.RenderPort render() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ink.tuanzi.neomacros.port.Draw3DPort draw3d() {
                throw new UnsupportedOperationException();
            }

            @Override
            public PlayerPort player() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @BeforeEach
    void setUp() throws Exception {
        pool = new ScriptThreadPool(2);
        var catalog = new EventCatalog();
        catalog.scanPackage("ink.tuanzi.neomacros.core.event.def");
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = adapterOf(chatLines);
        ScriptApiEnvironment.install(bus, adapter);
        registry = new BindingRegistry("26.3");
        registry.registerAll(List.of(EventsApi.class, ThreadApi.class, ChatApi.class, StorageApi.class));
        registry.promoteRootMembers("thread", List.of("sleep", "ticks", "nextTick", "runOnMain"));
        engine = new GraalJsEngine(new ModuleResolver() {
            @Override
            public Optional<String> virtualSource(String specifier) {
                return registry.virtualSourceFor(specifier, PermissionView.ALLOW_ALL);
            }

            @Override
            public Optional<Path> fileFor(String specifier) {
                return Optional.empty();
            }
        });
        runtime = new ScriptRuntime(engine, adapter, catalog, bus, pool, 500);
        context = runtime.createContext("integration");
    }

    @AfterEach
    void tearDown() {
        ScriptApiEnvironment.closeStorages();
        runtime.close();
        pool.close();
    }

    @Test
    void storagePutGetPersists(@TempDir Path storageDir) throws Exception {
        ScriptApiEnvironment.installStorage(storageDir);
        var manager = new ink.tuanzi.neomacros.core.module.ModuleManager(
                runtime, registry, PermissionView.ALLOW_ALL, scriptsDir, m -> { });
        manager.load(writeEntry(manager, "storer", String.join("\n",
                "import { chat } from '@neomacros/chat';",
                "import { storage } from '@neomacros/storage';",
                "storage.put('count', { n: 7 });",
                "chat.log('COUNT-' + storage.get('count').n);",
                "export function startup() {}",
                "")));

        assertTrue(chatLines.contains("log:COUNT-7"), "chat lines were: " + chatLines);
        ScriptApiEnvironment.closeStorages();
        assertTrue(Files.exists(storageDir.resolve("storer.json")),
                "storage file should be flushed on closeStorages");
    }

    @Test
    void loadsSmokeScriptThroughVirtualModules() throws Exception {
        var manager = new ink.tuanzi.neomacros.core.module.ModuleManager(
                runtime, registry, PermissionView.ALLOW_ALL, scriptsDir, m -> { });
        manager.load(writeEntry(manager, "smoke", String.join("\n",
                "import { events, thread } from '@neomacros/core';",
                "import { chat } from '@neomacros/chat';",
                "",
                "let ticks = 0;",
                "events.on('tick', () => {",
                "  ticks++;",
                "  if (ticks === 2) {",
                "    chat.log('TICKS-OK');",
                "  }",
                "});",
                "thread.sleep(10).then(() => {",
                "  chat.log('SLEEP-OK');",
                "});",
                "export function startup() {}",
                "")));

        runtime.eventBus().publish("tick", 1L);
        runtime.eventBus().publish("tick", 2L);

        assertEventually(() -> chatLines.contains("log:TICKS-OK"),
                "tick handler should run; chat lines were: " + chatLines);
        assertEventually(() -> chatLines.contains("log:SLEEP-OK"),
                "sleep promise should resolve; chat lines were: " + chatLines);
    }

    /** Writes one script folder (manifest + index.js) and returns its discovery entry. */
    private ink.tuanzi.neomacros.core.module.ScriptEntry writeEntry(
            ink.tuanzi.neomacros.core.module.ModuleManager manager, String name, String source)
            throws Exception {
        Path folder = scriptsDir.resolve("event").resolve(name);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("neomacro.yml"),
                "name: " + name + "\nversion: 1.0.0\nauthor: test\n"
                        + "config:\n  event:\n    on: [tick]\n");
        Files.writeString(folder.resolve("index.js"), source);
        return manager.discover().stream()
                .filter(e -> e.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("entry not discovered: " + name));
    }

    private static void assertEventually(java.util.function.Supplier<Boolean> condition, String message)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(20);
        }
        assertEquals(true, condition.get(), message);
    }
}
