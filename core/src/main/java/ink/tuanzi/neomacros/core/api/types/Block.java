package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.Optional;

/**
 * Live block handle: state reads re-query the world on every access; a
 * block outside the loaded world reads back as undefined instead of raising.
 * @zh 活方块句柄：状态读取每次都重新查询世界；未加载世界的方块读取返回 undefined 而不是抛错。
 */
@ApiClass(name = "Block", kind = ApiClassKind.HANDLE)
public final class Block implements ScriptHandle {

    private final BlockPos pos;

    /** Creates the handle (facade entry; package-private by design otherwise).
     * @zh 构造句柄（门面入口）。 */
    public Block(BlockPos pos) {
        this.pos = pos;
    }

    /** The block position.
     * @zh 方块坐标。 */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public BlockPos getPos() {
        return pos;
    }

    /** The current block state; undefined outside the loaded world.
     * @zh 当前方块状态；未加载世界内为 undefined。 */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public BlockState getState() {
        return snapshot().map(data -> new BlockState(data.id(), data.properties())).orElse(null);
    }

    /**
     * Escape hatch: the raw host block state object. Requires the
     * {@code java} permission.
     *
     * @zh 穿透口：原始宿主方块状态对象（需 java 权限）。
     */
    @ApiSince("0.1.0")
    @ApiPermission("java")
    public Object getRaw() {
        return ScriptApiEnvironment.adapter().world().rawByPos(
                new ink.tuanzi.neomacros.port.model.Pos3d(
                        pos.getX(), pos.getY(), pos.getZ())).orElse(null);
    }

    /** The biome identifier at the block position.
     * @zh 方块位置的群系标识。 */
    @ApiSince("0.2.0")
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
    @ApiMC("*")
    public Block relative(String direction, Integer n) {
        BlockPos next = pos.offset(direction, n == null ? 1 : n);
        return snapshot(next).isPresent() ? new Block(next) : null;
    }

    /** Whether the block is still loaded in the world.
     * @zh 方块是否仍在世界中加载。 */
    @ApiSince("0.1.0")
    public boolean isValid() {
        return snapshot().isPresent();
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
}
