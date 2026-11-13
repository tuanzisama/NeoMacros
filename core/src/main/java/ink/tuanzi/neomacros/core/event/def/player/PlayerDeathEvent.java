package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.core.api.types.BlockPos;
import ink.tuanzi.neomacros.port.model.EntitySnapshot;
import ink.tuanzi.neomacros.port.model.ItemStackData;

import java.util.List;

/**
 * Payload of the local player death event; {@code deathPos} is the block the
 * player occupied when the death edge was observed and {@code inventory} is
 * the point-in-time snapshot of every non-empty inventory stack (an empty
 * inventory reads as an empty list, never {@code null}).
 *
 * @zh 本地玩家死亡事件载荷；{@code deathPos} 为观察到死亡边沿时玩家所处方块坐标，{@code inventory} 为全部非空物品堆的时点快照（空背包为 []，不会是 null）。
 * @zh-param player 玩家的时点快照
 * @zh-param deathPos 死亡时点的方块坐标
 * @zh-param inventory 死亡时点的非空物品堆快照
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:death", direction = ApiEvent.Direction.LOCAL)
public record PlayerDeathEvent(EntitySnapshot player,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") BlockPos deathPos,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") List<ItemStackData> inventory) {
}
