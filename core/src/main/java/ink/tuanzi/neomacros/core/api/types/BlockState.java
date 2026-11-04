package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable block state: the namespaced block identifier plus the vanilla
 * state properties ({@code facing}, {@code open}, ...).
 * @zh 不可变方块状态：带命名空间的方块标识符加原版状态属性（{@code facing}、{@code open} 等）。
 */
@ApiClass(name = "BlockState", kind = ApiClassKind.DATA)
public final class BlockState implements ink.tuanzi.neomacros.core.api.ScriptHandle {

    private final String id;
    private final Map<String, String> props;

    BlockState(String id) {
        this(id, Map.of());
    }

    /** Creates the state from an identifier and its vanilla properties.
     * @zh 由标识符与原版属性构造状态。 */
    public BlockState(String id, Map<String, String> props) {
        this.id = id;
        this.props = Map.copyOf(props);
    }

    /** The namespaced block identifier, e.g. {@code minecraft:chest}.
     * @zh 带命名空间的方块标识符，如 {@code minecraft:chest}。 */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public String getId() {
        return id;
    }

    /** The vanilla state properties.
     * @zh 原版状态属性。 */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public Map<String, String> getProps() {
        return props;
    }

    /** Whether the state's block matches the identifier (namespace optional).
     * @zh 状态的方块是否匹配标识符（可省略命名空间）。 */
    @ApiSince("0.1.0")
    public boolean is(String idOrPredicate) {
        String probe = idOrPredicate.contains(":") ? idOrPredicate
                : "minecraft:" + idOrPredicate;
        return id.equals(probe);
    }

    @Override
    public String toString() {
        return id + props;
    }
}
