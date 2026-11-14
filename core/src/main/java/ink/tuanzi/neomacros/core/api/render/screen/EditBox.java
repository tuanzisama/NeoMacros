package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.List;
import java.util.function.Function;

/**
 * Live handle for an {@code editBox} control (JsMacros
 * {@code TextFieldWidgetHelper}, Mojang word per G-003 §5.2). Reads come
 * from the session cache; writes land on the live widget through the
 * {@code setControl} whitelist. Cursor/selection moves are transient
 * widget operations carried as patch keys ({@code cursor/cursorTo/shift/
 * selection}) and never round-trip into the spec mirror semantics.
 *
 * <p>{@code setFilter} installs a script predicate consulted before user
 * edits enter the widget (the {@code setTextPredicate} slot): the adapter
 * asks the session through {@code SessionSink.filterText} with the
 * candidate full text; only an explicit {@code false} return rejects
 * (fail-open). Script-initiated {@code setValue} writes bypass the gate.
 * On 26.x vanilla dropped its native filter, so the adapter's edit box
 * subclass owns the gate on every node.</p>
 *
 * @zh {@code editBox} 控件的活句柄（JsMacros {@code TextFieldWidgetHelper}，类名按 Mojang 词）。读自会话缓存；写经 {@code setControl} 白名单落到活控件。光标/选区移动是瞬态操作，以补丁键（cursor/cursorTo/shift/selection）承载、不进 spec 镜像语义。{@code setFilter} 安装脚本谓词，在用户输入进入控件前经 {@code SessionSink.filterText} 以候选全文征询；仅显式 {@code false} 拒绝（fail-open）；脚本 {@code setValue} 写不走该闸。
 */
@ApiClass(name = "EditBox", kind = ApiClassKind.HANDLE,
        wraps = "net.minecraft.client.gui.components.EditBox")
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public class EditBox extends Widget<EditBox> {

    EditBox(BuiltScreen screen, String id) {
        super(screen, id);
    }

    /** The current text ({@code getText}, Mojang {@code value}).
     * @zh 当前文本（{@code getText}，Mojang {@code value}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public String getValue() {
        ControlState state = state();
        return state != null && state.text() != null ? state.text() : "";
    }

    /** Sets the text (script writes bypass the text filter) and returns this handle.
     * @zh 设置文本（脚本写不走文本过滤器）并返回自身。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox setValue(@ApiType("string") String text) {
        write("text", text != null ? text : "");
        return this;
    }

    /** Sets the editable text color (ARGB) and returns this handle.
     * @zh 设置可编辑态文字颜色（ARGB）并返回自身。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox setTextColor(@ApiType("number") int color) {
        write("textColor", color);
        return this;
    }

    /** Whether the box accepts input ({@code isEditable}; script-written, mirror-backed).
     * @zh 是否可输入（{@code isEditable}；仅脚本可写、镜像为真源）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean isEditable() {
        ControlState state = state();
        return state == null || state.editable() == null || state.editable();
    }

    /** Enables/disables input and returns this handle.
     * @zh 启用/禁用输入并返回自身。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox setEditable(@ApiType("boolean") boolean editable) {
        write("editable", editable);
        return this;
    }

    /** Sets the uneditable text color (ARGB) and returns this handle.
     * @zh 设置不可编辑态文字颜色（ARGB）并返回自身。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox setTextColorUneditable(@ApiType("number") int color) {
        write("uneditableColor", color);
        return this;
    }

    /** The selected text ({@code getSelectedText}, Mojang {@code highlighted}).
     * @zh 当前选中文本（{@code getSelectedText}，Mojang {@code highlighted}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public String getHighlighted() {
        ControlState state = state();
        return state != null && state.selectedText() != null
                ? state.selectedText() : "";
    }

    /** Sets the placeholder suggestion shown when empty and returns this handle.
     * @zh 设置空框时的占位提示文本并返回自身。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox setSuggestion(@ApiType("string") String suggestion) {
        write("suggestion", suggestion != null ? suggestion : "");
        return this;
    }

    /** The maximum text length ({@code getMaxLength}; script-written, mirror-backed).
     * @zh 最大文本长度（{@code getMaxLength}；仅脚本可写、镜像为真源）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getMaxLength() {
        ControlState state = state();
        return state != null && state.maxLength() != null ? state.maxLength() : 32767;
    }

    /** Sets the maximum text length and returns this handle.
     * @zh 设置最大文本长度并返回自身。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox setMaxLength(@ApiType("number") int length) {
        write("maxLength", length);
        return this;
    }

    /** Selects the range {@code [start, end]} (cursor to end) and returns this handle.
     * @zh 选中 {@code [start, end]} 区间（光标移到末尾）并返回自身。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox setSelection(@ApiType("number") int start,
                                @ApiType("number") int end) {
        write("selection", List.of(start, end));
        return this;
    }

    /**
     * Installs the text filter consulted before user edits enter the
     * widget ({@code setTextPredicate}). Return {@code false} to reject
     * the candidate text; any other return (including no return) accepts.
     *
     * @zh 安装文本过滤器（{@code setTextPredicate}）：用户输入进入控件前征询，返回 {@code false} 拒绝候选文本，其余（含无返回）放行。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox setFilter(@ApiType("(text: string) => boolean")
                             Function<String, Object> filter) {
        if (filter == null) {
            screen.clearTextFilter(id);
        } else {
            screen.putTextFilter(id, filter);
        }
        return this;
    }

    /** Clears the text filter installed by {@link #setFilter} ({@code resetTextPredicate}).
     * @zh 清除 {@link #setFilter} 安装的文本过滤器（{@code resetTextPredicate}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox resetFilter() {
        screen.clearTextFilter(id);
        return this;
    }

    /**
     * Moves the cursor to an absolute position ({@code setCursorPosition}).
     * With {@code shift} omitted the vanilla behavior applies; with
     * {@code shift} the current selection anchor is kept (the selection
     * extends to the new position).
     *
     * @zh 把光标移到绝对位置（{@code setCursorPosition}）。省略 {@code shift} 走原版行为；带 {@code shift} 则保留选区锚点（选区延伸到新位置）。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox setCursorPosition(@ApiType("number") int position,
                                     @ApiOptional @ApiType("boolean") Boolean shift) {
        if (shift == null) {
            write("cursor", position);
        } else {
            screen.setControl(id, java.util.Map.of("cursor", position, "shift", shift));
        }
        return this;
    }

    /** Moves the cursor to the start, optionally extending the selection ({@code setCursorToStart} → Mojang {@code moveCursorToStart}).
     * @zh 光标移到开头，可选延伸选区（{@code setCursorToStart} → Mojang {@code moveCursorToStart}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox moveCursorToStart(@ApiOptional @ApiType("boolean") Boolean shift) {
        screen.setControl(id, java.util.Map.of(
                "cursorTo", "start", "shift", Boolean.TRUE.equals(shift)));
        return this;
    }

    /** Moves the cursor to the end, optionally extending the selection ({@code setCursorToEnd} → Mojang {@code moveCursorToEnd}).
     * @zh 光标移到末尾，可选延伸选区（{@code setCursorToEnd} → Mojang {@code moveCursorToEnd}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public EditBox moveCursorToEnd(@ApiOptional @ApiType("boolean") Boolean shift) {
        screen.setControl(id, java.util.Map.of(
                "cursorTo", "end", "shift", Boolean.TRUE.equals(shift)));
        return this;
    }
}
