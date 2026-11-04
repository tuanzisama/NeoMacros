package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Immutable block-grid position: an integer {@link Vec3} with directional
 * offsets. {@code new BlockPos(10.7, 64.2, 200.9)} floors onto the grid.
 * @zh 不可变的方块网格坐标：整数化的 {@link Vec3}，带六个方向的偏移方法。{@code new BlockPos(10.7, 64.2, 200.9)} 会向下取整到网格。
 */
@ApiClass(name = "BlockPos", kind = ApiClassKind.DATA)
public final class BlockPos extends Vec3 {

    /** Creates the origin position (engine factory instantiation).
     * @zh 构造原点坐标（引擎工厂实例化用）。 */
    public BlockPos() {
        super(0, 0, 0);
    }

    /** Creates the position from grid coordinates (floored).
     * @zh 由网格坐标构造。 */
    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    /** Creates the position from world coordinates (floored).
     * @zh 由世界坐标构造（向下取整）。 */
    public BlockPos(double x, double y, double z) {
        this((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /** The X grid coordinate.
     * @zh X 网格坐标。 */
    @ApiSince("0.1.0")
    public int getIntX() {
        return (int) Math.floor(getX());
    }

    /** The Y grid coordinate.
     * @zh Y 网格坐标。 */
    @ApiSince("0.1.0")
    public int getIntY() {
        return (int) Math.floor(getY());
    }

    /** The Z grid coordinate.
     * @zh Z 网格坐标。 */
    @ApiSince("0.1.0")
    public int getIntZ() {
        return (int) Math.floor(getZ());
    }

    /** One block up.
     * @zh 上移一格。 */
    @ApiSince("0.1.0")
    public BlockPos up() {
        return up(1);
    }

    /** {@code n} blocks up.
     * @zh 上移 n 格。 */
    @ApiSince("0.1.0")
    public BlockPos up(int n) {
        return new BlockPos(getIntX(), getIntY() + n, getIntZ());
    }

    /** One block down.
     * @zh 下移一格。 */
    @ApiSince("0.1.0")
    public BlockPos down() {
        return down(1);
    }

    /** {@code n} blocks down.
     * @zh 下移 n 格。 */
    @ApiSince("0.1.0")
    public BlockPos down(int n) {
        return new BlockPos(getIntX(), getIntY() - n, getIntZ());
    }

    /** One block north (negative Z).
     * @zh 北移一格（Z 减一）。 */
    @ApiSince("0.1.0")
    public BlockPos north() {
        return new BlockPos(getIntX(), getIntY(), getIntZ() - 1);
    }

    /** One block south (positive Z).
     * @zh 南移一格（Z 加一）。 */
    @ApiSince("0.1.0")
    public BlockPos south() {
        return new BlockPos(getIntX(), getIntY(), getIntZ() + 1);
    }

    /** One block west (negative X).
     * @zh 西移一格（X 减一）。 */
    @ApiSince("0.1.0")
    public BlockPos west() {
        return new BlockPos(getIntX() - 1, getIntY(), getIntZ());
    }

    /** One block east (positive X).
     * @zh 东移一格（X 加一）。 */
    @ApiSince("0.1.0")
    public BlockPos east() {
        return new BlockPos(getIntX() + 1, getIntY(), getIntZ());
    }

    /** Offsets by {@code n} steps along one of the six cardinal directions.
     * @zh 沿六个基本方向之一偏移 n 格。 */
    @ApiSince("0.1.0")
    public BlockPos offset(String direction, int n) {
        return switch (direction.toLowerCase(java.util.Locale.ROOT)) {
            case "up" -> up(n);
            case "down" -> down(n);
            case "north" -> north();
            case "south" -> south();
            case "west" -> west();
            case "east" -> east();
            default -> throw new IllegalArgumentException("unknown direction: " + direction);
        };
    }

    /** The center of the block as a world-coordinate vector.
     * @zh 方块中心的世界坐标向量。 */
    @ApiSince("0.1.0")
    public Vec3 center() {
        return new Vec3(getIntX() + 0.5, getIntY() + 0.5, getIntZ() + 0.5);
    }

    @Override
    public String toString() {
        return "BlockPos(" + getIntX() + ", " + getIntY() + ", " + getIntZ() + ")";
    }

    @Override
    public Object construct(Object[] args) {
        if (args != null && args.length == 1 && args[0] instanceof BlockPos pos) {
            return pos;
        }
        if (args != null && args.length == 1 && args[0] instanceof Vec3 vec) {
            return vec.floor();
        }
        double x = args != null && args.length > 0 && args[0] instanceof Number n ? n.doubleValue() : 0;
        double y = args != null && args.length > 1 && args[1] instanceof Number n ? n.doubleValue() : 0;
        double z = args != null && args.length > 2 && args[2] instanceof Number n ? n.doubleValue() : 0;
        return new BlockPos(x, y, z);
    }
}
