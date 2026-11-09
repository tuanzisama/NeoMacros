package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.core.api.types.TextComponent;
import ink.tuanzi.neomacros.port.model.TextSegmentData;

import java.util.List;

/**
 * The {@code neo.chat} module: chat output services. Sending targets the
 * connected server; log and actionbar render locally only. Text-taking
 * members accept a {@code TextComponent | string}: components render with
 * their full styling where the surface supports it, strings render plain.
 *
 * @zh {@code neo.chat}：聊天输出服务。say 发往所连服务器；log 与 actionbar 仅本地渲染。文本类成员接受 {@code TextComponent | string}：组件在其表面支持处按完整样式渲染，字符串按纯文本渲染。
 */
@ApiModule(name = "chat", category = "界面")
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class ChatApi {

    /**
     * Sends the message to the connected server as if typed by the player.
     * A component rides its plain form (the server chat is text-only).
     *
     * @zh 以玩家身份向服务器发送聊天消息。组件按纯文本形态发送（服务器聊天是纯文本）。
     * @zh-param message 消息文本或组件
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void say(@ApiType("TextComponent | string") Object message) {
        TextComponent component = componentOf(message, "say");
        ScriptApiEnvironment.adapter().chat().say(
                component != null ? component.getPlain() : requireText(message, "say"));
    }

    /**
     * Prints the message to the local chat HUD only; components render with
     * full color/decoration/hover/click.
     *
     * @zh 仅在本地聊天栏打印消息；组件按完整颜色/装饰/悬停/点击渲染。
     * @zh-param message 消息文本或组件
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void log(@ApiType("TextComponent | string") Object message) {
        TextComponent component = componentOf(message, "log");
        if (component != null) {
            ScriptApiEnvironment.adapter().chat().renderRichText(component.flatten(), false);
            return;
        }
        ScriptApiEnvironment.adapter().chat().log(requireText(message, "log"));
    }

    /**
     * Renders the message on the action bar above the hotbar; components
     * render with full styling.
     *
     * @zh 在快捷栏上方的动作条显示消息；组件按完整样式渲染。
     * @zh-param message 消息文本或组件
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void actionbar(@ApiType("TextComponent | string") Object message) {
        TextComponent component = componentOf(message, "actionbar");
        if (component != null) {
            ScriptApiEnvironment.adapter().chat().renderRichText(component.flatten(), true);
            return;
        }
        ScriptApiEnvironment.adapter().chat().actionbar(requireText(message, "actionbar"));
    }

    /**
     * Shows a title (and optional subtitle) overlay; null lines are skipped
     * and non-null durations override the vanilla fade timings in ticks.
     * Lines may be components or strings.
     *
     * @zh 显示标题（与可选副标题）覆盖层；null 行跳过，非 null 时长（tick）覆盖原版渐变时间。行可为组件或字符串。
     * @zh-param title 主标题（可省）
     * @zh-param subtitle 副标题（可省）
     * @zh-param fadeIn 淡入 tick（可省）
     * @zh-param stay 停留 tick（可省）
     * @zh-param fadeOut 淡出 tick（可省）
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void title(@ApiOptional @ApiType("TextComponent | string") Object title,
            @ApiOptional @ApiType("TextComponent | string") Object subtitle,
            @ApiOptional Integer fadeIn, @ApiOptional Integer stay,
            @ApiOptional Integer fadeOut) {
        if (title instanceof TextComponent || subtitle instanceof TextComponent) {
            ScriptApiEnvironment.adapter().chat().titleRich(
                    segmentsOf(title), segmentsOf(subtitle), fadeIn, stay, fadeOut);
            return;
        }
        ScriptApiEnvironment.adapter().chat().title(
                title == null ? null : String.valueOf(title),
                subtitle == null ? null : String.valueOf(subtitle),
                fadeIn, stay, fadeOut);
    }

    /**
     * Shows a system toast notification in the screen corner; lines may be
     * components or strings.
     *
     * @zh 在屏幕角落显示系统 toast 通知；行可为组件或字符串。
     * @zh-param title 标题
     * @zh-param message 正文
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void toast(@ApiOptional @ApiType("TextComponent | string") Object title,
            @ApiOptional @ApiType("TextComponent | string") Object message) {
        if (title instanceof TextComponent || message instanceof TextComponent) {
            ScriptApiEnvironment.adapter().chat().toastRich(
                    segmentsOf(title), segmentsOf(message));
            return;
        }
        ScriptApiEnvironment.adapter().chat().toast(
                title == null ? null : String.valueOf(title),
                message == null ? null : String.valueOf(message));
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

    /** The union argument as a built value, or null when absent/not one. */
    private static TextComponent componentOf(Object message, String member) {
        if (message == null) {
            throw new IllegalArgumentException(member + ": message is required");
        }
        return message instanceof TextComponent component ? component : null;
    }

    /** The union argument as a plain string, rejecting absent values. */
    private static String requireText(Object message, String member) {
        if (message == null) {
            throw new IllegalArgumentException(member + ": message is required");
        }
        return String.valueOf(message);
    }

    /** One union argument as a render line: a component flattens to its
     * styled segments, a string becomes a single literal line, null stays
     * null (skipped line). */
    private static List<TextSegmentData> segmentsOf(Object message) {
        if (message == null) {
            return null;
        }
        if (message instanceof TextComponent component) {
            return component.flatten();
        }
        return List.of(new TextSegmentData(String.valueOf(message), null, null,
                null, null, null, null, null, null, null, null));
    }
}
