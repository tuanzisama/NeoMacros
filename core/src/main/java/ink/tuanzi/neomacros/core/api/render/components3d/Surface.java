package ink.tuanzi.neomacros.core.api.render.components3d;

import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.core.api.render.Draw3D;
import ink.tuanzi.neomacros.core.api.types.Pos2D;
import ink.tuanzi.neomacros.core.api.types.Pos3D;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.Pos3d;
import ink.tuanzi.neomacros.port.model.ShapeSpec;

import java.util.UUID;

/**
 * A flat rectangular panel placed in the world — a 2D overlay living in
 * three dimensions. Its size is in blocks; {@link #getWidth()} and
 * {@link #getHeight()} report surface pixels (the smaller side carries
 * {@link #getMinSubdivisions()} pixels, the other side scales with the
 * aspect ratio). The renderer re-derives the panel's four corners every
 * frame from its position, size and rotations (applied y, then x, then z,
 * in degrees), so live mutations re-project immediately.
 *
 * <p>Three settings decide whether it can be seen: {@code cull} depth-
 * tests it against terrain (off draws it over the world), {@code renderBack}
 * draws the far side (off hides the panel from behind), and
 * {@code rotateToPlayer} overwrites the rotations each frame with a
 * camera-facing billboard. {@link #bindToEntity(EntityApi.Entity)} makes
 * the panel follow an entity's position plus {@link #getBoundOffset()}.
 * Lighting defaults to full brightness; {@link #setWorldLight()} and
 * {@link #setLight(int, int)} record world/fixed levels (the current
 * pipelines draw flat colour).</p>
 *
 * <p>This class is both element and builder (G-001 拍板②): take one from
 * {@link Draw3D#surfaceBuilder()} or {@link Draw3D#addDraw2D}, configure
 * with the fluent setters, then {@link #buildAndAdd()} to attach it.
 * {@link Draw3D#surfaceBuilder()} defaults: 10 × 10 blocks, centred
 * rotation, rendered back, no depth culling, full-bright, one subdivision
 * across the smaller side; the {@code addDraw2D} quick forms default to a
 * 1 × 1 panel, no rotation, corner pivot, 200 subdivisions, no back face
 * and no depth culling.</p>
 *
 * @zh 放在世界中的矩形平面（三维里的二维面板）：尺寸以格计，{@code width}/{@code height} 报告表面像素；渲染器每帧由位置/尺寸/旋转（y、x、z 顺序，度）重算四角。{@code cull}/{@code renderBack}/{@code rotateToPlayer} 决定可见性，{@code bindToEntity} 跟随实体。该类同时是元素与构建器（拍板②）。
 */
