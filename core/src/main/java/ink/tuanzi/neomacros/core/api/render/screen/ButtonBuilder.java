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
 * Builder for {@code button} controls: the root-constructed fluent chain
 * behind the JsMacros {@code screen.buttonBuilder()} factory (G-003 §5.1
 * cross-task fold — the factory member is accounted to G-035 and lands
 * here). {@code height}/{@code size} come from the
 * {@link AbstractWidgetBuilder} skeleton; {@code action} maps to
 * {@link #onClick(Consumer)}; the builder-side {@code getAction} readback
 * is compat-held (G-003 §13 回写⑤) with its capability on
 * {@code build().onClick()}.
 *
 * @zh {@code button} 控件构建器：JsMacros {@code screen.buttonBuilder()} 工厂的根构造器对位（G-003 §5.1 跨任务折叠——工厂成员记 G-035、在此落地）。{@code height}/{@code size} 来自 {@link AbstractWidgetBuilder} 骨架；{@code action} 映射为 {@link #onClick(Consumer)}；构建器侧 {@code getAction} 回读归 compat 自持（能力在 {@code build().onClick()}）。
 */
@ApiClass(name = "ButtonBuilder", kind = ApiClassKind.DATA)
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public final class ButtonBuilder extends AbstractWidgetBuilder<ButtonBuilder>
        implements SelfConstructing {

    private Consumer<Object[]> onClick;

    /** Creates the builder (root constructor, also the engine factory hook).
     * @zh 创建构建器（根构造器，兼引擎工厂钩子）。 */
    public ButtonBuilder() {
    }

    @Override
    public Object construct(Object[] args) {
        if (args != null && args.length > 0) {
            throw new IllegalArgumentException("ButtonBuilder takes no arguments");
        }
        return new ButtonBuilder();
    }

    @Override
    protected String type() {
        return "button";
    }

    @Override
    protected void contribute(Map<String, Object> raw) {
        if (onClick != null) {
            raw.put("onClick", onClick);
        }
    }

    /** Registers the press callback ({@code action} → {@code onClick}).
     * @zh 注册点击回调（{@code action} → {@code onClick}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ButtonBuilder onClick(
            @ApiType("(e: {id: string, type: string}) => void")
                    Consumer<Object[]> handler) {
        this.onClick = handler;
        return this;
    }
}
