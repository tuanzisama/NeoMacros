package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.api.bindgen.BindingRegistry;
import ink.tuanzi.neomacros.core.api.bindgen.PermissionView;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reflect handles crossing the engine boundary: guest ES classes with
 * {@code instanceof} support, getter-derived properties (the modifier
 * flags), arrays of handles, annotation proxies, and the module's
 * permission-gated entry points under a {@code java} view.
 */
class ReflectHandleRenderingTest {

    @TempDir
    Path scriptsDir;

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
        engine = new GraalJsEngine();
        runtime = new ScriptRuntime(engine, null,
                new EventCatalog(), new EventBus(new EventCatalog(), Runnable::run), pool, 500);
        var context = runtime.createContext("reflect");
        session = (GraalJsEngine.GraalJsSession) engine.openSession(context, scriptsDir);
        session.installBindings(registry.neoTree(PermissionView.of(Set.of("java"))));
    }

    @AfterEach
    void tearDown() {
        runtime.close();
        pool.close();
    }

    private void run(String fileName, String source) throws Exception {
        Path entry = scriptsDir.resolve(fileName);
        Files.writeString(entry, source);
        session.importModule(fileName.substring(0, fileName.indexOf('.')), entry);
    }

    private Value global(String name) {
        return session.global(name);
    }

    @Test
    void classHandleRendersAsGuestClassWithProperties() throws Exception {
        run("reflect-class.mjs", """
                const cls = neo.reflect.forName('java.lang.System');
                globalThis.__className = cls.className;
                globalThis.__instanceof = cls instanceof neo.ReflectClass;
                globalThis.__primitive = neo.reflect.forName('int').className;
                globalThis.__super = cls.superclass().className;
                globalThis.__noSuper = neo.reflect.forName('java.lang.Object').superclass() === undefined;
                globalThis.__ifaceArray = Array.isArray(cls.interfaces());
                globalThis.__rawClassName = cls.raw.className;
                """);

        assertEquals("java.lang.System", global("__className").asString());
        assertTrue(global("__instanceof").asBoolean(), "root guest class enables instanceof");
        assertEquals("int", global("__primitive").asString(), "primitive names resolve");
        assertEquals("java.lang.Object", global("__super").asString());
        assertTrue(global("__noSuper").asBoolean(), "absent superclass reads undefined");
        assertTrue(global("__ifaceArray").asBoolean());
        assertEquals("java.lang.Class", global("__rawClassName").asString(),
                "raw escape hatch wraps the Class object");
    }

    @Test
    void fieldHandleExposesFlagsTypeAndRead() throws Exception {
        run("reflect-field.mjs", """
                const cls = neo.reflect.forName('java.lang.System');
                const out = cls.field('out');
                globalThis.__handle = out instanceof neo.ReflectField;
                globalThis.__name = out.name;
                globalThis.__static = out.static === true;
                globalThis.__public = out.public === true;
                globalThis.__private = out.private === false;
                globalThis.__type = out.type().className;
                globalThis.__modifiers = typeof out.modifiers === 'function' && out.modifiers() > 0;
                globalThis.__readable = out.get(null) !== null;
                const fields = cls.declaredFields();
                globalThis.__array = Array.isArray(fields) && fields.length > 0
                        && fields.every((f) => f instanceof neo.ReflectField);
                """);

        assertTrue(global("__handle").asBoolean());
        assertEquals("out", global("__name").asString());
        assertTrue(global("__static").asBoolean(), "isStatic renders as the .static property");
        assertTrue(global("__public").asBoolean());
        assertTrue(global("__private").asBoolean());
        assertEquals("java.io.PrintStream", global("__type").asString());
        assertTrue(global("__modifiers").asBoolean());
        assertTrue(global("__readable").asBoolean(), "static field read through get(null)");
        assertTrue(global("__array").asBoolean(), "enumeration crosses as a JS array");
    }

    @Test
    void methodAndConstructorHandlesInvoke() throws Exception {
        run("reflect-invoke.mjs", """
                const cls = neo.reflect.forName('java.lang.System');
                const now = cls.method('currentTimeMillis');
                globalThis.__handle = now instanceof neo.ReflectMethod;
                globalThis.__name = now.name;
                globalThis.__static = now.static === true;
                globalThis.__return = now.returnType().className;
                globalThis.__params = Array.isArray(now.parameterTypes())
                        && now.parameterTypes().length === 0;
                const tick = now.invoke(null);
                globalThis.__invoked = typeof tick === 'number' && tick > 0;

                const list = neo.reflect.forName('java.util.ArrayList');
                const ctor = list.constructor();
                globalThis.__ctorHandle = ctor instanceof neo.ReflectConstructor;
                globalThis.__ctorPublic = ctor.public === true;
                const instance = ctor.newInstance([]);
                globalThis.__built = instance.className === 'java.util.ArrayList';

                const add = neo.reflect.method(instance, 'add', ['java.lang.Object']);
                globalThis.__addHandle = add instanceof neo.ReflectMethod;
                globalThis.__added = neo.reflect.invoke(add, instance, ['x']) === true;
                globalThis.__size = instance.size() === 1;
                """);

        assertTrue(global("__handle").asBoolean());
        assertEquals("currentTimeMillis", global("__name").asString());
        assertTrue(global("__static").asBoolean());
        assertEquals("long", global("__return").asString());
        assertTrue(global("__params").asBoolean());
        assertTrue(global("__invoked").asBoolean());
        assertTrue(global("__ctorHandle").asBoolean());
        assertTrue(global("__ctorPublic").asBoolean());
        assertTrue(global("__built").asBoolean());
        assertTrue(global("__addHandle").asBoolean(),
                "module method() returns the guest ReflectMethod");
        assertTrue(global("__added").asBoolean(), "module invoke accepts the handle");
        assertTrue(global("__size").asBoolean(), "instance method dispatched on the raw list");
    }

    @Test
    void annotationQueriesCrossAsProxies() throws Exception {
        run("reflect-annotation.mjs", """
                const cls = neo.reflect.forName('java.lang.System');
                globalThis.__array = Array.isArray(cls.annotations());
                globalThis.__absent = cls.isAnnotationPresent('java.lang.Deprecated') === false;
                globalThis.__missingName = (() => {
                    try { cls.isAnnotationPresent('com.example.Nope'); return 'not-reached'; }
                    catch (e) { return String(e.message || e); }
                })();

                const retention = neo.reflect.forName('java.lang.Deprecated');
                globalThis.__present =
                        retention.isAnnotationPresent('java.lang.annotation.Retention');
                const anno = retention.annotation('java.lang.annotation.Retention');
                globalThis.__value = anno.value().toString();
                """);

        assertTrue(global("__array").asBoolean());
        assertTrue(global("__absent").asBoolean());
        assertTrue(global("__missingName").asString().contains("unknown annotation class"),
                () -> "unknown annotation name rejects: " + global("__missingName").asString());
        assertTrue(global("__present").asBoolean(), "runtime-retained annotation found");
        assertEquals("RUNTIME", global("__value").asString(),
                "annotation attributes read through the proxy");
    }
}
