package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.core.api.render.components.Draw2DElement;
import ink.tuanzi.neomacros.port.model.WindowData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scene replay over a recording port: draw order (layer z → element z →
 * insertion), visibility skips, upsert identity, percentage reflow, color
 * normalization (拍板④), and the disposed-handle semantics.
 */
class HudScenePaintTest {

    private static final WindowData WINDOW = new WindowData(640, 360, 640, 360);

    private final RecordingHudRender render = new RecordingHudRender();

    private HudLayer newLayer(String id, int z) {
        return new HudLayer(id, id, z, render, Runnable::run, () -> WINDOW);
    }

    @Test
    void elementsDrawByZThenInsertion() {
        HudLayer layer = newLayer("main", 0);
        layer.text("a", "A", null);
        layer.text("b", "B", Map.of("zIndex", 5));
        layer.rect("c", Map.of("x", 1, "y", 2, "w", 3, "h", 4, "zIndex", -1));
        layer.text("d", "D", null);

        List<String> frame = render.frame(0);
        assertEquals(List.of(
                "rect:1,2,3,4,ffffffff",
                "text:0,0,A,ffffffff",
                "text:0,0,D,ffffffff",
                "text:0,0,B,ffffffff"), frame,
                "negative z first, equal z by insertion, higher z last");
    }

    @Test
    void invisibleElementsAndLayersPaintNothing() {
        HudLayer layer = newLayer("main", 0);
        layer.text("shown", "yes", null);
        layer.text("hidden", "no", Map.of("visible", false));
        assertEquals(1, render.frame(0).size());

        layer.setVisible(false);
        assertEquals(List.of(), render.frame(0), "an invisible layer keeps state, paints nothing");
        assertFalse(layer.isVisible() == null);
        layer.setVisible(true);
        assertEquals(1, render.frame(0).size());
    }

    @Test
    void upsertKeepsHandleAndInsertionSlot() {
        HudLayer layer = newLayer("main", 0);
        Draw2DElement first = layer.text("greet", "hi", null);
        layer.text("other", "x", null);
        Draw2DElement updated = layer.text("greet", "hello", null);

        assertSame(first, updated, "same id + same kind keeps the handle");
        assertEquals(List.of("greet", "other"), layer.getIds());
        assertEquals(List.of("text:0,0,hello,ffffffff", "text:0,0,x,ffffffff"),
                render.frame(0), "the update repaints in the original slot");
    }

    @Test
    void kindChangeReplacesCellAndDisposesOldHandle() {
        HudLayer layer = newLayer("main", 0);
        Draw2DElement text = layer.text("item", "old", null);
        Draw2DElement rect = layer.rect("item",
                Map.of("x", 4, "y", 5, "w", 6, "h", 7));

        assertNotSame(text, rect, "a different kind hands out a new handle");
        assertTrue(text.isDisposed(), "the replaced handle is disposed");
        assertNull(text.getId(), "a disposed handle reads undefined");
        assertFalse(rect.isDisposed());
        assertEquals(List.of("rect:4,5,6,7,ffffffff"), render.frame(0),
                "only the live kind paints");
    }

    @Test
    void removeAndClearDisposeHandles() {
        HudLayer layer = newLayer("main", 0);
        Draw2DElement kept = layer.text("keep", "k", null);
        Draw2DElement gone = layer.text("gone", "g", null);

        layer.remove("gone");
        assertTrue(gone.isDisposed());
        assertNull(layer.element("gone"));
        assertFalse(kept.isDisposed());

        layer.clear();
        assertTrue(kept.isDisposed(), "clear disposes every outstanding handle");
        assertEquals(List.of(), layer.getIds());
        assertEquals(List.of(), render.frame(0));
    }

    @Test
    void percentageCoordinatesReflowWithTheViewport() {
        AtomicReference<WindowData> window =
                new AtomicReference<>(new WindowData(640, 360, 640, 360));
        HudLayer layer = new HudLayer("main", "main", 0, render, Runnable::run,
                window::get);
        layer.text("p", "pct", Map.of("x", "50%", "y", "25%"));

        assertEquals(List.of("text:320,90,pct,ffffffff"), render.frame(0));
        assertEquals(640, layer.getWidth());
        assertEquals(360, layer.getHeight());

        window.set(new WindowData(1280, 720, 1280, 720));
        assertEquals(List.of("text:640,180,pct,ffffffff"), render.frame(0),
                "the next frame resolves percentages against the new viewport");
        assertEquals(1280, layer.getWidth());
    }

