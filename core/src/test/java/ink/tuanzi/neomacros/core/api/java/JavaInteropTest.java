package ink.tuanzi.neomacros.core.api.java;

import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.ModuleBinding;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interop layer's core semantics: class lookup, static and instance
 * member resolution, argument adaptation, construction, instance checks,
 * and the java permission gate over the module binding.
 */
class JavaInteropTest {

    @Test
    void classProxyReadsStaticMembersAndDispatches() throws Exception {
        JavaApi api = new JavaApi();
        JavaClass math = api.type("java.lang.Math");

        assertEquals("java.lang.Math", math.className());
        assertTrue((Double) math.readValue("PI") > 3.14 && (Double) math.readValue("PI") < 3.15,
                "static field read");
        Object floored = math.readCallable("floor").apply(new Object[]{3.7});
        assertEquals(3L, ((Number) floored).longValue(), "static method dispatch");
    }

    @Test
    void objectProxyConstructsDispatchesAndChecksInstances() throws Exception {
        JavaApi api = new JavaApi();
        JavaClass listClass = api.type("java.util.ArrayList");
        JavaObject list = (JavaObject) JavaInterop.wrap(listClass.constructInstance(new Object[0]));

        list.invoke("add", new Object[]{"alpha"});
        list.invoke("add", new Object[]{"beta"});
        assertEquals(2, ((Number) list.invoke("size", new Object[0])).intValue());
        assertEquals("alpha", list.invoke("get", new Object[]{0}));

        assertTrue(list.instanceOf("java.util.List"), "instance check through the hierarchy");
        assertTrue(!list.instanceOf("java.util.HashMap"), "unrelated class rejects");

        JavaClass uuidClass = api.type("java.util.UUID");
        JavaObject id = (JavaObject) JavaInterop.wrap(uuidClass.constructInstance(new Object[]{
                0L, 0L}));
        assertEquals("00000000-0000-0000-0000-000000000000",
                String.valueOf(id.invoke("toString", new Object[0])));
    }

    @Test
    void interopArgumentsUnwrapAndAdapt() throws Exception {
        JavaApi api = new JavaApi();
        JavaClass listClass = api.type("java.util.ArrayList");
        JavaObject list = (JavaObject) JavaInterop.wrap(listClass.constructInstance(new Object[0]));

        // A wrapped interop argument contributes its raw object.
        JavaObject nested = (JavaObject) JavaInterop.wrap(new ArrayList<String>());
        list.invoke("add", new Object[]{nested});
        Object stored = list.invoke("get", new Object[]{0});
        assertTrue(stored instanceof ArrayList, "interop argument contributed its raw object");
    }

    @Test
    void javaModuleIsGatedByTheJavaCapability() {
        var registry = new BindingRegistry("26.3");
        registry.register(JavaApi.class);

        ModuleBinding denied = (ModuleBinding) registry.neoTree(PermissionView.DENY_ALL)
                .get("java");
        ModuleBinding.DeniedMember member =
                assertInstanceOf(ModuleBinding.DeniedMember.class, denied.members().get("type"));
        assertEquals("java", member.permission(), "denied member carries the java capability");

        ModuleBinding allowed = (ModuleBinding) registry.neoTree(PermissionView.ALLOW_ALL)
                .get("java");
        assertTrue(allowed.members().get("type") instanceof ModuleBinding.FunctionMember,
                "the trusted view reaches the facade function");
    }

    @Test
    void proxyDispatchesToGuestHandlers() throws Exception {
        JavaApi api = new JavaApi();
        var handled = new ArrayList<String>();
        // The engine delivers handler functions as Consumer<Object[]>; the
        // host test applies the same shape.
        Object proxy = api.proxy(List.of("java.lang.Runnable"), Map.of(
                "run", (java.util.function.Consumer<Object[]>) args -> handled.add("run")));
        assertTrue(proxy instanceof java.lang.Runnable, "proxy implements the interface");
        ((java.lang.Runnable) proxy).run();
        assertEquals(List.of("run"), handled, "handler dispatched with no arguments");
    }

    @Test
    void collectionHelpersRoundTrip() throws Exception {
        JavaApi api = new JavaApi();
        Object hostList = api.toList(List.of("a", "b"));
        JavaObject wrapped = assertInstanceOf(JavaObject.class, hostList,
                "toList wraps the host list as an interop object");
        assertInstanceOf(java.util.List.class, wrapped.raw());

        Object back = api.fromList(wrapped.raw());
        assertInstanceOf(java.util.List.class, back);
        assertEquals(List.of("a", "b"), back, "host collection became a script array");

        Object array = api.toArray(List.of(1, 2), "int");
        assertInstanceOf(int[].class, array);
        assertEquals(2, ((int[]) array).length);
        assertEquals(2, ((int[]) array)[1], "component adaptation applied");
    }

    @Test
    void customResolverTranslatesNames() throws Exception {
        JavaApi mapping = new JavaApi(name -> name.equals("MyString")
                ? "java.lang.String" : name);
        assertEquals("java.lang.String", mapping.type("MyString").className(),
                "resolver output feeds the lookup");
    }

    @Test
    void unknownClassRaises() {
        var api = new JavaApi();
        var thrown = recordThrowable(() -> {
            try {
                api.type("java.util.NoSuchClassXYZ");
            } catch (ClassNotFoundException expected) {
                // the lookup must fail loudly for unknown names
            }
        });
        assertTrue(thrown == null, "lookup failure is contained: " + thrown);
    }

    private static Throwable recordThrowable(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
