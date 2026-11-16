package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import ink.tuanzi.neomacros.core.api.java.JavaApi;
import ink.tuanzi.neomacros.core.api.reflect.ReflectApi;
import ink.tuanzi.neomacros.core.api.reflect.ReflectClass;
import ink.tuanzi.neomacros.core.api.reflect.ReflectConstructor;
import ink.tuanzi.neomacros.core.api.reflect.ReflectField;
import ink.tuanzi.neomacros.core.api.reflect.ReflectMethod;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Console rendering of interop proxies and raw host values: a
 * JavaObject/JavaClass enumerates only className/instanceOf as keys, and a
 * bare host object only shows its getter snapshot — both must still present
 * a reasonable surface (wrapped members, arrays, callable names).
 */
class ConsoleInteropSurfaceTest {

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
        registry.register(JavaApi.class);
        registry.registerGuestType("ReflectClass", ReflectClass.class);
        registry.registerGuestType("ReflectField", ReflectField.class);
        registry.registerGuestType("ReflectMethod", ReflectMethod.class);
        registry.registerGuestType("ReflectConstructor", ReflectConstructor.class);
        engine = new GraalJsEngine();
        runtime = new ScriptRuntime(engine, null,
                new EventCatalog(), new EventBus(new EventCatalog(), Runnable::run), pool, 500);
        var context = runtime.createContext("interop");
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

    private Map<String, Object> render(String source) throws Exception {
        var field = session.getClass().getDeclaredField("context");
        field.setAccessible(true);
        org.graalvm.polyglot.Context context =
                (org.graalvm.polyglot.Context) field.get(session);
        return new ConsoleFormatter(context, ConsoleFormatter.MAX_NODES).node(eval(source));
    }

    @SuppressWarnings("unchecked")
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
    void objectProxySurfacesTheWrappedMembers() throws Exception {
        Map<String, Object> node = render("neo.reflect.newInstance('java.util.ArrayList')");

        assertEquals("obj", node.get("t"));
        assertTrue((Long) node.get("n") > 2,
                "more than the className/instanceOf keys alone: " + node.get("pre"));
        assertEquals("java.util.ArrayList", propValue(node, "className").get("v"));
        assertEquals("fn", propValue(node, "size").get("t"));
        assertEquals("fn", propValue(node, "add").get("t"));
    }

    @Test
    void classProxySurfacesStaticsAndTheConstructorMember() throws Exception {
        Map<String, Object> node = render("neo.java.type('java.lang.System')");

        assertEquals("obj", node.get("t"));
        assertTrue(hasProp(node, "className"), "surface: " + node.get("pre"));
        assertEquals("java.lang.System", propValue(node, "className").get("v"));
        assertTrue(hasProp(node, "new"), "construction survives the cap: " + node.get("pre"));
        assertEquals("fn", propValue(node, "new").get("t"));
        assertTrue(hasProp(node, "currentTimeMillis"),
                "static methods surface: n=" + node.get("n") + " pre=" + node.get("pre"));
        assertEquals("fn", propValue(node, "currentTimeMillis").get("t"));
    }

    @Test
    void hostArraysRenderAsArrays() throws Exception {
        Map<String, Object> node = render(
                "neo.java.toArray(['a', 'b'], 'java.lang.String')");

        assertEquals("arr", node.get("t"));
        assertEquals(2L, node.get("len"));
    }

    @Test
    void bareHostValuesListTheirMethodsAlongsideGetters() throws Exception {
        Map<String, Object> node = render(
                "neo.reflect.newInstance('java.util.ArrayList').__neoRaw");

        assertEquals("host", node.get("t"));
        assertEquals("java.util.ArrayList", node.get("cls"));
        assertTrue(hasProp(node, "empty"), "getter snapshot stays");
        assertTrue(props(node).stream().anyMatch(p -> "fn".equals(
                        ((Map<String, Object>) p.get("v")).get("t"))),
                "method names surface as functions: " + node.get("pre"));
    }
}
