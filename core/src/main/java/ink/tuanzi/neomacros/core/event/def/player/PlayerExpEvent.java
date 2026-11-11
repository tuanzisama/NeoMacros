package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the experience change event.
 *
 * @zh 经验值变化事件载荷。
 * @zh-param level 当前经验等级
 * @zh-param progress 当前等级进度（0.0-1.0）
 * @zh-param change 经验值增量
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:exp", direction = ApiEvent.Direction.LOCAL)
public record PlayerExpEvent(int level, float progress, int change) {
}
