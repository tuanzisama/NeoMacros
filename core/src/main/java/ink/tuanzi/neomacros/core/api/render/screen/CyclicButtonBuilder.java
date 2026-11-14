package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.core.api.SelfConstructing;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builder for {@code cycleButton} controls: the root-constructed fluent
 * chain behind the JsMacros {@code screen.cyclicButtonBuilder(valueToText)}
 * factory (G-035 row34 fold — the factory's converter argument moves to
 * {@link #valueToText(Function)}, since the engine's constructor bridge
 * only carries scalars). {@code values}/{@code alternatives} seed the two
 * option lists, {@code initially} the starting value, {@code action} maps
 * to {@link #onChange(Consumer)}, and {@code alternateToggle} registers
 * the per-cycle list selector that reaches the adapter through
 * {@code SessionSink.alternateToggle}. Builder-side readbacks
 * ({@code getAction}/{@code getInitialValue}/…) are compat-held with
 * their capability on the {@code build()} product (G-003 §5.2).
 *
 * <p>The JsMacros {@code valueToText} converter is consumed at
 * {@link #build()}: every option is rendered once into the neutral
 * {@code valueLabels} map, so the adapter and
 * {@code CycleButton.getStringValue} read plain strings and no function
 * ever crosses the port. The generic {@code T} collapses to strings
 * (control option values are strings).</p>
 *
 * @zh {@code cycleButton} 控件构建器：JsMacros {@code screen.cyclicButtonBuilder(valueToText)} 工厂的根构造器对位（G-035 row34 折叠——工厂的转换器参数移到 {@link #valueToText(Function)}，引擎构造器桥只携带标量）。{@code values}/{@code alternatives} 播种两张选项表，{@code initially} 起始值，{@code action} 映射为 {@link #onChange(Consumer)}，{@code alternateToggle} 注册逐步换表选择器、经 {@code SessionSink.alternateToggle} 到达 adapter。构建器侧回读（{@code getAction}/{@code getInitialValue}/…）compat 自持、能力在 {@code build()} 产物。{@code valueToText} 在 {@link #build()} 时消费：每个选项渲染一次进中立 {@code valueLabels}，adapter 与 {@code CycleButton.getStringValue} 读纯文本、函数永不跨端口。泛型 {@code T} 收敛为字符串。
 */
@ApiClass(name = "CyclicButtonBuilder", kind = ApiClassKind.DATA)
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public final class CyclicButtonBuilder
        extends AbstractWidgetBuilder<CyclicButtonBuilder>
        implements SelfConstructing {

    private String initialValue;
    private final List<String> defaultValues = new ArrayList<>();
    private final List<String> alternateValues = new ArrayList<>();
    private Consumer<Object[]> onChange;
    private Function<String, Object> valueToText;
    private BooleanSupplier alternateToggle;
    private Boolean omitTextOption;
    private String prefix;

    /** Creates the builder (root constructor, also the engine factory hook).
     * @zh 创建构建器（根构造器，兼引擎工厂钩子）。 */
    public CyclicButtonBuilder() {
    }

    @Override
    public Object construct(Object[] args) {
        if (args != null && args.length > 0) {
            // The constructor bridge flattens functions to null; a silently
            // dropped converter would be worse than a loud miss.
            throw new IllegalArgumentException("CyclicButtonBuilder takes no"
                    + " arguments; set the converter with valueToText(fn)");
        }
        return new CyclicButtonBuilder();
    }

    @Override
    protected String type() {
        return "cycleButton";
    }

    @Override
    protected void contribute(Map<String, Object> raw) {
        if (defaultValues.isEmpty()) {
            throw new IllegalStateException(
                    "CyclicButtonBuilder requires at least one value"
                            + " (values(...))");
        }
        raw.put("values", List.copyOf(defaultValues));
        if (!alternateValues.isEmpty()) {
            raw.put("alternatives", List.copyOf(alternateValues));
        }
        if (initialValue != null) {
            raw.put("initial", initialValue);
        }
        if (prefix != null) {
            raw.put("prefix", prefix);
        }
        if (omitTextOption != null) {
            raw.put("omitTextOption", omitTextOption);
        }
        if (alternateToggle != null) {
            raw.put("alternateToggle", alternateToggle);
        }
        if (valueToText != null) {
            raw.put("valueLabels", renderLabels());
        }
        if (onChange != null) {
            raw.put("onChange", onChange);
        }
    }

    /** Renders every option through the converter once (build-time). */
    private Map<String, String> renderLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (String value : defaultValues) {
            labels.put(value, render(value));
        }
        for (String value : alternateValues) {
            labels.putIfAbsent(value, render(value));
        }
        return labels;
    }

    private String render(String value) {
        Object out = valueToText.apply(value);
        return out != null ? String.valueOf(out) : value;
    }

    /** Seeds the starting option value ({@code initially}).
     * @zh 播种起始选项值（{@code initially}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CyclicButtonBuilder initially(@ApiType("string") String value) {
        this.initialValue = value;
        return this;
    }

    /**
     * Replaces the default option list; the alternate list is kept.
     * Pass a second list to replace both ({@code values}).
     *
     * @zh 替换默认选项表、保留备用表；传第二张表则两表皆替换（{@code values}）。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CyclicButtonBuilder values(
            @ApiType("string[]") List<String> defaults,
            @ApiOptional @ApiType("string[]") List<String> alternatives) {
        defaultValues.clear();
        if (defaults != null) {
            defaultValues.addAll(defaults);
        }
        if (alternatives != null) {
            alternateValues.clear();
            alternateValues.addAll(alternatives);
        }
        return this;
    }

    /** Replaces the alternate option list ({@code alternatives}).
     * @zh 替换备用选项表（{@code alternatives}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CyclicButtonBuilder alternatives(@ApiType("string[]") List<String> values) {
        alternateValues.clear();
        if (values != null) {
            alternateValues.addAll(values);
        }
        return this;
    }

    /**
     * Registers the value-change callback ({@code action} →
     * {@code onChange}); it fires on every cycle and on user presses,
     * not on {@code setValue}.
     *
     * @zh 注册值变更回调（{@code action} → {@code onChange}）；每次步进与用户点击触发，{@code setValue} 不触发。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CyclicButtonBuilder onChange(
            @ApiType("(e: {id: string, type: string, value?: string}) => void")
                    Consumer<Object[]> handler) {
        this.onChange = handler;
        return this;
    }

    /**
     * Sets the converter that renders one option value into its display
     * text ({@code valueToText}). It runs at {@link #build()} for every
     * option; return {@code null} to keep the raw value.
     *
     * @zh 设置把选项值渲染为显示文本的转换器（{@code valueToText}）。在 {@link #build()} 时对每个选项执行；返回 {@code null} 保留原值。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CyclicButtonBuilder valueToText(
            @ApiType("(value: string) => string") Function<String, Object> converter) {
        this.valueToText = converter;
        return this;
    }

    /**
     * Sets the function that picks the option list for each cycle:
     * {@code true} selects the alternate list, {@code false} the default
     * ({@code alternateToggle}). Without a toggle the adapter keeps the
     * vanilla selector (Alt held). The function must be set here — a
     * function inside an object literal cannot return its verdict.
     *
     * @zh 设置每次步进选表的函数：{@code true} 选备用表、{@code false} 选默认表（{@code alternateToggle}）。未设置时 adapter 保留原版选择器（按住 Alt）。函数只能经此设置——对象字面量里的函数无法带回判定。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CyclicButtonBuilder alternateToggle(
            @ApiType("() => boolean") BooleanSupplier toggle) {
        this.alternateToggle = toggle;
        return this;
    }

    /**
     * Sets whether the widget shows only the option value instead of the
     * option prefix and value ({@code omitTextOption}); a blank prefix
     * is omitted regardless.
     *
     * @zh 设置控件只显示选项值、不显示选项前缀（{@code omitTextOption}）；前缀为空时无论如何都省略。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CyclicButtonBuilder omitTextOption(@ApiType("boolean") boolean omitted) {
        this.omitTextOption = omitted;
        return this;
    }

    /**
     * Sets the prefix displayed before the value ({@code option}); a
     * blank prefix is omitted from the rendered text.
     *
     * @zh 设置显示在值之前的前缀（{@code option}）；空前缀不参与渲染。
     */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CyclicButtonBuilder option(@ApiType("string") String option) {
        this.prefix = option != null && !option.isEmpty() ? option : null;
        return this;
    }
}
