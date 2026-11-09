package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.api.ChatApi;
import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.module.ModuleManager;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Component/TextComponent round trip over the real engine: the guest builds
 * a component tree with {@code new Component(...)}, reads the value face,
 * and hands the built value back through the union chat members — the
 * conversion path must unwrap the guest handle to the live Java object.
 */
class ComponentRoundTripIntegrationTest {

    @TempDir
    Path scriptsDir;

    private ScriptThreadPool pool;
    private ScriptRuntime runtime;
    private BindingRegistry registry;
    private final List<String> chatLines = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        pool = new ScriptThreadPool(2);
        var catalog = new EventCatalog();
        catalog.scanPackage("ink.tuanzi.neomacros.core.event.def");
        var bus = new EventBus(catalog, Runnable::run);
        var adapter = ScriptLoadIntegrationTest.adapterOf(chatLines);
        ScriptApiEnvironment.install(bus, adapter);
        registry = new BindingRegistry("26.3");
        registry.registerAll(List.of(ChatApi.class));
        registry.registerGuestType("Component", ink.tuanzi.neomacros.core.api.types.Component.class);
        registry.registerGuestType("TextComponent", ink.tuanzi.neomacros.core.api.types.TextComponent.class);
        registry.registerGuestType("Pos3D", ink.tuanzi.neomacros.core.api.types.Pos3D.class);
        var engine = new GraalJsEngine();
        runtime = new ScriptRuntime(engine, adapter, catalog, bus, pool, 500);
        runtime.createContext("component-round-trip");
    }

    @AfterEach
    void tearDown() {
        ScriptApiEnvironment.closeStorages();
        runtime.close();
        pool.close();
    }

    @Test
    void guestBuildsAndSendsComponents() throws Exception {
        var manager = new ModuleManager(runtime, registry, PermissionView.ALLOW_ALL,
                scriptsDir, m -> { });
        try {
            manager.load(writeEntry(manager, "component-test", SCRIPT));
        } catch (RuntimeException e) {
            throw new AssertionError("load failed; chatLines=" + chatLines, e);
        }

        for (String key : List.of("plain", "json", "instanceof", "builder",
                "children", "content")) {
            assertEventually(() -> chatLines.contains("log:CT-" + key + "=true"),
                    "component result '" + key + "' should hold; chat lines were: " + chatLines);
        }
        assertTrue(chatLines.contains("rich:abc:false:3"),
                "log(component) rides the rich path; chat lines were: " + chatLines);
        assertTrue(chatLines.contains("rich:bar:true:1"),
                "actionbar(component) rides the rich path; chat lines were: " + chatLines);
        assertTrue(chatLines.contains("say:srv"),
                "say(component) rides the plain path; chat lines were: " + chatLines);
    }

    private ink.tuanzi.neomacros.core.module.ScriptEntry writeEntry(
            ModuleManager manager, String name, String source) throws Exception {
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

    private void assertEventually(java.util.function.BooleanSupplier condition,
            String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && !condition.getAsBoolean()) {
            Thread.sleep(25);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    private static final String SCRIPT = String.join("\n",
            "const comp = new neo.Component('a')",
            "  .color('red')",
            "  .append(new neo.Component('b').italic(true))",
            "  .append('c')",
            "  .build();",
            "const results = {",
            "  plain: comp.plain === 'abc',",
            "  json: JSON.stringify(comp) === '{\"text\":\"a\",\"color\":\"red\",\"extra\":[{\"text\":\"b\",\"italic\":true},{\"text\":\"c\"}]}',",
            "  instanceof: comp instanceof neo.TextComponent,",
            "  builder: new neo.Component('x') instanceof neo.Component,",
            "  children: comp.children.length === 2 && comp.children[0].plain === 'b',",
            "  content: comp.content === 'a'",
            "};",
            "for (const key of Object.keys(results)) {",
            "  neo.chat.log('CT-' + key + '=' + results[key]);",
            "}",
            "neo.chat.log(comp);",
            "neo.chat.actionbar(new neo.Component('bar').build());",
            "neo.chat.say(new neo.Component('srv').build());",
            "",
            "export function startup() {}");
}
