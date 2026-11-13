package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.Optional;

/**
 * Live block handle: state reads re-query the world on every access; a
 * block outside the loaded world reads back as undefined instead of raising.
 * @zh 活方块句柄：状态读取每次都重新查询世界；未加载世界的方块读取返回 undefined 而不是抛错。
 */
@ApiClass(name = "Block", kind = ApiClassKind.HANDLE)
public final class Block implements ScriptHandle {

    private final BlockPos pos;

    /** Creates the handle (engine factory; package-private by design otherwise).
     * @zh 构造句柄。 */
    public Block(BlockPos pos) {
        this.pos = pos;
    }

    /** The block position.
     * @zh 方块坐标。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public BlockPos getPos() {
        return pos;
    }

    /** The current block state; undefined outside the loaded world.
     * @zh 当前方块状态；未加载世界内为 undefined。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public BlockState getState() {
        return snapshot().map(data -> new BlockState(data.id(), data.properties(), data.extras())).orElse(null);
    }

    /**
     * Escape hatch: the raw host block state object. Requires the
     * {@code java} permission.
     *
     * @zh 穿透口：原始宿主方块状态对象（需 java 权限）。
     */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiPermission("java")
    @ApiType("JavaObject")
    public Object getRaw() {
        return ScriptApiEnvironment.adapter().world().rawByPos(
                new ink.tuanzi.neomacros.port.model.Pos3d(
                        pos.getX(), pos.getY(), pos.getZ())).orElse(null);
    }

    /** The biome identifier at the block position.
     * @zh 方块位置的群系标识。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public String getBiome() {
        return ScriptApiEnvironment.adapter().world().biomeAt(
                new ink.tuanzi.neomacros.port.model.Pos3d(
                        pos.getX(), pos.getY(), pos.getZ())).orElse(null);
    }

    /** The chunk coordinate containing the block.
     * @zh 方块所在区块坐标。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public ChunkPos getChunk() {
        return new ChunkPos(pos);
    }

    /**
     * The block {@code n} steps in one cardinal direction, or undefined
     * outside the loaded world.
     *
     * @zh 沿基本方向偏移 n 格的方块；未加载世界内为 undefined。
     * @zh-param direction 方向名（up/down/north/south/east/west）
     * @zh-param n 步数（可省略，默认 1）
     */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Block relative(String direction, Integer n) {
        BlockPos next = pos.offset(direction, n == null ? 1 : n);
        return snapshot(next).isPresent() ? new Block(next) : null;
    }

