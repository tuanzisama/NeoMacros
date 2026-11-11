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
 * {@code minecraft:air}.
 *
 * @zh 盔甲变化事件载荷；四槽按脚/腿/胸/头排列，空槽读作 {@code minecraft:air}。
 * @zh-param armor 四个盔甲槽的物品堆
 */
@ApiSince("0.2.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "player:armor", direction = ApiEvent.Direction.LOCAL)
public record PlayerArmorEvent(List<ItemStackData> armor) {
}
