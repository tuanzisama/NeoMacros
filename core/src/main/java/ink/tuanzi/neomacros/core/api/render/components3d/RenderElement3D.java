package ink.tuanzi.neomacros.core.api.render.components3d;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.core.api.render.Draw3D;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ShapeSpec;

/**
 * One drawable element living inside a {@link Draw3D} overlay: a retained,
 * mutable handle whose current state is re-projected into the world every
 * frame by the engine-side pipeline (26.x resident gizmo / 1.x
 * WorldRenderEvents) — scripts never register a per-frame callback.
 *
 * <p>Elements are ordered by canonical class name first, then by
 * {@link #compareToSame(RenderElement3D)} within a class; insertion order
 * plays no part. Elements are built through their container's
 * {@code boxBuilder()}/{@code lineBuilder()} face (the builder folds into
 * the element itself), attached with {@code buildAndAdd()} or
 * {@link Draw3D#reAddElement(RenderElement3D)}, and detached with
 * {@link Draw3D#remove(RenderElement3D)} — a detached element keeps its
 * state and can be re-added.</p>
 *
 * @zh 放在 {@link Draw3D} 图层里的可绘制元素：有状态、可变的持久句柄，其当前状态由引擎侧管线每帧重投到世界中（脚本永不注册每帧回调）。同类元素按类名排序，同类内按 {@link #compareToSame} 比较，与插入顺序无关。
 */
@ApiClass(name = "RenderElement3D", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public abstract class RenderElement3D<T extends RenderElement3D<T>>
        implements ScriptHandle, Comparable<RenderElement3D<T>> {

    /** The overlay this element belongs to, or null while detached. */
    private Draw3D container;

    /** The port key of this element while attached, null otherwise. */
    private String shapeId;

    /** The overlay this element was created for (its builder's origin). */
    private Draw3D origin;

    /**
     * Compares two elements by canonical class name and, for elements of the
     * same class, delegates to {@link #compareToSame(RenderElement3D)}.
     *
     * @param other the element to compare against
     * @return a negative number, zero, or a positive number as this element
     *         sorts before, with, or after {@code other}
     * @zh 先按类的规范名比较，同类元素再交给 {@code compareToSame} 排序。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @SuppressWarnings("unchecked")
    @Override
    public int compareTo(RenderElement3D<T> other) {
        int byClass = getClass().getName().compareTo(other.getClass().getName());
        return byClass != 0 ? byClass : compareToSame((T) other);
    }

    /**
     * Orders two elements of this exact class; implementations compare
     * their own geometry and ignore colours and render flags.
     *
     * @param other the same-class element to compare against
     * @return the comparison result
     * @zh 比较同类元素：只比较自身几何，忽略颜色与渲染开关。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public abstract int compareToSame(T other);

    // ---- internal wiring between the element, its container and the port --

    /**
     * The current shape snapshot pushed to the port while attached.
     *
     * @zh 附加状态下推送到端口的当前形状快照。
     */
    @ApiInternal
    public abstract ShapeSpec toSpec();

    /** Binds this element to {@code owner} under {@code newShapeId}. */
    @ApiInternal
    public final void attach(Draw3D owner, String newShapeId) {
        this.container = owner;
        this.shapeId = newShapeId;
    }

    /** Detaches this element from its overlay (state is kept). */
    @ApiInternal
    public final void detach() {
        this.container = null;
        this.shapeId = null;
    }

    /** Remembers the overlay a builder originated from. */
    @ApiInternal
    public final void origin(Draw3D owner) {
        this.origin = owner;
    }

    /** The overlay this element was built for, or null. */
    @ApiInternal
    public final Draw3D origin() {
        return origin;
    }

    /** The overlay currently holding this element, or null when detached. */
    @ApiInternal
    public final Draw3D container() {
        return container;
    }

    /** The port shape key while attached, null otherwise. */
    @ApiInternal
    public final String shapeId() {
        return shapeId;
    }

    /** Re-projects this element's state into the port when attached. */
    @ApiInternal
    public final void push() {
        if (container != null && shapeId != null) {
            container.updateElement(this);
        }
    }
}
