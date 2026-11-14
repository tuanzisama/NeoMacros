package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.core.api.render.HudLayer;
import ink.tuanzi.neomacros.core.api.render.RecordingHudRender;
import ink.tuanzi.neomacros.port.model.WindowData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-034 component faces over a recording port: subclass upserts for
 * {@code Draw2DText}/{@code Draw2DRect}/{@code Draw2DLine}, the
 * component-specific state (text metrics, shadow, corners, endpoints,
 * stroke, folded color forms), the paint primitives they emit (line,
 * shadow text, transform scopes) and the disposal contract.
 */
class Draw2DComponentTest {

    private static final WindowData WINDOW = new WindowData(640, 360, 640, 360);

    private final RecordingHudRender render = new RecordingHudRender();

    private HudLayer newLayer(String id) {
        return new HudLayer(id, id, 0, render, Runnable::run, () -> WINDOW);
    }

    @Test
    void upsertReturnsTheComponentSubclass() {
        HudLayer layer = newLayer("main");

        Draw2DText text = layer.text("t", "hello", null);
        Draw2DRect rect = layer.rect("r", Map.of("x", 1, "y", 2, "w", 3, "h", 4));
        Draw2DLine line = layer.line("l", Map.of(
                "x1", 0, "y1", 0, "x2", 10, "y2", 10));

        assertInstanceOf(Draw2DElement.class, text);
        assertInstanceOf(Draw2DElement.class, rect);
        assertInstanceOf(Draw2DElement.class, line);
        assertSame(text, layer.element("t"), "the element face hands back the subclass");
        assertSame(line, layer.element("l"));
        assertEquals(List.of("t", "r", "l"), layer.getIds());
    }

    @Test
    void textMetricsFollowTheSetTextContract() {
        HudLayer layer = newLayer("main");
        Draw2DText text = layer.text("t", "hello", null);

        // The stub port measures with the neutral length * 6 estimate and
        // an 8px line height (RenderPort defaults).
        assertEquals(30, text.getWidth(), "the cached font width seeds the box");
        assertEquals(8, text.getHeight(), "the font line height seeds the box");
        assertEquals(30, text.getScaledWidth());
        assertEquals("hello", text.getText());

        assertSame(text, text.setText("hi"));
        assertEquals(12, text.getWidth(), "setText re-measures (JsMacros contract)");
        assertEquals("hi", text.getText());

        text.setText(null);
        assertEquals("", text.getText(), "null becomes the empty string");
        assertEquals(0, text.getWidth());
    }

    @Test
    void textShadowAndColorForms() {
        HudLayer layer = newLayer("main");
        Draw2DText text = layer.text("t", "x", Map.of("shadow", true));

        assertTrue(text.hasShadow(), "the shadow option seeds the state");
        assertSame(text, text.setShadow(false));
        assertFalse(text.hasShadow());

        assertSame(text, text.setColor(0x11223344));
        assertEquals(0x11223344, text.getColor());
        assertEquals(0x11, text.getColor() >>> 24, "alpha rides the top byte");

        // color + alpha: the alpha replaces the packed top byte.
        text.setColor(0x00AABBCC, 0x80);
        assertEquals(0x80AABBCC, text.getColor());

        // rgb / rgba Builder forms fold into the same member.
        text.setColor(0x10, 0x20, 0x30);
        assertEquals(0xFF102030, text.getColor());
        text.setColor(0x10, 0x20, 0x30, 0x40);
        assertEquals(0x40102030, text.getColor());

        // 拍板④ string form.
        text.setColor("#80ff0000");
        assertEquals(0x80FF0000, text.getColor());
    }

    @Test
    void rectCornerViewRoundTrips() {
        HudLayer layer = newLayer("main");
        Draw2DRect rect = layer.rect("r", Map.of(
                "x", 10, "y", 20, "w", 40, "h", 30));

        assertEquals(10, rect.getX1());
        assertEquals(20, rect.getY1());
        assertEquals(50, rect.getX2());
        assertEquals(50, rect.getY2());

        assertSame(rect, rect.setX1(15));
        assertEquals(15, rect.getX1());
        assertEquals(35, rect.getWidth(), "moving the first corner resizes");
        assertEquals(50, rect.getX2(), "the second corner stays put");

        rect.setPos2(5, 25);
        assertEquals(5, rect.getX2());
        assertEquals(25, rect.getY2());
        assertEquals(5, rect.getX(), "the stored box re-anchors to the min corner");
        assertEquals(10, rect.getWidth());
        assertTrue(rect.getX1() > rect.getX2(), "the corner order flipped");
        assertEquals(15, rect.getScaledLeft() + rect.getScaledWidth(),
                "the far edge still reads the first corner");

        // setWidth anchors the smaller coordinate (JsMacros parity).
        rect.setWidth(30);
        assertEquals(5, rect.getX(), "the min corner stays anchored");
        assertEquals(30, rect.getWidth());
        assertEquals(35, rect.getX1(), "the far (first) edge moved with the width");

        // moveTo puts the min corner at the requested position.
        rect.moveTo(100, 200);
        assertEquals(100, rect.getX());
        assertEquals(200, rect.getY());
    }

