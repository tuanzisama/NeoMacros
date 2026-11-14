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
 * Builder for {@code lockButton} controls: the root-constructed fluent
 * chain (G-035 rows 35/36 fold — the boolean seed lands through
 * {@link #locked(boolean)} and the {@code construct([bool])} form).
 * {@code action} maps to
 * {@link #onClick(Consumer)}; the builder-side {@code getAction}/
 * {@code isLocked} readbacks are compat-held with their capability on
 * the {@code build()} product (G-003 §5.2).
 *
 * @zh {@code lockButton} 控件构建器：根构造的链式构建（G-035 row35/36 折叠——布尔种子经 {@link #locked(boolean)} 与 {@code construct([bool])} 形承接）。{@code action} 映射为 {@link #onClick(Consumer)}；构建器侧 {@code getAction}/{@code isLocked} 回读 compat 自持、能力在 {@code build()} 产物。
 */
@ApiClass(name = "LockButtonBuilder", kind = ApiClassKind.DATA)
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public final class LockButtonBuilder extends AbstractWidgetBuilder<LockButtonBuilder>
        implements SelfConstructing {

    private Boolean locked;
    private Consumer<Object[]> onClick;

    /** Creates the builder (root constructor, also the engine factory hook).
     * @zh 创建构建器（根构造器，兼引擎工厂钩子）。 */
    public LockButtonBuilder() {
    }

    @Override
    public Object construct(Object[] args) {
        if (args != null && args.length > 1) {
            throw new IllegalArgumentException(
                    "LockButtonBuilder takes at most one boolean"
                            + " (the initial locked state)");
        }
        LockButtonBuilder builder = new LockButtonBuilder();
        if (args != null && args.length == 1) {
            if (args[0] instanceof Boolean seed) {
                builder.locked = seed;
            } else if (args[0] != null) {
                throw new IllegalArgumentException(
                        "LockButtonBuilder takes at most one boolean"
                                + " (the initial locked state)");
            }
        }
        return builder;
    }

    @Override
    protected String type() {
        return "lockButton";
    }

    @Override
    protected void contribute(Map<String, Object> raw) {
        if (locked != null) {
            raw.put("locked", locked);
        }
        if (onClick != null) {
            raw.put("onClick", onClick);
        }
    }

    /** Sets the initial locked state ({@code locked}).
     * @zh 设置初始锁定状态（{@code locked}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public LockButtonBuilder locked(@ApiType("boolean") boolean locked) {
        this.locked = locked;
        return this;
    }

    /** Registers the press callback ({@code action} → {@code onClick}).
     * @zh 注册点击回调（{@code action} → {@code onClick}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public LockButtonBuilder onClick(
            @ApiType("(e: {id: string, type: string}) => void")
                    Consumer<Object[]> handler) {
        this.onClick = handler;
        return this;
    }
}