    @Test
    void colorsAcceptNumbersHexAndVanillaNames() {
        HudLayer layer = newLayer("main", 0);
        layer.rect("named", Map.of("x", 0, "y", 0, "w", 1, "h", 1,
                "color", "red"));
        layer.rect("hex", Map.of("x", 0, "y", 0, "w", 1, "h", 1,
                "color", "#80ff4040"));
        layer.rect("short", Map.of("x", 0, "y", 0, "w", 1, "h", 1,
                "color", "#f00"));
        layer.rect("argb", Map.of("x", 0, "y", 0, "w", 1, "h", 1,
                "color", 0x11223344));

        assertEquals(List.of(
                "rect:0,0,1,1,ffff5555",
                "rect:0,0,1,1,80ff4040",
                "rect:0,0,1,1,ffff0000",
                "rect:0,0,1,1,11223344"), render.frame(0));
    }

    @Test
    void disposedLayerReadsUndefinedAndThrowsOnMethods() {
        HudLayer layer = newLayer("main", 0);
        layer.text("a", "a", null);
        layer.dispose();

        assertTrue(layer.isDisposed(), "the disposed flag stays readable");
        assertNull(layer.getId());
        assertNull(layer.getZIndex());
        assertNull(layer.getWidth());
        assertNull(layer.getHeight());
        assertNull(layer.isVisible());
        assertNull(layer.getElements());
        assertNull(layer.getIds());
        assertNull(layer.element("a"));

        assertThrows(IllegalStateException.class, () -> layer.text("b", "b", null));
        assertThrows(IllegalStateException.class,
                () -> layer.rect("b", Map.of("x", 0, "y", 0, "w", 1, "h", 1)));
        assertThrows(IllegalStateException.class, () -> layer.setZIndex(3));
        assertThrows(IllegalStateException.class, () -> layer.setVisible(true));
        assertThrows(IllegalStateException.class, layer::clear);
        assertThrows(IllegalStateException.class, layer::init);
        assertEquals(List.of("add:main:0", "remove:main"), render.events(),
                "dispose drops the port registration exactly once");
        assertEquals(List.of(), render.frame(0));
    }

    @Test
    void initPreservesElementsAndReservedIdsAreRejected() {
        HudLayer layer = newLayer("main", 0);
        layer.text("a", "a", null);
        layer.init();
        assertEquals(1, render.frame(0).size(), "init without a callback keeps elements");

        assertThrows(IllegalArgumentException.class,
                () -> layer.text("$internal", "x", null));
        assertThrows(IllegalArgumentException.class,
                () -> layer.rect("$internal", Map.of("x", 0, "y", 0, "w", 1, "h", 1)));
        assertThrows(IllegalArgumentException.class, () -> layer.rect("box", null));
        assertThrows(IllegalArgumentException.class,
                () -> layer.rect("box", Map.of("x", 0, "y", 0, "w", 1)));
    }

    @Test
    void layerZOrderingAcrossLayersIsOwnedByThePort() {
        HudLayer low = newLayer("low", 0);
        HudLayer high = newLayer("high", 10);
        low.text("t", "low", null);
        high.text("t", "high", null);

        assertEquals(List.of("add:low:0", "add:high:10"), render.events());
        assertEquals(List.of("low", "high"), render.layerIds().get());

        high.setZIndex(-5);
        assertEquals(List.of("add:low:0", "add:high:10", "setz:high:-5"),
                render.events(), "setLayerZ is scheduled without re-adding");
        assertEquals(List.of("high", "low"), render.layerIds().get(),
                "the port re-orders by the new z");
        assertEquals(List.of("text:0,0,high,ffffffff", "text:0,0,low,ffffffff"),
                render.frame(0));
    }
}
