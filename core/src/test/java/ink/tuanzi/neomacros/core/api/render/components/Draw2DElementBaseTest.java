package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.core.api.render.HudLayer;
import ink.tuanzi.neomacros.core.api.render.RecordingHudRender;
import ink.tuanzi.neomacros.port.model.WindowData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base-class face of {@link Draw2DElement} (G-032B): fluent setters over
 * the published {@link ElementSpec}, geometry getters (raw / scaled /
 * parent / bounds), back references, element-level z reordering and
 * visibility, validation rules, and the disposed-handle
 * semantics table.
 */
class Draw2DElementBaseTest {

    private static final WindowData WINDOW = new WindowData(640, 360, 640, 360);

    private final RecordingHudRender render = new RecordingHudRender();

    private HudLayer newLayer(String id) {
        return new HudLayer(id, id, 0, render, Runnable::run, () -> WINDOW);
    }

    @Test
    void fluentSettersChainAndRoundTrip() {
        HudLayer layer = newLayer("main");
        Draw2DElement el = layer.rect("r",
                Map.of("x", 0, "y", 0, "w", 1, "h", 1));

        assertSame(el, el.setX(1), "every setter returns this element");
        assertSame(el, el.setY(2));
        assertSame(el, el.setPos(3, 4));
        assertSame(el, el.setWidth(10));
        assertSame(el, el.setHeight(20));
        assertSame(el, el.setSize(30, 40));
        assertSame(el, el.setScale(2));
        assertSame(el, el.setRotation(45));
        assertSame(el, el.setRotateCenter(true));
        assertSame(el, el.setZIndex(7));
        assertSame(el, el.setVisible(false));
        assertSame(el, el.moveTo(50, 60));

        assertEquals(50, el.getX());
        assertEquals(60, el.getY());
        assertEquals(30, el.getWidth());
        assertEquals(40, el.getHeight());
        assertEquals(2f, el.getScale());
        assertEquals(45f, el.getRotation());
        assertTrue(el.isRotateCenter());
        assertEquals(7, el.getZIndex());
        assertFalse(el.isVisible());
    }

    @Test
    void backReferencesAnswerTheHostingLayer() {
        HudLayer layer = newLayer("main");
        Draw2DElement el = layer.text("a", "hi", null);

        assertEquals("a", el.getId());
        assertFalse(el.isDisposed());
        assertSame(layer, el.getLayer(),
                "getLayer returns the hosting layer");
        assertSame(layer, el.getParent(),
                "v1 parent box is always the hosting layer");
    }

    @Test
    void percentageGeometryRoundTripsAndResolvesInBounds() {
        AtomicReference<WindowData> window =
                new AtomicReference<>(new WindowData(640, 360, 640, 360));
        HudLayer layer = new HudLayer("main", "main", 0, render, Runnable::run,
                window::get);
        Draw2DElement el = layer.rect("r", Map.of(
                "x", "50%", "y", "25%", "w", "50%", "h", "25%"));

        assertEquals("50%", el.getX(), "the raw percentage string round-trips");
        assertEquals("25%", el.getY());
        Draw2DElement.Bounds bounds = el.getBounds();
        assertEquals(320, bounds.x());
        assertEquals(90, bounds.y());
        assertEquals(320, bounds.width());
        assertEquals(90, bounds.height());
        assertEquals(List.of("rect:320,90,320,90,ffffffff"), render.frame(0),
                "paint resolves percentages against the viewport");

        window.set(new WindowData(1280, 720, 1280, 720));
        Draw2DElement.Bounds reflowed = el.getBounds();
        assertEquals(640, reflowed.x(), "the next read re-resolves");
        assertEquals(180, reflowed.y());

        el.setX(5);
        assertEquals(5, el.getX(), "an absolute write replaces the percentage");
    }

    @Test
    void scaledGeometryAppliesScaleAndTruncatesTowardZero() {
        HudLayer layer = newLayer("main");
        Draw2DElement el = layer.rect("r",
                Map.of("x", 10, "y", 20, "w", 100, "h", 50));

        el.setScale(2.5);
        assertEquals(250, el.getScaledWidth());
        assertEquals(125, el.getScaledHeight());
        assertEquals(10, el.getScaledLeft(), "scaling does not move the edge");
        assertEquals(20, el.getScaledTop());

        Draw2DElement.Bounds bounds = el.getBounds();
        assertEquals(10, bounds.x());
        assertEquals(20, bounds.y());
        assertEquals(250, bounds.width(), "bounds carry the scaled extent");
        assertEquals(125, bounds.height());

        el.setSize(3, 7);
        el.setScale(1.5);
        assertEquals(4, el.getScaledWidth(), "(int) 4.5 truncates toward zero");
    }

    @Test
    void parentSizeFollowsTheViewport() {
        AtomicReference<WindowData> window =
                new AtomicReference<>(new WindowData(640, 360, 640, 360));
        HudLayer layer = new HudLayer("main", "main", 0, render, Runnable::run,
                window::get);
        Draw2DElement el = layer.text("a", "hi", null);

        assertEquals(640, el.getParentWidth());
        assertEquals(360, el.getParentHeight());

        window.set(new WindowData(1280, 720, 1280, 720));
        assertEquals(1280, el.getParentWidth(),
                "the v1 parent box is the layer viewport");
        assertEquals(720, el.getParentHeight());
    }

