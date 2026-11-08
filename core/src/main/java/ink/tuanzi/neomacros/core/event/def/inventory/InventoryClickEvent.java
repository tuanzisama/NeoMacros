package ink.tuanzi.neomacros.core.event.def.inventory;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the inventory click event, fired after a container slot click
 * is processed (manual clicks and script-issued clicks share the path).
 *
 * @zh 物品栏点击事件载荷（容器槽位点击处理完成后触发，手动与脚本点击同路）。
 * @zh-param containerId 菜单 id
 * @zh-param kind 容器类别
 * @zh-param slot 点击槽位号
 * @zh-param button 鼠标键（0 左键 1 右键）
 * @zh-param shift 是否快速移动
 */
@ApiSince("0.3.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "inventory:click")
public record InventoryClickEvent(int containerId, String kind, int slot, int button, boolean shift) {
}
