package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import ink.tuanzi.neomacros.port.model.RayOptions;

import java.util.Optional;

/**
 * Live raycast result: the first hit along the ray. {@code block} and
 * {@code entity} are mutually exclusive; both are live handles.
 * @zh 活射线检测结果：沿射线的首个命中。{@code block} 与 {@code entity} 互斥，均为活句柄。
 */
@ApiClass(name = "RayHit", kind = ApiClassKind.HANDLE)
public final class RayHit implements ScriptHandle {

    private final Pos3D origin;
    private final Pos3D direction;
    private final RayOptions options;

    /** Creates the handle (facade entry).
     * @zh 构造句柄（门面入口）。 */
    public RayHit(Pos3D origin, Pos3D direction, RayOptions options) {
        this.origin = origin;
        this.direction = direction;
        this.options = options;
    }

    /** The hit block handle, or undefined on entity hits / misses.
     * @zh 命中方块句柄；命中实体或未命中时为 undefined。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Block getBlock() {
        return snapshot()
                .filter(hit -> hit.block() != null)
                .map(hit -> new Block(new BlockPos(
                        hit.block().pos().x(), hit.block().pos().y(), hit.block().pos().z())))
                .orElse(null);
    }

    /** The hit entity handle, or undefined on block hits / misses.
     * @zh 命中实体句柄；命中方块或未命中时为 undefined。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public ink.tuanzi.neomacros.core.api.entity.EntityApi.Entity getEntity() {
        return snapshot()
                .filter(hit -> hit.entity() != null)
                .map(hit -> new ink.tuanzi.neomacros.core.api.entity.EntityApi.Entity(
                        hit.entity().uuid()))
                .orElse(null);
    }

    /** The exact hit position.
     * @zh 精确命中位置。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Pos3D getPos() {
        return snapshot()
                .map(hit -> new Pos3D(hit.pos().x(), hit.pos().y(), hit.pos().z()))
                .orElse(origin.add(direction.normalize().scale(options.maxDist())));
    }

    /** The distance from the origin to the hit (max distance on a miss).
     * @zh 原点到命中点的距离（未命中时为最大距离）。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public double getDistance() {
        return snapshot().map(ink.tuanzi.neomacros.port.model.RayHit::distance)
                .orElse(options.maxDist());
    }

    /** Whether the ray hit anything at all.
     * @zh 射线是否命中了任何目标。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
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
