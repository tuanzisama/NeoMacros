package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.core.api.render.components3d.Box;
import ink.tuanzi.neomacros.core.api.render.components3d.Line3D;
import ink.tuanzi.neomacros.core.api.render.components3d.RenderElement3D;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.Draw3DPort;
import ink.tuanzi.neomacros.port.model.ShapeSpec;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A world-space overlay holding retained 3D elements, obtained from
 * {@code render.layer(id)}. Shapes persist across frames until removed or
 * the layer is cleared; the engine-side pipeline re-projects them every
 * frame (26.x resident gizmo / 1.x WorldRenderEvents), so scripts never
 * register a per-frame callback.
 *
 * <p>Two faces share one layer: the immediate primitives on
 * {@code neo.render} ({@code drawBox}/{@code drawLine}/…) write fire-and-
 * forget shapes into this same layer (queryable only through the port),
 * while elements created here ({@code boxBuilder()}/{@code lineBuilder()})
 * are retained handles with {@link #getElements()}, {@link #remove} and
 * live mutation. Layers are keyed per module owner; a module's layers are
 * cleared automatically when it unloads.</p>
 *
 * @zh 世界坐标系的保留式 3D 图层句柄（{@code render.layer(id)} 取得）。图形跨帧保留，由引擎侧管线每帧重投，脚本无须注册每帧回调。即时原语（drawBox 等）与保留元素（boxBuilder 等）共享同一图层。
 */
@ApiClass(name = "Draw3D", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public final class Draw3D implements ScriptHandle {

    /** The port key (owner-namespaced) this overlay writes through. */
    private final String key;

    /** The script-visible layer id (owner prefix stripped). */
    private final String id;

    private final Draw3DPort port;

    /** Retained elements in insertion order (copy-on-write for render reads). */
    private final List<RenderElement3D<?>> elements = new CopyOnWriteArrayList<>();

    /** Shape-id sequence shared by raw primitives and elements. */
    private final AtomicLong seq = new AtomicLong();

    /** Creates one overlay bound to a port layer key (facade-internal). */
    Draw3D(String key, String id, Draw3DPort port) {
        this.key = key;
        this.id = id;
        this.port = port;
    }

    /**
     * The script-visible layer id this overlay was created under.
     *
     * @zh 创建该图层时使用的公开 id（不含 owner 前缀）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String getId() {
        return id;
    }

    /**
     * The retained elements currently attached, in insertion order.
     *
     * @zh 当前挂载的保留元素（插入序）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public List<RenderElement3D<?>> getElements() {
        return List.copyOf(elements);
    }

    /**
     * Attaches an existing element (one returned by {@code build()}) to this
     * overlay. An element already attached elsewhere is rejected; attaching
     * the same instance twice is a no-op — unlike JsMacros duplicates, the
     * keyed port keeps one entry per element.
     *
     * @param element the element to attach
     * @throws IllegalStateException when the element belongs to another overlay
     * @zh 把已有元素（{@code build()} 的产物）挂到本图层；已在别处挂载时抛错，重复挂载为 no-op。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void reAddElement(RenderElement3D<?> element) {
        addElement(element);
    }

    /**
     * Creates a box builder bound to this overlay: a detached box with
     * origin corners, opaque white colours, unfilled and drawn over the
     * world. Attach it with {@code buildAndAdd()}; for a one-block box
     * chain {@code .forBlock(pos)} onto the result.
     *
     * @zh 创建绑定到本图层的盒子构建器（零点角点、白色不透明、不填充、画在世界之上）；单方块盒用 {@code .forBlock(pos)}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box boxBuilder() {
        Box box = new Box();
        box.origin(this);
        return box;
    }

    /**
     * Creates a line builder bound to this overlay: a zero-length line at
     * the origin, opaque white, drawn over the world.
     *
     * @zh 创建绑定到本图层的线段构建器（原点零长度、白色不透明、画在世界之上）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Line3D lineBuilder() {
        Line3D line = new Line3D();
        line.origin(this);
        return line;
    }

    /**
     * Detaches one element and removes its shape from the world. Detached
     * elements keep their state and can be re-added with
     * {@link #reAddElement(RenderElement3D)}. Removing an element that is
     * not attached here does nothing.
     *
     * @param element the element to detach
     * @return this overlay
     * @zh 摘除一个元素并从世界中移除其形状；元素状态保留，可再挂回。移除非本图层元素为 no-op。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw3D remove(RenderElement3D<?> element) {
        if (element == null || element.container() != this) {
            return this;
        }
        port.removeShape(key, element.shapeId());
        elements.remove(element);
        element.detach();
        return this;
    }

    /**
     * Removes every shape on this layer — retained elements and the shapes
     * written by the immediate {@code drawBox}-style primitives alike —
     * while keeping the layer itself.
     *
     * @zh 清空本图层的全部形状（保留元素与即时原语一并清除），图层本身保留。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void clear() {
        for (RenderElement3D<?> element : elements) {
            element.detach();
        }
        elements.clear();
        port.clearLayer(key);
    }

    // ---- internal wiring (facade + element builder faces) -----------------

    /** Attaches {@code element} under a fresh shape id, pushing its state. */
    @ApiInternal
    public void addElement(RenderElement3D<?> element) {
        if (element == null) {
            throw new IllegalArgumentException("element is required");
        }
        if (element.shapeId() != null) {
            if (element.container() == this) {
                return;
            }
            throw new IllegalStateException(
                    "element is already attached to another overlay");
        }
        element.attach(this, "e" + seq.incrementAndGet());
        elements.add(element);
        port.putShape(key, element.shapeId(), element.toSpec());
    }

    /** Re-projects one attached element's current state into the port. */
    @ApiInternal
    public void updateElement(RenderElement3D<?> element) {
        String shapeId = element.shapeId();
        if (shapeId != null && element.container() == this) {
            port.putShape(key, shapeId, element.toSpec());
        }
    }

    /**
     * Writes one fire-and-forget primitive shape (the immediate
     * {@code drawBox}-style face) into this layer without tracking it as an
     * element.
     */
    void addRaw(ShapeSpec spec) {
        port.putShape(key, "r" + seq.incrementAndGet(), spec);
    }

    /** The port key this overlay writes through (facade-internal). */
    String portKey() {
        return key;
    }
}
