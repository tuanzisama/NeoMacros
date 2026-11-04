package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiMC;
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

    private static final java.util.Set<String> AIR_IDS = java.util.Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private static final java.util.List<String> LIQUID_IDS = java.util.List.of(
            "minecraft:water", "minecraft:flowing_water", "minecraft:lava",
            "minecraft:flowing_lava");

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

    /**
     * Whether the state matches an exact property map (every entry equals;
     * an {@code id} key also compares the identifier).
     *
     * @zh 状态是否与精确属性表匹配（每项都相等；{@code id} 键同时比较标识符）。
     * @zh-param props 属性表
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public boolean isProps(java.util.Map<String, String> props) {
        for (java.util.Map.Entry<String, String> entry : props.entrySet()) {
            if (entry.getKey().equals("id")) {
                if (!is(entry.getValue())) {
                    return false;
                }
            } else if (!String.valueOf(props.get(entry.getKey()))
                    .equals(String.valueOf(this.props.get(entry.getKey())))) {
                return false;
            }
        }
        return true;
    }

    /** Whether the state is one of the air blocks.
     * @zh 状态是否为空气类方块。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public boolean getIsAir() {
        return AIR_IDS.contains(id);
    }

    /** Whether the state is a liquid (water or lava family).
     * @zh 状态是否为液体（水/熔岩族）。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public boolean getLiquid() {
        return LIQUID_IDS.stream().anyMatch(id::startsWith);
    }

    @Override
    public String toString() {
        return id + props;
    }
}
