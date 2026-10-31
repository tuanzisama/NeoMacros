package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the status effect change event; fires when the local player
 * gains or loses a status effect.
 *
 * @zh 玩家状态效果变更时触发。
 * @zh-param effectId 效果标识（如 minecraft:speed）
 * @zh-param added true=新增效果，false=移除效果
 * @zh-param amplifier 效果等级
 */
@ApiSince("0.1.0")
@ApiEvent(name = "player:effect")
public record PlayerEffectEvent(String effectId, boolean added, int amplifier) {
}
