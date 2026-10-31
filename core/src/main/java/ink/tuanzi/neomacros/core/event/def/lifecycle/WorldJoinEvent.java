package ink.tuanzi.neomacros.core.event.def.lifecycle;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Raised when the player joins a world.
 *
 * @zh 玩家进入世界时触发。
 */
@ApiSince("0.1.0")
@ApiEvent(name = "world:join")
public record WorldJoinEvent() {
}
