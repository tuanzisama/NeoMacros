package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the local player health change event; {@code change} is the
 * signed difference against the previous sample (positive for a gain,
 * negative for a loss).
 *
 * @zh 本地玩家生命值变化事件载荷；{@code change} 为相对上一次采样的带符号差值（增加为正，减少为负）。
 * @zh-param health 变化后的生命值
 * @zh-param change 带符号的生命值差量
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:health", direction = ApiEvent.Direction.LOCAL)
public record PlayerHealthEvent(double health,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") double change) {
}