    @Test
    void lineEndpointViewAndStroke() {
        HudLayer layer = newLayer("main");
        // Endpoints given in reverse order: the first endpoint stays the
        // first, the stored box is the min-corner extent.
        Draw2DLine line = layer.line("l", Map.of(
                "x1", 100, "y1", 80, "x2", 40, "y2", 50,
                "color", 0xFF112233, "width", 3));

        assertEquals(100, line.getX1());
        assertEquals(80, line.getY1());
        assertEquals(40, line.getX2());
        assertEquals(50, line.getY2());
        assertEquals(40, line.getScaledLeft(), "the box anchors at the min corner");
        assertEquals(50, line.getScaledTop());
        assertEquals(60, line.getScaledWidth(), "|x2 - x1|");
        assertEquals(30, line.getScaledHeight(), "|y2 - y1|");
        assertEquals(60, line.getWidth(), "the base width face is the extent");
        assertEquals(3f, line.getStrokeWidth());
        assertEquals(0xFF112233, line.getColor());

        // Moving an endpoint re-anchors the box; both endpoints now sit on
        // the same point (the second one was already at 40,50).
        assertSame(line, line.setPos1(40, 50));
        assertEquals(40, line.getX1());
        assertEquals(50, line.getY1());
        assertEquals(40, line.getX2(), "the second endpoint stays put");
        assertEquals(50, line.getY2());
        assertEquals(0, line.getScaledWidth(), "now a zero-length line");
        assertEquals(0, line.getScaledHeight());

        line.setPos1(10, 10);
        line.setPos2(20, 30);
        assertEquals(10, line.getX1());
        assertEquals(20, line.getX2());
        assertEquals(10, line.getWidth());
        assertEquals(20, line.getHeight());

        // Stroke: stored as-is, non-positive allowed (draws nothing later).
        assertSame(line, line.setStrokeWidth(0));
        assertEquals(0f, line.getStrokeWidth());
        line.setStrokeWidth(2.5);
        assertEquals(2.5f, line.getStrokeWidth());
    }

    @Test
    void componentFluentChainsReturnTheComponentInstance() {
        HudLayer layer = newLayer("main");

        Draw2DText text = layer.text("t", "x", null);
        assertSame(text, text.setText("y").setShadow(true)
                .setColor(0xFF0000).setZIndex(3).setVisible(false));

        Draw2DRect rect = layer.rect("r", Map.of("x", 0, "y", 0, "w", 1, "h", 1));
        assertSame(rect, rect.setX1(1).setY1(2).setPos1(3, 4)
                .setX2(5).setY2(6).setPos2(7, 8)
                .setColor(0x00FF00).setAlpha(9).setRotation(15));

        Draw2DLine line = layer.line("l", Map.of("x1", 0, "y1", 0, "x2", 1, "y2", 1));
        assertSame(line, line.setX1(1).setY1(2).setPos1(3, 4)
                .setX2(5).setY2(6).setPos2(7, 8)
                .setStrokeWidth(2).setColor(0x0000FF).setAlpha(10));
    }

    @Test
    void componentMutatorsFollowTheDisposalContract() {
        HudLayer layer = newLayer("main");
        Draw2DText text = layer.text("t", "x", null);
        Draw2DRect rect = layer.rect("r", Map.of("x", 0, "y", 0, "w", 1, "h", 1));
        Draw2DLine line = layer.line("l", Map.of("x1", 0, "y1", 0, "x2", 1, "y2", 1));

        layer.remove("t");
        layer.remove("r");
        layer.remove("l");

        assertTrue(text.isDisposed());
        assertThrows(IllegalStateException.class, () -> text.setText("y"));
        assertThrows(IllegalStateException.class, () -> text.setShadow(true));
        assertThrows(IllegalStateException.class, () -> text.setColor(0xFFFFFF));
        assertNull(text.getText());
        assertNull(text.hasShadow());
        assertNull(text.getColor());

        assertThrows(IllegalStateException.class, () -> rect.setX1(1));
        assertThrows(IllegalStateException.class, () -> rect.setColor(0xFFFFFF));
        assertThrows(IllegalStateException.class, () -> rect.setAlpha(1));
        assertNull(rect.getX1());
        assertNull(rect.getX2());
        assertNull(rect.getAlpha());

        assertThrows(IllegalStateException.class, () -> line.setX1(1));
        assertThrows(IllegalStateException.class, () -> line.setPos2(1, 2));
        assertThrows(IllegalStateException.class, () -> line.setStrokeWidth(1));
        assertThrows(IllegalStateException.class, () -> line.setColor(0xFFFFFF));
        assertNull(line.getX1());
        assertNull(line.getStrokeWidth());
        assertNull(line.getColor());
    }

