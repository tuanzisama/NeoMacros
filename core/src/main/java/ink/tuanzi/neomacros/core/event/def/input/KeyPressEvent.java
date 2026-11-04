package ink.tuanzi.neomacros.core.event.def.input;

import ink.tuanzi.neomacros.doc.api.ApiEvent;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.List;

/**
 * Payload of the raw keyboard event; synchronous subscribers may cancel the
 * key press before the game processes it.
 *
 * @zh 原始键盘事件载荷；同步订阅可在游戏处理前取消按键。
 * @zh-param key 按键名（如 key.keyboard.r）
 * @zh-param code GLFW 键码
 * @zh-param action 边沿方向：press 或 release
 * @zh-param modifiers 按下的修饰键列表
 */
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
@ApiEvent(name = "key:press", cancellable = true)
public record KeyPressEvent(String key, int code, String action, List<String> modifiers) {
}
