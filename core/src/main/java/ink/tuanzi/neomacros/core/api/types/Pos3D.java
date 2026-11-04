package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.SelfConstructing;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable three-dimensional point and direction: every operation returns a
 * new instance. The script-facing workhorse for positions, directions, and
 * offsets; {@code JSON.stringify} renders it as {@code {"x":…,"y":…,"z":…}}.
 * @zh 不可变三维点/向量：所有运算都返回新实例。位置、方向与偏移的脚本端主力类型；{@code JSON.stringify} 直接输出 {@code {"x":…,"y":…,"z":…}}。
 */
@ApiClass(name = "Pos3D", kind = ApiClassKind.DATA)
@ApiSince("0.2.0")
@ApiLoader("*")
@ApiMC("*")
public class Pos3D implements ScriptHandle, SelfConstructing {

    private final double x;
    private final double y;
    private final double z;

    /** Creates the point from world coordinates.
     * @zh 由世界坐标构造。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Creates the zero point (engine factory instantiation).
     * @zh 构造零点（引擎工厂实例化用）。 */
    public Pos3D() {
        this(0, 0, 0);
    }

    /** The X coordinate.
     * @zh X 坐标。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public double getX() {
        return x;
    }

    /** The Y coordinate.
     * @zh Y 坐标。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public double getY() {
        return y;
    }

    /** The Z coordinate.
     * @zh Z 坐标。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public double getZ() {
        return z;
    }

    /** Adds another point or vector.
     * @zh 加上另一个点/向量。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D add(Pos3D other) {
        return new Pos3D(x + other.x, y + other.y, z + other.z);
    }

    /** Subtracts another point or vector.
     * @zh 减去另一个点/向量。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D sub(Pos3D other) {
        return new Pos3D(x - other.x, y - other.y, z - other.z);
    }

    /** Multiplies component-wise by another vector.
     * @zh 与另一向量按分量相乘。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D mul(Pos3D other) {
        return new Pos3D(x * other.x, y * other.y, z * other.z);
    }

    /** Scales by a factor.
     * @zh 按系数缩放。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D scale(double factor) {
        return new Pos3D(x * factor, y * factor, z * factor);
    }

    /** Divides component-wise by another vector.
     * @zh 与另一向量按分量相除。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D div(Pos3D other) {
        return new Pos3D(x / other.x, y / other.y, z / other.z);
    }

    /** The vector length.
     * @zh 向量长度。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** The squared vector length.
     * @zh 向量长度的平方。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public double lengthSq() {
        return x * x + y * y + z * z;
    }

    /** The unit vector of the same direction (zero vector stays zero).
     * @zh 同方向的单位向量（零向量保持为零）。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D normalize() {
        double len = length();
        return len == 0 ? this : new Pos3D(x / len, y / len, z / len);
    }

    /** The distance to another point.
     * @zh 到另一点的距离。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public double distanceTo(Pos3D other) {
        return sub(other).length();
    }

    /** The squared distance to another point.
     * @zh 到另一点的距离平方。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public double distanceSq(Pos3D other) {
        return sub(other).lengthSq();
    }

    /** The dot product.
     * @zh 点积。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public double dot(Pos3D other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /** The cross product.
     * @zh 叉积。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D cross(Pos3D other) {
        return new Pos3D(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    /** Linear interpolation toward another point by {@code t} (0..1).
     * @zh 向另一点按 {@code t}（0..1）线性插值。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D lerp(Pos3D other, double t) {
        return new Pos3D(
                x + (other.x - x) * t,
                y + (other.y - y) * t,
                z + (other.z - z) * t);
    }

    /** Returns a copy with the X coordinate replaced.
     * @zh 返回替换 X 坐标后的副本。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D withX(double value) {
        return new Pos3D(value, y, z);
    }

    /** Returns a copy with the Y coordinate replaced.
     * @zh 返回替换 Y 坐标后的副本。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D withY(double value) {
        return new Pos3D(x, value, z);
    }

    /** Returns a copy with the Z coordinate replaced.
     * @zh 返回替换 Z 坐标后的副本。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos3D withZ(double value) {
        return new Pos3D(x, y, value);
    }

    /** Floors the coordinates onto the block grid.
     * @zh 将坐标向下取整到方块网格。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public BlockPos floor() {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /** JSON form so {@code JSON.stringify} renders the point directly.
     * @zh JSON 形式，使 {@code JSON.stringify} 直接输出坐标。 */
    @ApiSince("0.1.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Map<String, Object> toJSON() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x", x);
        out.put("y", y);
        out.put("z", z);
        return out;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Pos3D vec
                && Double.doubleToLongBits(x) == Double.doubleToLongBits(vec.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(vec.y)
                && Double.doubleToLongBits(z) == Double.doubleToLongBits(vec.z);
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(x) ^ (Double.doubleToLongBits(y) * 31)
                ^ (Double.doubleToLongBits(z) * 961);
        return (int) (bits ^ (bits >> 32));
    }

    @Override
    public Object construct(Object[] args) {
        if (args != null && args.length == 1 && args[0] instanceof Pos3D vec) {
            return vec;
        }
        return new Pos3D(coord(args, 0), coord(args, 1), coord(args, 2));
    }

    private static double coord(Object[] args, int index) {
        if (args != null && index < args.length && args[index] instanceof Number number) {
            return number.doubleValue();
        }
        return 0;
    }
}
