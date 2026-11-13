package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ItemStackData;

/**
 * Payload of the held item change event; the main hand and the off hand are
 * tracked separately and {@code offHand} names which one moved. The stacks
 * are the point-in-time snapshots of the two hands around the change, and a
 * stack that was empty reads as {@code null} (undefined to scripts).
 *
 * @zh 手持物品变化事件载荷；主手与副手分别跟踪，{@code offHand} 标明发生变化的是哪只手。物品堆为变化前后的时点快照，空手读作 null（脚本侧为 undefined）。
 * @zh-param previous 变化前的该手物品堆（可为 null）
 * @zh-param current 变化后的该手物品堆（可为 null）
 * @zh-param offHand true=副手变化，false=主手变化
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:heldItem", direction = ApiEvent.Direction.LOCAL)
public record PlayerHeldItemEvent(ItemStackData previous, ItemStackData current,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") boolean offHand) {
}
