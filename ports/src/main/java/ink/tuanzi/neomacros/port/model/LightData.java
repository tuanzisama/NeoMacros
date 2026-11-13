package ink.tuanzi.neomacros.port.model;

/**
 * Point-in-time light levels at one block position: the sky-light layer and
 * the block-emitted layer, each independent of the other.
 *
 * @param sky   sky-light level, 0 to 15
 * @param block block-light level, 0 to 15
 */
public record LightData(int sky, int block) {
}
