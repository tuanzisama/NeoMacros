package ink.tuanzi.neomacros.port.model;

/**
 * Immutable result of one raycast: the first hit along the ray. Exactly one
 * of {@code block}/{@code entity} is non-null.
 *
 * @param block    the hit block (id + position + face), or null when the ray hit an entity
 * @param entity   the hit entity snapshot, or null when the ray hit a block
 * @param pos      the exact hit position in world coordinates
 * @param distance distance from the ray origin to the hit position
 */
public record RayHit(BlockInteraction block, /* nullable on entity hits */ EntitySnapshot entity,
        Pos3d pos, double distance) {
}
