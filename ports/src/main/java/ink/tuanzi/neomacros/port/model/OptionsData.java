package ink.tuanzi.neomacros.port.model;

/**
 * Point-in-time copy of a small read-only slice of the game options.
 *
 * @param fov            field of view
 * @param gamma          brightness
 * @param guiScale       GUI scale factor
 * @param renderDistance render distance in chunks
 */
public record OptionsData(int fov, double gamma, int guiScale, int renderDistance) {
}
