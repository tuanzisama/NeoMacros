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
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiType("Pos2DJson")
    public Map<String, Object> toJSON() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x", x);
        out.put("y", y);
        return out;
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
    public int compare(Pos2D other) {
        int byX = Double.compare(x, other.x);
        return byX != 0 ? byX : Double.compare(y, other.y);
    }

    /**
     * The JSON form of one 2D point ({@code {x, y}}).
     *
     * @zh 二维坐标的 JSON 形式（{@code {x, y}}）。
     */
    @ApiClass(name = "Pos2DJson", kind = ApiClassKind.DATA)
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public record Pos2DJson(double x, double y) {
    }
}
