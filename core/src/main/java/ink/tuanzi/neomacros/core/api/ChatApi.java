package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * The {@code neo.chat} facade: chat output services. Sending targets the
 * connected server; log and actionbar render locally only.
 *
 * @zh {@code neo.chat} 门面：聊天输出服务。say 发往所连服务器；log 与 actionbar 仅本地渲染。
 */
@ApiModule(name = "chat", category = "界面")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class ChatApi {

    /**
     * Sends the message to the connected server as if typed by the player.
     *
     * @zh 以玩家身份向服务器发送聊天消息。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void say(String message) {
        ScriptApiEnvironment.adapter().chat().say(message);
    }

    /**
     * Prints the message to the local chat HUD only.
     *
     * @zh 仅在本地聊天栏打印消息。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void log(String message) {
        ScriptApiEnvironment.adapter().chat().log(message);
    }

    /**
     * Renders the message on the action bar above the hotbar.
     *
     * @zh 在快捷栏上方的动作条显示消息。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void actionbar(String message) {
        ScriptApiEnvironment.adapter().chat().actionbar(message);
    }

    /**
     * Shows a title (and optional subtitle) overlay; null lines are skipped
     * and non-null durations override the vanilla fade timings in ticks.
     *
     * @zh 显示标题（与可选副标题）覆盖层；null 行跳过，非 null 时长（tick）覆盖原版渐变时间。
     * @zh-param title 主标题（可省）
     * @zh-param subtitle 副标题（可省）
     * @zh-param fadeIn 淡入 tick（可省）
     * @zh-param stay 停留 tick（可省）
     * @zh-param fadeOut 淡出 tick（可省）
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void title(String title, String subtitle, Integer fadeIn, Integer stay,
            Integer fadeOut) {
        ScriptApiEnvironment.adapter().chat().title(title, subtitle, fadeIn, stay, fadeOut);
    }

    /**
     * Shows a system toast notification in the screen corner.
     *
     * @zh 在屏幕角落显示系统 toast 通知。
     * @zh-param title 标题
     * @zh-param message 正文
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void toast(String title, String message) {
        ScriptApiEnvironment.adapter().chat().toast(title, message);
    }

    /**
     * The rendered pixel width of the text in the chat font.
     *
     * @zh 文本在聊天字体下的渲染像素宽度。
     * @zh-param text 文本
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getWidth(String text) {
        return ScriptApiEnvironment.adapter().chat().textWidth(text);
    }
}
