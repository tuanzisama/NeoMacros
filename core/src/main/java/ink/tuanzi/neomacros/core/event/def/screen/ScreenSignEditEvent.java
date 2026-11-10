package ink.tuanzi.neomacros.core.event.def.screen;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.List;

/**
 * Payload of the sign-edit open event; synchronous subscribers may skip
 * opening the editor ({@code true} or {@code {closeScreen: true}}, the
 * possibly replaced text is submitted directly) or return
 * {@code {lines: [...]}} to replace the four lines.
 *
 * @zh 告示牌编辑打开事件载荷；同步订阅返回 truthy（或 {@code {closeScreen:true}}）跳过编辑界面直接提交，或返回 {@code {lines:[...]}} 替换四行文本。
 * @zh-param x 方块 X 坐标
 * @zh-param y 方块 Y 坐标
 * @zh-param z 方块 Z 坐标
 * @zh-param front 是否正面
 * @zh-param lines 当前四行文本
 */
@ApiSince("0.4.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "screen:signEdit", cancellable = true)
public record ScreenSignEditEvent(int x, int y, int z, boolean front, List<String> lines) {
}
