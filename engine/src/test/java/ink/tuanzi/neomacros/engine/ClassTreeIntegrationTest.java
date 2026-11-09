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
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guest class-tree integration over the real engine: {@code @ApiClass}
 * handles render as instances of real ES classes (instanceof chains,
 * inherited getters, zero-arg methods, toStringTag, JSON snapshots, user
 * subclassing, read-only properties), while un-annotated handles keep the
 * flat member-proxy rendering.
 */
class ClassTreeIntegrationTest {

    /** Root of a four-level cell hierarchy. */
    @ApiClass(name = "TreeNode", kind = ApiClassKind.HANDLE)
    public static class TreeNode implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        /** The node kind label. */
        public String getKind() {
            return "node";
        }

        /** Zero-argument method surface. */
        public String label() {
            return "node-label";
        }
    }

    /** Second level. */
    @ApiClass(name = "Branch", kind = ApiClassKind.HANDLE)
    public static class Branch extends TreeNode {

        /** The branch factor. */
        public int getBranchFactor() {
            return 2;
        }
    }

    /** Third level (adds no members; exercises the empty subclass shape). */
    @ApiClass(name = "Twig", kind = ApiClassKind.HANDLE)
    public static class Twig extends Branch {
    }

    /** Fourth level. */
    @ApiClass(name = "Leaf", kind = ApiClassKind.HANDLE)
    public static class Leaf extends Twig {

        /** The leaf color. */
        public String getColor() {
            return "green";
        }

        /** JSON string so {@code JSON.stringify} renders the handle directly. */
        public String toJSON() {
            return "{\"color\":\"green\"}";
        }
    }

    /** Bare handle without {@code @ApiClass}: keeps the flat proxy rendering. */
    public static class Bare implements ink.tuanzi.neomacros.core.api.ScriptHandle {

        /** The bare value. */
        public String getValue() {
            return "bare";
        }
    }

    /** Test facade handing out hierarchy instances. */
    @ApiModule(name = "tree", category = "test")
    public static class TreeApi {

        /** A leaf instance. */
        @ApiSince("0.1.0")
        public Leaf leaf() {
            return new Leaf();
        }

        /** The same runtime instance upcast to the base cell type. */
        @ApiSince("0.1.0")
        public TreeNode upcast() {
            return new Leaf();
        }

        /** A bare un-annotated handle. */
        @ApiSince("0.1.0")
        public Bare bare() {
            return new Bare();
        }
    }

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
        registry.registerAll(List.of(TreeApi.class, ChatApi.class));
        var engine = new GraalJsEngine();
        runtime = new ScriptRuntime(engine, adapter, catalog, bus, pool, 500);
        runtime.createContext("class-tree");
    }

    @AfterEach
    void tearDown() {
        ScriptApiEnvironment.closeStorages();
        runtime.close();
        pool.close();
    }

    @Test
    void annotatedHandlesRenderAsRealClasses() throws Exception {
        var manager = new ModuleManager(runtime, registry, PermissionView.ALLOW_ALL,
                scriptsDir, m -> { });
        manager.load(writeEntry(manager, "tree-test", SCRIPT));

        for (String key : List.of("instanceofChain", "upcastResolvesRuntime", "inheritedGetter",
                "ownGetter", "midGetter", "zeroArgMethod", "toStringTag", "jsonSnapshot",
                "userSubclass", "readOnlyProps", "bareProxy", "bareNoTag")) {
            assertEventually(() -> chatLines.contains("log:TREE-" + key + "=true"),
                    "class-tree result '" + key + "' should hold; chat lines were: " + chatLines);
        }
    }

    private static final String SCRIPT = String.join("\n",
            "export function startup() {",
            "  neo.chat.log('TREE-STARTUP');",
            "}",
            "",
            "const leaf = neo.tree.leaf();",
            "const LeafCtor = leaf.constructor;",
            "const TwigCtor = Object.getPrototypeOf(LeafCtor);",
            "const BranchCtor = Object.getPrototypeOf(TwigCtor);",
            "const TreeNodeCtor = Object.getPrototypeOf(BranchCtor);",
            "const up = neo.tree.upcast();",
            "class Tracked extends LeafCtor {",
            "  shout() { return 'leaf:' + this.kind; }",
            "}",
            "const wrapped = new Tracked(leaf.__cell);",
            "const results = {",
            "  instanceofChain: leaf instanceof TreeNodeCtor && leaf instanceof BranchCtor",
            "    && leaf instanceof TwigCtor && leaf instanceof LeafCtor,",
            "  upcastResolvesRuntime: up instanceof LeafCtor && up instanceof BranchCtor,",
            "  inheritedGetter: leaf.kind === 'node',",
            "  ownGetter: leaf.color === 'green',",
            "  midGetter: leaf.branchFactor === 2,",
            "  zeroArgMethod: leaf.label() === 'node-label',",
            "  toStringTag: Object.prototype.toString.call(leaf) === '[object Leaf]',",
            "  jsonSnapshot: JSON.parse(JSON.stringify(leaf)).color === 'green',",
            "  toJSONString: typeof leaf.toJSON() === 'string',",
            "  userSubclass: wrapped instanceof TreeNodeCtor && wrapped.shout() === 'leaf:node'",
            "    && wrapped.color === 'green',",
            "  readOnlyProps: (() => { try { leaf.color = 'red'; return false; } catch (e) { return true; } })(),",
            "};",
            "for (const [key, value] of Object.entries(results)) {",
            "  neo.chat.log('TREE-' + key + '=' + value);",
            "}",
            "neo.chat.log('TREE-bareProxy=' + (neo.tree.bare().value === 'bare'));",
            "neo.chat.log('TREE-bareNoTag=' + (Object.prototype.toString.call(neo.tree.bare()) === '[object Object]'));",
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
