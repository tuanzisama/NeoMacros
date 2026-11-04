package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.SelfConstructing;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable three-dimensional vector: every operation returns a new
 * instance. The script-facing workhorse for positions, directions, and
 * offsets; {@code JSON.stringify} renders it as {@code {"x":…,"y":…,"z":…}}.
 */
@ApiClass(name = "Vec3", kind = ApiClassKind.DATA)
public class Vec3 implements ScriptHandle, SelfConstructing {

    private final double x;
    private final double y;
    private final double z;

    /** Creates the vector from world coordinates. */
    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Creates the zero vector (engine factory instantiation). */
    public Vec3() {
        this(0, 0, 0);
    }

    /** The X coordinate. */
    @ApiSince("0.1.0")
    public double getX() {
        return x;
    }

    /** The Y coordinate. */
    @ApiSince("0.1.0")
    public double getY() {
        return y;
    }

    /** The Z coordinate. */
    @ApiSince("0.1.0")
    public double getZ() {
        return z;
    }

    /** Adds another vector. */
    @ApiSince("0.1.0")
    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    /** Subtracts another vector. */
    @ApiSince("0.1.0")
    public Vec3 sub(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    /** Scales by a factor. */
    @ApiSince("0.1.0")
    public Vec3 mul(double factor) {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    /** The vector length. */
    @ApiSince("0.1.0")
    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** The squared vector length. */
    @ApiSince("0.1.0")
    public double lengthSq() {
        return x * x + y * y + z * z;
    }

    /** The unit vector of the same direction (zero vector stays zero). */
    @ApiSince("0.1.0")
    public Vec3 normalize() {
        double len = length();
        return len == 0 ? this : new Vec3(x / len, y / len, z / len);
    }

    /** The distance to another vector. */
    @ApiSince("0.1.0")
    public double distanceTo(Vec3 other) {
        return sub(other).length();
    }

    /** The dot product. */
    @ApiSince("0.1.0")
    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /** The cross product. */
    @ApiSince("0.1.0")
    public Vec3 cross(Vec3 other) {
        return new Vec3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    /** Linear interpolation toward another vector by {@code t} (0..1). */
    @ApiSince("0.1.0")
    public Vec3 lerp(Vec3 other, double t) {
        return new Vec3(
                x + (other.x - x) * t,
                y + (other.y - y) * t,
                z + (other.z - z) * t);
    }

    /** Returns a copy with the X coordinate replaced. */
    @ApiSince("0.1.0")
    public Vec3 withX(double value) {
        return new Vec3(value, y, z);
    }

    /** Returns a copy with the Y coordinate replaced. */
    @ApiSince("0.1.0")
    public Vec3 withY(double value) {
        return new Vec3(x, value, z);
    }

    /** Returns a copy with the Z coordinate replaced. */
    @ApiSince("0.1.0")
    public Vec3 withZ(double value) {
        return new Vec3(x, y, value);
    }

    /** Floors the coordinates onto the block grid. */
    @ApiSince("0.1.0")
    public BlockPos floor() {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /** JSON form so {@code JSON.stringify} renders the vector directly. */
    @ApiSince("0.1.0")
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
        return other instanceof Vec3 vec
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
        if (args != null && args.length == 1 && args[0] instanceof Vec3 vec) {
            return vec;
        }
        return new Vec3(coord(args, 0), coord(args, 1), coord(args, 2));
    }

    private static double coord(Object[] args, int index) {
        if (args != null && index < args.length && args[index] instanceof Number number) {
            return number.doubleValue();
        }
        return 0;
    }
}
