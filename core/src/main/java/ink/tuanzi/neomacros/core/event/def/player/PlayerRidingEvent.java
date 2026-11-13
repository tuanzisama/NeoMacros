package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;

/**
 * Payload of the riding change event; the vehicle snapshot is
 * {@code null} when the player dismounted. {@code previousVehicle} carries
 * the mirror image: the vehicle left behind on a dismount, {@code null} when
 * the player mounted from foot.
 *
 * @zh 乘坐状态变化事件载荷；vehicle 为 null 表示玩家已下车。{@code previousVehicle} 为镜像字段：下车时是离开的原载具，上车时为 null。
 * @zh-param vehicle 所乘坐实体的快照；下车时为 null
 * @zh-param previousVehicle 上一个载具的快照；从地面登上时为 null
 */
@ApiSince("0.2.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:riding", direction = ApiEvent.Direction.LOCAL)
public record PlayerRidingEvent(EntitySnapshot vehicle,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") EntitySnapshot previousVehicle) {
}
