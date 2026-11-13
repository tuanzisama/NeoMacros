package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.EffectSnapshot;

/**
 * Payload of the status effect change event; fires when the local player
 * gains or loses a status effect. {@code previousEffect} is the effect as
 * it stood before this update: {@code null} on a gain, the replaced effect
 * on an amplifier change, and the removed effect on a removal (where
 * {@code added} is false and the new state is absent).
 *
 * @zh 玩家状态效果变更时触发。{@code previousEffect} 为本次更新前的效果快照：新增时为 null，等级变化时为被替换的旧效果，移除时为被移除的效果（此时 {@code added} 为 false，新状态缺席）。
 * @zh-param effectId 效果标识（如 minecraft:speed）
 * @zh-param added true=新增效果，false=移除效果
 * @zh-param amplifier 效果等级
 * @zh-param previousEffect 更新前的效果快照；新增时为 null
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:effect", direction = ApiEvent.Direction.LOCAL)
public record PlayerEffectEvent(String effectId, boolean added, int amplifier,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") EffectSnapshot previousEffect) {
}
