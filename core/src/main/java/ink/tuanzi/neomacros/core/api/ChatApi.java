package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * The {@code neo.chat} facade: chat output services. Sending targets the
 * connected server; log and actionbar render locally only.
 */
@ApiModule(name = "chat", category = "界面")
public final class ChatApi {

    /**
     * Sends the message to the connected server as if typed by the player.
     *
     * @zh 以玩家身份向服务器发送聊天消息。
     */
    @ApiSince("0.1.0")
    public void say(String message) {
        ScriptApiEnvironment.adapter().chat().say(message);
    }

    /**
     * Prints the message to the local chat HUD only.
     *
     * @zh 仅在本地聊天栏打印消息。
     */
    @ApiSince("0.1.0")
    public void log(String message) {
        ScriptApiEnvironment.adapter().chat().log(message);
    }

    /**
     * Renders the message on the action bar above the hotbar.
     *
     * @zh 在快捷栏上方的动作条显示消息。
     */
    @ApiSince("0.1.0")
    public void actionbar(String message) {
        ScriptApiEnvironment.adapter().chat().actionbar(message);
    }
}
