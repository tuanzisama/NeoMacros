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
 * Builder for {@code editBox} controls (NeoMacros keeps the task-card
 * word while the live handle uses the Mojang word {@link EditBox}):
 * {@code suggestion(s)} seeds the placeholder, {@code action} maps to
 * {@link #onChange(Consumer)}; the builder-side {@code getSuggestion}
 * readback is compat-held with its capability on
 * {@code build().suggestion}.
 *
 * @zh {@code editBox} 控件构建器（任务卡词保留，活句柄取 Mojang 词 {@link EditBox}）：{@code suggestion(s)} 播种占位提示，{@code action} 映射为 {@link #onChange(Consumer)}；构建器侧 {@code getSuggestion} 回读归 compat 自持（能力在 {@code build().suggestion}）。
 */
@ApiClass(name = "TextFieldBuilder", kind = ApiClassKind.DATA)
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public final class TextFieldBuilder extends AbstractWidgetBuilder<TextFieldBuilder>
        implements SelfConstructing {

    private String suggestion;
    private Consumer<Object[]> onChange;

    /** Creates the builder (root constructor, also the engine factory hook).
     * @zh 创建构建器（根构造器，兼引擎工厂钩子）。 */
    public TextFieldBuilder() {
    }

    @Override
    public Object construct(Object[] args) {
        if (args != null && args.length > 0) {
            throw new IllegalArgumentException("TextFieldBuilder takes no arguments");
        }
        return new TextFieldBuilder();
    }

    @Override
    protected String type() {
        return "editBox";
    }

    @Override
    protected void contribute(Map<String, Object> raw) {
        if (suggestion != null) {
            raw.put("suggestion", suggestion);
        }
        if (onChange != null) {
            raw.put("onChange", onChange);
        }
    }

    /** Seeds the placeholder suggestion shown when the box is empty.
     * @zh 播种空框时的占位提示文本。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public TextFieldBuilder suggestion(@ApiType("string") String suggestion) {
        this.suggestion = suggestion;
        return this;
    }

    /** Registers the text-change callback ({@code action} → {@code onChange}).
     * @zh 注册文本变更回调（{@code action} → {@code onChange}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public TextFieldBuilder onChange(
            @ApiType("(e: {id: string, type: string, text?: string}) => void")
                    Consumer<Object[]> handler) {
        this.onChange = handler;
        return this;
    }
}