@ApiClass(name = "Surface", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public class Surface extends RenderElement3D<Surface> {

    /** Lighting mode recorded for scripts; pipelines draw flat colour. */
    private enum Light { FULL_BRIGHT, WORLD, FIXED }

    private double x;
    private double y;
    private double z;

    /** Physical size in blocks. */
    private double width = 10;
    private double height = 10;

    /** Rotations in degrees, applied y, then x, then z. */
    private double xRot;
    private double yRot;
    private double zRot;

    private boolean rotateCenter;
    private boolean rotateToPlayer;

    /** Followed entity handle / captured UUID, or null while unbound. */
    private EntityApi.Entity boundEntity;
    private UUID boundUuid;
    private double offsetX;
    private double offsetY;
    private double offsetZ;

    private int minSubdivisions = 1;
    private boolean renderBack;
    private boolean cull;
    /** Blocks per z-index unit (element faces scale with it). */
    private double zIndexScale = 0.001;

    private Light lightMode = Light.FULL_BRIGHT;
    private int blockLight;
    private int skyLight;

    /** Creates a detached 10 × 10 panel at the origin with class defaults. */
    public Surface() {
    }

    /**
     * Moves this surface to {@code pos}'s coordinates: the panel's top-left
     * corner, or its centre while {@link #setRotateCenter(boolean)} is on.
     * Coordinates are copied; later changes to {@code pos} do not move it.
     *
     * @zh 移动到 {@code pos} 的坐标（拷贝）：默认为面板左上角，{@code rotateCenter} 开启时为面板中心。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setPos(Pos3D pos) {
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
     * Binds this surface to an entity: while the entity is alive, its
     * position plus {@link #getBoundOffset()} determines the render
     * position. Passing {@code null} unbinds it.
     *
     * @param boundEntity the entity to follow, or null to unbind
     * @zh 绑定跟随的实体（渲染位置 = 实体位置 + 偏移）；传 {@code null} 解绑。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface bindToEntity(EntityApi.Entity boundEntity) {
        this.boundEntity = boundEntity;
        this.boundUuid = boundEntity == null ? null : parseUuid(boundEntity);
        push();
        return this;
    }

    /**
     * The followed entity, or {@code null} while unbound.
     *
     * @zh 跟随的实体句柄；未绑定时为 {@code null}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public EntityApi.Entity getBoundEntity() {
        return boundEntity;
    }

    /**
     * Sets the offset added to the bound entity's position, in blocks; it
     * has no effect while the surface is unbound. Coordinates are copied.
     *
     * @zh 设置叠加在实体位置上的偏移（格）；未绑定时无效。拷贝坐标值。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setBoundOffset(Pos3D boundOffset) {
        if (boundOffset == null) {
            throw new IllegalArgumentException("boundOffset is required");
        }
        this.offsetX = boundOffset.getX();
        this.offsetY = boundOffset.getY();
        this.offsetZ = boundOffset.getZ();
        push();
        return this;
    }

    /**
     * The entity-following offset as a point copy.
     *
     * @zh 实体跟随偏移的拷贝。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Pos3D getBoundOffset() {
        return new Pos3D(offsetX, offsetY, offsetZ);
    }

    /**
     * Sets whether the surface is rotated to face the camera every frame,
     * overwriting the configured rotations while enabled.
     *
     * @zh 设置是否每帧转向面向摄像机（开启时覆盖已配置的旋转）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setRotateToPlayer(boolean rotateToPlayer) {
        this.rotateToPlayer = rotateToPlayer;
        push();
        return this;
    }

    /**
     * Whether the surface faces the camera during rendering.
     *
     * @zh 渲染时是否面向摄像机。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean doesRotateToPlayer() {
        return rotateToPlayer;
    }

    /**
     * Sets the surface's rotations in degrees, applied in the order y,
     * then x, then z (y swings the panel around to a compass direction,
     * the other two tilt it).
     *
     * @zh 设置旋转（度），按 y、x、z 顺序应用。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setRotations(double x, double y, double z) {
        this.xRot = x;
        this.yRot = y;
        this.zRot = z;
        push();
        return this;
    }

    /**
     * Sets the physical width and height in blocks (not pixel room — the
     * pixels are subdivisions of this size).
     *
     * @zh 设置物理宽高（格）；像素是该尺寸的细分。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setSizes(double width, double height) {
        this.width = width;
        this.height = height;
        push();
        return this;
    }

    /**
     * The physical width and height in blocks as a copy; writing to it
     * does not resize the surface — use {@link #setSizes(double, double)}.
     *
     * @zh 物理宽高（格）的拷贝；修改它不会改变尺寸。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Pos2D getSizes() {
        return new Pos2D(width, height);
    }

    /**
     * Sets the number of subdivisions across the smaller side; values of
     * {@code 0} or below are raised to {@code 1}.
     *
     * @zh 设置较短边的细分数；小于等于 {@code 0} 时提升为 {@code 1}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setMinSubdivisions(int minSubdivisions) {
        this.minSubdivisions = Math.max(1, minSubdivisions);
        push();
        return this;
    }

    /**
     * The subdivision count across this surface's smaller side.
     *
     * @zh 较短边的细分数。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getMinSubdivisions() {
        return minSubdivisions;
    }

    /**
     * Makes everything on this surface render at full brightness,
     * ignoring world lighting (the default).
     *
     * @zh 全亮渲染，忽略世界光照（默认）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setFullBrightLight() {
        this.lightMode = Light.FULL_BRIGHT;
        push();
        return this;
    }

    /**
     * Makes the surface use the block and sky light at its render
     * position, sampled each frame.
     *
     * @zh 每帧在渲染位置采样方块光与天空光。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setWorldLight() {
        this.lightMode = Light.WORLD;
        push();
        return this;
    }

    /**
     * Sets fixed block and sky light levels for this surface.
     *
     * @zh 设置固定的方块光与天空光等级。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setLight(int blockLight, int skyLight) {
        this.lightMode = Light.FIXED;
        this.blockLight = blockLight;
        this.skyLight = skyLight;
        push();
        return this;
    }

    /**
     * The surface width in pixels: the smaller side carries
     * {@link #getMinSubdivisions()} pixels and the other side scales with
     * the surface's aspect ratio, truncated to an integer.
     *
     * @zh 面板宽度（像素）：较短边为 {@code minSubdivisions} 像素，另一边按宽高比换算并截断。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getWidth() {
        if (width <= 0 || height <= 0) {
            return minSubdivisions;
        }
        return width <= height
                ? minSubdivisions
                : (int) (minSubdivisions * width / height);
    }

    /**
     * The surface height in pixels, scaled like {@link #getWidth()}.
     *
     * @zh 面板高度（像素），换算规则同 {@link #getWidth()}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getHeight() {
        if (width <= 0 || height <= 0) {
            return minSubdivisions;
        }
        return height <= width
                ? minSubdivisions
                : (int) (minSubdivisions * height / width);
    }

    /**
     * Selects the rotation pivot (and what {@code pos} means): the panel's
     * corner while off, its centre while on.
     *
     * @zh 选择旋转轴心（及 {@code pos} 含义）：关闭时为角点，开启时为中心。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setRotateCenter(boolean rotateCenter) {
        this.rotateCenter = rotateCenter;
        push();
        return this;
    }

    /**
     * Whether rotations pivot around the centre rather than the corner.
     *
     * @zh 旋转是否绕中心而非角点。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isRotatingCenter() {
        return rotateCenter;
    }

    /**
     * Whether the back of the panel is drawn as well as its front.
     *
     * @zh 是否绘制面板背面。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean shouldRenderBack() {
        return renderBack;
    }

    /**
     * Controls whether the back of the panel is drawn; with it off the
     * surface disappears when the camera is behind it.
     *
     * @zh 控制是否绘制背面；关闭时摄像机位于背面则面板不可见。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface renderBack(boolean renderBack) {
        this.renderBack = renderBack;
        push();
        return this;
    }

    /**
     * Enables depth testing (terrain can hide the panel) or draws it over
     * the world; defaults to {@code false}.
     *
     * @zh 开启深度测试（地形可遮挡）或画在世界之上；默认 {@code false}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface cull(boolean cull) {
        this.cull = cull;
        push();
        return this;
    }

    /**
     * Whether the panel is depth tested; {@code false} draws it over the
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
     * The x rotation in degrees.
     *
     * @zh X 轴旋转（度）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public double getXRotation() {
        return xRot;
    }

    /**
     * Sets the x rotation in degrees.
     *
     * @zh 设置 X 轴旋转（度）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setXRotation(double xRot) {
        this.xRot = xRot;
        push();
        return this;
    }

    /**
     * The y rotation in degrees.
     *
     * @zh Y 轴旋转（度）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public double getYRotation() {
        return yRot;
    }

    /**
     * Sets the y rotation in degrees.
     *
     * @zh 设置 Y 轴旋转（度）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setYRotation(double yRot) {
        this.yRot = yRot;
        push();
        return this;
    }

    /**
     * The z rotation in degrees.
     *
     * @zh Z 轴旋转（度）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public double getZRotation() {
        return zRot;
    }

    /**
     * Sets the z rotation in degrees.
     *
     * @zh 设置 Z 轴旋转（度）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setZRotation(double zRot) {
        this.zRot = zRot;
        push();
        return this;
    }

    /**
     * Sets the physical width in blocks, keeping the height.
     *
     * @zh 设置宽度（格），高度不变。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setWidth(double width) {
        this.width = width;
        push();
        return this;
    }

    /**
     * Sets the physical height in blocks, keeping the width.
     *
     * @zh 设置高度（格），宽度不变。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setHeight(double height) {
        this.height = height;
        push();
        return this;
    }

    /**
     * Scales each element's z-index into a rendering offset in blocks;
     * defaults to {@code 0.001} blocks per z-index unit.
     *
     * @zh 每个 z-index 折算的渲染偏移（格），默认每单位 {@code 0.001} 格。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface setZIndexScale(double zIndexScale) {
        this.zIndexScale = zIndexScale;
        push();
        return this;
    }

    /**
     * The blocks-per-z-index-unit scale.
     *
     * @zh 每 z-index 单位的格数比例。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public double getZIndexScale() {
        return zIndexScale;
    }

    /**
     * Surfaces do not use a z-index for ordering; always {@code 0}.
     *
     * @zh 面板不使用 z-index 排序，恒为 {@code 0}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getZIndex() {
        return 0;
    }

    /**
     * The world position as a point copy (the panel corner, or its centre
     * while {@code rotateCenter} is on).
     *
     * @zh 世界位置的拷贝（面板角点，{@code rotateCenter} 开启时为中心）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Pos3D getPos() {
        return new Pos3D(x, y, z);
    }

    /**
     * The rotations in degrees as a point copy ({@code x, y, z}).
     *
     * @zh 旋转（度）的拷贝（{@code x, y, z}）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Pos3D getRotations() {
        return new Pos3D(xRot, yRot, zRot);
    }

    /**
     * Returns this surface without attaching it to an overlay.
     *
     * @zh 返回该面板本身，但不挂载到图层。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface build() {
        return this;
    }

    /**
     * Attaches this surface to the overlay its builder came from and
     * returns it.
     *
     * @return this surface, now attached
     * @throws IllegalStateException when already attached or built detached
     * @zh 挂载到构建时所属图层并返回自身。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Surface buildAndAdd() {
        if (shapeId() != null) {
            throw new IllegalStateException("surface is already attached to an overlay");
        }
        Draw3D target = origin();
        if (target == null) {
            throw new IllegalStateException("surface was not created from an overlay builder");
        }
        target.addElement(this);
        return this;
    }

    /**
     * Orders surfaces by position, then rotations, then size; surfaces
     * matching on all three compare equal (values, not spatial distance).
     *
     * @zh 按位置、旋转、尺寸依次比较；三者全同则相等。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @Override
    public int compareToSame(Surface other) {
        int result = Double.compare(x, other.x);
        if (result == 0) {
            result = Double.compare(y, other.y);
        }
        if (result == 0) {
            result = Double.compare(z, other.z);
        }
        if (result == 0) {
            result = Double.compare(xRot, other.xRot);
        }
        if (result == 0) {
            result = Double.compare(yRot, other.yRot);
        }
        if (result == 0) {
            result = Double.compare(zRot, other.zRot);
        }
        if (result == 0) {
            result = Double.compare(width, other.width);
        }
        if (result == 0) {
            result = Double.compare(height, other.height);
        }
        return result;
    }

    /** Snapshot pushed to the port: placement, geometry and render flags. */
    @ApiInternal
    @Override
    public ShapeSpec toSpec() {
        return new ShapeSpec.Panel(
                new Pos3d(x, y, z), width, height,
                xRot, yRot, zRot, rotateCenter, rotateToPlayer,
                boundUuid, new Pos3d(offsetX, offsetY, offsetZ),
                0xFFFFFFFF, renderBack, cull);
    }

    /** Reads the handle's UUID string, tolerating factory-built handles. */
    private static UUID parseUuid(EntityApi.Entity entity) {
        try {
            return UUID.fromString(entity.getUuid());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
