package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

/**
 * Live handle for a {@code slider} control (JsMacros
 * {@code SliderWidgetHelper}). {@code value} is the normalized 0..1
 * position; {@code steps} is the discrete snapping count ({@code 0} =
 * continuous). Writes land on the live widget in place — {@code setValue}
 * without an event echo, {@code setSteps} re-snaps the current value and
 * refreshes the label.
 *
 * @zh {@code slider} 控件的活句柄（JsMacros {@code SliderWidgetHelper}）。{@code value} 为 0..1 归一位置；{@code steps} 为离散吸附步数（{@code 0} = 连续）。写就地落到活控件——{@code setValue} 无事件回声，{@code setSteps} 重吸附当前值并刷新文本。
 */
@ApiClass(name = "Slider", kind = ApiClassKind.HANDLE,
        wraps = "net.minecraft.client.gui.components.AbstractSliderButton")
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public class Slider extends Widget<Slider> {

    Slider(BuiltScreen screen, String id) {
        super(screen, id);
    }

    /** The normalized 0..1 value ({@code getValue}).
     * @zh 0..1 归一值（{@code getValue}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public double getValue() {
        ControlState state = state();
        return state != null && state.value() != null ? state.value() : 0.0;
    }

    /** Sets the normalized value (clamped to 0..1) and returns this handle.
     * @zh 设置 0..1 归一值（截断到 0..1）并返回自身。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Slider setValue(@ApiType("number") double value) {
        write("value", value);
        return this;
    }

    /** The discrete step count, {@code 0} for continuous ({@code getSteps}).
     * @zh 离散步数，{@code 0} 表示连续（{@code getSteps}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public int getSteps() {
        ControlState state = state();
        return state != null && state.steps() != null ? state.steps() : 0;
    }

    /** Sets the discrete step count and returns this handle.
     * @zh 设置离散步数并返回自身。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Slider setSteps(@ApiType("number") int steps) {
        write("steps", steps);
        return this;
    }
}
