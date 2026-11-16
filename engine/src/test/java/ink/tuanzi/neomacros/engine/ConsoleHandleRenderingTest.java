package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.api.reflect.ReflectApi;
import ink.tuanzi.neomacros.core.api.reflect.ReflectClass;
import ink.tuanzi.neomacros.core.api.reflect.ReflectConstructor;
import ink.tuanzi.neomacros.core.api.reflect.ReflectField;
import ink.tuanzi.neomacros.core.api.reflect.ReflectMethod;
import ink.tuanzi.neomacros.core.api.types.Pos3D;
import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Console rendering of glue-class handle instances: members live on the
 * prototype behind a hidden {@code __cell}, so plain own-key enumeration
 * would see nothing and print {@code {}}. The formatter unwraps the cell
 * and renders the handle's live member surface instead.
 */
class ConsoleHandleRenderingTest {

    @TempDir
    Path scriptsDir;

    private final AtomicInteger evalSeq = new AtomicInteger();
    private ScriptThreadPool pool;
    private ScriptRuntime runtime;
    private GraalJsEngine engine;
    private GraalJsEngine.GraalJsSession session;

    @BeforeEach
    void setUp() throws Exception {
        pool = new ScriptThreadPool(2);
        BindingRegistry registry = new BindingRegistry("26.3");
        registry.register(ReflectApi.class);
        registry.registerGuestType("ReflectClass", ReflectClass.class);
        registry.registerGuestType("ReflectField", ReflectField.class);
        registry.registerGuestType("ReflectMethod", ReflectMethod.class);
        registry.registerGuestType("ReflectConstructor", ReflectConstructor.class);
        registry.registerGuestType("Pos3D", Pos3D.class);
        engine = new GraalJsEngine();
        runtime = new ScriptRuntime(engine, null,
                new EventCatalog(), new EventBus(new EventCatalog(), Runnable::run), pool, 500);
        var context = runtime.createContext("console");
        session = (GraalJsEngine.GraalJsSession) engine.openSession(context, scriptsDir);
        session.installBindings(registry.neoTree(PermissionView.of(Set.of("java"))));
    }

    @AfterEach
    void tearDown() {
        runtime.close();
        pool.close();
    }

    private Value eval(String source) throws Exception {
        String name = "case" + evalSeq.incrementAndGet();
        Path entry = scriptsDir.resolve(name + ".mjs");
        Files.writeString(entry, "globalThis.__out = " + source + ";\n");
        session.importModule(name, entry);
        return session.global("__out");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> render(String source) throws Exception {
        var field = session.getClass().getDeclaredField("context");
        field.setAccessible(true);
        org.graalvm.polyglot.Context context =
                (org.graalvm.polyglot.Context) field.get(session);
        return new ConsoleFormatter(context, ConsoleFormatter.MAX_NODES).node(eval(source));
    }

    private static List<Map<String, Object>> props(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.get("props");
    }

    private static boolean hasProp(Map<String, Object> node, String key) {
        return props(node).stream().anyMatch(p -> key.equals(p.get("k")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propValue(Map<String, Object> node, String key) {
        return (Map<String, Object>) props(node).stream()
                .filter(p -> key.equals(p.get("k"))).findFirst().orElseThrow().get("v");
    }

    @Test
    void reflectHandleRendersItsMemberSurfaceInsteadOfAnEmptyObject() throws Exception {
        Map<String, Object> node = render("neo.reflect.forName('java.lang.System')");

        assertEquals("obj", node.get("t"));
        assertTrue((Long) node.get("n") > 0, "the handle enumerates members, not {}");
        assertTrue(hasProp(node, "className"), "members include className");
        assertEquals("java.lang.System", propValue(node, "className").get("v"));
        assertTrue(hasProp(node, "field"), "methods cross as functions");
        assertEquals("fn", propValue(node, "field").get("t"));
        assertTrue(!String.valueOf(node.get("pre")).isEmpty(),
                "the collapsed preview carries content: " + node.get("pre"));
    }

    @Test
    void fieldHandleRendersNameAndModifierFlags() throws Exception {
        Map<String, Object> node = render(
                "neo.reflect.forName('java.lang.System').field('out')");

        assertEquals("obj", node.get("t"));
        assertTrue((Long) node.get("n") > 0);
        assertEquals("out", propValue(node, "name").get("v"));
        assertEquals("bool", propValue(node, "static").get("t"));
    }

    @Test
    void preExistingHandlesStopRenderingAsEmptyObjectsToo() throws Exception {
        Map<String, Object> node = render("new neo.Pos3D(1, 2, 3)");

        assertEquals("obj", node.get("t"));
        assertTrue((Long) node.get("n") > 0,
                "Pos3D printed {} before the cell unwrap; it must enumerate too");
        assertTrue(hasProp(node, "x") || hasProp(node, "toJSON"),
                "members surface: " + node.get("pre"));
    }

    @Test
    void plainObjectsWithoutACellKeepTheirOwnShape() throws Exception {
        Map<String, Object> node = render("({a: 1, b: 'two'})");

        assertEquals("obj", node.get("t"));
        assertEquals(2L, node.get("n"));
        assertEquals(1, ((Number) propValue(node, "a").get("v")).intValue());
        assertFalse(hasProp(node, "__cell"), "the hidden probe never surfaces");
    }

    @Test
    void nestedObjectPreviewsCarryTheirContent() {
        Map<String, Object> node = Map.of("t", "obj", "pre", "a: 1");
        assertEquals("{a: 1}", ConsoleFormatter.previewOf(node));
        assertEquals("{}", ConsoleFormatter.previewOf(Map.of("t", "obj", "pre", "")));
    }
}
