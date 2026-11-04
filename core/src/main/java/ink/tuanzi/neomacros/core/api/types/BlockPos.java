package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Immutable block-grid position: an integer {@link Pos3D} with directional
 * offsets. {@code new BlockPos(10.7, 64.2, 200.9)} floors onto the grid.
 * @zh 不可变的方块网格坐标：整数化的 {@link Pos3D}，带六个方向的偏移方法。{@code new BlockPos(10.7, 64.2, 200.9)} 会向下取整到网格。
 */
@ApiClass(name = "BlockPos", kind = ApiClassKind.DATA)
@ApiSince("0.2.0")
@ApiLoader("*")
@ApiMC("*")
public final class BlockPos extends Pos3D {

    /** Creates the origin position (engine factory instantiation).
     * @zh 构造原点坐标（引擎工厂实例化用）。 */
    public BlockPos() {
        super(0, 0, 0);
    }

    /** Creates the position from grid coordinates (floored).
     * @zh 由网格坐标构造。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    /** Creates the position from world coordinates (floored).
     * @zh 由世界坐标构造（向下取整）。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos(double x, double y, double z) {
        this((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /** The integer X grid coordinate (internal host-side helper; the guest
     * reads the integer-valued {@code x} property directly).
     * @zh 整数 X 网格坐标（宿主侧内部辅助；guest 端直接读整数值的 {@code x} 属性）。 */
    @ApiInternal
    public int getIntX() {
        return (int) Math.floor(getX());
    }

    /** The integer Y grid coordinate (internal host-side helper).
     * @zh 整数 Y 网格坐标（宿主侧内部辅助）。 */
    @ApiInternal
    public int getIntY() {
        return (int) Math.floor(getY());
    }

    /** The integer Z grid coordinate (internal host-side helper).
     * @zh 整数 Z 网格坐标（宿主侧内部辅助）。 */
    @ApiInternal
    public int getIntZ() {
        return (int) Math.floor(getZ());
    }

    /** One block up.
     * @zh 上移一格。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos up() {
        return up(1);
    }

    /** {@code n} blocks up.
     * @zh 上移 n 格。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos up(int n) {
        return new BlockPos(getIntX(), getIntY() + n, getIntZ());
    }

    /** One block down.
     * @zh 下移一格。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos down() {
        return down(1);
    }

    /** {@code n} blocks down.
     * @zh 下移 n 格。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos down(int n) {
        return new BlockPos(getIntX(), getIntY() - n, getIntZ());
    }

    /** One block north (negative Z).
     * @zh 北移一格（Z 减一）。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos north() {
        return north(1);
    }

    /** {@code n} blocks north (negative Z).
     * @zh 北移 n 格（Z 减 n）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos north(int n) {
        return new BlockPos(getIntX(), getIntY(), getIntZ() - n);
    }

    /** One block south (positive Z).
     * @zh 南移一格（Z 加一）。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos south() {
        return south(1);
    }

    /** {@code n} blocks south (positive Z).
     * @zh 南移 n 格（Z 加 n）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos south(int n) {
        return new BlockPos(getIntX(), getIntY(), getIntZ() + n);
    }

    /** One block west (negative X).
     * @zh 西移一格（X 减一）。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos west() {
        return west(1);
    }

    /** {@code n} blocks west (negative X).
     * @zh 西移 n 格（X 减 n）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos west(int n) {
        return new BlockPos(getIntX() - n, getIntY(), getIntZ());
    }

    /** One block east (positive X).
     * @zh 东移一格（X 加一）。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos east() {
        return east(1);
    }

    /** {@code n} blocks east (positive X).
     * @zh 东移 n 格（X 加 n）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos east(int n) {
        return new BlockPos(getIntX() + n, getIntY(), getIntZ());
    }

    /** Offsets by {@code n} steps along one of the six cardinal directions.
     * @zh 沿六个基本方向之一偏移 n 格。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos offset(String direction, int n) {
        return switch (direction.toLowerCase(java.util.Locale.ROOT)) {
            case "up" -> up(n);
            case "down" -> down(n);
            case "north" -> north(n);
            case "south" -> south(n);
            case "west" -> west(n);
            case "east" -> east(n);
            default -> throw new IllegalArgumentException("unknown direction: " + direction);
        };
    }

    /** The center of the block as a world-coordinate point.
     * @zh 方块中心的世界坐标点。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D center() {
        return new Pos3D(getIntX() + 0.5, getIntY() + 0.5, getIntZ() + 0.5);
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
        if (args != null && args.length == 1 && args[0] instanceof Pos3D vec) {
            return vec.floor();
        }
        double x = args != null && args.length > 0 && args[0] instanceof Number n ? n.doubleValue() : 0;
        double y = args != null && args.length > 1 && args[1] instanceof Number n ? n.doubleValue() : 0;
        double z = args != null && args.length > 2 && args[2] instanceof Number n ? n.doubleValue() : 0;
        return new BlockPos(x, y, z);
    }
}
