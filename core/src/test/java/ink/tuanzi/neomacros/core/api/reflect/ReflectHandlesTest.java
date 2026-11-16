package ink.tuanzi.neomacros.core.api.reflect;

import ink.tuanzi.neomacros.core.api.java.JavaObject;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reflect handle surface: class loading, the Class enumeration split
 * (public vs declared), exact-signature lookup, field get/set including
 * private and static members, method invocation, constructor
 * instantiation, modifier flags, annotation queries, and the module's
 * backward-compatible invoke/newInstance paths.
 */
class ReflectHandlesTest {

    private static final String MARKER = Marker.class.getName();

    private final ReflectApi api = new ReflectApi();

    @Retention(RetentionPolicy.RUNTIME)
    @interface Marker {
    }

    /** Interface used by the fixture's interface enumeration. */
    public interface FixtureIface {
    }

    @Marker
    public static class Fixture implements FixtureIface {
        public String publicField = "pub";

        @Marker
        private int secret = 42;

        private static String staticHidden = "s";

        public Fixture() {
        }

        private Fixture(String ignored) {
        }

        public void ping() {
        }

        private int hidden(int x) {
            return x + 1;
        }

        @Deprecated
        public String echo(String s) {
            return s;
        }

        public static int twice(int x) {
            return x * 2;
        }
    }

    private static ReflectClass fixtureClass() throws Exception {
        return new ReflectApi().forName(Fixture.class.getName());
    }

    @Test
    void forNameAndClassOfYieldClassHandles() throws Exception {
        ReflectClass cls = fixtureClass();
        assertEquals(Fixture.class.getName(), cls.getClassName());
        assertSame(Fixture.class, cls.getRaw(), "raw escape hatch");

        assertSame(cls, api.classOf(cls), "a class value answers itself");
        assertEquals(Fixture.class.getName(),
                api.classOf(new Fixture()).getClassName(), "instance runtime class");
        assertEquals(Fixture.class.getName(),
                api.classOf(Fixture.class).getClassName(), "raw class value answers itself");
        assertNull(api.classOf(null));
        assertEquals("int", api.forName("int").getClassName(), "primitive name");
    }

    @Test
    void enumerationsSplitPublicAndDeclared() throws Exception {
        ReflectClass cls = fixtureClass();

        List<String> publicFields = cls.fields().stream().map(ReflectField::getName).toList();
        assertEquals(List.of("publicField"), publicFields, "only public fields are public");
        assertEquals(3, cls.declaredFields().size(), "declared fields include private/static");

        List<String> declared = cls.declaredMethods().stream().map(ReflectMethod::getName)
                .toList();
        assertTrue(declared.containsAll(List.of("ping", "hidden", "echo", "twice")),
                "declared methods carry every fixture method: " + declared);
        assertTrue(cls.methods().size() >= declared.size(), "public methods include inherited");

        assertEquals(1, cls.constructors().size(), "one public constructor");
        assertEquals(2, cls.declaredConstructors().size(), "declared includes the private one");

        assertEquals("java.lang.Object", cls.superclass().getClassName());
        assertNull(api.forName("java.lang.Object").superclass(), "Object has no superclass");
        assertEquals(1, cls.interfaces().size());
        assertEquals(FixtureIface.class.getName(),
                cls.interfaces().get(0).getClassName());
    }

    @Test
    void lookupsResolvePrivateMembers() throws Exception {
        ReflectClass cls = fixtureClass();

        ReflectField secret = cls.field("secret");
        assertNotNull(secret);
        assertEquals("secret", secret.getName());
        assertNull(cls.field("nope"));

        assertNotNull(cls.method("ping", null), "zero-arg lookup without a signature");
        assertNotNull(cls.method("hidden", List.of("int")), "private overload by signature");
        assertNotNull(cls.method("echo", List.of("java.lang.String")));
        assertNull(cls.method("missing", null));

        ReflectConstructor publicCtor = cls.constructor(null);
        assertNotNull(publicCtor);
        assertTrue(publicCtor.isPublic());
        ReflectConstructor privateCtor = cls.constructor(List.of("java.lang.String"));
        assertNotNull(privateCtor);
        assertTrue(privateCtor.isPrivate());
        assertNull(cls.constructor(List.of("int")));
    }

    @Test
    void fieldGetSetRoundTripsPrivateAndStatic() throws Exception {
        ReflectClass cls = fixtureClass();
        Fixture instance = new Fixture();

        ReflectField secret = cls.field("secret");
        assertEquals(42, secret.get(instance), "private instance read");
        secret.set(instance, 7);
        assertEquals(7, secret.get(instance), "private instance write");
        assertEquals("int", secret.type().getClassName());
        assertTrue(secret.isPrivate());
        assertFalse(secret.isPublic());
        assertFalse(secret.isStatic());
        assertTrue(secret.modifiers() != 0);

        ReflectField publicField = cls.field("publicField");
        assertTrue(publicField.isPublic());

        ReflectField staticHidden = cls.field("staticHidden");
        assertTrue(staticHidden.isStatic());
        staticHidden.set(null, "t");
        assertEquals("t", staticHidden.get(null), "static write without a target");
        staticHidden.set(null, "s");
    }

