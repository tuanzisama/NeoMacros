package ink.tuanzi.neomacros.core.event.def.player;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ItemStackData;

import java.util.List;

/**
 * Payload of the armor change event; the four slots ride in
 * feet, legs, chest, head order and empty slots read as
 * {@code minecraft:air}. One event is published per changed slot, so
 * {@code slot}, {@code item} and {@code oldItem} name the slot that moved
 * while {@code armor} always carries the full current set. A durability-only
 * change stays on {@code item:damage} and does not fire this event.
 *
 * @zh 盔甲变化事件载荷；四槽按脚/腿/胸/头排列，空槽读作 {@code minecraft:air}。每个变化槽位各发布一次事件，因此 {@code slot}、{@code item}、{@code oldItem} 指向发生变化的槽位，而 {@code armor} 始终是当前全量。仅耐久变化走 {@code item:damage}，不触发本事件。
 * @zh-param armor 四个盔甲槽的物品堆
 * @zh-param slot 变化的盔甲槽位名（feet/legs/chest/head）
 * @zh-param item 变化后的该槽物品堆
 * @zh-param oldItem 变化前的该槽物品堆；无前一采样时为 undefined
 */
@ApiSince("0.2.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:armor", direction = ApiEvent.Direction.LOCAL)
public record PlayerArmorEvent(List<ItemStackData> armor,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") String slot,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") ItemStackData item,
        @ApiSince("0.5.0") @ApiMC("*") @ApiLoader("*") ItemStackData oldItem) {
}
