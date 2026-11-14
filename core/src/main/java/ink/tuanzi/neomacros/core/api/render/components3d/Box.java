package ink.tuanzi.neomacros.core.api.render.components3d;

import ink.tuanzi.neomacros.core.api.render.Draw3D;
import ink.tuanzi.neomacros.core.api.types.BlockPos;
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
 * An axis-aligned cuboid drawn in the world as an outline with an optional
 * filled interior. Its geometry is two opposite corners (order retained);
 * outline and fill colours are independent.
 *
 * <p>This class is both the element and its builder (G-001 拍板②): obtain
 * one from {@link Draw3D#boxBuilder()}, configure it with the fluent
 * setters, then {@link #buildAndAdd()} to attach it — mutations after
 * attachment re-project immediately. {@link #build()} returns the box
 * without attaching it. {@code cull} enables depth testing so terrain can
 * hide the box; the default {@code false} draws it over the world.</p>
 *
 * <p>Colour handling: a packed colour whose alpha byte is zero but whose
 * RGB is non-zero becomes opaque; fully transparent black stays
 * transparent. {@link #setColor(long, Integer)} keeps an explicit zero
 * alpha. Packed parameters accept the full-range JS literals
 * ({@code 0xFF00FF00}) through a Java-side {@code long}.</p>
 *
 * @zh 世界内的轴对齐长方体：描边 + 可选填充。该类同时是元素与构建器（拍板②）：从 {@code boxBuilder()} 取得，fluent 配置后 {@code buildAndAdd()} 挂载；挂载后的修改即改即生效。{@code cull} 开启深度测试（默认 false，画在世界之上）。
 */
@ApiClass(name = "Box", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public class Box extends RenderElement3D<Box> {

    private double x1;
    private double y1;
    private double z1;
    private double x2;
    private double y2;
    private double z2;

    /** Outline colour as last supplied (may carry a stray alpha byte). */
    private int color = 0xFFFFFF;
    /** Outline alpha kept separate from {@link #color}; default opaque. */
    private int alpha = 0xFF;
    /** Fill colour as last supplied. */
    private int fillColor = 0xFFFFFF;
    /** Fill alpha kept separate from {@link #fillColor}; default opaque. */
    private int fillAlpha = 0xFF;
    private boolean fill;
    private boolean cull;

    /** Creates a detached zero-size box at the origin with opaque white colours. */
    public Box() {
    }

    /**
     * Replaces both corners, retaining the supplied order.
     *
     * @param x1 first corner x
     * @param y1 first corner y
     * @param z1 first corner z
     * @param x2 opposite corner x
     * @param y2 opposite corner y
     * @param z2 opposite corner z
     * @zh 替换两个对角点，保留传入顺序。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box setPos(double x1, double y1, double z1, double x2, double y2, double z2) {
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
     * Covers the block at {@code pos}, extending one block along each axis
     * from its integer coordinates.
     *
     * @zh 覆盖 {@code pos} 所指方块：从其整数坐标沿各轴延伸一格。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box setPosToBlock(BlockPos pos) {
        if (pos == null) {
            throw new IllegalArgumentException("pos is required");
        }
        return blockAt(pos.getIntX(), pos.getIntY(), pos.getIntZ());
    }

    /**
     * Sets a cube centred on {@code pos} with side length {@code 2 * radius};
     * a negative radius reverses the corner order.
     *
     * @zh 以 {@code pos} 为中心、边长 {@code 2 * radius} 的立方体；半径为负时反转角点顺序。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box setPosToPoint(Pos3D pos, double radius) {
        if (pos == null) {
            throw new IllegalArgumentException("pos is required");
        }
        return setPos(pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
                pos.getX() + radius, pos.getY() + radius, pos.getZ() + radius);
    }

    /**
     * Sets the outline colour; a non-zero RGB value with a zero alpha byte
     * becomes opaque, transparent black stays transparent.
     *
     * @param color packed colour (full-range JS literals accepted)
     * @param alpha explicit alpha replacing the top byte; omitted derives it
     *              from {@code color} with the zero-alpha rule
     * @zh 设置描边颜色；RGB 非零而 alpha 为零时不透明，全零保持透明。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box setColor(long color, @ApiOptional Integer alpha) {
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
    public Box setColor(long color) {
        return setColor(color, null);
    }

    /**
     * Sets the fill colour with the same zero-alpha rule as
     * {@link #setColor(long, Integer)}; stored even while {@code fill} is
     * off.
     *
     * @param fillColor packed colour (full-range JS literals accepted)
     * @param alpha explicit fill alpha; omitted derives it from {@code fillColor}
     * @zh 设置填充颜色（同一零 alpha 规则）；即使未开启填充也会保存。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box setFillColor(long fillColor, @ApiOptional Integer alpha) {
        this.fillColor = (int) fillColor;
        this.fillAlpha = alpha != null ? alpha : deriveAlpha((int) fillColor);
        push();
        return this;
    }

    /**
     * Java-side shortcut for {@link #setFillColor(long, Integer)} without
     * an explicit alpha; hidden from the script surface.
     *
     * @zh {@link #setFillColor(long, Integer)} 的 Java 捷径（省略 alpha），不进脚本面。
     */
    @ApiInternal
    public Box setFillColor(long fillColor) {
        return setFillColor(fillColor, null);
    }

    /**
     * Replaces the outline alpha without touching the RGB bits or the fill.
     *
     * @zh 只替换描边 alpha，不动 RGB 与填充。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box setAlpha(int alpha) {
        this.alpha = alpha;
        push();
        return this;
    }

    /**
     * Replaces the fill alpha without touching the fill colour or outline.
     *
     * @zh 只替换填充 alpha，不动填充色与描边。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box setFillAlpha(int alpha) {
        this.fillAlpha = alpha;
        push();
        return this;
    }

    /**
     * Controls whether the interior is drawn; the outline is unaffected.
     *
     * @zh 控制是否绘制内部；不影响描边。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box setFill(boolean fill) {
        this.fill = fill;
        push();
        return this;
    }

    /**
     * Sets the first corner; a second corner is still required to define
     * the box.
     *
     * @zh 设置第一个角点；仍需第二个角点才能确定盒子。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box setPos1(Pos3D pos) {
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
     * Sets the opposite corner.
     *
     * @zh 设置对角点。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box setPos2(Pos3D pos) {
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
     * Sets both corners at once, creating copies of the supplied points.
     *
     * @param pos1 first corner
     * @param pos2 opposite corner
     * @zh 一次性设置两个角点（拷贝坐标值）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box pos(Pos3D pos1, Pos3D pos2) {
        if (pos1 == null || pos2 == null) {
            throw new IllegalArgumentException("both corners are required");
        }
        return setPos(pos1.getX(), pos1.getY(), pos1.getZ(),
                pos2.getX(), pos2.getY(), pos2.getZ());
    }

    /**
     * Sets both corners to cover the block at these coordinates, from its
     * corner one block further along each axis; replaces any previous
     * corners.
     *
     * @zh 覆盖该坐标处的方块（从角点沿各轴一格），替换已有角点。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box forBlock(BlockPos pos) {
        if (pos == null) {
            throw new IllegalArgumentException("pos is required");
        }
        return blockAt(pos.getIntX(), pos.getIntY(), pos.getIntZ());
    }

    /**
     * Enables depth testing (terrain can hide the box) or draws over the
     * world; defaults to {@code false}.
     *
     * @zh 开启深度测试（地形可遮挡）或画在世界之上；默认 {@code false}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box cull(boolean cull) {
        this.cull = cull;
        push();
        return this;
    }

    /**
     * Returns this box without attaching it to an overlay.
     *
     * @zh 返回该盒子本身，但不挂载到图层。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box build() {
        return this;
    }

    /**
     * Attaches this box to the overlay its builder came from and returns it.
     *
     * @return this box, now attached
     * @throws IllegalStateException when already attached or built detached
     * @zh 挂载到构建时所属图层并返回自身。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Box buildAndAdd() {
        if (shapeId() != null) {
            throw new IllegalStateException("box is already attached to an overlay");
        }
        Draw3D target = origin();
        if (target == null) {
            throw new IllegalStateException("box was not created from an overlay builder");
        }
        target.addElement(this);
        return this;
    }

    /**
     * The first corner as a point copy; modifying it does not move the box.
     *
     * @zh 第一个角点的拷贝；修改它不会移动盒子。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Pos3D getPos1() {
        return new Pos3D(x1, y1, z1);
    }

    /**
     * The opposite corner as a point copy.
     *
     * @zh 对角点的拷贝。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Pos3D getPos2() {
        return new Pos3D(x2, y2, z2);
    }

    /**
     * The outline colour's low 24 bits; read alpha separately with
     * {@link #getAlpha()}.
     *
     * @zh 描边颜色的低 24 位；alpha 见 {@link #getAlpha()}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getColor() {
        return color & 0xFFFFFF;
    }

    /**
     * The outline alpha.
     *
     * @zh 描边 alpha。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getAlpha() {
        return alpha;
    }

    /**
     * The fill colour's low 24 bits; read fill alpha separately with
     * {@link #getFillAlpha()}.
     *
     * @zh 填充颜色的低 24 位；填充 alpha 见 {@link #getFillAlpha()}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getFillColor() {
        return fillColor & 0xFFFFFF;
    }

    /**
     * The fill alpha.
     *
     * @zh 填充 alpha。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getFillAlpha() {
        return fillAlpha;
    }

    /**
     * Whether the interior is drawn.
     *
     * @zh 是否绘制内部。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isFilled() {
        return fill;
    }

    /**
     * Whether the box is depth tested; {@code false} draws it over the
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
     * Orders boxes lexicographically by their corner coordinates in
     * supplied order; colours, fill and culling are ignored.
     *
     * @zh 按角点坐标字典序比较；忽略颜色、填充与深度测试。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @Override
    public int compareToSame(Box other) {
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

    /** Snapshot pushed to the port: corners plus packed outline/fill colours. */
    @ApiInternal
    @Override
    public ShapeSpec toSpec() {
        int packedColor = (alpha << 24) | (color & 0xFFFFFF);
        int packedFill = (fillAlpha << 24) | (fillColor & 0xFFFFFF);
        return new ShapeSpec.Box(new Pos3d(x1, y1, z1), new Pos3d(x2, y2, z2),
                packedColor, packedFill, fill, cull);
    }

    /** Derives a packed colour's alpha byte, making non-black RGB opaque. */
    private static int deriveAlpha(int packed) {
        int derived = (packed >>> 24) & 0xFF;
        if (derived == 0 && (packed & 0xFFFFFF) != 0) {
            return 0xFF;
        }
        return derived;
    }

    /** Covers one block from its integer corner; internal shortcut. */
    private Box blockAt(int x, int y, int z) {
        return setPos(x, y, z, x + 1, y + 1, z + 1);
    }
}