    @Test
    void methodInvokeCoversInstanceStaticAndPrivate() throws Exception {
        ReflectClass cls = fixtureClass();
        Fixture instance = new Fixture();

        ReflectMethod echo = cls.method("echo", List.of("java.lang.String"));
        assertEquals("hi", echo.invoke(instance, List.of("hi")));
        assertEquals("java.lang.String", echo.returnType().getClassName());
        assertEquals(1, echo.parameterTypes().size());
        assertEquals("java.lang.String", echo.parameterTypes().get(0).getClassName());
        assertTrue(echo.isPublic());
        assertFalse(echo.isStatic());
        assertTrue(echo.modifiers() != 0);

        ReflectMethod hidden = cls.method("hidden", List.of("int"));
        assertEquals(3, ((Number) hidden.invoke(instance, List.of(2))).intValue(),
                "private method invocation");
        assertTrue(hidden.isPrivate());

        ReflectMethod twice = cls.method("twice", List.of("int"));
        assertTrue(twice.isStatic());
        assertEquals(6, ((Number) twice.invoke(null, List.of(3))).intValue(),
                "static invocation with a null target");
    }

    @Test
    void constructorHandlesInstantiatePublicAndPrivate() throws Exception {
        ReflectClass cls = fixtureClass();

        ReflectConstructor publicCtor = cls.constructor(null);
        assertInstanceOf(Fixture.class, publicCtor.newInstance(null));
        assertEquals(0, publicCtor.parameterTypes().size());

        ReflectConstructor privateCtor = cls.constructor(List.of("java.lang.String"));
        assertInstanceOf(Fixture.class, privateCtor.newInstance(List.of("x")),
                "private constructor instantiation");
        assertEquals(1, privateCtor.parameterTypes().size());
        assertEquals("java.lang.String", privateCtor.parameterTypes().get(0).getClassName());

        assertInstanceOf(Fixture.class, api.newInstance(privateCtor, List.of("y")),
                "module newInstance accepts the handle");
        assertInstanceOf(Fixture.class, api.newInstance(Fixture.class.getName(), null),
                "module newInstance still matches by class name");
    }

    @Test
    void annotationsSurfaceOnClassAndMembers() throws Exception {
        ReflectClass cls = fixtureClass();

        assertTrue(cls.isAnnotationPresent(MARKER), "runtime-retained class annotation");
        Object annotation = cls.annotation(MARKER);
        assertNotNull(annotation);
        JavaObject proxy = assertInstanceOf(JavaObject.class, annotation);
        assertInstanceOf(Marker.class, proxy.raw(), "annotation attributes stay readable");
        assertTrue(cls.annotations().size() >= 1);
        assertTrue(cls.declaredAnnotations().size() >= 1);

        assertFalse(cls.isAnnotationPresent("java.lang.Override"), "absent annotation");
        assertNull(cls.annotation("java.lang.Override"));
        assertThrows(IllegalArgumentException.class,
                () -> cls.isAnnotationPresent("com.example.NoSuchAnnotation"));
        assertThrows(IllegalArgumentException.class,
                () -> cls.isAnnotationPresent("java.lang.String"), "non-annotation class");

        ReflectField secret = cls.field("secret");
        assertTrue(secret.isAnnotationPresent(MARKER), "field-level annotation");
        assertTrue(secret.annotations().size() >= 1);

        ReflectMethod echo = cls.method("echo", List.of("java.lang.String"));
        assertTrue(echo.isAnnotationPresent("java.lang.Deprecated"), "method annotation");
        assertTrue(echo.declaredAnnotations().size() >= 1);

        ReflectConstructor ctor = cls.constructor(null);
        assertFalse(ctor.isAnnotationPresent(MARKER));
        assertEquals(0, ctor.annotations().size());
    }

    @Test
    void modulePathsAcceptHandlesAndRawMembers() throws Exception {
        ReflectClass cls = fixtureClass();
        Fixture instance = new Fixture();

        Object method = api.method(instance, "echo", List.of("java.lang.String"));
        ReflectMethod resolved = assertInstanceOf(ReflectMethod.class, method);
        assertEquals("yo", api.invoke(method, instance, List.of("yo")),
                "module invoke accepts the handle");
        assertEquals("yo", api.invoke(resolved.getRaw(), instance, List.of("yo")),
                "module invoke still accepts the raw Method");

        Object field = api.field(instance, "secret");
        assertInstanceOf(ReflectField.class, field);
        assertEquals(42, ((ReflectField) field).get(instance));

        ReflectConstructor constructor = api.constructor(cls, null);
        assertNotNull(constructor);
        assertInstanceOf(Fixture.class, api.newInstance(constructor, null));

        assertEquals(Fixture.class.getName(), api.className(cls),
                "className unwraps the class handle");
        assertEquals("java.lang.reflect.Field", api.className(field),
                "className unwraps member handles");
        assertEquals("java.lang.reflect.Method", api.className(method));
        assertEquals(Fixture.class.getName(), api.className(instance));
        assertNull(api.className(null));
    }
}
