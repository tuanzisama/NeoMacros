package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;

/**
 * Payload of the local player respawn event.
 *
 * @zh 本地玩家重生事件载荷。
 * @zh-param player 玩家的时点快照
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:respawn", direction = ApiEvent.Direction.LOCAL)
public record PlayerRespawnEvent(EntitySnapshot player) {
}
