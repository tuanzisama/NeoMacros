package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.core.api.render.HudLayer;
import ink.tuanzi.neomacros.core.api.render.RecordingHudRender;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.WindowData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inheritance contract for the G-034 / G-034B / G-034C component family:
 * {@link Draw2DElement} is an open base class whose fluent API and state
 * bridge survive subclassing. A stand-in component (the {@code Draw2DText}
 * shape) proves the extension pattern component tasks will use — read
 * {@link ElementSpec} through the protected {@link Draw2DElement.Backend},
 * republish a modified copy — while the engine-visible surface (public
 * members only, explicit annotation triple) stays exactly the base face.
 */
class Draw2DElementInheritanceTest {

    private static final WindowData WINDOW = new WindowData(640, 360, 640, 360);

    /**
     * Stand-in for a G-034 component subclass: adds component behaviour on
     * top of the base face without re-declaring any of it, exactly as
     * {@code Draw2DText}/{@code Draw2DRect} will.
     */
    private static final class FakeComponent extends Draw2DElement {

        FakeComponent(String id, HudLayer layer, Backend backend) {
            super(id, layer, backend);
        }

        /**
         * The G-034 extension pattern: component mutations ride the same
         * immutable spec publish path as the base setters.
         */
        FakeComponent pulse() {
            Backend bridge = backend();
            ElementSpec spec = bridge.spec();
            bridge.publish(spec.withScale(spec.scale() * 2));
            return this;
        }
    }

    /** Scene-stand-in backend with a fixed viewport, mirroring HudScene. */
    private static final class FakeBackend implements Draw2DElement.Backend {
        private volatile ElementSpec spec = new ElementSpec(
                ElementSpec.Kind.TEXT,
                ElementSpec.Coord.at(0),
                ElementSpec.Coord.at(0),
                ElementSpec.Coord.at(0),
                ElementSpec.Coord.at(0),
                0xFFFFFFFF,
                "hi",
                0,
                true,
                1f,
                0f,
                false);
        private volatile boolean disposed;

        @Override
        public ElementSpec spec() {
            return spec;
        }

        @Override
        public void publish(ElementSpec next) {
            if (disposed) {
                throw new IllegalStateException("element no longer valid");
            }
            spec = next;
        }

        @Override
        public int viewportWidth() {
            return 640;
        }

        @Override
        public int viewportHeight() {
            return 360;
        }

        @Override
        public boolean disposed() {
            return disposed;
        }
    }

    private final RecordingHudRender render = new RecordingHudRender();

    private FakeComponent newComponent(FakeBackend backend) {
        HudLayer layer = new HudLayer("main", "main", 0, render, Runnable::run,
                () -> WINDOW);
        return new FakeComponent("c", layer, backend);
    }

    @Test
    void baseClassIsOpenAndPubliclyConstructible() throws NoSuchMethodException {
        assertFalse(Modifier.isFinal(Draw2DElement.class.getModifiers()),
                "component tasks subclass the base handle");
        assertNotNull(Draw2DElement.class.getConstructor(
                String.class, HudLayer.class, Draw2DElement.Backend.class),
                "the scene constructs base and component handles through one constructor");
    }

    @Test
    void subclassInstancesRunTheFluentContract() {
        FakeBackend backend = new FakeBackend();
        FakeComponent el = newComponent(backend);

        assertSame(el, el.setX(10), "fluent setters return the subclass instance");
        assertSame(el, el.setPos(20, 30).setZIndex(4).setVisible(false));
        assertEquals(20, el.getX());
        assertEquals(30, el.getY());
        assertEquals(4, el.getZIndex());
        assertFalse(el.isVisible());
        assertEquals("c", el.getId());
        assertFalse(el.isDisposed());
    }

    @Test
    void subclassExtendsStateThroughTheBackendBridge() {
        FakeBackend backend = new FakeBackend();
        FakeComponent el = newComponent(backend);

        assertEquals(1f, el.getScale());
        el.pulse();
        assertEquals(2f, el.getScale(),
                "the component publish path feeds the same base getters");

        el.pulse().setRotation(90);
        assertEquals(4f, el.getScale(), "component and base mutations compose");
        assertEquals(90f, el.getRotation());
    }

    @Test
    void disposalSemanticsApplyToSubclassInstances() {
        FakeBackend backend = new FakeBackend();
        FakeComponent el = newComponent(backend);

        backend.disposed = true;
        assertTrue(el.isDisposed());
        assertNull(el.getId());
        assertNull(el.getLayer());
        assertNull(el.getX());
        assertNull(el.getBounds());
        assertNull(el.getScaledWidth());
        assertThrows(IllegalStateException.class, () -> el.setX(1));
        assertThrows(IllegalStateException.class, () -> el.pulse(),
                "component methods route through the same alive-check contract");
    }

