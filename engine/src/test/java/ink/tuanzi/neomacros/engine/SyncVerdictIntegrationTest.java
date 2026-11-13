package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.api.ChatApi;
import ink.tuanzi.neomacros.core.api.EventsApi;
import ink.tuanzi.neomacros.core.api.StorageApi;
import ink.tuanzi.neomacros.core.api.ThreadApi;
import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.event.def.chat.ChatTitleEvent;
import ink.tuanzi.neomacros.core.event.def.input.InputKeyPressEvent;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The events.onSync mutable-event channel end to end through a real script:
 * field writes commit through the hooks, e.cancel() cancels, the writable
 * cancelled accessor undoes an earlier cancel across priorities, MONITOR is
 * read-only, and the direction class hierarchy is introspectable.
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
        for (var descriptor : catalog.all()) {
            registry.registerEventType(descriptor.name(), descriptor.cancellable(),
                    descriptor.direction());
        }
    }

    @AfterEach
    void tearDown() {
        ScriptApiEnvironment.closeStorages();
        runtime.close();
        pool.close();
    }

    @Test
    void inventoryClickAndOpenCancelThroughScripts() throws Exception {
        load("inv-cancel", String.join("\n",
                "neo.events.onSync('inventory:click', (e) => {",
                "  if (e.slot === 5 && e.mode === 'throw') { e.cancel(); }",
                "});",
                "neo.events.onSync('inventory:open', (e) => {",
                "  if (e.kind === 'chest') { e.cancel(); }",
                "});",
                "neo.chat.log('INV-READY');",
                "export function startup() {}",
                ""));

        assertTrue(chatLines.contains("log:INV-READY"), "chat lines were: " + chatLines);

        var click = bus.publish("inventory:click",
                new ink.tuanzi.neomacros.core.event.def.inventory.InventoryClickEvent(
                        1, "chest", 5, 1, "throw", false));
        assertTrue(click.canceled(), "a throw click on slot 5 is cancelled");

        var pickupClick = bus.publish("inventory:click",
                new ink.tuanzi.neomacros.core.event.def.inventory.InventoryClickEvent(
                        1, "chest", 6, 0, "pickup", false));
        assertFalse(pickupClick.canceled(), "other clicks pass");

        var open = bus.publish("inventory:open",
                new ink.tuanzi.neomacros.core.event.def.inventory.InventoryOpenEvent(
                        "Chest", "chest", 27, 1, "GenericContainerScreen"));
        assertTrue(open.canceled(), "chest opens are cancelled");

        var playerOpen = bus.publish("inventory:open",
                new ink.tuanzi.neomacros.core.event.def.inventory.InventoryOpenEvent(
                        "Inventory", "player", 46, 0, "InventoryScreen"));
        assertFalse(playerOpen.canceled(), "other containers pass");
    }

    @Test
    void mutableWritesAndCancelReachThePublishResult() throws Exception {
        load("mutator", String.join("\n",
                "neo.events.onSync('chat:title', (e) => {",
                "  if (e.text.plain === 'swap-me') { e.text = 'swapped'; }",
                "  if (e.text.plain === 'cancel-me') { e.cancel(); }",
                "});",
                "neo.chat.log('SYNC-READY');",
                "export function startup() {}",
                ""));

        assertTrue(chatLines.contains("log:SYNC-READY"), "chat lines were: " + chatLines);

        // Field write commits through the hooks and lands as the verdict.
        var swapped = bus.publish("chat:title",
                new ChatTitleEvent("title", ChatComponent.plain("swap-me")));
        assertFalse(swapped.canceled());
        assertEquals(Map.of("text", "swapped"), swapped.verdict());

        // e.cancel() propagates.
        var canceled = bus.publish("chat:title",
                new ChatTitleEvent("title", ChatComponent.plain("cancel-me")));
        assertTrue(canceled.canceled());
        assertNull(canceled.verdict());

        // Untouched deliveries pass through with no verdict.
        var pass = bus.publish("chat:title",
                new ChatTitleEvent("title", ChatComponent.plain("untouched")));
        assertFalse(pass.canceled());
        assertNull(pass.verdict());
    }

    @Test
    void laterPriorityObservesWritesAndCanUndoCancel() throws Exception {
        load("chain", String.join("\n",
                "neo.events.onSync('chat:title', (e) => {",
                "  if (e.text.plain === 'swap-me') { e.cancel(); }",
                "}, { priority: neo.EventPriority.LOWEST });",
                "neo.events.onSync('chat:title', (e) => {",
                "  if (e.cancelled) { e.cancelled = false; }",
                "  e.text = 'final';",
                "}, { priority: neo.EventPriority.HIGHEST });",
                "neo.chat.log('CHAIN-READY');",
                "export function startup() {}",
                ""));

        assertTrue(chatLines.contains("log:CHAIN-READY"), "chat lines were: " + chatLines);
        var result = bus.publish("chat:title",
                new ChatTitleEvent("title", ChatComponent.plain("swap-me")));
        // The HIGHEST handler undid the LOWEST cancel and rewrote the text.
        assertFalse(result.canceled());
        assertEquals(Map.of("text", "final"), result.verdict());
    }

    @Test
    void monitorIsReadOnly() throws Exception {
        load("monitor", String.join("\n",
                "neo.events.onSync('chat:title', (e) => {",
                "  e.text = 'nope';",
                "}, { priority: neo.EventPriority.MONITOR });",
                "neo.chat.log('MONITOR-READY');",
                "export function startup() {}",
                ""));

        assertTrue(chatLines.contains("log:MONITOR-READY"), "chat lines were: " + chatLines);
        var result = bus.publish("chat:title",
                new ChatTitleEvent("title", ChatComponent.plain("original")));
        assertFalse(result.canceled());
        assertNull(result.verdict());
    }

    @Test
    void directionClassHierarchyAndIntrospectionFace() throws Exception {
        load("introspect", String.join("\n",
                "neo.events.on('input:keyPress', (e) => {",
                "  const isInput = e instanceof neo.InputEvent;",
                "  const underBase = e instanceof neo.BaseEvent;",
                "  const keyClass = e instanceof neo.InputKeyPressEvent;",
                "  const isState = e instanceof neo.StateEvent;",
                "  const isDisplay = e instanceof neo.DisplayEvent;",
                "  const proto1 = Object.getPrototypeOf(e);",
                "  const proto2 = Object.getPrototypeOf(proto1);",
                "  const same = proto2 === neo.InputEvent.prototype;",
                "  const names = proto2.name + '|' + neo.InputEvent.name;",
                "  neo.chat.log('FACE:' + isInput + ':' + underBase + ':' + keyClass",
                "      + ':' + isState + ':' + isDisplay + ':' + same + ':' + names",
                "      + ':' + e.direction + ':' + e.type);",
                "});",
                "export function startup() {}",
                ""));

        bus.publish("input:keyPress",
                new InputKeyPressEvent("key.keyboard.r", 19, "press", List.of()));
        // Async handlers run on the context loop; poll until the line lands.
        String face = "";
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            face = chatLines.stream().filter(l -> l.startsWith("log:FACE:"))
                    .findFirst().orElse("");
            if (!face.isEmpty()) {
                break;
            }
            Thread.sleep(10);
        }
        assertEquals("log:FACE:true:true:true:false:false:true:undefined|InputEvent:c2s:input:keyPress",
                face, "chat lines were: " + chatLines);
    }

    @Test
    void priorityOptionValidationAndNonMutableFieldGuard() throws Exception {
        load("options", String.join("\n",
                "let got = '';",
                "try { neo.events.onSync('chat:title', () => {}, { priority: 'high' }); }",
                "catch (e) { got = 'ERR:' + (e.message || e); }",
                "neo.chat.log(got === '' ? 'NO-ERR' : got);",
                "neo.events.onSync('chat:title', (e) => {",
                "  try { e.kind = 'redirect'; } catch (err) { neo.chat.log('GUARD:' + (err.message || err)); }",
                "});",
                "export function startup() {}",
                ""));

        assertTrue(chatLines.stream().anyMatch(l -> l.startsWith("log:ERR:")
                        && l.contains("neo.EventPriority")),
                "chat lines were: " + chatLines);
        bus.publish("chat:title", new ChatTitleEvent("title", ChatComponent.plain("x")));
        assertTrue(chatLines.stream().anyMatch(l -> l.startsWith("log:GUARD:")
                        && l.contains("not mutable")),
                "chat lines were: " + chatLines);
    }

    /** Writes one script folder (manifest + index.js) and loads it. */
    private void load(String name, String source) throws Exception {
        var manager = new ModuleManager(runtime, registry, PermissionView.ALLOW_ALL,
                scriptsDir, m -> { });
        Path folder = scriptsDir.resolve("event").resolve(name);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("neomacro.yml"),
                "name: " + name + "\nversion: 1.0.0\nauthor: test\n"
                        + "config:\n  event:\n    on: [tick]\n");
        Files.writeString(folder.resolve("index.js"), source);
        var entry = manager.discover().stream()
                .filter(e -> e.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("entry not discovered: " + name));
        manager.load(entry);
    }
}
