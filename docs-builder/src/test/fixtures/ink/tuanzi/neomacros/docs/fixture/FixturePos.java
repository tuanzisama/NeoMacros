package ink.tuanzi.neomacros.docs.fixture;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Fixture record referenced in signatures, rendered as a TypeScript
 * interface of its components.
 *
 * @zh 签名中引用的 fixture record，渲染为 TypeScript 接口。
 */
public record FixturePos(double x, double y, double z) {

    /** The X coordinate. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public double getX() { return x; }

    /** The Y coordinate. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public double getY() { return y; }

    /** The Z coordinate. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public double getZ() { return z; }
}