    /** Whether the block is still loaded in the world.
     * @zh 方块是否仍在世界中加载。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isValid() {
        return snapshot().isPresent();
    }

    /** The registered default state of the block type, or undefined when unloaded.
     * @zh 方块类型的注册默认状态；未加载时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public BlockState getDefaultState() {
        return type().map(data -> new BlockState(data.id(), data.defaultState()))
                .orElse(null);
    }

    /** The item form of the block as a stack of one, or undefined when the block has no item.
     * @zh 方块的物品形态（数量 1）；方块无物品形态时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public ink.tuanzi.neomacros.core.api.types.ItemStack getDefaultItemStack() {
        return type().flatMap(data -> java.util.Optional.ofNullable(data.defaultItemStack()))
                .map(ink.tuanzi.neomacros.core.api.types.ItemStack::of)
                .orElse(null);
    }

    /** Whether mobs can spawn inside the block's default state (non-solid, non-liquid).
     * @zh 生物是否可在该方块默认状态内生成（非固体且非液体）。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean canMobSpawnInside() {
        return type().map(ink.tuanzi.neomacros.port.model.BlockTypeData::canMobSpawnInside)
                .orElse(null);
    }

    /** Whether the block's shape is marked dynamic.
     * @zh 该方块的碰撞形状是否标记为动态。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean hasDynamicBounds() {
        return type().map(ink.tuanzi.neomacros.port.model.BlockTypeData::hasDynamicBounds)
                .orElse(null);
    }

    /** The block's explosion resistance factor.
     * @zh 方块的爆炸抗性系数。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Float getBlastResistance() {
        return type().map(ink.tuanzi.neomacros.port.model.BlockTypeData::blastResistance)
                .orElse(null);
    }

    /** The jump boost multiplier applied when standing on the block.
     * @zh 站在该方块上时的跳跃增益倍率。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Float getJumpVelocityMultiplier() {
        return type().map(ink.tuanzi.neomacros.port.model.BlockTypeData::jumpVelocityMultiplier)
                .orElse(null);
    }

    /** The friction (slipperiness) value of the block.
     * @zh 方块的摩擦（滑动）系数。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Float getSlipperiness() {
        return type().map(ink.tuanzi.neomacros.port.model.BlockTypeData::slipperiness)
                .orElse(null);
    }

    /** The entity movement speed factor of the block.
     * @zh 方块上的实体移动速度倍率。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Float getVelocityMultiplier() {
        return type().map(ink.tuanzi.neomacros.port.model.BlockTypeData::velocityMultiplier)
                .orElse(null);
    }

    /** The full block tag identifiers assigned to the block, empty when unloaded or untagged.
     * @zh 该方块所属的全部方块标签标识；未加载或无标签时为空数组。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public java.util.List<String> getTags() {
        return type().map(data -> java.util.List.copyOf(data.tags())).orElse(null);
    }

    /**
     * Whether the block belongs to the block tag. Unknown tags and tags of
     * other registries answer false; an unloaded world answers undefined.
     *
     * @zh 该方块是否属于指定方块标签；未知标签或其他注册表的标签为 false，未加载世界为 undefined。
     * @zh-param tag 标签名，可带 {@code #} 前缀、可省略命名空间
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean hasTag(String tag) {
        ink.tuanzi.neomacros.port.model.BlockTypeData data = type().orElse(null);
        if (data == null) {
            return null;
        }
        return data.tags().contains(normalizeTag(tag));
    }

    /** Every possible state defined for the block, or undefined when unloaded.
     * @zh 该方块定义的全部可能状态；未加载时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public java.util.List<BlockState> getStates() {
        return type().map(data -> data.states().stream()
                .map(props -> new BlockState(data.id(), props))
                .toList()).orElse(null);
    }

    /** The block's translated display name.
     * @zh 方块的翻译显示名称。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getName() {
        return type().map(ink.tuanzi.neomacros.port.model.BlockTypeData::name).orElse(null);
    }

    /**
     * Escape hatch: the raw host block entity object at this position, or
     * undefined when unloaded or the block carries none. Requires the
     * {@code java} permission.
     *
     * @zh 穿透口：此处的原始宿主方块实体对象（需 java 权限）；未加载或无方块实体时为 undefined。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiPermission("java")
    @ApiType("JavaObject")
    public Object getRawBlockEntity() {
        return ScriptApiEnvironment.adapter().world()
                .rawBlockEntityByPos(new ink.tuanzi.neomacros.port.model.Pos3d(
                        pos.getX(), pos.getY(), pos.getZ())).orElse(null);
    }

    /** Human-readable form.
     * @zh 人类可读形式。 */
    @Override
    public String toString() {
        return snapshot()
                .map(data -> "Block(" + data.id() + " @ " + pos + ")")
                .orElse("Block(unloaded @ " + pos + ")");
    }

    /** The latest block data, or empty when unloaded. */
    private Optional<ink.tuanzi.neomacros.port.model.BlockData> snapshot() {
        return snapshot(pos);
    }

    /** The block data at one position, or empty when unloaded. */
    private Optional<ink.tuanzi.neomacros.port.model.BlockData> snapshot(BlockPos at) {
        return ScriptApiEnvironment.adapter().world().blockAt(
                new ink.tuanzi.neomacros.port.model.Pos3d(at.getX(), at.getY(), at.getZ()));
    }

    /** The registry-level facts of the block currently at the position, or empty. */
    private Optional<ink.tuanzi.neomacros.port.model.BlockTypeData> type() {
        return snapshot().flatMap(data ->
                ScriptApiEnvironment.adapter().world().blockType(data.id()));
    }

    /** Normalizes one tag reference: an optional {@code #} prefix and a default namespace. */
    private static String normalizeTag(String tag) {
        String id = tag.startsWith("#") ? tag.substring(1) : tag;
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }
}
