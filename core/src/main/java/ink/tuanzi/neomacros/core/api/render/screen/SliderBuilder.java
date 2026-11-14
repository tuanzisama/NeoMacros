package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.core.api.SelfConstructing;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Builder for {@code slider} controls (JsMacros
 * {@code SliderWidgetHelper.SliderBuilder}): {@code steps(n)} sets the
 * discrete snapping count ({@code 0} = continuous), {@code initially(v)}
 * seeds the normalized 0..1 value, {@code action} maps to
 * {@link #onChange(Consumer)}. Builder-side {@code getSteps}/{@code getValue}
 * readbacks are compat-held with their capability on
 * {@code build().steps}/{@code build().value}.
 *
 * <p>Unit note (compat write-back): NeoMacros slider values are the
 * normalized 0..1 doubles of {@code ControlSpec.value} (the G-035
 * {@code addSlider} unit). JsMacros declared {@code initially(int)} /
 * {@code getValue(): int}; if the compat layer measures those in step
 * units it converts before delegating (G-003 §13 behavior write-back).</p>
 *
 * @zh {@code slider} 控件构建器（JsMacros {@code SliderWidgetHelper.SliderBuilder}）：{@code steps(n)} 设离散吸附步数（{@code 0} = 连续），{@code initially(v)} 播种 0..1 归一值，{@code action} 映射为 {@link #onChange(Consumer)}。构建器侧 {@code getSteps}/{@code getValue} 回读归 compat 自持（能力在 {@code build().steps}/{@code build().value}）。单位注：NeoMacros 滑条值 = {@code ControlSpec.value} 的 0..1 归一 double（G-035 {@code addSlider} 同一单位）；JsMacros 声明 int 形，compat 若按 steps 单位计量需先行换算。
 */
@ApiClass(name = "SliderBuilder", kind = ApiClassKind.DATA)
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public final class SliderBuilder extends AbstractWidgetBuilder<SliderBuilder>
        implements SelfConstructing {

    private Integer steps;
    private Double value;
    private Consumer<Object[]> onChange;

    /** Creates the builder (root constructor, also the engine factory hook).
     * @zh 创建构建器（根构造器，兼引擎工厂钩子）。 */
    public SliderBuilder() {
    }

    @Override
    public Object construct(Object[] args) {
        if (args != null && args.length > 0) {
            throw new IllegalArgumentException("SliderBuilder takes no arguments");
        }
        return new SliderBuilder();
    }

    @Override
    protected String type() {
        return "slider";
    }

    @Override
    protected void contribute(Map<String, Object> raw) {
        if (steps != null) {
            raw.put("steps", steps);
        }
        if (value != null) {
            raw.put("value", value);
        }
        if (onChange != null) {
            raw.put("onChange", onChange);
        }
    }

    /** Sets the discrete step count ({@code 0} = continuous).
     * @zh 设置离散步数（{@code 0} = 连续）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public SliderBuilder steps(@ApiType("number") int steps) {
        this.steps = steps;
        return this;
    }

    /** Seeds the normalized 0..1 initial value ({@code initially}).
     * @zh 播种 0..1 归一初始值（{@code initially}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public SliderBuilder initially(@ApiType("number") double value) {
        this.value = value;
        return this;
    }

    /** Registers the value-change callback ({@code action} → {@code onChange}).
     * @zh 注册值变更回调（{@code action} → {@code onChange}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public SliderBuilder onChange(
            @ApiType("(e: {id: string, type: string, value?: number}) => void")
                    Consumer<Object[]> handler) {
        this.onChange = handler;
        return this;
    }
}
