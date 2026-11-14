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
 * Handle to one texture-image element living inside a HUD layer, obtained
 * from {@code layer.image(id, …)} upserts. The G-034B component face of
 * {@link Draw2DElement}: the texture view ({@code setImage}/{@code image}
 * plus the {@code imageX/imageY/region…/texture…} source-rect properties)
 * and the tint color — destination geometry, transform, z-order,
 * alignment and lifecycle are inherited without re-declaration (G-034C
 * contract).
 *
 * <p><b>Texture consumption (G-030)</b>: the identifier is the same
 * {@code namespace:path} key the texture registry uses, so a
 * {@code CustomImage} feeds this face through {@code img.identifier}
 * (live, re-uploadable) or through {@code layer.image(id, {image})}
 * (immutable PNG snapshot, uploaded once). A bare name takes the
 * {@code minecraft} namespace; {@link #getImage()} answers the resolved
 * form. Region dimensions are stored and select the sampled texels; the
 * {@code 2D} fallback samples the destination extent when the region is
 * unset.</p>
 *
 * <p><b>Element is its own builder</b> (拍板②): every setter is fluent and
 * returns this handle.</p>
 *
 * @zh HUD 图层内单个纹理图像元素的句柄，由 {@code layer.image(id, …)} upsert 返回。{@link Draw2DElement} 的 G-034B 组件面：纹理视图（{@code setImage}/{@code image} 与 {@code imageX/imageY/region…/texture…} 源矩形属性）与着色——目标几何、变换、z 序、对齐、生命周期全部继承且不重声明（G-034C 契约）。
 * @zh <b>纹理消费（G-030）</b>：标识与纹理注册表同为 {@code namespace:path} 键，因此 {@code CustomImage} 经 {@code img.identifier}（活纹理，可重传）或 {@code layer.image(id, {image})}（不可变 PNG 快照，仅上传一次）供给本面。裸名取 {@code minecraft} 命名空间；{@link #getImage()} 返回解析后的形态。区域尺寸被存储并选择采样 texel；未设区域时回退为目标尺寸采样。
 * @zh <b>元素即 Builder</b>（拍板②）：全部 setter 为 fluent 并返回本句柄。
 */
@ApiClass(name = "Draw2DImage", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public class Draw2DImage extends Draw2DElement {

    /**
     * Host-side constructor wiring the handle to its scene cell; scripts
     * receive instances from {@code layer.image(...)} only.
     *
     * @param id      element id inside the owning layer
     * @param layer   the layer hosting this element (v1 parent box)
     * @param backend scene bridge publishing and reading the element state
     * @zh 宿主侧构造：把句柄接到其场景单元；脚本只从 {@code layer.image(...)} 获得实例。
     * @zh-param id 元素在其层内的 id
     * @zh-param layer 承载本元素的层（v1 父盒）
     * @zh-param backend 发布与读取元素状态的场景桥
     */
    public Draw2DImage(String id, HudLayer layer, Backend backend) {
        super(id, layer, backend);
    }

    /**
     * Sets the texture identifier, the source offset, the sampled region
     * and the whole-texture dimensions; the destination size is unchanged.
     * A bare name takes the {@code minecraft} namespace; {@code null} or
     * empty clears the texture (the element draws nothing). Region
     * dimensions select the sampled texels on the 2D overlay; {@code 0}
     * keeps the fallback of sampling the destination extent.
     *
     * @param id            texture to draw from, namespace optional
     * @param imageX        source x offset in texels
     * @param imageY        source y offset in texels
     * @param regionWidth   sampled region width in texels ({@code 0} = destination width)
     * @param regionHeight  sampled region height in texels ({@code 0} = destination height)
     * @param textureWidth  whole texture width normalizing the offsets
     * @param textureHeight whole texture height normalizing the offsets
     * @return this element for chaining
     * @zh 设置纹理标识、源偏移、采样区域与整纹理尺寸；目标尺寸不变。裸名取 {@code minecraft} 命名空间；{@code null} 或空串清除纹理（元素不绘制）。区域尺寸在 2D 覆盖层上选择采样 texel；{@code 0} 保持"按目标尺寸采样"回退。
     * @zh-param id 要绘制的纹理，命名空间可省
     * @zh-param imageX 源 x 偏移（texel）
     * @zh-param imageY 源 y 偏移（texel）
     * @zh-param regionWidth 采样区域宽（texel，{@code 0} = 目标宽）
     * @zh-param regionHeight 采样区域高（texel，{@code 0} = 目标高）
     * @zh-param textureWidth 归一化偏移的整纹理宽
     * @zh-param textureHeight 归一化偏移的整纹理高
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DImage setImage(String id, int imageX, int imageY,
            int regionWidth, int regionHeight, int textureWidth,
            int textureHeight) {
        requireAlive();
        backend().publish(backend().spec()
                .withTexture(ElementSpec.textureIdentifier(id))
                .withImageX(imageX)
                .withImageY(imageY)
                .withRegionWidth(regionWidth)
                .withRegionHeight(regionHeight)
                .withTextureWidth(textureWidth)
                .withTextureHeight(textureHeight));
        return this;
    }

    /**
     * The resolved {@code namespace:path} texture identifier this image
     * draws from; {@code undefined} when no texture has been set or once
     * disposed.
     *
     * @zh 本图像绘制所用的解析后 {@code namespace:path} 纹理标识；未设置纹理或失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String getImage() {
        return backend().disposed() ? null : backend().spec().texture();
    }

    /**
     * The source x offset inside the texture, in texels;
     * {@code undefined} once disposed.
     *
     * @zh 纹理内的源 x 偏移（texel）；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getImageX() {
        return backend().disposed() ? null : backend().spec().imageX();
    }

    /**
     * The source y offset inside the texture, in texels;
     * {@code undefined} once disposed.
     *
     * @zh 纹理内的源 y 偏移（texel）；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getImageY() {
        return backend().disposed() ? null : backend().spec().imageY();
    }

    /**
     * The sampled region width in texels — {@code 0} means the draw
     * derives it from the destination size; {@code undefined} once
     * disposed.
     *
     * @zh 采样区域宽（texel）——{@code 0} 表示按目标尺寸推导；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getRegionWidth() {
        return backend().disposed() ? null : backend().spec().regionWidth();
    }

    /**
     * The sampled region height in texels — {@code 0} means the draw
     * derives it from the destination size; {@code undefined} once
     * disposed.
     *
     * @zh 采样区域高（texel）——{@code 0} 表示按目标尺寸推导；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getRegionHeight() {
        return backend().disposed() ? null : backend().spec().regionHeight();
    }

    /**
     * The whole-texture width used to normalize the texel offsets;
     * {@code undefined} once disposed.
     *
     * @zh 归一化 texel 偏移所用的整纹理宽；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getTextureWidth() {
        return backend().disposed() ? null : backend().spec().textureWidth();
    }

    /**
     * The whole-texture height used to normalize the texel offsets;
     * {@code undefined} once disposed.
     *
     * @zh 归一化 texel 偏移所用的整纹理高；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getTextureHeight() {
        return backend().disposed() ? null : backend().spec().textureHeight();
    }

    /**
     * Sets the packed ARGB tint. Folded member (G-033 overload-fold rule —
     * one script binding per name): {@code setColor(color)} keeps the
     * packed color as given (拍板④ double form), {@code setColor(color,
     * alpha)} replaces the top byte, and {@code setColor(r, g, b[, a])}
     * builds the color from channels (opaque unless an alpha is given).
     *
     * @param color  packed color (number or string), or the red channel in rgb forms
     * @param alpha  optional alpha byte, or the green channel in rgb forms
     * @param blue   optional blue channel (rgb form)
     * @param alpha2 optional alpha channel of the rgba form
     * @return this element for chaining
     * @zh 设置图像着色。折叠成员（G-033 重载折叠铁律——每名一个脚本绑定）：{@code setColor(color)} 原样保留打包色（拍板④ 双形），{@code setColor(color, alpha)} 替换顶字节，{@code setColor(r, g, b[, a])} 由通道构建（未给 alpha 则不透明）。
     * @zh-param color 打包色（数字或字符串），rgb 形中为红通道
     * @zh-param alpha 可选 alpha 字节，rgb 形中为绿通道
     * @zh-param blue 可选蓝通道（rgb 形）
     * @zh-param alpha2 rgba 形的可选 alpha 通道
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DImage setColor(
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
     * The packed ARGB tint currently stored on the image;
     * {@code undefined} once disposed.
     *
     * @zh 图像当前存储的打包 ARGB 色；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getColor() {
        return backend().disposed() ? null : backend().spec().color();
    }

    /**
     * The alpha byte of the packed tint, from {@code 0} (transparent) to
     * {@code 255} (opaque); {@code undefined} once disposed.
     *
     * @zh 打包着色的 alpha 字节，{@code 0}（全透明）到 {@code 255}（不透明）；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getAlpha() {
        return backend().disposed() ? null
                : backend().spec().color() >>> 24;
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
    public Draw2DImage setColor(int color) {
        return setColor(color, null, null, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with a 拍板④ color string; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（拍板④ 颜色字符串），不进脚本面。
     */
    @ApiInternal
    public Draw2DImage setColor(String color) {
        return setColor(color, null, null, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with a color and an alpha byte; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（色 + alpha），不进脚本面。
     */
    @ApiInternal
    public Draw2DImage setColor(int color, int alpha) {
        return setColor(color, alpha, null, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with red/green/blue channels (opaque); hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（RGB 通道，不透明），不进脚本面。
     */
    @ApiInternal
    public Draw2DImage setColor(int red, int green, int blue) {
        return setColor(red, green, blue, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with red/green/blue/alpha channels; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（RGBA 通道），不进脚本面。
     */
    @ApiInternal
    public Draw2DImage setColor(int red, int green, int blue, int alpha) {
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
