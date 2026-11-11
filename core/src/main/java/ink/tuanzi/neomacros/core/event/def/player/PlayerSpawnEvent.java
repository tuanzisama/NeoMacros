package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;

/**
 * Payload of the local player spawning into the world event (first spawn of
 * a world session).
 *
 * @zh 本地玩家出生事件载荷（世界会话的首次出生）。
 * @zh-param player 玩家的时点快照
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:spawn", direction = ApiEvent.Direction.LOCAL)
public record PlayerSpawnEvent(EntitySnapshot player) {
}
