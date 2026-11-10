package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * One action-bar overlay message about to reach the HUD.
 *
 * @zh 即将上屏的动作栏消息。
 * @zh-param text 显示的文本
 * @zh-param source 来源：server（服务器动作栏包）或 local（客户端本地 overlay 提示）
 */
public record ActionbarDisplayData(ChatComponent text, String source) {

    /** The {@code source} value of the server action-bar packet path. */
    public static final String SERVER = "server";

    /** The {@code source} value of locally produced overlay messages. */
    public static final String LOCAL = "local";

    public ActionbarDisplayData {
        Objects.requireNonNull(text, "text cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
    }
}
