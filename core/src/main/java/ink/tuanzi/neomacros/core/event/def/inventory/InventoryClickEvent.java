package ink.tuanzi.neomacros.core.event.def.inventory;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Payload of the container slot click event, raised before the click is
 * handed to the game mode so synchronous subscribers can cancel it: a
 * cancelled click never sends its packet and no slot changes. Manual clicks
 * and script-issued clicks share the path, so a script's own
 * {@code inventory.click} call is cancellable too.
 *
 * @zh 容器槽位点击事件载荷（在点击交给游戏模式之前触发，同步订阅可取消：取消后点击包不发送、槽位不变）。手动与脚本点击同路，脚本自身的 {@code inventory.click} 同样可被取消。
 * @zh-param containerId 菜单 id
 * @zh-param kind 容器类别
 * @zh-param slot 点击槽位号（点击窗口外为 -999）
 * @zh-param button 鼠标键（0 左键、1 右键、2 中键）
 * @zh-param mode 点击模式（pickup/quick_move/throw/swap/clone/quick_craft/pickup_all）
 * @zh-param shift 是否快速移动（mode 为 quick_move 时同真）
 */
@ApiSince("0.6.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "inventory:click", direction = ApiEvent.Direction.C2S,
        cancellable = true)
public record InventoryClickEvent(int containerId, String kind, int slot, int button,
        String mode, boolean shift) {
}
