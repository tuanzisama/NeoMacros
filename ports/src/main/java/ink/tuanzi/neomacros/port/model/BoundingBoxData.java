package ink.tuanzi.neomacros.port.model;

/**
 * Immutable axis-aligned bounding box in world coordinate space.
 *
 * <p>Neutral value object carried across port signatures and event payloads;
 * never a live game reference.</p>
 */
public record BoundingBoxData(Pos3d min, Pos3d max) {

    public BoundingBoxData {
        if (min == null) {
            min = new Pos3d(0, 0, 0);
        }
        if (max == null) {
            max = new Pos3d(0, 0, 0);
        }
    }
}
