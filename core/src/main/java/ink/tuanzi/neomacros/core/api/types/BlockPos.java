package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Immutable block-grid position: an integer {@link Vec3} with directional
 * offsets. {@code new BlockPos(10.7, 64.2, 200.9)} floors onto the grid.
 */
@ApiClass(name = "BlockPos", kind = ApiClassKind.DATA)
public final class BlockPos extends Vec3 {

    /** Creates the origin position (engine factory instantiation). */
    public BlockPos() {
        super(0, 0, 0);
    }

    /** Creates the position from grid coordinates (floored). */
    public BlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    /** Creates the position from world coordinates (floored). */
    public BlockPos(double x, double y, double z) {
        this((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /** The X grid coordinate. */
    @ApiSince("0.1.0")
    public int getIntX() {
        return (int) Math.floor(getX());
    }

    /** The Y grid coordinate. */
    @ApiSince("0.1.0")
    public int getIntY() {
        return (int) Math.floor(getY());
    }

    /** The Z grid coordinate. */
    @ApiSince("0.1.0")
    public int getIntZ() {
        return (int) Math.floor(getZ());
    }

    /** One block up. */
    @ApiSince("0.1.0")
    public BlockPos up() {
        return up(1);
    }

    /** {@code n} blocks up. */
    @ApiSince("0.1.0")
    public BlockPos up(int n) {
        return new BlockPos(getIntX(), getIntY() + n, getIntZ());
    }

    /** One block down. */
    @ApiSince("0.1.0")
    public BlockPos down() {
        return down(1);
    }

    /** {@code n} blocks down. */
    @ApiSince("0.1.0")
    public BlockPos down(int n) {
        return new BlockPos(getIntX(), getIntY() - n, getIntZ());
    }

    /** One block north (negative Z). */
    @ApiSince("0.1.0")
    public BlockPos north() {
        return new BlockPos(getIntX(), getIntY(), getIntZ() - 1);
    }

    /** One block south (positive Z). */
    @ApiSince("0.1.0")
    public BlockPos south() {
        return new BlockPos(getIntX(), getIntY(), getIntZ() + 1);
    }

    /** One block west (negative X). */
    @ApiSince("0.1.0")
    public BlockPos west() {
        return new BlockPos(getIntX() - 1, getIntY(), getIntZ());
    }

    /** One block east (positive X). */
    @ApiSince("0.1.0")
    public BlockPos east() {
        return new BlockPos(getIntX() + 1, getIntY(), getIntZ());
    }

    /** Offsets by {@code n} steps along one of the six cardinal directions. */
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

    /** The center of the block as a world-coordinate vector. */
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
