package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.core.api.render.HudLayer;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

/**
 * Handle to one text element living inside a HUD layer, obtained from
 * {@code layer.text(id, …)} upserts. The G-034 component face of
 * {@link Draw2DElement}: text content, drop shadow, tint color, and the
 * font-measured box — everything else (geometry, transform, z-order,
 * alignment, lifecycle) is inherited from the base without re-declaration
 * (G-034C contract).
 *
 * <p><b>Text metrics</b>: the element's box is seeded from the font when
 * the text is set — {@link #getWidth()} answers the cached, unscaled
 * font-measured width and {@link #getHeight()} the font line height, then
 * {@code scaledWidth}/{@code bounds} apply {@link #getScale()} exactly like
 * JsMacros (G-032B legacy ① closed here; the measurement rides
 * {@code RenderPort.textWidth}).</p>
 *
 * <p><b>Element is its own builder</b> (拍板②): every setter is fluent and
 * returns this handle; the JsMacros {@code Text.Builder} family folds into
 * it.</p>
 *
 * @zh HUD 图层内单个文本元素的句柄，由 {@code layer.text(id, …)} upsert 返回。{@link Draw2DElement} 的 G-034 组件面：文本内容、投影、着色与字体度量盒——其余（几何、变换、z 序、对齐、生命周期）全部继承自基类且不重声明（G-034C 契约）。
 * @zh <b>文本度量</b>：文本被设置时以字体为元素盒播种——{@link #getWidth()} 返回缓存的未缩放字体量宽，{@link #getHeight()} 返回字体行高，随后 {@code scaledWidth}/{@code bounds} 按 {@link #getScale()} 缩放，与 JsMacros 完全一致（G-032B 遗留① 在此闭环；度量走 {@code RenderPort.textWidth}）。
 * @zh <b>元素即 Builder</b>（拍板②）：全部 setter 为 fluent 并返回本句柄；JsMacros 的 {@code Text.Builder} 类族折叠于此。
 */
