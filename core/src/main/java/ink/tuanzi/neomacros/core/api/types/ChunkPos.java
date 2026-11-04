package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.SelfConstructing;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable chunk-grid coordinate. Deliberately unrelated to {@link Pos3D}:
 * chunk coordinates and block coordinates are different spaces and every
 * conversion between them is explicit ({@code x = blockX >> 4}).
 * @zh 不可变区块坐标。刻意不与 {@link Pos3D} 发生继承关系：区块坐标与方块坐标分属两套坐标系，相互换算必须显式（{@code x = blockX >> 4}）。
 */
@ApiClass(name = "ChunkPos", kind = ApiClassKind.DATA)
@ApiMC("*")
public final class ChunkPos implements ScriptHandle, SelfConstructing {

    private final int x;
    private final int z;

    /** Creates the chunk coordinate from chunk-space X/Z.
     * @zh 由区块坐标系 X/Z 构造。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    /** Creates the chunk coordinate containing one block position.
     * @zh 由方块坐标构造其所在区块坐标。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public ChunkPos(BlockPos pos) {
        this(Math.floorDiv(pos.getIntX(), 16), Math.floorDiv(pos.getIntZ(), 16));
    }

    /** Creates the zero chunk (engine factory instantiation).
     * @zh 构造原点区块（引擎工厂实例化用）。 */
    public ChunkPos() {
        this(0, 0);
    }

    /** The chunk X ({@code blockX >> 4}).
     * @zh 区块 X（{@code blockX >> 4}）。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public int getX() {
        return x;
    }

    /** The chunk Z.
     * @zh 区块 Z。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public int getZ() {
        return z;
    }

    /** The west (-X) block-coordinate boundary, equal to {@code x << 4}.
     * @zh 区块西（-X）边界方块坐标，等于 {@code x << 4}。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public int getMinX() {
        return x << 4;
    }

    /** The north (-Z) block-coordinate boundary, equal to {@code z << 4}.
     * @zh 区块北（-Z）边界方块坐标，等于 {@code z << 4}。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public int getMinZ() {
        return z << 4;
    }

    /** The northwest corner block position on the world floor.
     * @zh 区块西北角的方块坐标（y=0，需要其他高度时用 withY 替换）。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public BlockPos minBlock() {
        return new BlockPos(getMinX(), 0, getMinZ());
    }

    /** Whether the block position falls inside this chunk.
     * @zh 判断方块坐标是否落在本区块内。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public boolean contains(Pos3D pos) {
        return new ChunkPos(pos.floor()).equals(this);
    }

    /** Translates by chunk-space offsets.
     * @zh 按区块坐标系平移。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public ChunkPos offset(int dx, int dz) {
        return new ChunkPos(x + dx, z + dz);
    }

    /** JSON form so {@code JSON.stringify} renders the chunk directly.
     * @zh JSON 形式，使 {@code JSON.stringify} 直接输出区块坐标。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public Map<String, Object> toJSON() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x", x);
        out.put("z", z);
        return out;
    }

    @Override
    public String toString() {
        return "ChunkPos(" + x + ", " + z + ")";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChunkPos chunk && x == chunk.x && z == chunk.z;
    }

    @Override
    public int hashCode() {
        return x * 31 + z;
    }

    @Override
    public Object construct(Object[] args) {
        if (args != null && args.length == 1 && args[0] instanceof BlockPos pos) {
            return new ChunkPos(pos);
        }
        if (args != null && args.length == 1 && args[0] instanceof Pos3D vec) {
            return new ChunkPos(vec.floor());
        }
        return new ChunkPos(intArg(args, 0), intArg(args, 1));
    }

    private static int intArg(Object[] args, int index) {
        if (args != null && index < args.length && args[index] instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
