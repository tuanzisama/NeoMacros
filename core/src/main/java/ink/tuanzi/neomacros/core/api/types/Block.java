package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiPermission;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.Optional;

/**
 * Live block handle: state reads re-query the world on every access; a
 * block outside the loaded world reads back as undefined instead of raising.
 */
@ApiClass(name = "Block", kind = ApiClassKind.HANDLE)
public final class Block implements ScriptHandle {

    private final BlockPos pos;

    /** Creates the handle (facade entry; package-private by design otherwise). */
    public Block(BlockPos pos) {
        this.pos = pos;
    }

    /** The block position. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public BlockPos getPos() {
        return pos;
    }

    /** The current block state; undefined outside the loaded world. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public BlockState getState() {
        return snapshot().map(data -> new BlockState(data.id())).orElse(null);
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

    /** Whether the block is still loaded in the world. */
    @ApiSince("0.1.0")
    public boolean isValid() {
        return snapshot().isPresent();
    }

    /** Human-readable form. */
    @Override
    public String toString() {
        return snapshot()
                .map(data -> "Block(" + data.id() + " @ " + pos + ")")
                .orElse("Block(unloaded @ " + pos + ")");
    }

    /** The latest block data, or empty when unloaded. */
    private Optional<ink.tuanzi.neomacros.port.model.BlockData> snapshot() {
        return ScriptApiEnvironment.adapter().world().blockAt(
                new ink.tuanzi.neomacros.port.model.Pos3d(pos.getX(), pos.getY(), pos.getZ()));
    }
}
