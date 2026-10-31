package ink.tuanzi.neomacros.port.model;

/**
 * Immutable position in world coordinate space.
 *
 * <p>Neutral value object carried across port signatures and event payloads;
 * never a live game reference.</p>
 */
public record Pos3d(double x, double y, double z) {
}
