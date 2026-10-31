package ink.tuanzi.neomacros.docs.fixture;

import ink.tuanzi.neomacros.doc.api.ApiModule;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Fixture module exercising the generation warning paths: no @zh module body,
 * no class-level @ApiSince, one fully documented member, and one member with
 * neither a translation nor a version annotation.
 *
 * <p>ES module only ({@code global=false}): reachable through the import
 * channel but absent from the neo global aggregate.</p>
 */
@ApiModule(name = "partial", category = "test", global = false)
public final class FixturePartialApi {

    /**
     * Returns a fully documented value.
     *
     * @return the tagged value
     * @zh 返回完整文档化的值。
     * @zh-return 标注过的值
     */
    @ApiSince("0.1.0")
    public String tagged() {
        return "tagged";
    }

    /**
     * Returns a value whose member lacks translation and version annotation.
     *
     * @param input the input value
     * @return the untouched input
     */
    public String untagged(String input) {
        return input;
    }
}