    @Test
    void elementZIndexReordersTheFrameWithoutLosingInsertionOrder() {
        HudLayer layer = newLayer("main");
        layer.text("a", "A", null);
        layer.text("b", "B", null);
        assertEquals(List.of("text:0,0,A,ffffffff", "text:0,0,B,ffffffff"),
                render.frame(0));

        Draw2DElement b = layer.element("b");
        b.setZIndex(-1);
        assertEquals(List.of("text:0,0,B,ffffffff", "text:0,0,A,ffffffff"),
                render.frame(0), "a z change re-sorts on the next frame");

        b.setZIndex(0);
        assertEquals(List.of("text:0,0,A,ffffffff", "text:0,0,B,ffffffff"),
                render.frame(0), "equal z falls back to insertion order");
    }

    @Test
    void visibleToggleSkipsPaintingButKeepsState() {
        HudLayer layer = newLayer("main");
        Draw2DElement el = layer.text("a", "hi", null);

        el.setVisible(false);
        assertFalse(el.isVisible());
        assertEquals(List.of(), render.frame(0), "invisible elements paint nothing");

        el.setVisible(true);
        assertTrue(el.isVisible());
        assertEquals(List.of("text:0,0,hi,ffffffff"), render.frame(0));
    }

    @Test
    void disposedHandleReadsUndefinedAndThrowsOnSetters() {
        HudLayer layer = newLayer("main");
        Draw2DElement el = layer.text("gone", "x", null);
        layer.remove("gone");

        assertTrue(el.isDisposed(), "the disposed flag stays readable");
        assertNull(el.getId());
        assertNull(el.getLayer());
        assertNull(el.getParent());
        assertNull(el.getX());
        assertNull(el.getY());
        assertNull(el.getWidth());
        assertNull(el.getHeight());
        assertNull(el.getScale());
        assertNull(el.getRotation());
        assertNull(el.isRotateCenter());
        assertNull(el.getZIndex());
        assertNull(el.isVisible());
        assertNull(el.getBounds());
        assertNull(el.getScaledWidth());
        assertNull(el.getScaledHeight());
        assertNull(el.getScaledLeft());
        assertNull(el.getScaledTop());
        assertNull(el.getScaledRight());
        assertNull(el.getScaledBottom());
        assertNull(el.getParentWidth());
        assertNull(el.getParentHeight());

        assertThrows(IllegalStateException.class, () -> el.setX(1));
        assertThrows(IllegalStateException.class, () -> el.setY(1));
        assertThrows(IllegalStateException.class, () -> el.setPos(1, 1));
        assertThrows(IllegalStateException.class, () -> el.setWidth(1));
        assertThrows(IllegalStateException.class, () -> el.setHeight(1));
        assertThrows(IllegalStateException.class, () -> el.setSize(1, 1));
        assertThrows(IllegalStateException.class, () -> el.setScale(2));
        assertThrows(IllegalStateException.class, () -> el.setRotation(45));
        assertThrows(IllegalStateException.class, () -> el.setRotateCenter(true));
        assertThrows(IllegalStateException.class, () -> el.setZIndex(1));
        assertThrows(IllegalStateException.class, () -> el.setVisible(false));
        assertThrows(IllegalStateException.class, () -> el.moveTo(1, 1));
        assertThrows(IllegalStateException.class, () -> el.moveToX(1));
        assertThrows(IllegalStateException.class, () -> el.moveToY(1));
        assertThrows(IllegalStateException.class,
                () -> el.alignHorizontally("left"));
        assertThrows(IllegalStateException.class,
                () -> el.alignVertically("top"));
        assertThrows(IllegalStateException.class,
                () -> el.align("left", "top"));
    }

    @Test
    void scaleAndRotationValidationRules() {
        HudLayer layer = newLayer("main");
        Draw2DElement el = layer.rect("r",
                Map.of("x", 0, "y", 0, "w", 1, "h", 1));

        assertThrows(IllegalArgumentException.class, () -> el.setScale(0),
                "zero scale throws");
        assertThrows(IllegalArgumentException.class, () -> el.setScale(-1));
        assertThrows(IllegalArgumentException.class, () -> el.setWidth(-1));
        assertThrows(IllegalArgumentException.class, () -> el.setHeight(-5));
        assertThrows(IllegalArgumentException.class, () -> el.setSize(-1, 5));

        assertThrows(IllegalArgumentException.class, () -> el.setSize(50, -1));
        assertEquals(50, el.getWidth(),
                "width applies before height, so a bad height keeps it");

        el.setRotation(270);
        assertEquals(-90f, el.getRotation(), "setters normalize to [-180, 180)");
        el.setRotation(-270);
        assertEquals(90f, el.getRotation());
        el.setRotation(180);
        assertEquals(-180f, el.getRotation());

        assertThrows(IllegalArgumentException.class,
                () -> layer.text("bad", "x", Map.of("scale", 0)),
                "the upsert opts path applies the same scale validation");
        assertThrows(IllegalArgumentException.class,
                () -> layer.rect("bad2",
                        Map.of("x", 0, "y", 0, "w", 1, "h", 1, "scale", -1)));
    }

    @Test
    void upsertOptsAreTheAuthoritativeFullState() {
        HudLayer layer = newLayer("main");
        Draw2DElement el = layer.text("a", "hi", Map.of("x", 1, "y", 2));
        el.setX(50).setZIndex(3);
        assertEquals(50, el.getX());

        Draw2DElement again = layer.text("a", "hello", Map.of("x", 1, "y", 2));
        assertSame(el, again, "same id + same kind keeps the handle");
        assertEquals(1, again.getX(),
                "a re-upsert writes the full opts state back");
        assertEquals(0, again.getZIndex(),
                "opts absent from the bag fall back to their defaults");
        assertEquals(List.of("text:1,2,hello,ffffffff"), render.frame(0));
    }
}
