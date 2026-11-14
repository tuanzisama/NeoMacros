package ink.tuanzi.neomacros.core.api.render.components3d;

import ink.tuanzi.neomacros.core.api.entity.EntityApi;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ShapeSpec;

import java.util.UUID;

/**
 * A {@link TraceLine} whose target follows an entity, with {@code yOffset}
 * added to its interpolated Y coordinate every frame. The adapter
 * re-resolves the entity by UUID each frame while it loads; if the entity
 * is absent the line simply stops drawing (it is not torn down). Until the
 * entity is bound the target stays at the origin.
 *
 * <p>Obtain one from {@link ink.tuanzi.neomacros.core.api.render.Draw3D#entityTraceLineBuilder()},
 * bind it with {@link #setEntity(EntityApi.Entity)}, then
 * {@link #buildAndAdd()} — or chain {@code .entity(e)} onto the builder
 * result and call the inherited {@code buildAndAdd()} directly. Defaults:
 * white, fully opaque, always on top, {@code yOffset} of {@code 0.5}.</p>
 *
 * @zh 目标跟随实体的 {@link TraceLine}：每帧在实体插值 Y 上叠加 {@code yOffset}，适配器按 UUID 逐帧重解析；实体缺席时线条仅停止绘制。默认白色不透明、画在世界之上、{@code yOffset} 为 {@code 0.5}。
 */
@ApiClass(name = "EntityTraceLine", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public class EntityTraceLine extends TraceLine {

    /** The followed entity handle, or null while unbound. */
    private EntityApi.Entity entity;

    /** The followed entity's UUID captured at bind time, or null. */
    private UUID entityUuid;

    /** Vertical offset above the entity's position, in blocks. */
    private double yOffset = 0.5;

    /** Creates an unbound entity-following trace at the origin. */
    public EntityTraceLine() {
    }

    /**
     * Binds this line to {@code entity}; its UUID is captured for the
     * per-frame re-resolution. Passing {@code null} unbinds it.
     *
     * @param entity the entity to follow, or null to unbind
     * @zh 绑定跟随的实体（捕获其 UUID 供逐帧重解析）；传 {@code null} 解绑。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public EntityTraceLine setEntity(EntityApi.Entity entity) {
        this.entity = entity;
        this.entityUuid = entity == null ? null : parseUuid(entity);
        push();
        return this;
    }

    /**
     * Sets the vertical offset added above the entity's position, in
     * blocks; defaults to {@code 0.5}.
     *
     * @zh 设置叠加在实体位置之上的垂直偏移（格），默认 {@code 0.5}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public EntityTraceLine setYOffset(double yOffset) {
        this.yOffset = yOffset;
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
    public EntityApi.Entity getEntity() {
        return entity;
    }

    /**
     * The vertical offset above the entity's position, in blocks.
     *
     * @zh 叠加在实体位置之上的垂直偏移（格）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public double getYOffset() {
        return yOffset;
    }

    /**
     * Returns this line without attaching it to an overlay (covariant
     * override of {@link TraceLine#build()}).
     *
     * @zh 返回该线本身，但不挂载到图层。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @Override
    public EntityTraceLine build() {
        super.build();
        return this;
    }

    /**
     * Attaches this line to the overlay its builder came from and returns
     * it (covariant override of {@link TraceLine#buildAndAdd()}).
     *
     * @return this line, now attached
     * @throws IllegalStateException when already attached or built detached
     * @zh 挂载到构建时所属图层并返回自身。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @Override
    public EntityTraceLine buildAndAdd() {
        super.buildAndAdd();
        return this;
    }

    /** Snapshot pushed to the port: entity UUID, offset, colour and depth. */
    @ApiInternal
    @Override
    public ShapeSpec toSpec() {
        return new ShapeSpec.EntityTrace(entityUuid, yOffset, packedColor(), depthTested());
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
