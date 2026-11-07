package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;

/**
 * Payload of the riding change event; the vehicle snapshot is
 * {@code null} when the player dismounted.
 *
 * @zh 乘坐状态变化事件载荷；vehicle 为 null 表示玩家已下车。
 * @zh-param vehicle 所乘坐实体的快照；下车时为 null
 */
@ApiSince("0.2.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:riding")
public record PlayerRidingEvent(EntitySnapshot vehicle) {
}
