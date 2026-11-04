package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import ink.tuanzi.neomacros.port.model.RayOptions;

import java.util.Optional;

/**
 * Live raycast result: the first hit along the ray. {@code block} and
 * {@code entity} are mutually exclusive; both are live handles.
 */
@ApiClass(name = "RayHit", kind = ApiClassKind.HANDLE)
public final class RayHit implements ScriptHandle {

    private final Vec3 origin;
    private final Vec3 direction;
    private final RayOptions options;

    /** Creates the handle (facade entry). */
    public RayHit(Vec3 origin, Vec3 direction, RayOptions options) {
        this.origin = origin;
        this.direction = direction;
        this.options = options;
    }

    /** The hit block handle, or undefined on entity hits / misses. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public Block getBlock() {
        return snapshot()
                .filter(hit -> hit.block() != null)
                .map(hit -> new Block(new BlockPos(
                        hit.block().pos().x(), hit.block().pos().y(), hit.block().pos().z())))
                .orElse(null);
    }

    /** The hit entity handle, or undefined on block hits / misses. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public ink.tuanzi.neomacros.core.api.entity.EntityApi.Entity getEntity() {
        return snapshot()
                .filter(hit -> hit.entity() != null)
                .map(hit -> new ink.tuanzi.neomacros.core.api.entity.EntityApi.Entity(
                        hit.entity().uuid()))
                .orElse(null);
    }

    /** The exact hit position. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public Vec3 getPos() {
        return snapshot()
                .map(hit -> new Vec3(hit.pos().x(), hit.pos().y(), hit.pos().z()))
                .orElse(origin.add(direction.normalize().mul(options.maxDist())));
    }

    /** The distance from the origin to the hit (max distance on a miss). */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public double getDistance() {
        return snapshot().map(ink.tuanzi.neomacros.port.model.RayHit::distance)
                .orElse(options.maxDist());
    }

    /** Whether the ray hit anything at all. */
    @ApiSince("0.1.0")
    public boolean isHit() {
        return snapshot()
                .map(hit -> hit.block() != null || hit.entity() != null)
                .orElse(false);
    }

    /** Re-runs the raycast for the live members. */
    private Optional<ink.tuanzi.neomacros.port.model.RayHit> snapshot() {
        var from = new ink.tuanzi.neomacros.port.model.Pos3d(
                origin.getX(), origin.getY(), origin.getZ());
        var dir = new ink.tuanzi.neomacros.port.model.Pos3d(
                direction.getX(), direction.getY(), direction.getZ());
        return ScriptApiEnvironment.adapter().world().raycast(from, dir, options);
    }
}
