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
 * Handle to one filled rectangle element living inside a HUD layer,
 * obtained from {@code layer.rect(id, …)} upserts. The G-034 component
 * face of {@link Draw2DElement}: the two-corner geometry view
 * ({@code x1/y1/x2/y2}) and the tint color — box size, transform,
 * z-order, alignment and lifecycle are inherited without re-declaration
 * (G-034C contract).
 *
 * <p><b>Corner model</b>: the stored box is always the min-corner origin
 * with a non-negative size (the G-032 native form), while the element's
 * <i>first</i> corner is observable through {@link #getX1()} and friends.
 * Corners may be given in either order — reversing them flips the
 * {@code flipX}/{@code flipY} flags; {@code getWidth()} is the absolute
 * distance between the corners, and the base {@code setWidth} anchors the
 * smaller coordinate exactly like JsMacros.</p>
 *
 * <p><b>Element is its own builder</b> (拍板②): every setter is fluent and
 * returns this handle; the JsMacros {@code Rect.Builder} family folds into
 * it.</p>
 *
 * @zh HUD 图层内单个填充矩形元素的句柄，由 {@code layer.rect(id, …)} upsert 返回。{@link Draw2DElement} 的 G-034 组件面：双角几何视图（{@code x1/y1/x2/y2}）与着色——盒尺寸、变换、z 序、对齐、生命周期全部继承且不重声明（G-034C 契约）。
 * @zh <b>角点模型</b>：存储盒恒为最小角原点 + 非负尺寸（G-032 原生形态），而元素的<i>第一</i>角经 {@link #getX1()} 等可观测。角点可任意顺序给出——反序会翻转 {@code flipX}/{@code flipY} 标志；{@code getWidth()} 为两角绝对距离，基类 {@code setWidth} 与 JsMacros 一样锚定较小坐标。
 * @zh <b>元素即 Builder</b>（拍板②）：全部 setter 为 fluent 并返回本句柄；JsMacros 的 {@code Rect.Builder} 类族折叠于此。
 */
@ApiClass(name = "Draw2DRect", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public class Draw2DRect extends Draw2DElement {

    /**
     * Host-side constructor wiring the handle to its scene cell; scripts
     * receive instances from {@code layer.rect(...)} only.
     *
     * @param id      element id inside the owning layer
     * @param layer   the layer hosting this element (v1 parent box)
     * @param backend scene bridge publishing and reading the element state
     * @zh 宿主侧构造：把句柄接到其场景单元；脚本只从 {@code layer.rect(...)} 获得实例。
     * @zh-param id 元素在其层内的 id
     * @zh-param layer 承载本元素的层（v1 父盒）
     * @zh-param backend 发布与读取元素状态的场景桥
     */
    public Draw2DRect(String id, HudLayer layer, Backend backend) {
        super(id, layer, backend);
    }

    /**
     * Sets the first corner's x coordinate without changing the second
     * corner; the stored box re-anchors to the min corner and the corner
     * order flips when the first corner crosses the second.
     *
     * @param x1 x pixel of the first corner
     * @return this element for chaining
     * @zh 设置第一角的 x 坐标且不改变第二角；存储盒重锚到最小角，第一角越过第二角时角点次序翻转。
     * @zh-param x1 第一角的 x 像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DRect setX1(int x1) {
        requireAlive();
        Box box = resolved();
        return publishBox(moveFirst(box, x1, box.firstY, box.secondX,
                box.secondY, true, false));
    }

    /**
     * The x coordinate of the first corner — not necessarily the left
     * edge; {@code undefined} once disposed.
     *
     * @zh 第一角的 x 坐标——不必是左边缘；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getX1() {
        return backend().disposed() ? null : resolved().firstX;
    }

    /**
     * Sets the first corner's y coordinate without changing the second
     * corner.
     *
     * @param y1 y pixel of the first corner
     * @return this element for chaining
     * @zh 设置第一角的 y 坐标且不改变第二角。
     * @zh-param y1 第一角的 y 像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DRect setY1(int y1) {
        requireAlive();
        Box box = resolved();
        return publishBox(moveFirst(box, box.firstX, y1, box.secondX,
                box.secondY, false, true));
    }

    /**
     * The y coordinate of the first corner — not necessarily the top edge;
     * {@code undefined} once disposed.
     *
     * @zh 第一角的 y 坐标——不必是上边缘；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getY1() {
        return backend().disposed() ? null : resolved().firstY;
    }

    /**
     * Sets both coordinates of the first corner, leaving the second
     * unchanged.
     *
     * @param x1 x pixel of the first corner
     * @param y1 y pixel of the first corner
     * @return this element for chaining
     * @zh 同时设置第一角的两个坐标，第二角不变。
     * @zh-param x1 第一角的 x 像素
     * @zh-param y1 第一角的 y 像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DRect setPos1(int x1, int y1) {
        requireAlive();
        Box box = resolved();
        return publishBox(moveFirst(box, x1, y1, box.secondX, box.secondY,
                true, true));
    }

    /**
     * Sets the second corner's x coordinate without changing the first
     * corner; a value left of the first corner flips the corner order.
     *
     * @param x2 x pixel of the second corner
     * @return this element for chaining
     * @zh 设置第二角的 x 坐标且不改变第一角；小于第一角的值会翻转角点次序。
     * @zh-param x2 第二角的 x 像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DRect setX2(int x2) {
        requireAlive();
        Box box = resolved();
        return publishBox(moveSecond(box, box.firstX, box.firstY, x2,
                box.secondY, true, false));
    }

    /**
     * The x coordinate of the second corner — not necessarily the right
     * edge; {@code undefined} once disposed.
     *
     * @zh 第二角的 x 坐标——不必是右边缘；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getX2() {
        return backend().disposed() ? null : resolved().secondX;
    }

    /**
     * Sets the second corner's y coordinate without changing the first
     * corner.
     *
     * @param y2 y pixel of the second corner
     * @return this element for chaining
     * @zh 设置第二角的 y 坐标且不改变第一角。
     * @zh-param y2 第二角的 y 像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DRect setY2(int y2) {
        requireAlive();
        Box box = resolved();
        return publishBox(moveSecond(box, box.firstX, box.firstY,
                box.secondX, y2, false, true));
    }

    /**
     * The y coordinate of the second corner — its other corner rather
     * than an edge; {@code undefined} once disposed.
     *
     * @zh 第二角的 y 坐标——是另一个角而非边缘；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getY2() {
        return backend().disposed() ? null : resolved().secondY;
    }

    /**
     * Sets both coordinates of the second corner, leaving the first
     * unchanged.
     *
     * @param x2 x pixel of the second corner
     * @param y2 y pixel of the second corner
     * @return this element for chaining
     * @zh 同时设置第二角的两个坐标，第一角不变。
     * @zh-param x2 第二角的 x 像素
     * @zh-param y2 第二角的 y 像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DRect setPos2(int x2, int y2) {
        requireAlive();
        Box box = resolved();
        return publishBox(moveSecond(box, box.firstX, box.firstY, x2, y2,
                true, true));
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
     * @zh 设置矩形着色。折叠成员（G-033 重载折叠铁律——每名一个脚本绑定）：{@code setColor(color)} 原样保留打包色（拍板④ 双形），{@code setColor(color, alpha)} 替换顶字节，{@code setColor(r, g, b[, a])} 由通道构建（未给 alpha 则不透明）。
     * @zh-param color 打包色（数字或字符串），rgb 形中为红通道
     * @zh-param alpha 可选 alpha 字节，rgb 形中为绿通道
     * @zh-param blue 可选蓝通道（rgb 形）
     * @zh-param alpha2 rgba 形的可选 alpha 通道
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DRect setColor(
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
     * The packed ARGB color currently stored on the rectangle;
     * {@code undefined} once disposed.
     *
     * @zh 矩形当前存储的打包 ARGB 色；失效后为 {@code undefined}。
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
    public Draw2DRect setAlpha(int alpha) {
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
    public Draw2DRect setColor(int color) {
        return setColor(color, null, null, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with a 拍板④ color string; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（拍板④ 颜色字符串），不进脚本面。
     */
    @ApiInternal
    public Draw2DRect setColor(String color) {
        return setColor(color, null, null, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with a color and an alpha byte; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（色 + alpha），不进脚本面。
     */
    @ApiInternal
    public Draw2DRect setColor(int color, int alpha) {
        return setColor(color, alpha, null, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with red/green/blue channels (opaque); hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（RGB 通道，不透明），不进脚本面。
     */
    @ApiInternal
    public Draw2DRect setColor(int red, int green, int blue) {
        return setColor(red, green, blue, null);
    }

    /**
     * Java-side shortcut for {@link #setColor(Object, Object, Object, Object)}
     * with red/green/blue/alpha channels; hidden from the script surface.
     *
     * @zh {@link #setColor(Object, Object, Object, Object)} 的 Java 捷径（RGBA 通道），不进脚本面。
     */
    @ApiInternal
    public Draw2DRect setColor(int red, int green, int blue, int alpha) {
        return setColor((Object) red, (Object) green, (Object) blue,
                (Object) alpha);
    }

    /** The resolved box plus both corners in absolute pixels. */
    private record Box(int x, int y, int w, int h, int firstX, int firstY,
            int secondX, int secondY) {
    }

    /** Reads the live resolved box and its derived corners. */
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

    /** Moves the first corner; the second corner stays put. */
    private static Box moveFirst(Box box, int firstX, int firstY,
            int secondX, int secondY, boolean moveX, boolean moveY) {
        int targetX = moveX ? firstX : box.firstX;
        int targetY = moveY ? firstY : box.firstY;
        return rebox(targetX, targetY, secondX, secondY);
    }

    /** Moves the second corner; the first corner stays put. */
    private static Box moveSecond(Box box, int firstX, int firstY,
            int secondX, int secondY, boolean moveX, boolean moveY) {
        int targetX = moveX ? secondX : box.secondX;
        int targetY = moveY ? secondY : box.secondY;
        return rebox(firstX, firstY, targetX, targetY);
    }

    /** Rebuilds the min-corner box from two arbitrary corners. */
    private static Box rebox(int firstX, int firstY, int secondX, int secondY) {
        int x = Math.min(firstX, secondX);
        int y = Math.min(firstY, secondY);
        return new Box(x, y, Math.abs(secondX - firstX),
                Math.abs(secondY - firstY), firstX, firstY, secondX, secondY);
    }

    /** Publishes a corner edit as the min-corner box plus flip flags. */
    private Draw2DRect publishBox(Box box) {
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