    @Test
    void kindReplacementSwapsTheComponentHandle() {
        HudLayer layer = newLayer("main");
        Draw2DText text = layer.text("t", "x", null);

        Draw2DRect rect = layer.rect("t", Map.of("x", 0, "y", 0, "w", 1, "h", 1));

        assertTrue(text.isDisposed(), "the kind change disposes the old handle");
        assertNull(text.getText());
        assertSame(rect, layer.element("t"));
        assertThrows(IllegalStateException.class, () -> text.setText("y"));
    }

    @Test
    void paintReachesLineShadowAndPlainPrims() {
        HudLayer layer = newLayer("main");
        layer.text("plain", "hi", Map.of("x", 5, "y", 6));
        layer.text("shadowed", "yo", Map.of(
                "x", 7, "y", 8, "shadow", true, "color", 0x80FF0000));
        layer.line("l", Map.of(
                "x1", 1, "y1", 2, "x2", 3, "y2", 4, "color", 0xFF00FF00));
        layer.rect("r", Map.of(
                "x", 9, "y", 10, "w", 11, "h", 12, "color", "#f00"));

        assertEquals(List.of(
                "text:5,6,hi,ffffffff",
                "text-shadow:7,8,yo,80ff0000",
                "line:1,2,3,4,ff00ff00,1",
                "rect:9,10,11,12,ffff0000"),
                render.frame(0),
                "every component kind reaches its neutral primitive");
    }

    @Test
    void transformScopesThePaint() {
        HudLayer layer = newLayer("main");
        // No transform by default: a plain rect paints bare.
        layer.rect("plain", Map.of("x", 1, "y", 2, "w", 3, "h", 4));

        // Corner rotation is one scope around the body.
        layer.rect("turned", Map.of(
                "x", 10, "y", 20, "w", 30, "h", 40, "rotation", 45));

        // Center rotation nests a second scope: scale about the corner,
        // then the rotation about the scaled center (10+15, 20+20).
        layer.rect("spun", Map.of(
                "x", 10, "y", 20, "w", 30, "h", 40,
                "scale", 2, "rotation", 90, "visible", false));

        assertEquals(List.of(
                "rect:1,2,3,4,ffffffff",
                "transform:10,20,45,1,1",
                "rect:10,20,30,40,ffffffff",
                "transform-pop"),
                render.frame(0),
                "the default state paints bare; a rotation scopes the body");

        // Flip the hidden element visible and pivot about its center to
        // observe the nested scopes.
        layer.element("spun").setVisible(true).setRotateCenter(true);
        assertEquals(List.of(
                "rect:1,2,3,4,ffffffff",
                "transform:10,20,45,1,1",
                "rect:10,20,30,40,ffffffff",
                "transform-pop",
                "transform:10,20,0,2,2",
                "transform:25,40,90,1,1",
                "rect:10,20,30,40,ffffffff",
                "transform-pop",
                "transform-pop"),
                render.frame(0),
                "rotateCenter nests the rotation scope inside the scale scope");
    }

    @Test
    void lineWithNonPositiveStrokePaintsNothing() {
        HudLayer layer = newLayer("main");
        layer.line("gone", Map.of(
                "x1", 0, "y1", 0, "x2", 5, "y2", 5, "width", 0));
        layer.line("kept", Map.of(
                "x1", 0, "y1", 0, "x2", 5, "y2", 5, "width", 2));

        assertEquals(List.of("line:0,0,5,5,ffffffff,2"), render.frame(0),
                "a non-positive stroke draws nothing (JsMacros parity)");
    }

    @Test
    void textScaleAppliesThroughTheTransformScope() {
        HudLayer layer = newLayer("main");
        layer.text("t", "hi", Map.of("x", 4, "y", 4, "scale", 2));

        assertEquals(List.of(
                "transform:4,4,0,2,2",
                "text:4,4,hi,ffffffff",
                "transform-pop"),
                render.frame(0),
                "the scaled text draws inside its transform scope");
        Draw2DText text = (Draw2DText) layer.element("t");
        assertEquals(24, text.getScaledWidth(),
                "the cached width applies the scale (JsMacros parity)");
    }
}
