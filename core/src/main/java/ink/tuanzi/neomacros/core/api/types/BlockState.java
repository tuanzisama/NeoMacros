package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
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
    private final Map<String, Object> extras;

    private static final java.util.Set<String> AIR_IDS = java.util.Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private static final java.util.List<String> LIQUID_IDS = java.util.List.of(
            "minecraft:water", "minecraft:flowing_water", "minecraft:lava",
            "minecraft:flowing_lava");

    BlockState(String id) {
        this(id, Map.of(), Map.of());
    }

    /** Creates the state from an identifier and its vanilla properties.
     * @zh 由标识符与原版属性构造状态。 */
    public BlockState(String id, Map<String, String> props) {
        this(id, props, Map.of());
    }

    /** Creates the state with neutral physics/light extras attached. */
    public BlockState(String id, Map<String, String> props,
            Map<String, Object> extras) {
        this.id = id;
        this.props = Map.copyOf(props);
        this.extras = Map.copyOf(extras);
    }

    /** Reads one extras entry, or null when absent. */
    private Object extra(String key) {
        return extras.get(key);
    }

    /** The namespaced block identifier, e.g. {@code minecraft:chest}.
     * @zh 带命名空间的方块标识符，如 {@code minecraft:chest}。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getId() {
        return id;
    }

    /** The vanilla state properties.
     * @zh 原版状态属性。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Map<String, String> getProps() {
        return props;
    }

    /** Whether the state's block matches the identifier (namespace optional).
     * @zh 状态的方块是否匹配标识符（可省略命名空间）。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
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
    /** The block light emission level (0-15).
     * @zh 方块发光等级（0-15）。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getLuminance() {
        Object value = extra("luminance");
        return value instanceof Integer i ? i : null;
    }

    /** The destroy hardness, or undefined for unbreakable blocks.
     * @zh 破坏硬度；不可破坏方块为 undefined。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Double getHardness() {
        Object value = extra("hardness");
        return value instanceof Number n && n.doubleValue() >= 0
                ? n.doubleValue() : null;
    }

    /** Whether the state visually occludes the sky and neighbors.
     * @zh 该状态是否遮蔽天光与相邻面。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isOpaque() {
        Object value = extra("opaque");
        return value instanceof Boolean b ? b : null;
    }

    /** Whether the state emits redstone power.
     * @zh 该状态是否发出红石信号。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean emitsRedstonePower() {
        Object value = extra("emitsRedstonePower");
        return value instanceof Boolean b ? b : null;
    }

    /** Whether the state carries a block entity.
     * @zh 该状态是否带方块实体。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean hasBlockEntity() {
        Object value = extra("hasBlockEntity");
        return value instanceof Boolean b ? b : null;
    }

    /** Whether the state receives random ticks.
     * @zh 该状态是否接收随机刻。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean hasRandomTicks() {
        Object value = extra("hasRandomTicks");
        return value instanceof Boolean b ? b : null;
    }

    /** Whether lava can ignite the block.
     * @zh 岩浆是否可点燃该方块。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isBurnable() {
        Object value = extra("isBurnable");
        return value instanceof Boolean b ? b : null;
    }

    /** Whether the block can be replaced by placing into it.
     * @zh 该方块是否可被放置覆盖（如花草）。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isReplaceable() {
        Object value = extra("replaceable");
        return value instanceof Boolean b ? b : null;
    }

    /** Whether the correct tool is required for drops.
     * @zh 掉落物是否需要正确工具。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isToolRequired() {
        Object value = extra("toolRequired");
        return value instanceof Boolean b ? b : null;
    }

    /** The piston push behaviour (lowercase, e.g. {@code normal}).
     * @zh 活塞推动行为（小写，如 {@code normal}）。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getPistonBehaviour() {
        Object value = extra("pistonBehaviour");
        return value instanceof String s ? s : null;
    }

    /** Whether this state suffocates entities standing inside it.
     * @zh 该状态是否会令其中的实体窒息。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isSuffocating() {
        Object value = extra("suffocating");
        return value instanceof Boolean b ? b : null;
    }

    /** The contained fluid identifier, or {@code null} when dry.
     * @zh 所含流体标识；无流体为 null。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getFluid() {
        Object value = extra("fluid");
        return value instanceof String s ? s : null;
    }

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
    @ApiLoader("*")
    @ApiMC("*")
    public boolean getIsAir() {
        return AIR_IDS.contains(id);
    }

    /** Whether the state is a liquid (water or lava family).
     * @zh 状态是否为液体（水/熔岩族）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean getLiquid() {
        return LIQUID_IDS.stream().anyMatch(id::startsWith);
    }

    @Override
    public String toString() {
        return id + props;
    }
}
