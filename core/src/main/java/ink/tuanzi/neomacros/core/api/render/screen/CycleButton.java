package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

/**
 * Live handle for a {@code cycleButton} control (JsMacros
 * {@code CyclingButtonWidgetHelper}, Mojang word per G-003 §5.3). The
 * generic {@code T} of JsMacros collapses to plain strings: control
 * option values are strings ({@code ControlSpec.values}), so the handle
 * reads and writes the string value.
 *
 * <p>{@code setValue} writes the value straight onto the button — the
 * configured change callback does <b>not</b> run (vanilla
 * {@code CycleButton.setValue} semantics); {@code cycle(n)} steps
 * {@code n} positions through the current option list (wrapping) and
 * <b>does</b> fire the change callback once, exactly like a press. The
 * live value rides the control's text slot.</p>
 *
 * @zh {@code cycleButton} 控件的活句柄（JsMacros {@code CyclingButtonWidgetHelper}，类名按 Mojang 词）。JsMacros 的泛型 {@code T} 收敛为纯字符串：控件选项值即字符串（{@code ControlSpec.values}）。{@code setValue} 直接把值写上按钮——配置的变更回调<b>不</b>运行（原版 {@code CycleButton.setValue} 语义）；{@code cycle(n)} 在当前选项表中步进 {@code n} 格（回绕）并<b>会</b>触发一次变更回调，与点击一致。活值承载在控件的 text 槽。
 */
@ApiClass(name = "CycleButton", kind = ApiClassKind.HANDLE,
        wraps = "net.minecraft.client.gui.components.CycleButton")
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public class CycleButton extends Widget<CycleButton> {

    CycleButton(BuiltScreen screen, String id) {
        super(screen, id);
    }

    /** The selected option value ({@code getValue}).
     * @zh 当前选中的选项值（{@code getValue}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public String getValue() {
        ControlState state = state();
        return state != null && state.text() != null ? state.text() : "";
    }

    /**
     * The current value rendered as plain display text by the builder's
     * {@code valueToText} converter, without the option prefix
     * ({@code getStringValue}); without a converter this is the raw
     * value.
     *
     * @zh 当前值经构建器 {@code valueToText} 转换器渲染的纯文本显示（不含选项前缀，{@code getStringValue}）；未配转换器时即原值。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public String getStringValue() {
        ControlState state = state();
        if (state == null) {
            return "";
        }
        String value = state.text() != null ? state.text() : "";
        ControlSpec spec = screen.specOf(id, "cycleButton");
        if (spec != null && spec.valueLabels() != null) {
            String label = spec.valueLabels().get(value);
            if (label != null) {
                return label;
            }
        }
        return value;
    }

    /**
     * Sets the displayed value directly, without cycling and without
     * running the change callback ({@code setValue}). Answers
     * {@code true} when the previous value already equalled
     * {@code value} (equality uses {@code equals}); the value is set
     * regardless.
     *
     * @zh 直接设置显示值：不循环、不触发变更回调（{@code setValue}）。前值已等于 {@code value} 时答 {@code true}（按 {@code equals} 比较）；无论是否相等都会写入。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean setValue(@ApiType("string") String value) {
        String current = getValue();
        String next = value != null ? value : "";
        write("text", next);
        return current.equals(next);
    }

    /**
     * Cycles {@code amount} positions through the current option list,
     * wrapping at either end; positive moves forward, negative backward
     * ({@code cycle}). The configured change callback runs once for the
     * cycle — including a cycle that lands on the same value.
     *
     * @zh 在当前选项表中步进 {@code amount} 格、两端回绕；正数向前、负数向后（{@code cycle}）。本次步进触发一次变更回调——落回原值也触发。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CycleButton cycle(@ApiType("number") int amount) {
        write("cycle", amount);
        return this;
    }
}
