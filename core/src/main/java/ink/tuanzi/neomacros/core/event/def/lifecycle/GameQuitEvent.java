package ink.tuanzi.neomacros.core.event.def.lifecycle;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Raised when the game client is shutting down.
 *
 * @zh 游戏客户端退出时触发。
 */
@ApiSince("0.1.0")
@ApiEvent(name = "game:quit")
public record GameQuitEvent() {
}