    @Test
    void engineSurfaceIsTheBaseFaceOnly() throws NoSuchMethodException {
        // The engine binds public members of the runtime class; a subclass
        // must inherit the base surface untouched and leak no host plumbing.
        for (Method method : FakeComponent.class.getMethods()) {
            if (method.getDeclaringClass() == Object.class
                    || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            assertEquals(Draw2DElement.class, method.getDeclaringClass(),
                    "subclasses inherit, never shadow, the base script face: "
                            + method.getName());
        }
        assertThrows(NoSuchMethodException.class,
                () -> FakeComponent.class.getMethod("backend"),
                "the protected bridge stays off the script surface");
        assertThrows(NoSuchMethodException.class,
                () -> FakeComponent.class.getMethod("spec"));
        assertThrows(NoSuchMethodException.class,
                () -> FakeComponent.class.getMethod("publish"));
        assertNotNull(Draw2DElement.class
                        .getDeclaredMethod("alignHorizontally", String.class)
                        .getAnnotation(ApiInternal.class),
                "the host-side align overloads stay internal, so the folded "
                        + "member is the only script binding per name");
    }

    @Test
    void inheritedMembersCarryTheExplicitAnnotationTriple()
            throws NoSuchMethodException {
        for (Method method : Draw2DElement.class.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)
                    || method.getDeclaringClass() == Object.class
                    || method.isAnnotationPresent(ApiInternal.class)) {
                // Internal host helpers never cross to the guest (the
                // engine filters them exactly like this loop does), so they
                // stay off the explicit-triple rule.
                continue;
            }
            String where = method.getName();
            assertNotNull(method.getAnnotation(ApiSince.class),
                    where + " misses @ApiSince");
            assertNotNull(method.getAnnotation(ApiMC.class),
                    where + " misses @ApiMC");
            assertNotNull(method.getAnnotation(ApiLoader.class),
                    where + " misses @ApiLoader");
            Method inherited = FakeComponent.class.getMethod(
                    method.getName(), method.getParameterTypes());
            assertEquals(Draw2DElement.class, inherited.getDeclaringClass(),
                    where + " must resolve to the base declaration");
            assertNotNull(inherited.getAnnotation(ApiSince.class),
                    where + " loses @ApiSince through inheritance");
        }
    }

    @Test
    void percentageHelpersWorkOnSubclassInstances() {
        FakeBackend backend = new FakeBackend();
        FakeComponent el = newComponent(backend);

        el.setWidth("50%");
        assertEquals("50%", el.getWidth());
        Draw2DElement.Bounds bounds = el.getBounds();
        assertEquals(320, bounds.width(), "50% of the 640px viewport");
        assertEquals(640, el.getParentWidth());

        el.moveTo("25%", 8);
        assertEquals("25%", el.getX());
        assertEquals(8, el.getY());
        assertEquals(160, el.getScaledLeft());
    }

    @Test
    void subclassInstancesRunTheAlignFace() {
        FakeBackend backend = new FakeBackend();
        FakeComponent el = newComponent(backend);
        el.setSize(100, 50);

        assertTrue(el instanceof Alignable,
                "the base handle carries the alignment contract");
        assertTrue(el instanceof RenderElement,
                "the base handle carries the z-order contract");

        assertSame(el, el.alignHorizontally("center"),
                "alignment returns the subclass instance");
        assertEquals(270, el.getX(), "parent-relative math runs on the subclass");
        assertEquals(370, el.getScaledRight(),
                "the derived edge reads the subclass geometry");

        FakeComponent other = newComponent(new FakeBackend());
        other.setSize(50, 10);
        el.alignHorizontally(other, "leftOnRight");
        assertEquals(50, el.getX(), "sibling-relative math reads the interface contract");

        el.moveToX(5);
        assertEquals(5, el.getX());
        assertEquals(0, el.getY(), "a single-axis move keeps the other coordinate");
        el.alignVertically("bottom");
        assertEquals(310, el.getY());
        assertEquals(5, el.getX());

        el.moveTo(1, 2);
        assertEquals(1, el.getX(), "the inherited moveTo still drives alignment state");
    }

    @Test
    void alignmentDisposalFollowsTheSubclassContract() {
        FakeBackend backend = new FakeBackend();
        FakeComponent el = newComponent(backend);
        el.setSize(100, 50);

        backend.disposed = true;
        assertThrows(IllegalStateException.class, () -> el.align("left", "top"));
        assertThrows(IllegalStateException.class, () -> el.moveToX(1));
        assertNull(el.getScaledRight());
        assertNull(el.getScaledBottom());
    }
}
