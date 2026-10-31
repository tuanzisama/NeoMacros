package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.BlockInteraction;

/**
 * Payload of the block use (right-click) event; synchronous subscribers may
 * cancel the interaction.
 *
 * @zh 玩家对方块使用物品事件载荷；同步订阅可取消交互。
 * @zh-param block 被交互方块的标识与位置
 */
@ApiSince("0.1.0")
@ApiEvent(name = "player:interactBlock", cancellable = true)
public record PlayerInteractBlockEvent(BlockInteraction block) {
}
