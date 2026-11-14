package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.core.api.render.HudLayer;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

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
 * moment — the JsMacros {@code Draw2DElement.Builder} family folds into
 * this handle instead of a parallel {@code XxxBuilder} class tree.</p>
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
 * @zh <b>元素即 Builder</b>（拍板②）：全部 setter 为 fluent 并返回 {@code this}，配置与写入是同一对象同一时刻——JsMacros 的 {@code Draw2DElement.Builder} 类族折叠进本句柄，不另建 {@code XxxBuilder} 类树。
 * @zh <b>状态模型</b>：元素自身不持有可变字段。setter 经 {@link Backend} 桥重新发布不可变 {@link ElementSpec}（volatile 写入，下一帧消费——G-001 §2.2）；getter 读取当前快照。几何接受缩放像素绝对值或视口百分比字符串（{@code "50%"}），绘制时按层视口求值。
 * @zh <b>失效</b>：元素被移除、被不同组件类型替换、随层 clear 或整层移除后句柄即失效；此后所有读属性返回 {@code undefined}（Java {@code null}），所有 setter 抛出 {@code IllegalStateException: element no longer valid}；仅 {@link #isDisposed()} 保持可读。
 */
@ApiClass(name = "Draw2DElement", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public class Draw2DElement implements ScriptHandle {

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
     * truncated toward zero (JsMacros {@code getScaledWidth} semantics).
     * {@code undefined} once the handle is disposed.
     *
     * @zh 元素解析后的绝对盒：缩放像素原点，加上百分比求值并乘以 {@link #getScale()} 后的尺寸（向零截断，沿 JsMacros {@code getScaledWidth} 语义）。句柄失效后为 {@code undefined}。
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
     * The layer hosting this element — the NeoMacros face for the JsMacros
     * {@code getDraw2D()} back-reference; {@code undefined} once disposed.
     *
     * @zh 承载本元素的图层——JsMacros {@code getDraw2D()} 反向引用的 NeoMacros 落点；失效后为 {@code undefined}。
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
     * throw. Width is applied first, so an invalid height leaves the width
     * changed (JsMacros semantics).
     *
     * @param width  absolute scaled pixels or a percentage string
     * @param height absolute scaled pixels or a percentage string
     * @return this element for chaining
     * @zh 设置固定宽高。允许 0；负尺寸抛错。先应用宽度，因此非法高度会保留已改的宽度（JsMacros 语义）。
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
     * Sets both position coordinates without changing size — the JsMacros
     * {@code moveTo} face, an alias of {@link #setPos}.
     *
     * @param x absolute scaled pixels or a percentage string
     * @param y absolute scaled pixels or a percentage string
     * @return this element for chaining
     * @zh 同时设置两个坐标且不改变尺寸——JsMacros 的 {@code moveTo} 面，{@link #setPos} 的别名。
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

    /** Truncates {@code value * scale} toward zero (JsMacros semantics). */
    private static int scaled(int value, float scale) {
        return (int) (value * scale);
    }
}
