package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.core.api.types.Block;

/**
 * Payload of the block attack (start breaking) event; synchronous
 * subscribers may cancel the break.
 *
 * @zh 玩家开始破坏方块事件载荷；同步订阅可取消破坏。
 * @zh-param block 被攻击方块的句柄
 * @zh-param face 交互面方向名（如 north）
 */
@ApiSince("0.1.0")
@ApiEvent(name = "player:attackBlock", cancellable = true)
public record PlayerAttackBlockEvent(Block block, String face) {
}
