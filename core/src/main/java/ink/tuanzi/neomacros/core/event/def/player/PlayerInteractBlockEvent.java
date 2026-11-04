package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.core.api.types.Block;

/**
 * Payload of the block use (right-click) event; synchronous subscribers may
 * cancel the interaction.
 *
 * @zh 玩家对方块使用物品事件载荷；同步订阅可取消交互。
 * @zh-param block 被交互方块的句柄
 * @zh-param face 交互面方向名（如 north）
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:interactBlock", cancellable = true)
public record PlayerInteractBlockEvent(Block block, String face) {
}
