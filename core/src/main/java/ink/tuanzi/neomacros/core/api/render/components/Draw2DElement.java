package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.core.api.render.HudLayer;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.Locale;

/**
 * Handle to one element living inside a HUD layer, obtained from
 * {@code layer.text(...)} / {@code layer.rect(...)} upserts and from
 * {@code layer.element(id)} / {@code layer.elements}. This class is the
 * base face of the component family: {@code Draw2DText} / {@code Draw2DRect}
 * (G-034), {@code Draw2DImage} / {@code Draw2DItem} (G-034B) and the
 * alignment surface (G-034C) extend it without changing the geometry,
 * transform, z-order and lifecycle contract declared here.
 *
 * <p><b>Element is its own builder</b> (拍板②): every setter is fluent and
 * returns {@code this}, so configuration and mutation are one object at one
 * moment, with no parallel {@code XxxBuilder} class tree.</p>
 *
 * <p><b>State model</b>: the element owns no mutable fields of its own.
 * Setters republish an immutable {@link ElementSpec} through the
 * {@link Backend} bridge (volatile write, next frame consumes it — G-001
 * §2.2); getters read the current snapshot. Geometry accepts absolute
 * scaled pixels or viewport percentage strings ({@code "50%"}), resolving
 * against the layer viewport at paint time.</p>
 *
 * <p><b>Disposal</b>: the handle turns disposed when its element is
 * removed, replaced by a different component kind, cleared from the layer,
 * or the layer itself is removed. Afterwards every read property answers
 * {@code undefined} (Java {@code null}) and every setter throws
 * {@code IllegalStateException: element no longer valid}; only
 * {@link #isDisposed()} stays readable.</p>
 *
 * @zh HUD 图层内单个元素的句柄，由 {@code layer.text(...)} / {@code layer.rect(...)} upsert 与 {@code layer.element(id)} / {@code layer.elements} 返回。本类是组件族的基类面：{@code Draw2DText} / {@code Draw2DRect}（G-034）、{@code Draw2DImage} / {@code Draw2DItem}（G-034B）与对齐面（G-034C）在不改动此处几何、变换、z 序与生命周期契约的前提下扩展它。
 * @zh <b>元素即 Builder</b>（拍板②）：全部 setter 为 fluent 并返回 {@code this}，配置与写入是同一对象同一时刻，不另建 {@code XxxBuilder} 类树。
 * @zh <b>状态模型</b>：元素自身不持有可变字段。setter 经 {@link Backend} 桥重新发布不可变 {@link ElementSpec}（volatile 写入，下一帧消费——G-001 §2.2）；getter 读取当前快照。几何接受缩放像素绝对值或视口百分比字符串（{@code "50%"}），绘制时按层视口求值。
 * @zh <b>失效</b>：元素被移除、被不同组件类型替换、随层 clear 或整层移除后句柄即失效；此后所有读属性返回 {@code undefined}（Java {@code null}），所有 setter 抛出 {@code IllegalStateException: element no longer valid}；仅 {@link #isDisposed()} 保持可读。
 */
