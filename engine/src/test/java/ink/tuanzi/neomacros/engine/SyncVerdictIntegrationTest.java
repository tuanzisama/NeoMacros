package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.api.EventsApi;
import ink.tuanzi.neomacros.core.api.StorageApi;
import ink.tuanzi.neomacros.core.api.ThreadApi;
import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.api.ChatApi;
import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import ink.tuanzi.neomacros.core.event.def.chat.ChatTitleEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The events.onSync verdict channel end to end through a real script: the
 * truthy return cancels, and a returned plain object lands on the publish
 * result as a neutral map (guest → host adaptation of the Function surface).
 */
class SyncVerdictIntegrationTest {

    @TempDir
    Path scriptsDir;

    private ScriptThreadPool pool;
    private ScriptRuntime runtime;
    private EventBus bus;
    private BindingRegistry registry;
    private final List<String> chatLines = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        pool = new ScriptThreadPool(2);
        var catalog = new EventCatalog();
        catalog.scanPackage("ink.tuanzi.neomacros.core.event.def");
        bus = new EventBus(catalog, Runnable::run);
        ScriptApiEnvironment.install(bus, ScriptLoadIntegrationTest.adapterOf(chatLines));
        registry = new BindingRegistry("26.3");
        registry.registerAll(List.of(EventsApi.class, ThreadApi.class, ChatApi.class, StorageApi.class));
        registry.promoteRootMembers("thread", List.of("sleep", "ticks", "nextTick", "runOnMain"));
        var engine = new GraalJsEngine();
        runtime = new ScriptRuntime(engine, ScriptLoadIntegrationTest.adapterOf(chatLines),
                catalog, bus, pool, 500);
        runtime.createContext("sync-verdict");
    }

    @AfterEach
    void tearDown() {
        ScriptApiEnvironment.closeStorages();
        runtime.close();
        pool.close();
    }

    @Test
    void scriptSyncVerdictsReachThePublishResult() throws Exception {
        var manager = new ModuleManager(runtime, registry, PermissionView.ALLOW_ALL, scriptsDir, m -> { });
        manager.load(entry(manager, "syncer", String.join("\n",
                "neo.events.onSync('chat:title', (e) => {",
                "  if (e.text.plain === 'cancel-me') { return true; }",
                "  if (e.kind === 'swap-me') { return { text: 'swapped' }; }",
                "  return undefined;",
                "});",
                "neo.chat.log('SYNC-READY');",
                "export function startup() {}",
                "")));

        assertTrue(chatLines.contains("log:SYNC-READY"), "chat lines were: " + chatLines);

        // Truthy scalar return: cancel, no verdict.
        var canceled = bus.publish("chat:title",
                new ChatTitleEvent("title", ChatComponent.plain("cancel-me")));
        assertTrue(canceled.canceled());
        assertEquals(null, canceled.verdict());

        // Plain-object return: no cancel; the fields land as a neutral map.
        var swapped = bus.publish("chat:title",
                new ChatTitleEvent("swap-me", ChatComponent.plain("original")));
        assertFalse(swapped.canceled());
        assertTrue(swapped.verdict() instanceof java.util.Map<?, ?> verdict,
                "verdict should be a neutral map: " + swapped.verdict());
        assertEquals("swapped", verdict(swapped.verdict()).get("text"));

        // Undefined return: neither cancel nor verdict.
        var pass = bus.publish("chat:title",
                new ChatTitleEvent("title", ChatComponent.plain("untouched")));
        assertFalse(pass.canceled());
        assertEquals(null, pass.verdict());
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> verdict(Object value) {
        return (java.util.Map<String, Object>) value;
    }

    /** Writes one script folder (manifest + index.js) and returns its discovery entry. */
    private ink.tuanzi.neomacros.core.module.ScriptEntry entry(ModuleManager manager, String name, String source)
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
}
