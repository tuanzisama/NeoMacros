package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.SelfConstructing;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable two-dimensional point: rotations (yaw, pitch) and screen
 * coordinates. Every operation returns a new instance.
 * @zh 不可变二维点：旋转（yaw, pitch）与屏幕坐标。所有运算都返回新实例。
 * @zh-example const rot = new Pos2D(90, 0); // yaw 90°, pitch 0°
 * const steeper = rot.add(new Pos2D(0, 45)); // pitch 抬到 45°
 */
@ApiClass(name = "Pos2D", kind = ApiClassKind.DATA)
@ApiSince("0.2.0")
@ApiLoader("*")
@ApiMC("*")
public final class Pos2D implements ScriptHandle, SelfConstructing {

    private final double x;
    private final double y;

    /** The zero rotation/origin constant.
     * @zh 零旋转/原点常量。 */
    public static final Pos2D ZERO = new Pos2D(0, 0);

    /** Creates the point from two coordinates.
     * @zh 由两个坐标构造。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** Creates the zero point (engine factory instantiation).
     * @zh 构造零点（引擎工厂实例化用）。 */
    public Pos2D() {
        this(0, 0);
    }

    /** The X coordinate.
     * @zh X 坐标。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public double getX() {
        return x;
    }

    /** The Y coordinate.
     * @zh Y 坐标。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public double getY() {
        return y;
    }

    /** Adds another point.
     * @zh 加上另一点。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos2D add(Pos2D other) {
        return new Pos2D(x + other.x, y + other.y);
    }

    /** Subtracts another point.
     * @zh 减去另一点。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos2D sub(Pos2D other) {
        return new Pos2D(x - other.x, y - other.y);
    }

    /** Multiplies component-wise by another point.
     * @zh 与另一点按分量相乘。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos2D mul(Pos2D other) {
        return new Pos2D(x * other.x, y * other.y);
    }

    /** Scales by a factor.
     * @zh 按系数缩放。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Pos2D scale(double factor) {
        return new Pos2D(x * factor, y * factor);
    }

    /** JSON form so {@code JSON.stringify} renders the point directly.
     * @zh JSON 形式，使 {@code JSON.stringify} 直接输出坐标。 */
    @ApiSince("0.3.0")
    @ApiLoader("*")
    @ApiMC("*")
    public String toJSON() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x", x);
        out.put("y", y);
        return ink.tuanzi.neomacros.core.util.JsonLite.write(out);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Pos2D point
                && Double.doubleToLongBits(x) == Double.doubleToLongBits(point.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(point.y);
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(x) ^ (Double.doubleToLongBits(y) * 31);
        return (int) (bits ^ (bits >> 32));
    }

    @Override
    @ApiInternal
    public Object construct(Object[] args) {
        if (args != null && args.length == 1 && args[0] instanceof Pos2D point) {
            return point;
        }
        return new Pos2D(
                args != null && args.length > 0 && args[0] instanceof Number n ? n.doubleValue() : 0,
                args != null && args.length > 1 && args[1] instanceof Number n ? n.doubleValue() : 0);
    }

    /** Component-wise lexicographic compare (x first, then y).
     * @zh 分量字典序比较（先 x 后 y）。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int compare(Pos2D other) {
        int byX = Double.compare(x, other.x);
        return byX != 0 ? byX : Double.compare(y, other.y);
    }

}
