package ink.tuanzi.neomacros.port.model;

/**
 * Options for one raycast: reach and what the ray may hit.
 *
 * @param maxDist  maximum ray length in blocks
 * @param entities whether the ray tests entities
 * @param blocks   whether the ray tests blocks
 * @param fluids   whether fluid counts as a block hit
 */
public record RayOptions(double maxDist, boolean entities, boolean blocks, boolean fluids) {

    /** The defaults the facade applies when the script passes no options. */
    public static RayOptions defaults() {
        return new RayOptions(32, true, true, false);
    }
}
