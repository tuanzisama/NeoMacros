package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.core.api.render.HudLayer;
import ink.tuanzi.neomacros.core.api.render.RecordingHudRender;
import ink.tuanzi.neomacros.port.model.WindowData;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Alignment face of {@link Draw2DElement} (the {@link Alignable}
 * contract): parent-relative anchoring against the layer viewport,
 * sibling-relative {@code <this>On<other>} anchoring, single-axis moves
 * that keep the other coordinate raw, derived scaled edges, the
 * percentage parser contract, and the disposal table for the new members.
 */
class AlignTest {

    private static final WindowData WINDOW = new WindowData(640, 360, 640, 360);

    private final RecordingHudRender render = new RecordingHudRender();

    private HudLayer newLayer() {
        return new HudLayer("main", "main", 0, render, Runnable::run,
                () -> WINDOW);
    }

    private static Draw2DElement rect(HudLayer layer, String id, int x, int y,
            int width, int height) {
        return layer.rect(id, Map.of("x", x, "y", y, "w", width, "h", height));
    }

    @Test
    void parentRelativeAlignmentAnchorsWithinTheViewport() {
        HudLayer layer = newLayer();
        Draw2DElement el = rect(layer, "a", 10, 20, 100, 50);

        assertSame(el, el.alignHorizontally("center"),
                "alignment stays fluent on the element handle");
        assertEquals(270, el.getX(), "(640 - 100) / 2");
        assertEquals(20, el.getY(), "the other axis keeps its coordinate");

        el.alignHorizontally("right");
        assertEquals(540, el.getX(), "the right edge lands on the parent edge");
        el.alignHorizontally("left", 5);
        assertEquals(5, el.getX(), "the offset shifts the resulting position");
        el.alignHorizontally("50%");
        assertEquals(270, el.getX(),
                "a percentage shares the space the element does not fill");

        el.alignVertically("bottom");
        assertEquals(310, el.getY(), "360 - 50");
        el.alignVertically("center");
        assertEquals(155, el.getY(), "(360 - 50) / 2");

        el.align("center", "bottom");
        assertEquals(270, el.getX());
        assertEquals(310, el.getY());
        el.align("left", 5, "top", 3);
        assertEquals(5, el.getX());
        assertEquals(3, el.getY());
    }

    @Test
    void parentRelativeUnsupportedTokensDoNothing() {
        HudLayer layer = newLayer();
        Draw2DElement el = rect(layer, "a", 10, 20, 100, 50);

        el.alignHorizontally("diagonal");
        assertEquals(10, el.getX(), "an unsupported token leaves the axis alone");
        el.alignHorizontally("150%");
        assertEquals(10, el.getX(), "an out-of-range percentage does nothing");
        el.alignVertically("left");
        assertEquals(20, el.getY(), "a wrong-axis keyword does nothing");
        assertThrows(NumberFormatException.class,
                () -> el.alignHorizontally("50.5%"),
                "a suffixed non-integer percentage throws");

        el.align("diagonal", "bottom");
        assertEquals(10, el.getX(), "the unsupported horizontal axis stays put");
        assertEquals(310, el.getY(), "the supported vertical axis moves");
    }

    @Test
    void siblingRelativeAlignmentAnchorsAgainstAnotherElement() {
        HudLayer layer = newLayer();
        Draw2DElement panel = rect(layer, "panel", 100, 40, 200, 100);
        Draw2DElement label = rect(layer, "label", 0, 0, 40, 20);

        label.alignHorizontally(panel, "leftOnRight");
        assertEquals(300, label.getX(), "the label's left edge on the panel's right edge");
        label.alignHorizontally(panel, "rightOnLeft");
        assertEquals(60, label.getX(), "the label's right edge on the panel's left edge");
        label.alignHorizontally(panel, "centerOnCenter");
        assertEquals(180, label.getX(), "100 + 200/2 - 40/2");
        label.alignHorizontally(panel, "center");
        assertEquals(180, label.getX(), "a bare token aligns the same anchor both sides");
        label.alignHorizontally(panel, "leftOnRight", 5);
        assertEquals(305, label.getX(), "the offset shifts the resulting position");
        label.alignHorizontally(panel, "0%On100%");
        assertEquals(300, label.getX(), "percentage anchors resolve per element");

        label.alignVertically(panel, "topOnBottom");
        assertEquals(140, label.getY(), "the label's top on the panel's bottom edge");

        label.align(panel, "center", "middle");
        assertEquals(180, label.getX());
        assertEquals(80, label.getY(), "middle is a center synonym on the vertical axis");
        label.align(panel, "left", 2, "top", 3);
        assertEquals(102, label.getX());
        assertEquals(43, label.getY());
    }

