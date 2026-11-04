package ink.tuanzi.neomacros.docs.fixture;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * A standalone self-constructing vector cell (not nested in any facade).
 *
 * @zh 独立的自构造向量 cell（不属于任何门面）。
 */
@ApiClass(name = "StandaloneVec", kind = ApiClassKind.DATA)
public class FixtureStandaloneVec {

    /** The X coordinate. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public double getX() { return 0; }

    /** The Y coordinate. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public double getY() { return 0; }

    /** The Z coordinate. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public double getZ() { return 0; }
}
