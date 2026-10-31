package ink.tuanzi.neomacros.core.event.def.tick;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the per-client-tick event; high-traffic, so wildcard dispatch
 * skips it by default (see the wildcard ignore list).
 *
 * @zh 每客户端 tick 事件载荷；高流量事件，默认不进通配分发。
 * @zh-param count 本次会话的累计 tick 计数
 */
@ApiSince("0.1.0")
@ApiEvent(name = "tick")
public record TickEvent(long count) {
}
