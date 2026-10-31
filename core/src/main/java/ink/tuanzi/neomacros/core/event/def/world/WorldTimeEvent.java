package ink.tuanzi.neomacros.core.event.def.world;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the world time jump event; fires only for discontinuous changes
 * (time set, day skip), not for the normal one-tick-per-tick advance.
 *
 * @zh 世界时间跳变事件载荷；仅时间被不连续修改（设置时间/跳过夜晚）时触发，
 * 正常每 tick 推进不触发。
 * @zh-param time 跳变后的世界时间
 */
@ApiSince("0.1.0")
@ApiEvent(name = "world:time")
public record WorldTimeEvent(long time) {
}
