package ink.tuanzi.neomacros.port.model;

/**
 * Neutral 2D drawing interface for HUD layers: text, rectangles,
 * outlines, lines, sprites, item icons, and the element transform scope.
 * The adapter maps these calls to the version-specific rendering API;
 * scripts access it through {@code neo.hud}.
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
     * Draws a texture region with a tint: the source rect starts at
     * {@code (u, v)} texels, spans {@code regionWidth} by {@code regionHeight}
     * texels normalized by the whole texture's
     * {@code textureWidth}/{@code textureHeight}, and lands in the
     * {@code width} by {@code height} destination rectangle tinted with the
     * ARGB {@code color} (G-001 §4.1 image elements — the
     * {@code Draw2DImage} paint face).
     *
     * <p>Defaulted so adapters written before the tint/region face keep
     * compiling and simply draw the plain sprite; the Fabric adapter and
     * the recording test contexts override it to carry the tint and the
     * source rect.</p>
     *
     * @param x           destination x in screen pixels
     * @param y           destination y in screen pixels
     * @param width       destination width in screen pixels
     * @param height      destination height in screen pixels
     * @param textureId   {@code namespace:path} texture identifier
     * @param color       ARGB tint applied to the sampled texels
     * @param u           source x offset in texels
     * @param v           source y offset in texels
     * @param regionWidth source width in texels ({@code 0} = destination width)
     * @param regionHeight source height in texels ({@code 0} = destination height)
     * @param textureWidth whole texture width used to normalize {@code u}/{@code regionWidth}
     * @param textureHeight whole texture height used to normalize {@code v}/{@code regionHeight}
     */
    default void sprite(int x, int y, int width, int height, String textureId,
            int color, int u, int v, int regionWidth, int regionHeight,
            int textureWidth, int textureHeight) {
        sprite(x, y, width, height, textureId);
    }

    /**
     * Draws one item stack icon in a 16-by-16 GUI-pixel box at
     * {@code (x, y)} — the {@code Draw2DItem} paint face (G-001 §5.2).
     * A null or empty stack draws nothing.
     *
     * <p>Defaulted so adapters written before the item face keep compiling
     * and simply draw nothing; the Fabric adapter overrides it with the
     * version-specific item call, and the recording test contexts override
     * it to keep the {@code item} primitive observable to assertions.</p>
     *
     * @param x           box x in screen pixels
     * @param y           box y in screen pixels
     * @param stack       neutral stack snapshot to draw
     * @param overlay     whether the game decorations (durability bar,
     *                    cooldown, count or label) are drawn
     * @param overlayText label replacing the stack count when the overlay
     *                    is on ({@code null} draws the stack count, an
     *                    empty string draws no label)
     */
    default void item(int x, int y, ink.tuanzi.neomacros.port.model.ItemStackData stack,
            boolean overlay, String overlayText) {
        // Adapters before the item face draw nothing.
    }

    /**
     * Draws a straight line between two points with an ARGB color and a
     * stroke thickness in screen pixels; non-positive thickness draws
     * nothing.
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
