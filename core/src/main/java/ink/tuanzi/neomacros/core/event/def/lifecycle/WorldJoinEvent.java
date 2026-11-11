package ink.tuanzi.neomacros.core.event.def.lifecycle;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Raised when the player joins a world.
 *
 * @zh 玩家进入世界时触发。
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "world:join", direction = ApiEvent.Direction.LOCAL)
public record WorldJoinEvent(String address) {
}
