package ink.tuanzi.neomacros.port.model;

/**
 * Point-in-time copy of the game window metrics.
 *
 * @param width        framebuffer width in pixels
 * @param height       framebuffer height in pixels
 * @param scaledWidth  GUI-scaled width
 * @param scaledHeight GUI-scaled height
 */
public record WindowData(int width, int height, int scaledWidth, int scaledHeight) {
}
