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
 * Handle to one straight line element living inside a HUD layer, obtained
 * from {@code layer.line(id, …)} upserts. The G-034 component face of
 * {@link Draw2DElement}: the two-endpoint geometry view
 * ({@code x1/y1/x2/y2}), the stroke thickness and the tint color — the
 * transform, z-order, alignment and lifecycle faces are inherited without
 * re-declaration (G-034C contract).
 *
 * <p><b>Endpoint model</b>: the stored box is always the min-corner
 * extent with a non-negative size (so {@code getScaledLeft/Top/Width/Height}
 * and alignment read the bounding box, JsMacros parity), while the
 * element's <i>first</i> endpoint — the default rotation pivot — stays
 * observable through {@link #getX1()} and friends. Endpoints may be given
 * in either order ({@code flipX}/{@code flipY} record the direction).</p>
 *
 * <p><b>{@code width} vs {@code strokeWidth}</b>: the inherited base
 * {@code width} face answers the line's horizontal extent
 * ({@code |x2 - x1|}); the JsMacros stroke thickness lives under
 * {@link #setStrokeWidth}/{@link #getStrokeWidth()} — the base face may
 * not be re-declared by subclasses (G-034C contract), so the
 * task-card {@code Line.setWidth/getWidth} name pair bridges the extent
 * semantics and the stroke moved to its own member (documented
 * deviation).</p>
 *
 * <p><b>Element is its own builder</b> (拍板②): every setter is fluent and
 * returns this handle; the JsMacros {@code Line.Builder} family folds into
 * it.</p>
 *
 * @zh HUD 图层内单个直线元素的句柄，由 {@code layer.line(id, …)} upsert 返回。{@link Draw2DElement} 的 G-034 组件面：双端点几何视图（{@code x1/y1/x2/y2}）、笔画粗细与着色——变换、z 序、对齐、生命周期全部继承且不重声明（G-034C 契约）。
 * @zh <b>端点模型</b>：存储盒恒为最小角跨度 + 非负尺寸（因此 {@code getScaledLeft/Top/Width/Height} 与对齐读包围盒，JsMacros 对等），而元素的<i>第一</i>端点（默认旋转枢轴）经 {@link #getX1()} 等仍可观测。端点可任意顺序给出（{@code flipX}/{@code flipY} 记录方向）。
 * @zh <b>{@code width} 与 {@code strokeWidth}</b>：继承的基类 {@code width} 面回答线的横向跨度（{@code |x2 - x1|}）；JsMacros 的笔画粗细落在 {@link #setStrokeWidth}/{@link #getStrokeWidth()}——子类不得重声明基类面（G-034C 契约），因此任务卡 {@code Line.setWidth/getWidth} 名对桥接跨度语义、笔画粗细移至独立成员（已记录偏离）。
 * @zh <b>元素即 Builder</b>（拍板②）：全部 setter 为 fluent 并返回本句柄；JsMacros 的 {@code Line.Builder} 类族折叠于此。
 */
@ApiClass(name = "Draw2DLine", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public class Draw2DLine extends Draw2DElement {

    /**
     * Host-side constructor wiring the handle to its scene cell; scripts
     * receive instances from {@code layer.line(...)} only.
     *
     * @param id      element id inside the owning layer
     * @param layer   the layer hosting this element (v1 parent box)
     * @param backend scene bridge publishing and reading the element state
     * @zh 宿主侧构造：把句柄接到其场景单元；脚本只从 {@code layer.line(...)} 获得实例。
     * @zh-param id 元素在其层内的 id
     * @zh-param layer 承载本元素的层（v1 父盒）
     * @zh-param backend 发布与读取元素状态的场景桥
     */
    public Draw2DLine(String id, HudLayer layer, Backend backend) {
        super(id, layer, backend);
    }

    /**
     * Sets the first endpoint's x coordinate without changing the other
     * coordinates; the stored box re-anchors to the min corner and the
     * endpoint direction flips when the first endpoint crosses the second.
     *
     * @param x1 x pixel of the first endpoint
     * @return this element for chaining
     * @zh 设置第一端点的 x 坐标且不改变其余坐标；存储盒重锚到最小角，第一端点越过第二端点时方向翻转。
     * @zh-param x1 第一端点的 x 像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DLine setX1(int x1) {
        requireAlive();
        Box box = resolved();
        return publishBox(rebox(x1, box.firstY, box.secondX, box.secondY));
    }

    /**
     * The x coordinate of the first endpoint — it need not be the
     * leftmost; {@code undefined} once disposed.
     *
     * @zh 第一端点的 x 坐标——不必是最左点；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getX1() {
        return backend().disposed() ? null : resolved().firstX;
    }

    /**
     * Sets the first endpoint's y coordinate without changing the other
     * coordinates.
     *
     * @param y1 y pixel of the first endpoint
     * @return this element for chaining
     * @zh 设置第一端点的 y 坐标且不改变其余坐标。
     * @zh-param y1 第一端点的 y 像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DLine setY1(int y1) {
        requireAlive();
        Box box = resolved();
        return publishBox(rebox(box.firstX, y1, box.secondX, box.secondY));
    }

    /**
     * The y coordinate of the first endpoint — it need not be the
     * topmost; {@code undefined} once disposed.
     *
     * @zh 第一端点的 y 坐标——不必是最上点；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getY1() {
        return backend().disposed() ? null : resolved().firstY;
    }

    /**
     * Sets both coordinates of the first endpoint without changing the
     * second.
     *
     * @param x1 x pixel of the first endpoint
     * @param y1 y pixel of the first endpoint
     * @return this element for chaining
     * @zh 同时设置第一端点的两个坐标，第二端点不变。
     * @zh-param x1 第一端点的 x 像素
     * @zh-param y1 第一端点的 y 像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DLine setPos1(int x1, int y1) {
        requireAlive();
        Box box = resolved();
        return publishBox(rebox(x1, y1, box.secondX, box.secondY));
    }

    /**
     * Sets the second endpoint's x coordinate without changing the other
     * coordinates; a value left of the first endpoint flips the direction.
     *
     * @param x2 x pixel of the second endpoint
     * @return this element for chaining
     * @zh 设置第二端点的 x 坐标且不改变其余坐标；小于第一端点的值会翻转方向。
     * @zh-param x2 第二端点的 x 像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DLine setX2(int x2) {
        requireAlive();
        Box box = resolved();
        return publishBox(rebox(box.firstX, box.firstY, x2, box.secondY));
    }

    /**
     * The x coordinate of the second endpoint — it need not be the
     * rightmost; {@code undefined} once disposed.
     *
     * @zh 第二端点的 x 坐标——不必是最右点；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getX2() {
        return backend().disposed() ? null : resolved().secondX;
    }

    /**
     * Sets the second endpoint's y coordinate without changing the other
     * coordinates.
     *
     * @param y2 y pixel of the second endpoint
     * @return this element for chaining
     * @zh 设置第二端点的 y 坐标且不改变其余坐标。
     * @zh-param y2 第二端点的 y 像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DLine setY2(int y2) {
        requireAlive();
        Box box = resolved();
        return publishBox(rebox(box.firstX, box.firstY, box.secondX, y2));
    }

    /**
     * The y coordinate of the second endpoint; {@code undefined} once
     * disposed.
     *
     * @zh 第二端点的 y 坐标；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getY2() {
        return backend().disposed() ? null : resolved().secondY;
    }

    /**
     * Sets both coordinates of the second endpoint, leaving the first
     * unchanged.
     *
     * @param x2 x pixel of the second endpoint
     * @param y2 y pixel of the second endpoint
     * @return this element for chaining
     * @zh 同时设置第二端点的两个坐标，第一端点不变。
     * @zh-param x2 第二端点的 x 像素
     * @zh-param y2 第二端点的 y 像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DLine setPos2(int x2, int y2) {
        requireAlive();
        Box box = resolved();
        return publishBox(rebox(box.firstX, box.firstY, x2, y2));
    }

    /**
     * Sets the line's stroke thickness in screen pixels. Values are stored
     * as-is; non-positive thickness draws nothing, fractional thickness is
     * rounded up when drawn (JsMacros parity). The inherited {@code width}
     * face keeps answering the line's horizontal extent — see the class
     * javadoc.
     *
     * @param width stroke thickness in screen pixels
     * @return this element for chaining
     * @zh 设置线的笔画粗细（屏幕像素）。数值原样存储；非正粗细不绘制，小数粗细绘制时向上取整（JsMacros 对等）。继承的 {@code width} 面仍回答线的横向跨度——见类 javadoc。
     * @zh-param width 笔画粗细（屏幕像素）
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DLine setStrokeWidth(double width) {
        requireAlive();
        backend().publish(backend().spec().withStroke((float) width));
        return this;
    }

    /**
     * The line's stroke thickness in screen pixels; {@code undefined} once
     * disposed.
     *
     * @zh 线的笔画粗细（屏幕像素）；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Float getStrokeWidth() {
        return backend().disposed() ? null : backend().spec().stroke();
    }

    /**
     * Sets the packed ARGB color. Folded member (G-033 overload-fold rule —
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
     * @zh 设置线的着色。折叠成员（G-033 重载折叠铁律——每名一个脚本绑定）：{@code setColor(color)} 原样保留打包色（拍板④ 双形），{@code setColor(color, alpha)} 替换顶字节，{@code setColor(r, g, b[, a])} 由通道构建（未给 alpha 则不透明）。
     * @zh-param color 打包色（数字或字符串），rgb 形中为红通道
     * @zh-param alpha 可选 alpha 字节，rgb 形中为绿通道
     * @zh-param blue 可选蓝通道（rgb 形）
     * @zh-param alpha2 rgba 形的可选 alpha 通道
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DLine setColor(
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
     * The packed ARGB color currently stored on the line; {@code undefined}
     * once disposed.
     *
     * @zh 线当前存储的打包 ARGB 色；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getColor() {
        return backend().disposed() ? null : backend().spec().color();
    }

    /**
     * Sets the alpha byte without changing the RGB channels; values are
     * not clamped (only their low eight bits survive, JsMacros parity).
     *
     * @param alpha alpha byte ({@code 0} transparent … {@code 255} opaque)
     * @return this element for chaining
     * @zh 设置 alpha 字节且不改变 RGB 通道；数值不钳制（仅低八位生效，JsMacros 对等）。
     * @zh-param alpha alpha 字节（{@code 0} 全透明 … {@code 255} 不透明）
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DLine setAlpha(int alpha) {
        requireAlive();
        int color = backend().spec().color();
        backend().publish(backend().spec().withColor(
                alpha << 24 | (color & 0x00FFFFFF)));
        return this;
    }

    /**
     * The alpha byte of the packed color, from {@code 0} (transparent) to
     * {@code 255} (opaque); {@code undefined} once disposed.
     *
     * @zh 打包色的 alpha 字节，{@code 0}（全透明）到 {@code 255}（不透明）；失效后为 {@code undefined}。
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
    public Draw2DLine setColor(int color) {
        return setColor(color, null, null, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with a 拍板④ color string; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（拍板④ 颜色字符串），不进脚本面。
     */
    @ApiInternal
    public Draw2DLine setColor(String color) {
        return setColor(color, null, null, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with a color and an alpha byte; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（色 + alpha），不进脚本面。
     */
    @ApiInternal
    public Draw2DLine setColor(int color, int alpha) {
        return setColor(color, alpha, null, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with red/green/blue channels (opaque); hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（RGB 通道，不透明），不进脚本面。
     */
    @ApiInternal
    public Draw2DLine setColor(int red, int green, int blue) {
        return setColor(red, green, blue, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with red/green/blue/alpha channels; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（RGBA 通道），不进脚本面。
     */
    @ApiInternal
    public Draw2DLine setColor(int red, int green, int blue, int alpha) {
        return setColor((Object) red, (Object) green, (Object) blue,
                (Object) alpha);
    }

    /** The resolved box plus both endpoints in absolute pixels. */
    private record Box(int x, int y, int w, int h, int firstX, int firstY,
            int secondX, int secondY) {
    }

    /** Reads the live resolved box and its derived endpoints. */
    private Box resolved() {
        ElementSpec spec = backend().spec();
        int viewportWidth = backend().viewportWidth();
        int viewportHeight = backend().viewportHeight();
        int x = spec.x().resolve(viewportWidth);
        int y = spec.y().resolve(viewportHeight);
        int w = spec.w().resolve(viewportWidth);
        int h = spec.h().resolve(viewportHeight);
        int firstX = spec.flipX() ? x + w : x;
        int firstY = spec.flipY() ? y + h : y;
        int secondX = spec.flipX() ? x : x + w;
        int secondY = spec.flipY() ? y : y + h;
        return new Box(x, y, w, h, firstX, firstY, secondX, secondY);
    }

    /** Rebuilds the min-corner box from two arbitrary endpoints. */
    private static Box rebox(int firstX, int firstY, int secondX, int secondY) {
        int x = Math.min(firstX, secondX);
        int y = Math.min(firstY, secondY);
        return new Box(x, y, Math.abs(secondX - firstX),
                Math.abs(secondY - firstY), firstX, firstY, secondX, secondY);
    }

    /** Publishes an endpoint edit as the min-corner box plus flip flags. */
    private Draw2DLine publishBox(Box box) {
        backend().publish(backend().spec()
                .withX(ElementSpec.Coord.at(box.x()))
                .withY(ElementSpec.Coord.at(box.y()))
                .withW(ElementSpec.Coord.at(box.w()))
                .withH(ElementSpec.Coord.at(box.h()))
                .withFlipX(box.firstX() > box.secondX())
                .withFlipY(box.firstY() > box.secondY()));
        return this;
    }

    /** Throws when the handle is no longer valid; used by every mutator. */
    private void requireAlive() {
        if (backend().disposed()) {
            throw new IllegalStateException("element no longer valid");
        }
    }
}
