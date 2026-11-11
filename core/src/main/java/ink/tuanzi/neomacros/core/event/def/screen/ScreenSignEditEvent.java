package ink.tuanzi.neomacros.core.event.def.screen;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.List;

/**
 * Payload of the sign-edit open event; synchronous subscribers may cancel the
 * editor, rewrite the four lines, or set {@code closeScreen} to submit the
 * (possibly replaced) text directly instead of opening the editor.
 *
 * @zh 告示牌编辑打开事件载荷；同步订阅可取消编辑界面、改写四行文本，或置 {@code closeScreen} 跳过编辑器直接提交（可能是替换后的）文本。
 * @zh-param x 方块 X 坐标
 * @zh-param y 方块 Y 坐标
 * @zh-param z 方块 Z 坐标
 * @zh-param front 是否正面
 * @zh-param lines 当前四行文本
 * @zh-param closeScreen 是否跳过编辑器直接提交（仅同步通道提交时有意义，异步载荷恒为 false）
 */
@ApiSince("0.4.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "screen:signEdit", direction = ApiEvent.Direction.S2C, cancellable = true, mutable = { "lines", "closeScreen" })
public record ScreenSignEditEvent(int x, int y, int z, boolean front, List<String> lines, boolean closeScreen) {
}