@ApiClass(name = "Draw2DText", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public class Draw2DText extends Draw2DElement {

    /**
     * Host-side constructor wiring the handle to its scene cell; scripts
     * receive instances from {@code layer.text(...)} only.
     *
     * @param id      element id inside the owning layer
     * @param layer   the layer hosting this element (v1 parent box)
     * @param backend scene bridge publishing and reading the element state
     * @zh 宿主侧构造：把句柄接到其场景单元；脚本只从 {@code layer.text(...)} 获得实例。
     * @zh-param id 元素在其层内的 id
     * @zh-param layer 承载本元素的层（v1 父盒）
     * @zh-param backend 发布与读取元素状态的场景桥
     */
    public Draw2DText(String id, HudLayer layer, Backend backend) {
        super(id, layer, backend);
    }

    /**
     * Replaces the displayed text and re-measures the element's box from
     * the font (the JsMacros {@code setText} contract: the cached width
     * refreshes here, not in the getter). Styling, hover text and click
     * actions do not exist on the NeoMacros v1 plain-text face; a null
     * string becomes empty. {@code undefined} once disposed.
     *
     * @param text the new text (null becomes empty)
     * @return this element for chaining
     * @zh 替换显示文本并按字体重新度量元素盒（JsMacros {@code setText} 契约：缓存宽度在此刷新而非 getter）。NeoMacros v1 纯文本面没有样式/悬浮/点击动作；null 按空串处理。失效后为 {@code undefined}。
     * @zh-param text 新文本（null 按空串）
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DText setText(String text) {
        requireAlive();
        String value = text == null ? "" : text;
        HudLayer hosting = getLayer();
        backend().publish(backend().spec()
                .withText(value)
                .withW(ElementSpec.Coord.at(hosting.measureTextWidth(value)))
                .withH(ElementSpec.Coord.at(hosting.measureTextLineHeight())));
        return this;
    }

    /**
     * The displayed text; {@code undefined} once disposed.
     *
     * @zh 显示的文本；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String getText() {
        return backend().disposed() ? null : backend().spec().text();
    }

    /**
     * Sets whether the game's font draws a drop shadow behind the glyphs.
     *
     * @param shadow {@code true} to draw the drop shadow
     * @return this element for chaining
     * @zh 设置字体是否在字形后绘制投影。
     * @zh-param shadow {@code true} 表示绘制投影
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DText setShadow(boolean shadow) {
        requireAlive();
        backend().publish(backend().spec().withShadow(shadow));
        return this;
    }

    /**
     * Whether the font draws a drop shadow behind this text; reads
     * {@code undefined} once disposed (the JsMacros {@code hasShadow}
     * face — a method, not a property, so the engine keeps the name).
     *
     * @zh 字体是否在本文本后绘制投影；失效后读到 {@code undefined}（JsMacros 的 {@code hasShadow} 面——方法而非属性，引擎保留该名）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Boolean hasShadow() {
        return backend().disposed() ? null : backend().spec().shadow();
    }

    /**
     * Sets the packed tint for the text. Folded member (G-033 overload-fold
     * rule — one script binding per name): {@code setColor(color)} keeps the
     * packed ARGB as given (拍板④ double form), {@code setColor(color,
     * alpha)} replaces the top byte, and {@code setColor(r, g, b[, a])}
     * builds the color from channels (opaque unless an alpha is given).
     *
     * @param color  packed color (number or string), or the red channel in rgb forms
     * @param alpha  optional alpha byte, or the green channel in rgb forms
     * @param blue   optional blue channel (rgb form)
     * @param alpha2 optional alpha channel of the rgba form
     * @return this element for chaining
     * @zh 设置文本着色。折叠成员（G-033 重载折叠铁律——每名一个脚本绑定）：{@code setColor(color)} 原样保留打包 ARGB（拍板④ 双形），{@code setColor(color, alpha)} 替换顶字节，{@code setColor(r, g, b[, a])} 由通道构建（未给 alpha 则不透明）。
     * @zh-param color 打包色（数字或字符串），rgb 形中为红通道
     * @zh-param alpha 可选 alpha 字节，rgb 形中为绿通道
     * @zh-param blue 可选蓝通道（rgb 形）
     * @zh-param alpha2 rgba 形的可选 alpha 通道
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DText setColor(
            @ApiType("number | string") Object color,
            @ApiOptional Object alpha,
            @ApiOptional Object blue,
            @ApiOptional Object alpha2) {
        requireAlive();
        backend().publish(backend().spec().withColor(
                ElementSpec.parseColor(color, alpha, blue, alpha2)));
        return this;
    }

    /**
     * The packed tint of the text, alpha in the top byte; separate from
     * colors carried by rich text (not modelled on the v1 face);
     * {@code undefined} once disposed.
     *
     * @zh 文本的打包着色（alpha 在顶字节）；与富文本携带的颜色无关（v1 面未建模）；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getColor() {
        return backend().disposed() ? null : backend().spec().color();
    }

    // ---- host-side color overloads: the folded member above owns the
    // ---- script surface (one member per name); these mirror the task-card
    // ---- call shapes for Java callers and stay off it.

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with a packed color; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（打包色），不进脚本面。
     */
    @ApiInternal
    public Draw2DText setColor(int color) {
        return setColor(color, null, null, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with a 拍板④ color string; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（拍板④ 颜色字符串），不进脚本面。
     */
    @ApiInternal
    public Draw2DText setColor(String color) {
        return setColor(color, null, null, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with a color and an alpha byte; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（色 + alpha），不进脚本面。
     */
    @ApiInternal
    public Draw2DText setColor(int color, int alpha) {
        return setColor(color, alpha, null, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with red/green/blue channels (opaque); hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（RGB 通道，不透明），不进脚本面。
     */
    @ApiInternal
    public Draw2DText setColor(int red, int green, int blue) {
        return setColor(red, green, blue, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with red/green/blue/alpha channels; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（RGBA 通道），不进脚本面。
     */
    @ApiInternal
    public Draw2DText setColor(int red, int green, int blue, int alpha) {
        return setColor((Object) red, (Object) green, (Object) blue,
                (Object) alpha);
    }

    /** Throws when the handle is no longer valid; used by every mutator. */
    private void requireAlive() {
        if (backend().disposed()) {
            throw new IllegalStateException("element no longer valid");
        }
    }
}
