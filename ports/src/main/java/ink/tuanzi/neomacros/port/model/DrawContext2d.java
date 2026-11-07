package ink.tuanzi.neomacros.port.model;

/**
 * Neutral 2D drawing interface for HUD layers: text, rectangles, and
 * outlines. The adapter maps these calls to the version-specific rendering
 * API; scripts access it through {@code neo.hud}.
 *
 * <p>All coordinates are screen-space pixels from the top-left corner.</p>
 */
public interface DrawContext2d {

    /** Draws a text string at (x, y) in white. */
    void text(int x, int y, String text);

    /** Draws a text string at (x, y) with an ARGB color. */
    void text(int x, int y, String text, int color);

    /** Fills a rectangle (x, y, width, height) with an ARGB color. */
    void rect(int x, int y, int width, int height, int color);

    /** Draws a 1-pixel rectangle outline. */
    void outline(int x, int y, int width, int height, int color);

    /**
     * Draws a texture scaled to the rectangle, identified as
     * {@code namespace:path}. Invalid identifiers draw nothing.
     */
    void sprite(int x, int y, int width, int height, String textureId);
}