@ApiClass(name = "Draw2DElement", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public class Draw2DElement implements ScriptHandle, Alignable, RenderElement {

    private static final String DISPOSED_MESSAGE = "element no longer valid";

    private final String id;
    private final HudLayer layer;
    private final Backend backend;

    /**
     * Host-side constructor wiring the handle to its scene cell; scripts
     * receive instances from the layer face and never build their own.
     * Component subclasses (G-034 / G-034B) are constructed the same way
     * once their component kind lands in the scene factory.
     *
     * @param id      element id inside the owning layer
     * @param layer   the layer hosting this element (v1 parent box)
     * @param backend scene bridge publishing and reading the element state
     */
    public Draw2DElement(String id, HudLayer layer, Backend backend) {
        this.id = id;
        this.layer = layer;
        this.backend = backend;
    }

    /**
     * Host-side state bridge between this handle and the scene cell that
     * owns its {@link ElementSpec}. Implemented by the core scene graph
     * ({@code HudScene}); scripts never see it. Component tasks extend the
     * contract by adding fields to {@link ElementSpec} and reading them
     * through {@link #spec()}, then republishing with {@link #publish}.
     *
     * @zh 本句柄与持有其 {@link ElementSpec} 的场景单元之间的宿主侧状态桥。由 core 场景图（{@code HudScene}）实现；脚本不可见。组件任务通过给 {@link ElementSpec} 加字段、经 {@link #spec()} 读取并用 {@link #publish} 重新发布来扩展契约。
     */
    @ApiInternal
    public interface Backend {

        /**
         * The current immutable state snapshot (never null while the
         * element is alive).
         *
         * @zh 当前不可变状态快照（元素存活期间不为 null）。
         */
        ElementSpec spec();

        /**
         * Republishes the element state; a z-order change bumps the scene
         * structure generation so the draw order rebuilds next frame.
         * Implementations re-check disposal and throw when the cell died
         * between a setter's alive-check and this call.
         *
         * @param next the replacement snapshot
         * @zh 重新发布元素状态；z 序变更会推进场景结构代数，使绘制序下一帧重建。实现在 setter 存活检查与本调用之间单元已亡时复查失效并抛错。
         */
        void publish(ElementSpec next);

        /**
         * Current viewport width in scaled pixels ({@code 0} before the
         * window exists) — the parent-box extent for percentage geometry.
         *
         * @zh 当前视口宽度（缩放后像素，窗口未就绪时为 {@code 0}）——百分比几何的父盒宽度。
         */
        int viewportWidth();

        /**
         * Current viewport height in scaled pixels ({@code 0} before the
         * window exists).
         *
         * @zh 当前视口高度（缩放后像素，窗口未就绪时为 {@code 0}）。
         */
        int viewportHeight();

        /**
         * Live disposal probe: the cell was removed or the scene disposed.
         *
         * @zh 存活探针：单元已移除或场景已失效。
         */
        boolean disposed();
    }

    /**
     * Resolved absolute box of the element: the origin in scaled pixels
     * plus the size after percentage resolution and {@link #getScale()},
     * truncated toward zero. {@code undefined} once the handle is
     * disposed.
     *
     * @zh 元素解析后的绝对盒：缩放像素原点，加上百分比求值并乘以 {@link #getScale()} 后的尺寸（向零截断）。句柄失效后为 {@code undefined}。
     */
    @ApiClass(name = "Bounds", kind = ApiClassKind.DATA)
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record Bounds(int x, int y, int width, int height) {
    }

    /**
     * The element id inside its layer; {@code undefined} once disposed.
     *
     * @zh 元素在其层内的 id；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getId() {
        return backend.disposed() ? null : id;
    }

    /**
     * Whether this handle has been invalidated (element removed, replaced,
     * cleared, or the layer disposed). Always readable.
     *
     * @zh 句柄是否已失效（元素被移除、替换、clear 或整层失效）。永远可读。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public boolean isDisposed() {
        return backend.disposed();
    }

    /**
     * The layer hosting this element — the layer back-reference;
     * {@code undefined} once disposed.
     *
     * @zh 承载本元素的图层——图层反向引用；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public HudLayer getLayer() {
        return backend.disposed() ? null : layer;
    }

    /**
     * The parent box used for parent-size queries. The v1 container is
     * one level deep, so this is always the hosting layer (nested parents
     * arrive with the P3 group element); {@code undefined} once disposed.
     *
     * @zh 用于父盒尺寸查询的父级。v1 容器只有一层，因此恒为所在图层（嵌套父级随 P3 组元素到来）；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public HudLayer getParent() {
        return backend.disposed() ? null : layer;
    }

    /**
     * The raw x position: absolute scaled pixels, or the percentage string
     * it was configured with; {@code undefined} once disposed.
     *
     * @zh 原始 x 位置：缩放像素绝对值，或配置时的百分比字符串；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    @ApiType("number | string")
    public Object getX() {
        return backend.disposed() ? null : backend.spec().x().raw();
    }

    /**
     * The raw y position: absolute scaled pixels, or the percentage string
     * it was configured with; {@code undefined} once disposed.
     *
     * @zh 原始 y 位置：缩放像素绝对值，或配置时的百分比字符串；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    @ApiType("number | string")
    public Object getY() {
        return backend.disposed() ? null : backend.spec().y().raw();
    }

    /**
     * The raw box width before scaling: absolute scaled pixels or the
     * percentage string it was configured with ({@code 0} for text until
     * sized); {@code undefined} once disposed. Use {@link #getScaledWidth()}
     * for the resolved, scale-applied number.
     *
     * @zh 缩放前的原始盒宽：缩放像素绝对值或配置时的百分比字符串（文本在显式设宽前为 {@code 0}）；失效后为 {@code undefined}。求值并乘以缩放的数值请用 {@link #getScaledWidth()}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    @ApiType("number | string")
    public Object getWidth() {
        return backend.disposed() ? null : backend.spec().w().raw();
    }

    /**
     * The raw box height before scaling: absolute scaled pixels or the
     * percentage string it was configured with; {@code undefined} once
     * disposed. Use {@link #getScaledHeight()} for the resolved number.
     *
     * @zh 缩放前的原始盒高：缩放像素绝对值或配置时的百分比字符串；失效后为 {@code undefined}。求值后的数值请用 {@link #getScaledHeight()}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    @ApiType("number | string")
    public Object getHeight() {
        return backend.disposed() ? null : backend.spec().h().raw();
    }

    /**
     * The render scale applied to this element; defaults to {@code 1}.
     * {@code undefined} once disposed.
     *
     * @zh 应用于本元素的渲染缩放；默认 {@code 1}。失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Float getScale() {
        return backend.disposed() ? null : backend.spec().scale();
    }

    /**
     * The rotation in degrees, normalized to {@code [-180, 180)} by
     * constructors and setters; defaults to {@code 0}. {@code undefined}
     * once disposed.
     *
     * @zh 旋转角度（度），由 setter 归一到 {@code [-180, 180)}；默认 {@code 0}。失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Float getRotation() {
        return backend.disposed() ? null : backend.spec().rotation();
    }

    /**
     * Whether rotation pivots about the element center rather than its
     * positioned corner; defaults to {@code false}. {@code undefined} once
     * disposed.
     *
     * @zh 旋转是否以元素中心为轴而非定位角；默认 {@code false}。失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isRotateCenter() {
        return backend.disposed() ? null : backend.spec().rotateCenter();
    }

    /**
     * This element's ordering value on its layer: lower values draw first,
     * ties keep insertion order; defaults to {@code 0}. {@code undefined}
     * once disposed.
     *
     * @zh 元素在其层上的绘制序：数值小者先画，同值按插入序；默认 {@code 0}。失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getZIndex() {
        return backend.disposed() ? null : backend.spec().zIndex();
    }

    /**
     * Whether this element paints. An invisible element keeps its state
     * and slot; it simply paints nothing. Defaults to {@code true}.
     * {@code undefined} once disposed.
     *
     * @zh 本元素是否绘制。不可见元素保留状态与插入位，只是不画。默认 {@code true}。失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isVisible() {
        return backend.disposed() ? null : backend.spec().visible();
    }

    /**
     * The resolved absolute box: percentage geometry evaluated against the
     * current viewport, width and height multiplied by {@link #getScale()}
     * and truncated toward zero; {@code undefined} once disposed.
     *
     * @zh 解析后的绝对盒：百分比几何按当前视口求值，宽高乘以 {@code getScale()} 并向零截断；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Bounds getBounds() {
        if (backend.disposed()) {
            return null;
        }
        ElementSpec spec = backend.spec();
        int viewportWidth = backend.viewportWidth();
        int viewportHeight = backend.viewportHeight();
        return new Bounds(spec.x().resolve(viewportWidth),
                spec.y().resolve(viewportHeight),
                scaled(spec.w().resolve(viewportWidth), spec.scale()),
                scaled(spec.h().resolve(viewportHeight), spec.scale()));
    }

    /**
     * The resolved width multiplied by {@link #getScale()}, truncated
     * toward zero (the extent alignment math uses); {@code undefined} once
     * disposed.
     *
     * @zh 求值后的宽度乘以 {@code getScale()} 并向零截断（对齐计算使用的边长）；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getScaledWidth() {
        if (backend.disposed()) {
            return null;
        }
        ElementSpec spec = backend.spec();
        return scaled(spec.w().resolve(backend.viewportWidth()), spec.scale());
    }

    /**
     * The resolved height multiplied by {@link #getScale()}, truncated
     * toward zero; {@code undefined} once disposed.
     *
     * @zh 求值后的高度乘以 {@code getScale()} 并向零截断；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getScaledHeight() {
        if (backend.disposed()) {
            return null;
        }
        ElementSpec spec = backend.spec();
        return scaled(spec.h().resolve(backend.viewportHeight()), spec.scale());
    }

    /**
     * The resolved left edge — scaling does not move it; {@code undefined}
     * once disposed.
     *
     * @zh 求值后的左边缘——缩放不改变它；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getScaledLeft() {
        return backend.disposed() ? null
                : backend.spec().x().resolve(backend.viewportWidth());
    }

    /**
     * The resolved top edge — scaling does not move it; {@code undefined}
     * once disposed.
     *
     * @zh 求值后的上边缘——缩放不改变它；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getScaledTop() {
        return backend.disposed() ? null
                : backend.spec().y().resolve(backend.viewportHeight());
    }

    /**
     * The parent box width in scaled pixels — the hosting layer's viewport
     * in v1; {@code undefined} once disposed.
     *
     * @zh 父盒宽度（缩放后像素）——v1 中即所在层的视口宽；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getParentWidth() {
        return backend.disposed() ? null : backend.viewportWidth();
    }

    /**
     * The parent box height in scaled pixels — the hosting layer's
     * viewport in v1; {@code undefined} once disposed.
     *
     * @zh 父盒高度（缩放后像素）——v1 中即所在层的视口高；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getParentHeight() {
        return backend.disposed() ? null : backend.viewportHeight();
    }

    /**
     * Sets the x position without changing y or dimensions.
     *
     * @param x absolute scaled pixels, or a percentage string such as {@code "50%"}
     * @return this element for chaining
     * @zh 设置 x 位置，不改变 y 与尺寸。
     * @zh-param x 缩放像素绝对值，或 {@code "50%"} 形式的百分比字符串
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement setX(@ApiType("number | string") Object x) {
        ensureAlive();
        backend.publish(backend.spec().withX(
                ElementSpec.Coord.parse(x, "x")));
        return this;
    }

    /**
     * Sets the y position without changing x or dimensions.
     *
     * @param y absolute scaled pixels, or a percentage string such as {@code "50%"}
     * @return this element for chaining
     * @zh 设置 y 位置，不改变 x 与尺寸。
     * @zh-param y 缩放像素绝对值，或 {@code "50%"} 形式的百分比字符串
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement setY(@ApiType("number | string") Object y) {
        ensureAlive();
        backend.publish(backend.spec().withY(
                ElementSpec.Coord.parse(y, "y")));
        return this;
    }

    /**
     * Sets both position coordinates without changing dimensions.
     *
     * @param x absolute scaled pixels or a percentage string
     * @param y absolute scaled pixels or a percentage string
     * @return this element for chaining
     * @zh 同时设置两个坐标，不改变尺寸。
     * @zh-param x 缩放像素绝对值或百分比字符串
     * @zh-param y 缩放像素绝对值或百分比字符串
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement setPos(@ApiType("number | string") Object x,
            @ApiType("number | string") Object y) {
        ensureAlive();
        backend.publish(backend.spec()
                .withX(ElementSpec.Coord.parse(x, "x"))
                .withY(ElementSpec.Coord.parse(y, "y")));
        return this;
    }

    /**
     * Sets a fixed width, replacing any percentage or tracking supplier.
     * Zero is allowed; negative pixel values throw.
     *
     * @param width absolute scaled pixels or a percentage string
     * @return this element for chaining
     * @zh 设置固定宽度，替换既有百分比或跟踪尺寸。允许 0；负像素值抛错。
     * @zh-param width 缩放像素绝对值或百分比字符串
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement setWidth(@ApiType("number | string") Object width) {
        ensureAlive();
        requireNonNegative(width, "width");
        backend.publish(backend.spec().withW(
                ElementSpec.Coord.parse(width, "width")));
        return this;
    }

    /**
     * Sets a fixed height, replacing any percentage or tracking supplier.
     * Zero is allowed; negative pixel values throw.
     *
     * @param height absolute scaled pixels or a percentage string
     * @return this element for chaining
     * @zh 设置固定高度，替换既有百分比或跟踪尺寸。允许 0；负像素值抛错。
     * @zh-param height 缩放像素绝对值或百分比字符串
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement setHeight(@ApiType("number | string") Object height) {
        ensureAlive();
        requireNonNegative(height, "height");
        backend.publish(backend.spec().withH(
                ElementSpec.Coord.parse(height, "height")));
        return this;
    }

    /**
     * Sets fixed width and height. Zero is allowed; negative dimensions
     * throw. Width is applied first, so an invalid height leaves the
     * width changed.
     *
     * @param width  absolute scaled pixels or a percentage string
     * @param height absolute scaled pixels or a percentage string
     * @return this element for chaining
     * @zh 设置固定宽高。允许 0；负尺寸抛错。先应用宽度，因此非法高度会保留已改的宽度。
     * @zh-param width 缩放像素绝对值或百分比字符串
     * @zh-param height 缩放像素绝对值或百分比字符串
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement setSize(@ApiType("number | string") Object width,
            @ApiType("number | string") Object height) {
        setWidth(width);
        return setHeight(height);
    }

    /**
     * Sets the render scale applied to this element. Zero and negative
     * values throw. The value is stored immediately; the render transform
     * that applies it ships with the component tasks (G-034).
     *
     * @param scale factor greater than zero ( {@code 1} = unchanged )
     * @return this element for chaining
     * @zh 设置本元素的渲染缩放。零与负值抛错。数值立即入状态；应用它的渲染变换随组件任务（G-034）落地。
     * @zh-param scale 大于 0 的倍率（{@code 1} = 不变）
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement setScale(double scale) {
        ensureAlive();
        if (!(scale > 0)) {
            throw new IllegalArgumentException(
                    "scale must be greater than zero: " + scale);
        }
        backend.publish(backend.spec().withScale((float) scale));
        return this;
    }

    /**
     * Sets the rotation in degrees; the value is narrowed to {@code float}
     * and normalized to {@code [-180, 180)}, so {@code 270} reads back as
     * {@code -90}.
     *
     * @param rotation rotation in degrees
     * @return this element for chaining
     * @zh 设置旋转角度（度）；数值收窄为 {@code float} 并归一到 {@code [-180, 180)}，因此 {@code 270} 读回为 {@code -90}。
     * @zh-param rotation 旋转角度（度）
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement setRotation(double rotation) {
        ensureAlive();
        backend.publish(backend.spec().withRotation((float) rotation));
        return this;
    }

    /**
     * Sets whether rotation pivots about the element center rather than
     * its positioned corner.
     *
     * @param rotateCenter {@code true} to rotate about the center
     * @return this element for chaining
     * @zh 设置旋转是否以元素中心为轴（而非定位角）。
     * @zh-param rotateCenter {@code true} 表示绕中心旋转
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement setRotateCenter(boolean rotateCenter) {
        ensureAlive();
        backend.publish(backend.spec().withRotateCenter(rotateCenter));
        return this;
    }

    /**
     * Sets this element's ordering value on its layer; lower values draw
     * first, ties keep insertion order, negatives are allowed. The change
     * re-sorts the layer draw order on the next frame without touching the
     * port.
     *
     * @param zIndex new z-order (lower draws first)
     * @return this element for chaining
     * @zh 设置元素在层上的绘制序：数值小者先画，同值按插入序，允许负数。变更在下一帧重排层内绘制序，不触碰端口。
     * @zh-param zIndex 新绘制序（小者先画）
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement setZIndex(int zIndex) {
        ensureAlive();
        backend.publish(backend.spec().withZIndex(zIndex));
        return this;
    }

    /**
     * Shows or hides this element. Pure core state: an invisible element
     * keeps its slot and repaints nothing; the port is not touched.
     *
     * @param visible {@code false} to hide the element
     * @return this element for chaining
     * @zh 显示或隐藏本元素。纯 core 状态：不可见元素保留插入位且不绘制；不触碰端口。
     * @zh-param visible {@code false} 表示隐藏
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement setVisible(boolean visible) {
        ensureAlive();
        backend.publish(backend.spec().withVisible(visible));
        return this;
    }

    /**
     * Sets both position coordinates without changing size — an alias of
     * {@link #setPos}.
     *
     * @param x absolute scaled pixels or a percentage string
     * @param y absolute scaled pixels or a percentage string
     * @return this element for chaining
     * @zh 同时设置两个坐标且不改变尺寸——{@link #setPos} 的别名。
     * @zh-param x 缩放像素绝对值或百分比字符串
     * @zh-param y 缩放像素绝对值或百分比字符串
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement moveTo(@ApiType("number | string") Object x,
            @ApiType("number | string") Object y) {
        return setPos(x, y);
    }

    /**
     * The resolved right edge — the left edge plus the scaled width;
     * {@code undefined} once disposed.
     *
     * @zh 求值后的右边缘——左边缘加缩放宽度；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getScaledRight() {
        Integer left = getScaledLeft();
        Integer width = getScaledWidth();
        return (left == null || width == null) ? null : left + width;
    }

    /**
     * The resolved bottom edge — the top edge plus the scaled height;
     * {@code undefined} once disposed.
     *
     * @zh 求值后的下边缘——上边缘加缩放高度；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getScaledBottom() {
        Integer top = getScaledTop();
        Integer height = getScaledHeight();
        return (top == null || height == null) ? null : top + height;
    }

    /**
     * Moves this element to {@code x}, retaining its current y coordinate —
     * the raw form, so a percentage y keeps reflowing with the viewport;
     * {@code undefined} once disposed. Zero and negative positions are
     * allowed.
     *
     * @param x absolute scaled pixels, or a percentage string such as {@code "50%"}
     * @return this element for chaining
     * @zh 把本元素移到 {@code x} 并保留当前 y 坐标——保留原始形态，因此百分比 y 仍随视口 reflow；失效后为 {@code undefined}。允许 0 与负坐标。
     * @zh-param x 缩放像素绝对值，或 {@code "50%"} 形式的百分比字符串
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement moveToX(@ApiType("number | string") Object x) {
        ensureAlive();
        return moveTo(x, backend.spec().y().raw());
    }

    /**
     * Moves this element to {@code y}, retaining its current x coordinate —
     * the raw form, so a percentage x keeps reflowing with the viewport;
     * {@code undefined} once disposed. Zero and negative positions are
     * allowed.
     *
     * @param y absolute scaled pixels, or a percentage string such as {@code "50%"}
     * @return this element for chaining
     * @zh 把本元素移到 {@code y} 并保留当前 x 坐标——保留原始形态，因此百分比 x 仍随视口 reflow；失效后为 {@code undefined}。允许 0 与负坐标。
     * @zh-param y 缩放像素绝对值，或 {@code "50%"} 形式的百分比字符串
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement moveToY(@ApiType("number | string") Object y) {
        ensureAlive();
        return moveTo(backend.spec().x().raw(), y);
    }

    /**
     * Aligns this element horizontally within its parent box, or against a
     * sibling element when the first argument is an element. Parent-relative
     * tokens are {@code left}, {@code center}, {@code right} or a
     * whole-number percentage of the space the element's scaled width leaves
     * unused; the offset is added to the resulting x. Sibling-relative
     * tokens take the form {@code <thisAnchor>On<otherAnchor>} (a bare
     * token aligns the same anchor on both elements); malformed sibling
     * alignments throw, unsupported parent tokens do nothing. The y
     * coordinate keeps its raw form; {@code undefined} once disposed.
     *
     * @param alignmentOrParentOffset parent form: the alignment token
     * @param alignmentOrOffset sibling form: the alignment token; parent form: pixels added to the result
     * @param offset sibling form: pixels added to the resulting x position
     * @return this element for chaining
     * @zh 在父盒内水平对齐本元素；首参为元素时改为相对兄弟元素对齐。父盒相对词为 {@code left}、{@code center}、{@code right} 或占自身缩放宽度用剩空间的整数百分比，偏移加到结果 x 上。兄弟相对词形如 {@code <本元素锚>On<对方锚>}（裸词表示两侧同锚）；畸形兄弟对齐抛错，父盒不支持词静默不动。y 坐标保留原始形态；失效后为 {@code undefined}。
     * @zh-param alignmentOrParentOffset 父盒形：对齐词
     * @zh-param alignmentOrOffset 兄弟形：对齐词；父盒形：加到结果上的像素
     * @zh-param offset 兄弟形：加到结果 x 位置上的像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement alignHorizontally(
            @ApiType("string | Draw2DElement") Object alignmentOrParentOffset,
            @ApiOptional @ApiType("string | number") Object alignmentOrOffset,
            @ApiOptional @ApiType("number") Object offset) {
        ensureAlive();
        if (alignmentOrParentOffset instanceof Alignable other) {
            String alignment = requireAlignment(alignmentOrOffset, "alignment");
            return moveTo(elementShift(this, other, alignment, true,
                    requireOffset(offset, "offset")), backend.spec().y().raw());
        }
        if (alignmentOrParentOffset instanceof String alignment) {
            Integer shift = parentShift(alignment, true,
                    size(getParentWidth()) - size(getScaledWidth()));
            if (shift == null) {
                return this;
            }
            return moveTo(shift + requireOffset(alignmentOrOffset, "offset"),
                    backend.spec().y().raw());
        }
        throw new IllegalArgumentException(
                "alignment must be a string or an element: "
                        + alignmentOrParentOffset);
    }

    /**
     * Aligns this element vertically within its parent box, or against a
     * sibling element when the first argument is an element.
     * Parent-relative tokens are {@code top}, {@code center},
     * {@code bottom} or a whole-number percentage of the space the
     * element's scaled height leaves unused; the offset is added to the
     * resulting y. Sibling-relative tokens take the form
     * {@code <thisAnchor>On<otherAnchor>} (a bare token aligns the same
     * anchor on both elements); malformed sibling alignments throw,
     * unsupported parent tokens do nothing. The x coordinate keeps its raw
     * form; {@code undefined} once disposed.
     *
     * @param alignmentOrParentOffset parent form: the alignment token
     * @param alignmentOrOffset sibling form: the alignment token; parent form: pixels added to the result
     * @param offset sibling form: pixels added to the resulting y position
     * @return this element for chaining
     * @zh 在父盒内垂直对齐本元素；首参为元素时改为相对兄弟元素对齐。父盒相对词为 {@code top}、{@code center}、{@code bottom} 或占自身缩放高度用剩空间的整数百分比，偏移加到结果 y 上。兄弟相对词形如 {@code <本元素锚>On<对方锚>}（裸词表示两侧同锚）；畸形兄弟对齐抛错，父盒不支持词静默不动。x 坐标保留原始形态；失效后为 {@code undefined}。
     * @zh-param alignmentOrParentOffset 父盒形：对齐词
     * @zh-param alignmentOrOffset 兄弟形：对齐词；父盒形：加到结果上的像素
     * @zh-param offset 兄弟形：加到结果 y 位置上的像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement alignVertically(
            @ApiType("string | Draw2DElement") Object alignmentOrParentOffset,
            @ApiOptional @ApiType("string | number") Object alignmentOrOffset,
            @ApiOptional @ApiType("number") Object offset) {
        ensureAlive();
        if (alignmentOrParentOffset instanceof Alignable other) {
            String alignment = requireAlignment(alignmentOrOffset, "alignment");
            return moveTo(backend.spec().x().raw(), elementShift(this, other,
                    alignment, false, requireOffset(offset, "offset")));
        }
        if (alignmentOrParentOffset instanceof String alignment) {
            Integer shift = parentShift(alignment, false,
                    size(getParentHeight()) - size(getScaledHeight()));
            if (shift == null) {
                return this;
            }
            return moveTo(backend.spec().x().raw(),
                    shift + requireOffset(alignmentOrOffset, "offset"));
        }
        throw new IllegalArgumentException(
                "alignment must be a string or an element: "
                        + alignmentOrParentOffset);
    }

    /**
     * Aligns this element on both axes within its parent box, or against a
     * sibling element when the first argument is an element. Four call
     * shapes are accepted: {@code align(horizontal, vertical)},
     * {@code align(horizontal, horizontalOffset, vertical, verticalOffset)},
     * {@code align(other, horizontal, vertical)} and
     * {@code align(other, horizontal, horizontalOffset, vertical, verticalOffset)}.
     * An axis whose parent-relative token is unsupported stays put;
     * sibling-relative tokens always resolve or throw. {@code undefined}
     * once disposed.
     *
     * @param horizontalOrOther parent form: the horizontal alignment; sibling form: the element to align against
     * @param alignmentOrOffset parent {@code align(h, v)}: the vertical alignment; sibling {@code align(other, h, v)}: the horizontal alignment; offset forms: the horizontal offset
     * @param verticalOrHorizontalOffset parent offset form: the vertical alignment; sibling offset form: the horizontal offset; sibling {@code align(other, h, v)}: the vertical alignment
     * @param verticalOrVerticalOffset sibling offset form: the vertical alignment; parent offset form: the vertical offset
     * @param verticalOffset sibling offset form: the vertical offset
     * @return this element for chaining
     * @zh 在父盒内双轴对齐本元素；首参为元素时改为相对兄弟元素对齐。接受四种调用形：{@code align(水平, 垂直)}、{@code align(水平, 水平偏移, 垂直, 垂直偏移)}、{@code align(other, 水平, 垂直)} 与 {@code align(other, 水平, 水平偏移, 垂直, 垂直偏移)}。父盒相对词不支持的轴保持原位；兄弟相对词要么求值要么抛错。失效后为 {@code undefined}。
     * @zh-param horizontalOrOther 父盒形：水平对齐词；兄弟形：作为对齐基准的元素
     * @zh-param alignmentOrOffset 父盒 {@code align(h, v)}：垂直对齐词；兄弟 {@code align(other, h, v)}：水平对齐词；带偏移形：水平偏移
     * @zh-param verticalOrHorizontalOffset 父盒带偏移形：垂直对齐词；兄弟带偏移形：水平偏移；兄弟 {@code align(other, h, v)}：垂直对齐词
     * @zh-param verticalOrVerticalOffset 兄弟带偏移形：垂直对齐词；父盒带偏移形：垂直偏移
     * @zh-param verticalOffset 兄弟带偏移形：垂直偏移
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement align(
            @ApiType("string | Draw2DElement") Object horizontalOrOther,
            @ApiType("string | number") Object alignmentOrOffset,
            @ApiOptional @ApiType("string | number") Object verticalOrHorizontalOffset,
            @ApiOptional @ApiType("string | number") Object verticalOrVerticalOffset,
            @ApiOptional @ApiType("number") Object verticalOffset) {
        ensureAlive();
        if (horizontalOrOther instanceof Alignable other) {
            String horizontal = requireAlignment(alignmentOrOffset, "horizontal");
            String vertical;
            int horizontalOffset;
            int verticalOffsetValue;
            if (verticalOrHorizontalOffset instanceof String verticalToken) {
                horizontalOffset = 0;
                verticalOffsetValue = 0;
                vertical = verticalToken;
            } else {
                horizontalOffset = requireOffset(verticalOrHorizontalOffset,
                        "horizontalOffset");
                vertical = requireAlignment(verticalOrVerticalOffset, "vertical");
                verticalOffsetValue = requireOffset(verticalOffset, "verticalOffset");
            }
            return moveTo(elementShift(this, other, horizontal, true,
                            horizontalOffset),
                    elementShift(this, other, vertical, false, verticalOffsetValue));
        }
        if (horizontalOrOther instanceof String horizontal) {
            String vertical;
            int horizontalOffset;
            int verticalOffsetValue;
            if (alignmentOrOffset instanceof String verticalToken) {
                horizontalOffset = 0;
                verticalOffsetValue = 0;
                vertical = verticalToken;
            } else {
                horizontalOffset = requireOffset(alignmentOrOffset,
                        "horizontalOffset");
                vertical = requireAlignment(verticalOrHorizontalOffset, "vertical");
                verticalOffsetValue = requireOffset(verticalOrVerticalOffset,
                        "verticalOffset");
            }
            Integer shiftX = parentShift(horizontal, true,
                    size(getParentWidth()) - size(getScaledWidth()));
            Integer shiftY = parentShift(vertical, false,
                    size(getParentHeight()) - size(getScaledHeight()));
            if (shiftX == null && shiftY == null) {
                return this;
            }
            if (shiftX == null) {
                return moveTo(backend.spec().x().raw(),
                        shiftY + verticalOffsetValue);
            }
            if (shiftY == null) {
                return moveTo(shiftX + horizontalOffset,
                        backend.spec().y().raw());
            }
            return moveTo(shiftX + horizontalOffset, shiftY + verticalOffsetValue);
        }
        throw new IllegalArgumentException(
                "alignment must be a string or an element: " + horizontalOrOther);
    }

    // ---- host-side alignment overloads: the folded members above own the
    // ---- script surface (one member per name); these mirror the ergonomic
    // ---- call shapes for Java callers and stay off it.

    /**
     * Java-side shortcut for
     * {@link #alignHorizontally(Object, Object, Object)} without an offset;
     * hidden from the script surface.
     *
     * @zh {@link #alignHorizontally(Object, Object, Object)} 的 Java 捷径（省略偏移），不进脚本面。
     */
    @ApiInternal
    public Draw2DElement alignHorizontally(String alignment) {
        return alignHorizontally(alignment, null, null);
    }

    /**
     * Java-side shortcut for
     * {@link #alignHorizontally(Object, Object, Object)} with a
     * parent-relative offset; hidden from the script surface.
     *
     * @zh {@link #alignHorizontally(Object, Object, Object)} 的 Java 捷径（带父盒偏移），不进脚本面。
     */
    @ApiInternal
    public Draw2DElement alignHorizontally(String alignment, int offset) {
        return alignHorizontally(alignment, offset, null);
    }

    /**
     * Java-side shortcut for
     * {@link #alignHorizontally(Object, Object, Object)} against a sibling;
     * hidden from the script surface.
     *
     * @zh {@link #alignHorizontally(Object, Object, Object)} 的 Java 捷径（相对兄弟元素），不进脚本面。
     */
    @ApiInternal
    public Draw2DElement alignHorizontally(Alignable other, String alignment) {
        return alignHorizontally((Object) other, alignment, null);
    }

    /**
     * Java-side shortcut for
     * {@link #alignHorizontally(Object, Object, Object)} against a sibling
     * with an offset; hidden from the script surface.
     *
     * @zh {@link #alignHorizontally(Object, Object, Object)} 的 Java 捷径（相对兄弟元素带偏移），不进脚本面。
     */
    @ApiInternal
    public Draw2DElement alignHorizontally(Alignable other, String alignment,
            int offset) {
        return alignHorizontally((Object) other, alignment, offset);
    }

    /**
     * Java-side shortcut for
     * {@link #alignVertically(Object, Object, Object)} without an offset;
     * hidden from the script surface.
     *
     * @zh {@link #alignVertically(Object, Object, Object)} 的 Java 捷径（省略偏移），不进脚本面。
     */
    @ApiInternal
    public Draw2DElement alignVertically(String alignment) {
        return alignVertically(alignment, null, null);
    }

    /**
     * Java-side shortcut for
     * {@link #alignVertically(Object, Object, Object)} with a
     * parent-relative offset; hidden from the script surface.
     *
     * @zh {@link #alignVertically(Object, Object, Object)} 的 Java 捷径（带父盒偏移），不进脚本面。
     */
    @ApiInternal
    public Draw2DElement alignVertically(String alignment, int offset) {
        return alignVertically(alignment, offset, null);
    }

    /**
     * Java-side shortcut for
     * {@link #alignVertically(Object, Object, Object)} against a sibling;
     * hidden from the script surface.
     *
     * @zh {@link #alignVertically(Object, Object, Object)} 的 Java 捷径（相对兄弟元素），不进脚本面。
     */
    @ApiInternal
    public Draw2DElement alignVertically(Alignable other, String alignment) {
        return alignVertically((Object) other, alignment, null);
    }

    /**
     * Java-side shortcut for
     * {@link #alignVertically(Object, Object, Object)} against a sibling
     * with an offset; hidden from the script surface.
     *
     * @zh {@link #alignVertically(Object, Object, Object)} 的 Java 捷径（相对兄弟元素带偏移），不进脚本面。
     */
    @ApiInternal
    public Draw2DElement alignVertically(Alignable other, String alignment,
            int offset) {
        return alignVertically((Object) other, alignment, offset);
    }

    /**
     * Java-side shortcut for
     * {@link #align(Object, Object, Object, Object, Object)} without
     * offsets; hidden from the script surface.
     *
     * @zh {@link #align(Object, Object, Object, Object, Object)} 的 Java 捷径（省略偏移），不进脚本面。
     */
    @ApiInternal
    public Draw2DElement align(String horizontal, String vertical) {
        return align(horizontal, vertical, null, null, null);
    }

    /**
     * Java-side shortcut for
     * {@link #align(Object, Object, Object, Object, Object)} with offsets;
     * hidden from the script surface.
     *
     * @zh {@link #align(Object, Object, Object, Object, Object)} 的 Java 捷径（带偏移），不进脚本面。
     */
    @ApiInternal
    public Draw2DElement align(String horizontal, int horizontalOffset,
            String vertical, int verticalOffset) {
        return align(horizontal, horizontalOffset, vertical, verticalOffset, null);
    }

    /**
     * Java-side shortcut for
     * {@link #align(Object, Object, Object, Object, Object)} against a
     * sibling without offsets; hidden from the script surface.
     *
     * @zh {@link #align(Object, Object, Object, Object, Object)} 的 Java 捷径（相对兄弟元素省略偏移），不进脚本面。
     */
    @ApiInternal
    public Draw2DElement align(Alignable other, String horizontal,
            String vertical) {
        return align((Object) other, horizontal, vertical, null, null);
    }

    /**
     * Java-side shortcut for
     * {@link #align(Object, Object, Object, Object, Object)} against a
     * sibling with offsets; hidden from the script surface.
     *
     * @zh {@link #align(Object, Object, Object, Object, Object)} 的 Java 捷径（相对兄弟元素带偏移），不进脚本面。
     */
    @ApiInternal
    public Draw2DElement align(Alignable other, String horizontal,
            int horizontalOffset, String vertical, int verticalOffset) {
        return align((Object) other, horizontal, horizontalOffset, vertical,
                verticalOffset);
    }

    /**
     * The state bridge for component subclasses (G-034 / G-034B): read the
     * current {@link ElementSpec} and republish a modified copy. Protected
     * visibility keeps it off the script surface (the engine binds public
     * members only) and out of the docs model.
     *
     * @zh 组件子类（G-034 / G-034B）使用状态桥：读取当前 {@link ElementSpec} 并重新发布修改后的副本。protected 可见性使其不进入脚本面（引擎只绑定公有成员）与文档模型。
     */
    protected Backend backend() {
        return backend;
    }

    /** Throws when the handle no longer valid; used by every mutator. */
    private void ensureAlive() {
        if (backend.disposed()) {
            throw new IllegalStateException(DISPOSED_MESSAGE);
        }
    }

    /** Rejects negative pixel dimensions before they reach the spec. */
    private static void requireNonNegative(Object value, String what) {
        if (value instanceof Number number && number.doubleValue() < 0) {
            throw new IllegalArgumentException(
                    what + " must not be negative: " + value);
        }
    }

    /** Truncates {@code value * scale} toward zero. */
    private static int scaled(int value, float scale) {
        return (int) (value * scale);
    }

    /** Requires a string alignment token in a folded align argument. */
    private static String requireAlignment(Object value, String what) {
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException(
                what + " must be a string: " + value);
    }

    /** Requires a folded align offset: null is zero, anything else a number. */
    private static int requireOffset(Object value, String what) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException(what + " must be a number: " + value);
    }

    /** Unboxes a live geometry read; a null read means a disposed element. */
    private static int size(Integer value) {
        if (value == null) {
            throw new IllegalStateException(DISPOSED_MESSAGE);
        }
        return value;
    }

    /**
     * Resolves a parent-relative alignment token against the unused space
     * ({@code parent - self}) and adds the pixel offset; {@code null} means
     * the token is unsupported on this axis and nothing moves.
     */
    private static Integer parentShift(String alignment, boolean horizontal,
            int available) {
        String token = alignment.trim().toLowerCase(Locale.ROOT);
        Integer anchor = keywordAnchor(token, horizontal, available);
        if (anchor != null) {
            return anchor;
        }
        if (token.endsWith("%")) {
            int percentage = Alignable.parsePercentage(token);
            if (percentage < 0) {
                return null;
            }
            return (int) Math.round(available * percentage / 100.0);
        }
        return null;
    }

    /**
     * Resolves a sibling-relative {@code <this>On<other>} alignment: this
     * element's anchor lands on the sibling's anchor, plus the pixel
     * offset. A bare token aligns the same anchor on both elements;
     * malformed tokens throw.
     */
    private static int elementShift(Draw2DElement self, Alignable other,
            String alignment, boolean horizontal, int offset) {
        String[] anchors = splitAnchors(alignment);
        int ownSize = horizontal ? size(self.getScaledWidth())
                : size(self.getScaledHeight());
        int otherSize = horizontal ? size(other.getScaledWidth())
                : size(other.getScaledHeight());
        int otherOrigin = horizontal ? size(other.getScaledLeft())
                : size(other.getScaledTop());
        int ownAnchor = anchorShift(anchors[0], horizontal, ownSize);
        int otherAnchor = anchorShift(anchors[1], horizontal, otherSize);
        return otherOrigin + otherAnchor - ownAnchor + offset;
    }

    /** Splits {@code <this>On<other>} case-insensitively; a bare token
     *  aligns the same anchor on both sides. */
    private static String[] splitAnchors(String alignment) {
        String[] parts = alignment.trim().toLowerCase(Locale.ROOT)
                .split("on", -1);
        if (parts.length == 1 && !parts[0].isEmpty()) {
            return new String[] {parts[0], parts[0]};
        }
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException(
                    "alignment must be <anchor>On<anchor>: " + alignment);
        }
        return parts;
    }

    /** A keyword anchor ({@code left}/{@code center}/{@code right} or the
     *  vertical set) at {@code size}, or null when the token is not one. */
    private static Integer keywordAnchor(String token, boolean horizontal,
            int size) {
        if (horizontal) {
            return switch (token) {
                case "left" -> 0;
                case "center", "middle" -> size / 2;
                case "right" -> size;
                default -> null;
            };
        }
        return switch (token) {
            case "top" -> 0;
            case "center", "middle" -> size / 2;
            case "bottom" -> size;
            default -> null;
        };
    }

    /** An element-relative anchor: keyword, percentage, or throw. */
    private static int anchorShift(String token, boolean horizontal, int size) {
        Integer anchor = keywordAnchor(token, horizontal, size);
        if (anchor != null) {
            return anchor;
        }
        if (token.endsWith("%")) {
            int percentage = Alignable.parsePercentage(token);
            if (percentage >= 0) {
                return (int) Math.round(size * percentage / 100.0);
            }
        }
        throw new IllegalArgumentException("unsupported alignment: " + token);
    }
}
