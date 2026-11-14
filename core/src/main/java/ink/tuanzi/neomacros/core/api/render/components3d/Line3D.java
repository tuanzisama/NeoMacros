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
 * A fixed line segment between two ordered world-space endpoints. Unlike a
 * box the endpoints are not reordered — the first stays the first.
 *
 * <p>Like {@link Box} this class is both element and builder (G-001
 * 拍板②): take one from {@link Draw3D#lineBuilder()}, configure it with the
 * fluent setters, then {@link #buildAndAdd()} to attach it. {@code cull}
 * controls depth testing ({@code true} lets geometry obscure the line);
 * {@code alwaysOnTop} is its inverse. Defaults: opaque white, drawn over
 * the world.</p>
 *
 * <p>Packed colour parameters accept the full-range JS literals
 * ({@code 0xFF00FF00}) through a Java-side {@code long}.</p>
 *
 * @zh 连接两个有序端点的固定线段（端点不重排）。与 Box 一样既是元素也是构建器（拍板②）；{@code cull} 控制深度测试，{@code alwaysOnTop} 是其反义。
 */
@ApiClass(name = "Line3D", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public class Line3D extends RenderElement3D<Line3D> {

    private double x1;
    private double y1;
    private double z1;
    private double x2;
    private double y2;
    private double z2;

    /** Line colour as last supplied (may carry a stray alpha byte). */
    private int color = 0xFFFFFF;
    /** Alpha kept separate from {@link #color}; default opaque. */
    private int alpha = 0xFF;
    private boolean cull;

    /** Creates a detached zero-length line at the origin, opaque white. */
    public Line3D() {
    }

    /**
     * Replaces both endpoints in the supplied order (not min/max bounds).
     *
     * @zh 按传入顺序替换两个端点（不是最小/最大边界）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Line3D setPos(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.x2 = x2;
        this.y2 = y2;
        this.z2 = z2;
        push();
        return this;
    }

    /**
     * Sets the first endpoint.
     *
     * @zh 设置第一个端点。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Line3D setPos1(Pos3D pos) {
        if (pos == null) {
            throw new IllegalArgumentException("pos is required");
        }
        this.x1 = pos.getX();
        this.y1 = pos.getY();
        this.z1 = pos.getZ();
        push();
        return this;
    }

    /**
     * Sets the second endpoint.
     *
     * @zh 设置第二个端点。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Line3D setPos2(Pos3D pos) {
        if (pos == null) {
            throw new IllegalArgumentException("pos is required");
        }
        this.x2 = pos.getX();
        this.y2 = pos.getY();
        this.z2 = pos.getZ();
        push();
        return this;
    }

    /**
     * Sets both ordered endpoints at once, creating copies of the supplied
     * points.
     *
     * @zh 一次性设置两个有序端点（拷贝坐标值）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Line3D pos(Pos3D pos1, Pos3D pos2) {
        if (pos1 == null || pos2 == null) {
            throw new IllegalArgumentException("both endpoints are required");
        }
        return setPos(pos1.getX(), pos1.getY(), pos1.getZ(),
                pos2.getX(), pos2.getY(), pos2.getZ());
    }

    /**
     * Sets the packed colour; a non-zero RGB value with a zero alpha byte
     * becomes opaque, transparent black stays transparent.
     *
     * @param color packed colour (full-range JS literals accepted)
     * @param alpha explicit alpha replacing the top byte; omitted derives it
     *              from {@code color} with the zero-alpha rule
     * @zh 设置打包颜色；RGB 非零而 alpha 为零时不透明。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Line3D setColor(long color, @ApiOptional Integer alpha) {
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
    public Line3D setColor(long color) {
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
    public Line3D setAlpha(int alpha) {
        this.alpha = alpha;
        push();
        return this;
    }

    /**
     * Sets depth testing: {@code true} lets geometry obscure the line.
     *
     * @zh 设置深度测试：{@code true} 时可被地形遮挡。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Line3D cull(boolean cull) {
        this.cull = cull;
        push();
        return this;
    }

    /**
     * Draws the line over the world regardless of depth; the inverse of
     * {@link #cull(boolean)}.
     *
     * @zh 无视深度画在世界之上，是 {@code cull} 的反义。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Line3D setAlwaysOnTop(boolean alwaysOnTop) {
        this.cull = !alwaysOnTop;
        push();
        return this;
    }

    /**
     * Returns this line without attaching it to an overlay.
     *
     * @zh 返回该线段本身，但不挂载到图层。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Line3D build() {
        return this;
    }

    /**
     * Attaches this line to the overlay its builder came from and returns
     * it.
     *
     * @return this line, now attached
     * @throws IllegalStateException when already attached or built detached
     * @zh 挂载到构建时所属图层并返回自身。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Line3D buildAndAdd() {
        if (shapeId() != null) {
            throw new IllegalStateException("line is already attached to an overlay");
        }
        Draw3D target = origin();
        if (target == null) {
            throw new IllegalStateException("line was not created from an overlay builder");
        }
        target.addElement(this);
        return this;
    }

    /**
     * The first endpoint as a point copy.
     *
     * @zh 第一个端点的拷贝。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Pos3D getPos1() {
        return new Pos3D(x1, y1, z1);
    }

    /**
     * The second endpoint as a point copy.
     *
     * @zh 第二个端点的拷贝。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Pos3D getPos2() {
        return new Pos3D(x2, y2, z2);
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
     * Whether the line is depth tested; {@code false} draws it over the
     * world.
     *
     * @zh 是否深度测试；{@code false} 画在世界之上。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isCulled() {
        return cull;
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
     * Orders lines by their endpoint coordinates in endpoint order;
     * reversing the segment can change the result. Colour and culling are
     * ignored.
     *
     * @zh 按端点坐标顺序比较；反转端点可能改变结果。忽略颜色与深度测试。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @Override
    public int compareToSame(Line3D other) {
        int result = Double.compare(x1, other.x1);
        if (result == 0) {
            result = Double.compare(y1, other.y1);
        }
        if (result == 0) {
            result = Double.compare(z1, other.z1);
        }
        if (result == 0) {
            result = Double.compare(x2, other.x2);
        }
        if (result == 0) {
            result = Double.compare(y2, other.y2);
        }
        if (result == 0) {
            result = Double.compare(z2, other.z2);
        }
        return result;
    }

    /** Snapshot pushed to the port: ordered endpoints plus colour and cull. */
    @ApiInternal
    @Override
    public ShapeSpec toSpec() {
        int packed = (alpha << 24) | (color & 0xFFFFFF);
        return new ShapeSpec.Line(new Pos3d(x1, y1, z1), new Pos3d(x2, y2, z2), packed, cull);
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
