package ink.tuanzi.neomacros.core.api.render.components3d;

import ink.tuanzi.neomacros.core.api.render.Draw3D;
import ink.tuanzi.neomacros.core.api.types.Pos3D;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ShapeSpec;

/**
 * A line from the camera toward a script-selected world-space target. The
 * renderer updates the camera-side endpoint every frame; {@link #getPos()}
 * and {@link #setPos(Pos3D)} refer to the target endpoint only.
 *
 * <p>Like {@link Box} this class is both element and builder (G-001
 * 拍板②): take one from {@link Draw3D#traceLineBuilder()}, configure it
 * with the fluent setters, then {@link #buildAndAdd()} to attach it.
 * Defaults: opaque white target, drawn over terrain ({@code alwaysOnTop}
 * enabled, i.e. {@code cull == false}).</p>
 *
 * <p>Packed colour parameters accept the full-range JS literals
 * ({@code 0xFF00FF00}) through a Java-side {@code long}.</p>
 *
 * @zh 从摄像机指向脚本选定世界目标点的连线；渲染器每帧更新摄像机一端，{@code pos} 只指目标端点。与 Box 一样既是元素也是构建器（拍板②）；默认白色不透明、画在世界之上。
 */
@ApiClass(name = "TraceLine", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public class TraceLine extends RenderElement3D<TraceLine> {

    /** Target endpoint; the camera side is derived during rendering. */
    private double x;
    private double y;
    private double z;

    /** Line colour as last supplied (may carry a stray alpha byte). */
    private int color = 0xFFFFFF;
    /** Alpha kept separate from {@link #color}; default opaque. */
    private int alpha = 0xFF;
    /** Depth testing; {@code false} draws over terrain (always-on-top). */
    private boolean cull;

    /** Creates a detached trace toward the origin, opaque white, over terrain. */
    public TraceLine() {
    }

    /**
     * Sets the target endpoint from the coordinates in {@code pos}; later
     * changes to {@code pos} do not move the line (coordinates are copied).
     *
     * @zh 从 {@code pos} 的坐标设置目标端点（拷贝坐标值，之后改动 {@code pos} 不再移动线）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TraceLine setPos(Pos3D pos) {
        if (pos == null) {
            throw new IllegalArgumentException("pos is required");
        }
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        push();
        return this;
    }

    /**
     * Sets the packed colour; a non-zero RGB value with a zero alpha byte
     * becomes opaque, transparent black stays transparent.
     *
     * @param color packed colour (full-range JS literals accepted)
     * @param alpha explicit alpha replacing the top byte; omitted derives it
     *              from {@code color} with the zero-alpha rule
     * @zh 设置打包颜色；RGB 非零而 alpha 为零时不透明，全零保持透明。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TraceLine setColor(long color, @ApiOptional Integer alpha) {
        this.color = (int) color;
        this.alpha = alpha != null ? alpha : deriveAlpha((int) color);
        push();
        return this;
    }

    /**
     * Java-side shortcut for {@link #setColor(long, Integer)} without an
     * explicit alpha; hidden from the script surface.
     *
     * @zh {@link #setColor(long, Integer)} 的 Java 捷径（省略 alpha），不进脚本面。
     */
    @ApiInternal
    public TraceLine setColor(long color) {
        return setColor(color, null);
    }

    /**
     * Replaces the alpha without changing the RGB bits.
     *
     * @zh 只替换 alpha，不动 RGB。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TraceLine setAlpha(int alpha) {
        this.alpha = alpha;
        push();
        return this;
    }

    /**
     * Sets whether depth testing is disabled ({@code true}, draws over
     * terrain) or enabled ({@code false}); defaults to {@code true}.
     *
     * @zh 设置是否关闭深度测试（{@code true} = 画在世界之上）；默认 {@code true}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TraceLine setAlwaysOnTop(boolean alwaysOnTop) {
        this.cull = !alwaysOnTop;
        push();
        return this;
    }

    /**
     * Returns this trace line without attaching it to an overlay.
     *
     * @zh 返回该追踪线本身，但不挂载到图层。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TraceLine build() {
        return this;
    }

    /**
     * Attaches this trace line to the overlay its builder came from and
     * returns it.
     *
     * @return this trace line, now attached
     * @throws IllegalStateException when already attached or built detached
     * @zh 挂载到构建时所属图层并返回自身。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TraceLine buildAndAdd() {
        if (shapeId() != null) {
            throw new IllegalStateException("trace line is already attached to an overlay");
        }
        Draw3D target = origin();
        if (target == null) {
            throw new IllegalStateException("trace line was not created from an overlay builder");
        }
        target.addElement(this);
        return this;
    }

    /**
     * The target endpoint as a point copy.
     *
     * @zh 目标端点的拷贝。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Pos3D getPos() {
        return new Pos3D(x, y, z);
    }

    /**
     * The line colour's low 24 bits; read alpha separately with
     * {@link #getAlpha()}.
     *
     * @zh 线颜色的低 24 位；alpha 见 {@link #getAlpha()}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getColor() {
        return color & 0xFFFFFF;
    }

    /**
     * The line alpha.
     *
     * @zh 线的 alpha。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getAlpha() {
        return alpha;
    }

    /**
     * Whether the line is drawn over the world regardless of depth.
     *
     * @zh 是否无视深度画在世界之上。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isAlwaysOnTop() {
        return !cull;
    }

    /**
     * Orders trace lines by their target endpoint's coordinates; colour and
     * depth testing are ignored.
     *
     * @zh 按目标端点坐标比较；忽略颜色与深度测试。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @Override
    public int compareToSame(TraceLine other) {
        int result = Double.compare(x, other.x);
        if (result == 0) {
            result = Double.compare(y, other.y);
        }
        if (result == 0) {
            result = Double.compare(z, other.z);
        }
        return result;
    }

    /** Snapshot pushed to the port: target endpoint, colour and depth bucket. */
    @ApiInternal
    @Override
    public ShapeSpec toSpec() {
        return new ShapeSpec.CameraTrace(new Pos3d(x, y, z), packedColor(), cull);
    }

    /** Packed ARGB colour of this line (subclass snapshots reuse it). */
    @ApiInternal
    protected final int packedColor() {
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    /** Whether this line is depth tested (subclass snapshots reuse it). */
    @ApiInternal
    protected final boolean depthTested() {
        return cull;
    }

    /** Derives a packed colour's alpha byte, making non-black RGB opaque. */
    private static int deriveAlpha(int packed) {
        int derived = (packed >>> 24) & 0xFF;
        if (derived == 0 && (packed & 0xFFFFFF) != 0) {
            return 0xFF;
        }
        return derived;
    }
}
