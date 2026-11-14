package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.model.WindowData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Live handle for one control on an open {@link BuiltScreen} — the shared
 * face of the widget family ({@code ClickableWidgetHelper}
 * account, G-036B). Binds one control id to its owning session: reads
 * answer the session cache, writes go through
 * {@link BuiltScreen#setControl} onto the live widget. The generic
 * parameter is the concrete handle type, so fluent calls chain back to
 * the subclass ({@code gui.slider(id).setPos(x, y).setValue(v)}).
 *
 * <p>Tooltips live on the session (shared across handle queries) and are
 * mirrored into the adapter so rebuilds keep them; {@code click} is a
 * transient press+release at the widget's top-left corner. Layout reads
 * ({@code scaled*}) answer the widget box, {@code parent*} the host
 * window's scaled size — the full-window screen extent. After the session
 * closes, reads answer neutral defaults and every mutation throws through
 * {@code setControl}.</p>
 *
 * @zh 打开的 {@link BuiltScreen} 上一个控件的活句柄——控件族共享面（{@code ClickableWidgetHelper} 账，G-036B）。把控件 id 绑定到会话：读走会话缓存、写经 {@link BuiltScreen#setControl} 落到活控件。泛型参数是具体句柄类型，fluent 调用链回子类。tooltip 存会话（跨句柄查询共享）并镜像进 adapter 以在重建后保留；{@code click} 是在控件左上角的瞬态按下+松开。布局读（scaled*）答控件盒，parent* 答宿主窗口缩放尺寸（全窗屏范围）。会话关闭后读回中立默认值、写一律 throw。
 */
@ApiClass(name = "Widget", kind = ApiClassKind.HANDLE,
        wraps = "net.minecraft.client.gui.components.AbstractWidget")
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public class Widget<B extends Widget<B>> implements ScriptHandle {

    final BuiltScreen screen;
    final String id;

    Widget(BuiltScreen screen, String id) {
        this.screen = screen;
        this.id = id;
    }

    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

    /** The control id this handle is bound to.
     * @zh 该句柄绑定的控件 id。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public String getId() {
        return id;
    }

    /** The owning session handle.
     * @zh 所属会话句柄。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BuiltScreen getScreen() {
        return screen;
    }

    /** The live snapshot, or null when closed/unknown. */
    final ControlState state() {
        return screen.control(id);
    }

    /** Applies one whitelisted patch through the session (throws when closed). */
    final void write(String key, Object value) {
        screen.setControl(id, Map.of(key, value));
    }

    // ------------------------------------------------------------------
    // Geometry (ClickableWidgetHelper account, G-036B)
    // ------------------------------------------------------------------

    /** The widget's left edge in screen pixels ({@code getX}).
     * @zh 控件左边缘的屏内像素坐标（{@code getX}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getX() {
        ControlState state = state();
        return state != null ? state.x() : 0;
    }

    /** The widget's top edge in screen pixels ({@code getY}).
     * @zh 控件上边缘的屏内像素坐标（{@code getY}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getY() {
        ControlState state = state();
        return state != null ? state.y() : 0;
    }

    /** The widget width in pixels ({@code getWidth}).
     * @zh 控件宽度（像素，{@code getWidth}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getWidth() {
        ControlState state = state();
        return state != null ? state.width() : 0;
    }

    /** The widget height in pixels ({@code getHeight}).
     * @zh 控件高度（像素，{@code getHeight}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getHeight() {
        ControlState state = state();
        return state != null ? state.height() : 0;
    }

    /** Moves the widget without resizing it ({@code setPos}).
     * @zh 移动控件、不改尺寸（{@code setPos}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B setPos(@ApiType("number") int x, @ApiType("number") int y) {
        screen.setControl(id, Map.of("x", x, "y", y));
        return self();
    }

    /** Sets the width, keeping the height ({@code setWidth}).
     * @zh 设置宽度、保持高度（{@code setWidth}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B setWidth(@ApiType("number") int width) {
        write("width", width);
        return self();
    }

    /** Moves the widget to a new position ({@code moveTo}, the alignment helper).
     * @zh 把控件移到新位置（{@code moveTo}，对齐助手）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B moveTo(@ApiType("number") int x, @ApiType("number") int y) {
        return setPos(x, y);
    }

    /** The widget width alignment reads ({@code getScaledWidth}; no scaling on screens).
     * @zh 对齐读取的控件宽度（{@code getScaledWidth}；屏上无缩放）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getScaledWidth() {
        return getWidth();
    }

    /** The widget height alignment reads ({@code getScaledHeight}).
     * @zh 对齐读取的控件高度（{@code getScaledHeight}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getScaledHeight() {
        return getHeight();
    }

    /** The left edge alignment reads ({@code getScaledLeft}).
     * @zh 对齐读取的左边缘（{@code getScaledLeft}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getScaledLeft() {
        return getX();
    }

    /** The top edge alignment reads ({@code getScaledTop}).
     * @zh 对齐读取的上边缘（{@code getScaledTop}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getScaledTop() {
        return getY();
    }

    /** The host window width for parent-relative alignment ({@code getParentWidth}).
     * @zh 父盒相对对齐用的宿主窗口宽度（{@code getParentWidth}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getParentWidth() {
        return state() != null ? parentWidth() : 0;
    }

    /** The host window height for parent-relative alignment ({@code getParentHeight}).
     * @zh 父盒相对对齐用的宿主窗口高度（{@code getParentHeight}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getParentHeight() {
        return state() != null ? parentHeight() : 0;
    }

    /**
     * The host window's scaled width — the parent-box extent of a
     * full-window script screen (shared with the builder base; answers
     * {@code 0} when no client window exists yet).
     *
     * @zh 宿主窗口缩放宽度——全窗脚本屏的父盒范围（与构建器基类共用；尚无窗口时答 {@code 0}）。
     */
    static int parentWidth() {
        try {
            var adapter = ScriptApiEnvironment.adapter();
            if (adapter == null) {
                return 0;
            }
            return adapter.client().window()
                    .map(WindowData::scaledWidth).orElse(0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * The host window's scaled height (see {@link #parentWidth()}).
     *
     * @zh 宿主窗口缩放高度（见 {@link #parentWidth()}）。
     */
    static int parentHeight() {
        try {
            var adapter = ScriptApiEnvironment.adapter();
            if (adapter == null) {
                return 0;
            }
            return adapter.client().window()
                    .map(WindowData::scaledHeight).orElse(0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Label / active / z (ClickableWidgetHelper account, G-036B)
    // ------------------------------------------------------------------

    /** The label text ({@code getLabel}, Mojang {@code message}).
     * @zh 标签文本（{@code getLabel}，Mojang {@code message}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public String getMessage() {
        ControlState state = state();
        return state != null && state.label() != null ? state.label() : "";
    }

    /** Sets the label and returns this handle ({@code setLabel} → {@code setMessage}).
     * @zh 设置标签并返回自身（{@code setLabel} → {@code setMessage}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B setMessage(@ApiType("string") String message) {
        write("label", message != null ? message : "");
        return self();
    }

    /** Whether the widget accepts input ({@code getActive}).
     * @zh 控件是否可交互（{@code getActive}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean getActive() {
        ControlState state = state();
        return state == null || state.active();
    }

    /** Enables/disables input and returns this handle ({@code setActive}).
     * @zh 启用/禁用交互并返回自身（{@code setActive}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B setActive(@ApiType("boolean") boolean active) {
        write("active", active);
        return self();
    }

    /** The draw/hit ordering key ({@code getZIndex}).
     * @zh 绘制/命中排序键（{@code getZIndex}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getZIndex() {
        ControlState state = state();
        return state != null ? state.z() : 0;
    }

    // ------------------------------------------------------------------
    // Interaction and tooltips (ClickableWidgetHelper account, G-036B)
    // ------------------------------------------------------------------

    /**
     * Sends a left-button press and release at the widget's top-left
     * corner ({@code click}): the port runs it on the game main thread,
     * so the call always completes the press before returning (the
     * {@code await} flag has no effect and is not exposed).
     * Inactive widgets swallow the press, exactly as a real click does.
     *
     * @zh 在控件左上角发送左键按下+松开（{@code click}）：端口在游戏主线程执行，返回前按压必已完成（{@code await} 参数无效果、不暴露）。不可交互控件吞掉按压，与真实点击一致。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B click() {
        write("click", Boolean.TRUE);
        return self();
    }

    /**
     * Replaces the tooltip list ({@code setTooltip}). Accepts one value
     * or a list of values; every entry is rendered as plain text
     * ({@code toString}), lines join into one multi-line tooltip.
     *
     * @zh 替换 tooltip 列表（{@code setTooltip}）。接受单个值或值列表；每项按纯文本（{@code toString}）渲染，多行合并为一个 tooltip。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B setTooltip(@ApiType("any | any[]") Object tooltips) {
        applyTooltips(tooltipLines(tooltips));
        return self();
    }

    /** Appends one tooltip, keeping insertion order ({@code addTooltip}).
     * @zh 追加一个 tooltip、保持插入序（{@code addTooltip}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public B addTooltip(@ApiType("any") Object tooltip) {
        List<String> lines = new ArrayList<>(screen.tooltipsOf(id));
        lines.add(String.valueOf(tooltip));
        applyTooltips(lines);
        return self();
    }

    /**
     * Removes one tooltip by index or by text ({@code removeTooltip}):
     * a number removes at that zero-based index, anything else removes
     * the first entry whose text equals {@code toString()}. Answers
     * {@code false} when nothing matched.
     *
     * @zh 按下标或按文本移除一个 tooltip（{@code removeTooltip}）：数字按下标移除，其余按 {@code toString()} 等值移除首个匹配。无匹配答 {@code false}。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean removeTooltip(@ApiType("number | any") Object target) {
        List<String> lines = new ArrayList<>(screen.tooltipsOf(id));
        boolean removed;
        if (target instanceof Number index) {
            int at = index.intValue();
            removed = at >= 0 && at < lines.size();
            if (removed) {
                lines.remove(at);
            }
        } else {
            removed = lines.remove(String.valueOf(target));
        }
        if (removed) {
            applyTooltips(lines);
        }
        return removed;
    }

    /** The tooltip lines in insertion order ({@code getTooltips}).
     * @zh 按插入序的 tooltip 文本列表（{@code getTooltips}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public List<String> getTooltips() {
        return screen.tooltipsOf(id);
    }

    /** Stores the list in the session and mirrors it onto the widget. */
    private void applyTooltips(List<String> lines) {
        write("tooltip", lines);
        screen.replaceTooltips(id, lines);
    }

    /** Normalizes the {@code setTooltip} payload to a list of lines. */
    private static List<String> tooltipLines(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> lines = new ArrayList<>(list.size());
            for (Object entry : list) {
                lines.add(String.valueOf(entry));
            }
            return lines;
        }
        return List.of(String.valueOf(value));
    }
}
