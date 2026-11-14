package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.render.components3d.Box;
import ink.tuanzi.neomacros.core.api.render.components3d.EntityTraceLine;
import ink.tuanzi.neomacros.core.api.render.components3d.Line3D;
import ink.tuanzi.neomacros.core.api.render.components3d.RenderElement3D;
import ink.tuanzi.neomacros.core.api.render.components3d.Surface;
import ink.tuanzi.neomacros.core.api.render.components3d.TraceLine;
import ink.tuanzi.neomacros.core.api.types.Pos3D;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
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
 * while elements created here ({@code boxBuilder()}/{@code lineBuilder()}/
 * {@code traceLineBuilder()}/{@code entityTraceLineBuilder()}/
 * {@code surfaceBuilder()}/{@code addDraw2D()})
 * are retained handles with {@link #getElements()}, {@link #remove} and
 * live mutation. Layers are keyed per module owner; a module's layers are
 * cleared automatically when it unloads.</p>
 *
 * @zh 世界坐标系的保留式 3D 图层句柄（{@code render.layer(id)} 取得）。图形跨帧保留，由引擎侧管线每帧重投，脚本无须注册每帧回调。即时原语（drawBox 等）与保留元素（boxBuilder、traceLineBuilder、surfaceBuilder、addDraw2D 等）共享同一图层。
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
     * the same instance twice is a no-op — the keyed port keeps one entry
     * per element. This is also the attach face used by the
     * {@code addTraceLine} quick form.
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
     * Creates a trace-line builder bound to this overlay: a detached line
     * targeting the origin, opaque white, drawn over the world. Its
     * camera-side endpoint is recalculated from the camera every frame.
     *
     * @zh 创建绑定到本图层的追踪线构建器（目标为原点、白色不透明、画在世界之上）；摄像机一端每帧重算。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TraceLine traceLineBuilder() {
        TraceLine line = new TraceLine();
        line.origin(this);
        return line;
    }

    /**
     * Creates an entity-trace-line builder bound to this overlay: a
     * detached white, fully opaque, always-on-top line following an
     * entity, with a {@code 0.5}-block vertical offset. No entity is set
     * by default; a line built without one simply does not draw until one
     * is bound.
     *
     * @zh 创建绑定到本图层的实体追踪线构建器（白色不透明、画在世界之上、跟随实体、垂直偏移 {@code 0.5} 格）；默认未绑定实体。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public EntityTraceLine entityTraceLineBuilder() {
        EntityTraceLine line = new EntityTraceLine();
        line.origin(this);
        return line;
    }

    /**
     * Creates a surface builder bound to this overlay: a detached
     * {@code 10 × 10}-block panel with centred rotation, a rendered back,
     * no depth culling, full-bright lighting and one subdivision across
     * the smaller side.
     *
     * @zh 创建绑定到本图层的面板构建器（{@code 10 × 10} 格、绕中心旋转、绘制背面、不深度测试、全亮、较短边 1 细分）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface surfaceBuilder() {
        Surface surface = new Surface();
        surface.origin(this);
        surface.setRotateCenter(true);
        surface.renderBack(true);
        return surface;
    }

    /**
     * Adds a camera-to-target line toward a fixed world position, drawn
     * over terrain by default. The coordinates are read at call time;
     * later changes to {@code pos} do not move the target.
     *
     * @param pos the target position (coordinates copied at call time)
     * @param color packed colour (full-range JS literals accepted)
     * @param alpha explicit alpha from 0 to 255; omitted applies the
     *              zero-alpha rule from {@code color}
     * @return the attached trace line
     * @zh 添加一条指向固定世界位置的摄像机连线（默认画在世界之上）；坐标在调用时拷贝。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TraceLine addTraceLine(Pos3D pos, long color, @ApiOptional Integer alpha) {
        if (pos == null) {
            throw new IllegalArgumentException("pos is required");
        }
        TraceLine line = traceLineBuilder();
        line.setPos(pos);
        line.setColor(color, alpha);
        return line.buildAndAdd();
    }

    /**
     * Adds a camera-to-entity line following {@code entity} at a chosen
     * vertical offset above its feet, recalculated each frame. The line
     * draws over terrain by default.
     *
     * @param entity the entity to point at
     * @param color packed colour (full-range JS literals accepted)
     * @param alpha explicit alpha from 0 to 255; omitted applies the
     *              zero-alpha rule from {@code color}
     * @param yOffset blocks above the entity's position; defaults to 0.5
     * @param alwaysOnTop draw over the world instead of letting terrain
     *                    hide the line; defaults to true
     * @return the attached entity trace line
     * @zh 添加一条跟随实体的摄像机连线（每帧重解析），默认画在世界之上。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public EntityTraceLine addEntityTraceLine(EntityApi.Entity entity, long color,
            @ApiOptional Integer alpha, @ApiOptional Double yOffset,
            @ApiOptional Boolean alwaysOnTop) {
        if (entity == null) {
            throw new IllegalArgumentException("entity is required");
        }
        EntityTraceLine line = entityTraceLineBuilder();
        line.setEntity(entity);
        line.setColor(color, alpha);
        if (yOffset != null) {
            line.setYOffset(yOffset);
        }
        if (alwaysOnTop != null) {
            line.setAlwaysOnTop(alwaysOnTop);
        }
        return line.buildAndAdd();
    }

    /**
     * Java-side shortcut for {@link #addEntityTraceLine} with default
     * alpha, offset and depth bucket; hidden from the script surface.
     *
     * @zh {@link #addEntityTraceLine} 缺省参数的 Java 捷径，不进脚本面。
     */
    @ApiInternal
    public EntityTraceLine addEntityTraceLine(EntityApi.Entity entity, long color) {
        return addEntityTraceLine(entity, color, null, null, null);
    }

    /**
     * Adds a world-space panel at {@code (x, y, z)} with the requested
     * geometry and rendering options. Defaults follow the quick form: a
     * {@code 1 × 1}-block panel, no rotation (corner pivot), 200 minimum
     * subdivisions, no back-face rendering and no depth culling.
     *
     * @param x panel corner x
     * @param y panel corner y
     * @param z panel corner z
     * @param xRot rotation about the x axis in degrees; defaults to 0
     * @param yRot rotation about the y axis in degrees; defaults to 0
     * @param zRot rotation about the z axis in degrees; defaults to 0
     * @param width panel width in blocks; defaults to 1
     * @param height panel height in blocks; defaults to 1
     * @param minSubdivisions subdivisions across the shorter side,
     *                        clamped to at least 1; defaults to 200
     * @param renderBack whether the back face is drawn; defaults to false
     * @param cull whether the panel is depth tested; defaults to false
     * @return the attached surface
     * @zh 按给定几何与渲染选项添加世界面板；缺省为快速形默认（{@code 1 × 1}、无旋转、200 细分、不绘制背面、不深度测试）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface addDraw2D(double x, double y, double z,
            @ApiOptional Double xRot, @ApiOptional Double yRot, @ApiOptional Double zRot,
            @ApiOptional Double width, @ApiOptional Double height,
            @ApiOptional Integer minSubdivisions, @ApiOptional Boolean renderBack,
            @ApiOptional Boolean cull) {
        Surface surface = new Surface();
        surface.origin(this);
        surface.setPos(new Pos3D(x, y, z));
        surface.setRotations(xRot != null ? xRot : 0, yRot != null ? yRot : 0,
                zRot != null ? zRot : 0);
        surface.setSizes(width != null ? width : 1, height != null ? height : 1);
        surface.setMinSubdivisions(minSubdivisions != null ? minSubdivisions : 200);
        surface.renderBack(renderBack != null && renderBack);
        surface.cull(cull != null && cull);
        return surface.buildAndAdd();
    }

    /**
     * Java-side shortcut for {@link #addDraw2D} with quick-form defaults;
     * hidden from the script surface.
     *
     * @zh {@link #addDraw2D} 快速形缺省参数的 Java 捷径，不进脚本面。
     */
    @ApiInternal
    public Surface addDraw2D(double x, double y, double z) {
        return addDraw2D(x, y, z, null, null, null, null, null, null, null, null);
    }

    /**
     * Adds an existing surface (one returned by {@code build()}) to this
     * overlay. A surface already attached elsewhere is rejected; attaching
     * the same instance twice is a no-op — the keyed port keeps one entry
     * per element (see {@link #reAddElement(RenderElement3D)}).
     *
     * @param surface the surface to attach
     * @throws IllegalStateException when the surface belongs to another overlay
     * @zh 把已有面板挂到本图层；已在别处挂载时抛错，重复挂载为 no-op（键控端口每元素一格）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void addSurface(Surface surface) {
        addElement(surface);
    }

    /**
     * Removes the trace line from this overlay if present; detached state
     * is kept so it can be re-attached.
     *
     * @param traceLine the trace line to remove
     * @return this overlay
     * @zh 从本图层摘除追踪线（若在）；状态保留，可再挂回。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw3D removeTraceLine(TraceLine traceLine) {
        return remove(traceLine);
    }

    /**
     * Removes the panel from this overlay if present; the surface object
     * and its state are not modified.
     *
     * @param surface the surface to take off this overlay
     * @zh 从本图层面板摘除（若在）；面板对象与其状态不受影响。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void removeDraw2D(Surface surface) {
        remove(surface);
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
