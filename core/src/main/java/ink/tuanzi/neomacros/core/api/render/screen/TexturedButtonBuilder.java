package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.core.api.SelfConstructing;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builder for {@code imageButton} controls (JsMacros
 * {@code TexturedButtonBuilder}): the four sprite slots of the vanilla
 * {@code WidgetSprites} quadruple as plain string ids — the JsMacros
 * {@code ResourceLocation}/{@code String} overload pairs collapse into one
 * string form per slot (G-003 §5.2 signature merge 2→1). All four must be
 * set before {@link #build()} (the quadruple is validated as a whole).
 *
 * @zh {@code imageButton} 控件构建器（JsMacros {@code TexturedButtonBuilder}）：原版 {@code WidgetSprites} 四元组的四个贴图槽，一律为字符串 id——JsMacros 的 {@code ResourceLocation}/{@code String} 双载荷按槽合并为单 string 形（G-003 §5.2 签名 2→1）。{@link #build()} 前四槽必须齐全（四元组整体校验）。
 */
@ApiClass(name = "TexturedButtonBuilder", kind = ApiClassKind.DATA)
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public final class TexturedButtonBuilder
        extends AbstractWidgetBuilder<TexturedButtonBuilder> implements SelfConstructing {

    private Consumer<Object[]> onClick;
    private String enabled;
    private String disabled;
    private String enabledFocused;
    private String disabledFocused;

    /** Creates the builder (root constructor, also the engine factory hook).
     * @zh 创建构建器（根构造器，兼引擎工厂钩子）。 */
    public TexturedButtonBuilder() {
    }

    @Override
    public Object construct(Object[] args) {
        if (args != null && args.length > 0) {
            throw new IllegalArgumentException("TexturedButtonBuilder takes no arguments");
        }
        return new TexturedButtonBuilder();
    }

    @Override
    protected String type() {
        return "imageButton";
    }

    @Override
    protected void contribute(Map<String, Object> raw) {
        if (onClick != null) {
            raw.put("onClick", onClick);
        }
        if (enabled != null || disabled != null || enabledFocused != null
                || disabledFocused != null) {
            if (enabled == null || disabled == null || enabledFocused == null
                    || disabledFocused == null) {
                throw new IllegalStateException(
                        "TexturedButtonBuilder requires all four textures "
                                + "(enabled/disabled/enabledFocused/disabledFocused)");
            }
            Map<String, String> sprites = new LinkedHashMap<>();
            sprites.put("enabled", enabled);
            sprites.put("disabled", disabled);
            sprites.put("enabledFocused", enabledFocused);
            sprites.put("disabledFocused", disabledFocused);
            raw.put("sprites", sprites);
        }
    }

    /** Registers the press callback ({@code action} → {@code onClick}).
     * @zh 注册点击回调（{@code action} → {@code onClick}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public TexturedButtonBuilder onClick(
            @ApiType("(e: {id: string, type: string}) => void")
                    Consumer<Object[]> handler) {
        this.onClick = handler;
        return this;
    }

    /** Sets the enabled-state texture id ({@code enabledTexture}).
     * @zh 设置可用态贴图 id（{@code enabledTexture}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public TexturedButtonBuilder enabledTexture(@ApiType("string") String enabled) {
        this.enabled = enabled;
        return this;
    }

    /** Sets the disabled-state texture id ({@code disabledTexture}).
     * @zh 设置禁用态贴图 id（{@code disabledTexture}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public TexturedButtonBuilder disabledTexture(@ApiType("string") String disabled) {
        this.disabled = disabled;
        return this;
    }

    /** Sets the focused-enabled texture id ({@code enabledFocusedTexture}).
     * @zh 设置聚焦可用态贴图 id（{@code enabledFocusedTexture}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public TexturedButtonBuilder enabledFocusedTexture(
            @ApiType("string") String enabledFocused) {
        this.enabledFocused = enabledFocused;
        return this;
    }

    /** Sets the focused-disabled texture id ({@code disabledFocusedTexture}).
     * @zh 设置聚焦禁用态贴图 id（{@code disabledFocusedTexture}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public TexturedButtonBuilder disabledFocusedTexture(
            @ApiType("string") String disabledFocused) {
        this.disabledFocused = disabledFocused;
        return this;
    }
}
