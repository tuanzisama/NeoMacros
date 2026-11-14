package ink.tuanzi.neomacros.port.model;

/**
 * Neutral 2D drawing interface for HUD layers: text, rectangles,
 * outlines, lines, and the element transform scope. The adapter maps
 * these calls to the version-specific rendering API; scripts access it
 * through {@code neo.hud}.
 *
 * <p>All coordinates are screen-space pixels from the top-left corner.</p>
 */
public interface DrawContext2d {

    /** Draws a text string at (x, y) in white. */
    void text(int x, int y, String text);

    /** Draws a text string at (x, y) with an ARGB color. */
    void text(int x, int y, String text, int color);

    /**
     * Draws a text string at (x, y) with an ARGB color and an optional
     * drop shadow. Defaulted so adapters written before the shadow face
     * keep compiling and draw without the shadow.
     *
     * @param shadow whether the font draws a drop shadow behind the glyphs
     */
    default void text(int x, int y, String text, int color, boolean shadow) {
        text(x, y, text, color);
    }

    /** Fills a rectangle (x, y, width, height) with an ARGB color. */
    void rect(int x, int y, int width, int height, int color);

    /** Draws a 1-pixel rectangle outline. */
    void outline(int x, int y, int width, int height, int color);

    /**
     * Draws a texture scaled to the rectangle, identified as
     * {@code namespace:path}. Invalid identifiers draw nothing.
     */
    void sprite(int x, int y, int width, int height, String textureId);

    /**
     * Draws a straight line between two points with an ARGB color and a
     * stroke thickness in screen pixels; non-positive thickness draws
     * nothing (JsMacros parity).
     *
     * <p>The default implementation degrades to stepped rectangle fills
     * (G-001 §6.2 — neither milestone node ships an arbitrary-angle GUI
     * line primitive), so every adapter renders lines without a
     * version branch. Adapters may override with a native primitive;
     * the recording test contexts override it to keep the {@code line}
     * primitive observable to assertions.</p>
     *
     * @param width stroke thickness in screen pixels (rounded up when drawn)
     */
    default void line(int x1, int y1, int x2, int y2, int color, float width) {
        if (!(width > 0)) {
            return;
        }
        int thickness = Math.max(1, (int) Math.ceil(width));
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int step = 0; step <= steps; step++) {
            int px = steps == 0 ? x1
                    : (int) Math.round(x1 + (double) (x2 - x1) * step / steps);
            int py = steps == 0 ? y1
                    : (int) Math.round(y1 + (double) (y2 - y1) * step / steps);
            rect(px, py, px + thickness, py + thickness, color);
        }
    }

    /**
     * Runs {@code body} with an element transform applied: the drawing is
     * mapped as a scale followed by a rotation about
     * {@code (pivotX, pivotY)}, leaving the pivot fixed, so the body keeps
     * drawing at absolute screen coordinates (G-001 §2.2 / §5.2 — the
     * rotation/scale application for {@code Draw2DElement}).
     *
     * <p>Defaulted so adapters written before the transform face keep
     * compiling and simply draw the body untransformed.</p>
     *
     * @param pivotX pivot x in screen pixels
     * @param pivotY pivot y in screen pixels
     * @param degrees rotation in degrees (counter-clockwise)
     * @param scaleX x scale factor ({@code 1} = unchanged)
     * @param scaleY y scale factor ({@code 1} = unchanged)
     * @param body   drawing callback drawing at absolute coordinates
     */
    default void transform(float pivotX, float pivotY, float degrees,
            float scaleX, float scaleY, Runnable body) {
        body.run();
    }
}
