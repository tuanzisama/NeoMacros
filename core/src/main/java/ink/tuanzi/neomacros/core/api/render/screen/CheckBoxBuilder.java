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
 * Builder for {@code checkbox} controls (JsMacros
 * {@code CheckBoxWidgetHelper.CheckBoxBuilder}, Mojang spelling for the
 * handle): {@code checked(b)} seeds the initial state, {@code action}
 * maps to {@link #onChange(Consumer)}; the builder-side {@code isChecked}
 * readback is compat-held with its capability on {@code build().checked}.
 *
 * @zh {@code checkbox} 控件构建器（JsMacros {@code CheckBoxWidgetHelper.CheckBoxBuilder}，句柄侧取 Mojang 拼写）：{@code checked(b)} 播种初始态，{@code action} 映射为 {@link #onChange(Consumer)}；构建器侧 {@code isChecked} 回读归 compat 自持（能力在 {@code build().checked}）。
 */
@ApiClass(name = "CheckBoxBuilder", kind = ApiClassKind.DATA)
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public final class CheckBoxBuilder extends AbstractWidgetBuilder<CheckBoxBuilder>
        implements SelfConstructing {

    private Boolean checked;
    private Consumer<Object[]> onChange;

    /** Creates the builder (root constructor, also the engine factory hook).
     * @zh 创建构建器（根构造器，兼引擎工厂钩子）。 */
    public CheckBoxBuilder() {
    }

    @Override
    public Object construct(Object[] args) {
        if (args != null && args.length > 1) {
            throw new IllegalArgumentException(
                    "CheckBoxBuilder takes at most one argument (checked)");
        }
        CheckBoxBuilder builder = new CheckBoxBuilder();
        if (args != null && args.length == 1 && args[0] instanceof Boolean initial) {
            builder.checked = initial;
        }
        return builder;
    }

    @Override
    protected String type() {
        return "checkbox";
    }

    @Override
    protected void contribute(Map<String, Object> raw) {
        if (checked != null) {
            raw.put("checked", checked);
        }
        if (onChange != null) {
            raw.put("onChange", onChange);
        }
    }

    /** Seeds the initial checked state ({@code isChecked} reads it back).
     * @zh 播种初始勾选态（{@code isChecked} 回读该值）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CheckBoxBuilder checked(@ApiType("boolean") boolean checked) {
        this.checked = checked;
        return this;
    }

    /** Registers the value-change callback ({@code action} → {@code onChange}).
     * @zh 注册值变更回调（{@code action} → {@code onChange}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public CheckBoxBuilder onChange(
            @ApiType("(e: {id: string, type: string, checked?: boolean}) => void")
                    Consumer<Object[]> handler) {
        this.onChange = handler;
        return this;
    }
}
