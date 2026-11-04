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
 */
@ApiClass(name = "BlockState", kind = ApiClassKind.DATA)
public final class BlockState implements ink.tuanzi.neomacros.core.api.ScriptHandle {

    private final String id;
    private final Map<String, String> props;

    BlockState(String id) {
        this(id, Map.of());
    }

    /** Creates the state from an identifier and its vanilla properties. */
    public BlockState(String id, Map<String, String> props) {
        this.id = id;
        this.props = Map.copyOf(props);
    }

    /** The namespaced block identifier, e.g. {@code minecraft:chest}. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public String getId() {
        return id;
    }

    /** The vanilla state properties. */
    @ApiSince("0.1.0")
    @ApiReadOnly
    public Map<String, String> getProps() {
        return props;
    }

    /** Whether the state's block matches the identifier (namespace optional). */
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