    @Test
    void malformedSiblingAlignmentsThrow() {
        HudLayer layer = newLayer();
        Draw2DElement panel = rect(layer, "panel", 100, 40, 200, 100);
        Draw2DElement label = rect(layer, "label", 0, 0, 40, 20);

        assertThrows(IllegalArgumentException.class,
                () -> label.alignHorizontally(panel, "leftof"),
                "an unsupported anchor throws");
        assertThrows(IllegalArgumentException.class,
                () -> label.alignHorizontally(panel, "leftOnRightOn"),
                "more than two anchor halves throw");
        assertThrows(IllegalArgumentException.class,
                () -> label.alignHorizontally(panel, "leftOn"),
                "an empty second half throws");
        assertThrows(IllegalArgumentException.class,
                () -> label.alignHorizontally(panel, "150%OnLeft"),
                "an out-of-range percentage half is unsupported");
        assertThrows(IllegalArgumentException.class,
                () -> label.alignHorizontally(panel, "leftOnTop"),
                "a wrong-axis keyword half is unsupported");
        assertThrows(NumberFormatException.class,
                () -> label.alignHorizontally(panel, "50.5%OnLeft"),
                "a suffixed non-integer half throws");
        assertThrows(IllegalArgumentException.class,
                () -> label.alignHorizontally((Object) 42, "left", null),
                "a first argument that is neither string nor element throws");
    }

    @Test
    void moveToAxesRetainTheOtherCoordinate() {
        HudLayer layer = newLayer();
        Draw2DElement el = rect(layer, "a", 10, 20, 100, 50);

        assertSame(el, el.moveToX(50), "single-axis moves stay fluent");
        assertEquals(50, el.getX());
        assertEquals(20, el.getY(), "the untouched axis keeps its coordinate");
        el.moveToY(70);
        assertEquals(50, el.getX());
        assertEquals(70, el.getY());

        el.setY("50%");
        el.moveToX(5);
        assertEquals("50%", el.getY(),
                "a percentage coordinate keeps its raw form through a single-axis move");
        assertEquals(180, el.getBounds().y());
        el.setX("25%");
        el.moveToY(9);
        assertEquals("25%", el.getX());
        assertEquals(160, el.getBounds().x());
    }

    @Test
    void scaledEdgesFollowTheScaledBox() {
        HudLayer layer = newLayer();
        Draw2DElement el = rect(layer, "a", 10, 20, 100, 50);

        assertEquals(110, el.getScaledRight(), "left edge plus scaled width");
        assertEquals(70, el.getScaledBottom(), "top edge plus scaled height");

        el.setScale(2);
        assertEquals(210, el.getScaledRight(), "the scaled width feeds the edge");
        assertEquals(120, el.getScaledBottom());
    }

    @Test
    void percentageParserFollowsTheUpstreamContract() {
        assertEquals(50, Alignable.parsePercentage("50%"));
        assertEquals(0, Alignable.parsePercentage("0%"));
        assertEquals(100, Alignable.parsePercentage("100%"));
        assertEquals(-1, Alignable.parsePercentage("150%"),
                "out-of-range percentages answer -1");
        assertEquals(-1, Alignable.parsePercentage("-5%"));
        assertEquals(-1, Alignable.parsePercentage("50"),
                "a string without the suffix answers -1");
        assertEquals(-1, Alignable.parsePercentage(null));
        assertThrows(NumberFormatException.class,
                () -> Alignable.parsePercentage("50.5%"),
                "a suffixed non-integer throws");
        assertThrows(NumberFormatException.class,
                () -> Alignable.parsePercentage("%"));
    }

    @Test
    void disposedElementsRejectAlignment() {
        HudLayer layer = newLayer();
        Draw2DElement el = layer.text("gone", "x", null);
        layer.remove("gone");

        assertThrows(IllegalStateException.class,
                () -> el.alignHorizontally("left"));
        assertThrows(IllegalStateException.class,
                () -> el.alignVertically("top"));
        assertThrows(IllegalStateException.class,
                () -> el.align("left", "top"));
        assertThrows(IllegalStateException.class, () -> el.moveToX(1));
        assertThrows(IllegalStateException.class, () -> el.moveToY(1));
        assertNull(el.getScaledRight(), "derived edges read undefined");
        assertNull(el.getScaledBottom());
    }

    @Test
    void aligningAgainstDisposedSiblingThrows() {
        HudLayer layer = newLayer();
        Draw2DElement panel = rect(layer, "panel", 100, 40, 200, 100);
        Draw2DElement label = rect(layer, "label", 0, 0, 40, 20);
        layer.remove("panel");

        assertThrows(IllegalStateException.class,
                () -> label.alignHorizontally(panel, "leftOnRight"),
                "reading a disposed sibling's geometry fails the element contract");
    }
}
