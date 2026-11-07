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
 * Java-interop integration over the real engine: class lookup through the
 * {@code java} facade, static and instance member access from a script,
 * construction via the {@code new} member, and the trusted-view flow of the
 * java capability gate.
 */
class JavaInteropIntegrationTest {

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
        registry.registerAll(List.of(ChatApi.class,
                ink.tuanzi.neomacros.core.api.java.JavaApi.class));
        var engine = new GraalJsEngine();
        runtime = new ScriptRuntime(engine, adapter, catalog, bus, pool, 500);
        runtime.createContext("java-interop");
    }

    @AfterEach
    void tearDown() {
        ScriptApiEnvironment.closeStorages();
        runtime.close();
        pool.close();
    }

    @Test
    void scriptsReachHostClassesThroughTheEscapeLayer() throws Exception {
        var manager = new ModuleManager(runtime, registry, PermissionView.ALLOW_ALL,
                scriptsDir, m -> { });
        manager.load(writeEntry(manager, "java-test", SCRIPT));

        for (String key : List.of("pi", "floor", "list", "uuid", "instanceOf",
                "proxy", "fromList", "toList")) {
            assertEventually(() -> chatLines.contains("log:JAVA-" + key + "=true"),
                    "interop result '" + key + "' should hold; chat lines were: " + chatLines);
        }
    }

    private static final String SCRIPT = String.join("\n",
            "export function startup() {",
            "  neo.chat.log('JAVA-STARTUP');",
            "}",
            "",
            "const MathCls = neo.java.type('java.lang.Math');",
            "neo.chat.log('JAVA-pi=' + (MathCls.PI > 3.14 && MathCls.PI < 3.15));",
            "neo.chat.log('JAVA-floor=' + (MathCls.floor(3.7) === 3));",
            "",
            "const list = neo.java.type('java.util.ArrayList').new();",
            "list.add('alpha');",
            "list.add('beta');",
            "neo.chat.log('JAVA-list=' + (list.size() === 2 && list.get(0) === 'alpha'",
            "    && list.get(1) === 'beta'));",
            "",
            "const id = neo.java.type('java.util.UUID').fromString('00000000-0000-0000-0000-000000000000');",
            "neo.chat.log('JAVA-uuid=' + (id.toString() === '00000000-0000-0000-0000-000000000000'));",
            "neo.chat.log('JAVA-instanceOf=' + id.instanceOf('java.util.UUID'));",
            "",
            "let ran = false;",
            "const runnable = neo.java.proxy(['java.lang.Runnable'], { run() { ran = true; } });",
            "runnable.run();",
            "neo.chat.log('JAVA-proxy=' + ran);",
            "const arr = neo.java.fromList(list);",
            "neo.chat.log('JAVA-fromList=' + (Array.isArray(arr) && arr.length === 2 && arr[0] === 'alpha'));",
            "const hostList = neo.java.toList(['x', 'y']);",
            "neo.chat.log('JAVA-toList=' + (hostList.size() === 2 && hostList.get(1) === 'y'));",
            "");

    /** Writes one script folder (manifest + index.js) and returns its discovery entry. */
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

    private static void assertEventually(java.util.function.Supplier<Boolean> condition,
                                         String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(20);
        }
        assertTrue(condition.get(), message);
    }
}
