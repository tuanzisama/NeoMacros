package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * Whether the state's block belongs to the block tag. Unknown tags and
     * tags of other registries answer false; without a loaded registry
     * answer undefined.
     *
     * @zh 状态所属方块是否属于指定方块标签；未知标签或其他注册表的标签为 false，注册表不可用时为 undefined。
     * @zh-param tag 标签名，可带 {@code #} 前缀、可省略命名空间
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean hasTag(String tag) {
        ink.tuanzi.neomacros.port.model.BlockTypeData data =
                ink.tuanzi.neomacros.core.api.ScriptApiEnvironment.adapter().world()
                        .blockType(id).orElse(null);
        if (data == null) {
            return null;
        }
        return data.tags().contains(normalizeTag(tag));
    }

    /** Whether the state has a large (exceeds-cube) collision shape.
     * @zh 该状态是否具有超出立方体的大型碰撞形状。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean exceedsCube() {
        Object value = extra("exceedsCube");
        return value instanceof Boolean b ? b : null;
    }

    /** Whether the state supports an analog (comparator) output signal.
     * @zh 该状态是否支持比较器模拟输出信号。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean hasComparatorOutput() {
        Object value = extra("hasComparatorOutput");
        return value instanceof Boolean b ? b : null;
    }

    /**
     * The legacy movement-blocking flag (excludes cobwebs and bamboo
     * saplings), or undefined when the state carries no world snapshot.
     *
     * @zh 旧版阻挡移动标记（排除蛛网与竹笋）；状态无世界快照时为 undefined。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean blocksMovement() {
        Object value = extra("blocksMovement");
        return value instanceof Boolean b ? b : null;
    }

    /** The legacy solid flag, distinct from opacity, or undefined without a world snapshot.
     * @zh 旧版固体标记（与不透明度不同）；无世界快照时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isSolid() {
        Object value = extra("isSolid");
        return value instanceof Boolean b ? b : null;
    }

    /**
     * Whether the block state present at the position allows spawning the
     * given entity type in the currently loaded world; undefined without a
     * world, false for an unknown entity identifier.
     *
     * @zh 当前加载世界中该坐标处的方块状态是否允许指定实体类型生成；无世界时为 undefined，未知实体标识为 false。
     * @zh-param x 方块坐标 X
     * @zh-param y 方块坐标 Y
     * @zh-param z 方块坐标 Z
     * @zh-param entity 实体类型标识，可省略命名空间
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean allowsSpawning(int x, int y, int z, String entity) {
        return ink.tuanzi.neomacros.core.api.ScriptApiEnvironment.adapter().world()
                .allowsSpawningAt(new ink.tuanzi.neomacros.port.model.Pos3d(x, y, z), entity)
                .orElse(null);
    }

    /**
     * Whether the block state present at the position suffocates entities
     * standing inside it in the currently loaded world; undefined without
     * a world.
     *
     * @zh 当前加载世界中该坐标处的方块状态是否会令其中的实体窒息；无世界时为 undefined。
     * @zh-param x 方块坐标 X
     * @zh-param y 方块坐标 Y
     * @zh-param z 方块坐标 Z
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean shouldSuffocate(int x, int y, int z) {
        return ink.tuanzi.neomacros.core.api.ScriptApiEnvironment.adapter().world()
                .suffocatesAt(new ink.tuanzi.neomacros.port.model.Pos3d(x, y, z))
                .orElse(null);
    }

    // ---------------------------------------------------------------------
    // G-029: the typed state-property query face. Every vanilla property
    // reads through the props map; the convenience members below cover the
    // frequent keys with typed values, {@link #getProp(String)} answers any
    // other key, and {@link #getPropRange(String)} answers numeric ranges.
    // ---------------------------------------------------------------------

    /**
     * The value of one vanilla state property (e.g. {@code facing},
     * {@code half}), or undefined when the state has no such property.
     * Lookup is case-insensitive; values keep their serialized spelling
     * ({@code east}, {@code bottom}, {@code inner_left}, {@code true}).
     *
     * @zh 单个原版状态属性的值（如 {@code facing}、{@code half}）；状态无该属性为 undefined。属性名大小写不敏感，值保持序列化拼写。
     * @zh-param name 属性名
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getProp(String name) {
        return name == null ? null : props.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    /** Whether the state carries the given vanilla property.
     * @zh 状态是否带有指定原版属性。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public boolean hasProp(String name) {
        return name != null && props.containsKey(name.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * The {@code [min, max]} values one numeric state property can take
     * (e.g. {@code level} 0-15), read from the block type's defined states;
     * undefined when the block type is unknown or the property is absent or
     * not numeric.
     *
     * @zh 单个数值型状态属性可取的 {@code [min, max]} 区间（如 {@code level} 0-15），取自方块类型的全部状态；方块类型未知或属性不存在/非数值时为 undefined。
     * @zh-param name 属性名
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<Integer> getPropRange(String name) {
        if (name == null) {
            return null;
        }
        String key = name.toLowerCase(java.util.Locale.ROOT);
        ink.tuanzi.neomacros.port.model.BlockTypeData data =
                ink.tuanzi.neomacros.core.api.ScriptApiEnvironment.adapter().world()
                        .blockType(id).orElse(null);
        if (data == null) {
            return null;
        }
        Integer min = null;
        Integer max = null;
        for (Map<String, String> state : data.states()) {
            String value = state.get(key);
            if (value == null) {
                continue;
            }
            int parsed;
            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException notNumeric) {
                return null;
            }
            min = min == null || parsed < min ? parsed : min;
            max = max == null || parsed > max ? parsed : max;
        }
        return min == null ? null : List.of(min, max);
    }

    /** The facing direction name ({@code down}/{@code up}/{@code north}/...), or undefined without the property.
     * @zh 朝向方向名（{@code down}/{@code up}/{@code north} 等）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getFacing() {
        return getProp("facing");
    }

    /** The axis name ({@code x}/{@code y}/{@code z}), or undefined without the property.
     * @zh 轴名（{@code x}/{@code y}/{@code z}）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getAxis() {
        return getProp("axis");
    }

    /** The block half ({@code top}/{@code bottom}/{@code upper}/{@code lower}), or undefined without the property.
     * @zh 方块半部（{@code top}/{@code bottom}/{@code upper}/{@code lower}）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getHalf() {
        return getProp("half");
    }

    /** The slab type ({@code top}/{@code bottom}/{@code double}); undefined on non-slab blocks.
     * @zh台阶类型（{@code top}/{@code bottom}/{@code double}）；非台阶方块为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getSlabType() {
        return vocabProp("type", SLAB_TYPES);
    }

    /** The chest type ({@code single}/{@code left}/{@code right}); undefined on non-chest blocks.
     * @zh 箱体类型（{@code single}/{@code left}/{@code right}）；非箱子方块为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getChestType() {
        return vocabProp("type", CHEST_TYPES);
    }

    /** The piston-head type ({@code normal}/{@code sticky}); undefined on non-piston-head blocks.
     * @zh 活塞头类型（{@code normal}/{@code sticky}）；非活塞头方块为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getPistonType() {
        return vocabProp("type", PISTON_TYPES);
    }

    /** The stair shape ({@code straight}/{@code inner_left}/...); undefined on non-stair blocks.
     * @zh 台阶衔接形状（{@code straight}/{@code inner_left} 等）；非楼梯方块为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getStairShape() {
        return vocabProp("shape", STAIR_SHAPES);
    }

    /** The rail shape ({@code north_south}/{@code ascending_east}/...); undefined on non-rail blocks.
     * @zh 铁轨形状（{@code north_south}/{@code ascending_east} 等）；非铁轨方块为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getRailShape() {
        return vocabProp("shape", RAIL_SHAPES);
    }

    /** The door hinge side ({@code left}/{@code right}), or undefined without the property.
     * @zh 门铰链侧（{@code left}/{@code right}）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getDoorHinge() {
        return getProp("hinge");
    }

    /** The note-block instrument ({@code harp}/{@code bell}/...), or undefined without the property.
     * @zh 音符盒乐器（{@code harp}/{@code bell} 等）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getInstrument() {
        return getProp("instrument");
    }

    /** The bed part ({@code head}/{@code foot}), or undefined without the property.
     * @zh 床的部段（{@code head}/{@code foot}）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getBedPart() {
        return getProp("part");
    }

    /** The attach face ({@code floor}/{@code wall}/{@code ceiling}), or undefined without the property.
     * @zh 依附面（{@code floor}/{@code wall}/{@code ceiling}）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getFace() {
        return getProp("face");
    }

    /** The current level value (fluid/cauldron/composter), or undefined without the property.
     * @zh 当前等级值（流体/炼药锅/堆肥桶）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getLevel() {
        return intProp("level");
    }

    /** The honey level (0-5), or undefined without the property.
     * @zh 蜂蜜等级（0-5）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getHoneyLevel() {
        return intProp("honey_level");
    }

    /** The redstone power level (0-15), or undefined without the property.
     * @zh 红石信号强度（0-15）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getPower() {
        return intProp("power");
    }

    /** The repeater/comparator delay (1-4), or undefined without the property.
     * @zh 中继器/比较器延迟（1-4）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getDelay() {
        return intProp("delay");
    }

    /** The stability distance (vines/wool/sculk), or undefined without the property.
     * @zh 稳定距离（藤蔓/羊毛/幽匿）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getDistance() {
        return intProp("distance");
    }

    /** The note-block pitch (0-24), or undefined without the property.
     * @zh 音符盒音高（0-24）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getNote() {
        return intProp("note");
    }

    /** Whether the state is waterlogged, or undefined without the property.
     * @zh 状态是否含水；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean getWaterlogged() {
        return boolProp("waterlogged");
    }

    /** Whether the state reports a snowy cover, or undefined without the property.
     * @zh 状态是否带雪面覆盖；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean getSnowy() {
        return boolProp("snowy");
    }

    /** Whether the state is occupied (beds), or undefined without the property.
     * @zh 状态是否被占用（床）；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean getOccupied() {
        return boolProp("occupied");
    }

    /** Whether the piston is extended, or undefined without the property.
     * @zh 活塞是否伸出；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean getExtended() {
        return boolProp("extended");
    }

    /** Whether the leaves are persistent, or undefined without the property.
     * @zh 树叶是否永不凋落；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean getPersistent() {
        return boolProp("persistent");
    }

    /** Whether the block hangs from the face above, or undefined without the property.
     * @zh 方块是否悬挂在上方；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean getHanging() {
        return boolProp("hanging");
    }

    /** Whether the hopper/crafter is enabled, or undefined without the property.
     * @zh 漏斗/合成器是否启用；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean getEnabled() {
        return boolProp("enabled");
    }

    /** Whether the command block is conditional, or undefined without the property.
     * @zh 命令方块是否为条件模式；无该属性为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean getConditional() {
        return boolProp("conditional");
    }

    /** Reads one integer property; absent or non-numeric answers undefined. */
    private Integer intProp(String key) {
        String value = getProp(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException notNumeric) {
            return null;
        }
    }

    /** Reads one boolean property; absent or malformed answers undefined. */
    private Boolean boolProp(String key) {
        String value = getProp(key);
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        return "false".equals(value) ? Boolean.FALSE : null;
    }

    /**
     * Reads one property restricted to a value vocabulary: blocks sharing
     * the key but not the vocabulary (slab vs chest {@code type}, stair vs
     * rail {@code shape}) answer undefined, mirroring JsMacros' per-property
     * lookup that throws on the wrong property instance.
     */
    private String vocabProp(String key, java.util.Set<String> vocabulary) {
        String value = getProp(key);
        return value != null && vocabulary.contains(value) ? value : null;
    }

    /** Value vocabulary of the slab {@code type} property. */
    private static final java.util.Set<String> SLAB_TYPES =
            java.util.Set.of("top", "bottom", "double");

    /** Value vocabulary of the chest {@code type} property. */
    private static final java.util.Set<String> CHEST_TYPES =
            java.util.Set.of("single", "left", "right");

    /** Value vocabulary of the piston-head {@code type} property. */
    private static final java.util.Set<String> PISTON_TYPES =
            java.util.Set.of("normal", "sticky");

    /** Value vocabulary of the stair {@code shape} property. */
    private static final java.util.Set<String> STAIR_SHAPES = java.util.Set.of(
            "straight", "inner_left", "inner_right", "outer_left", "outer_right");

    /** Value vocabulary of the rail {@code shape} property. */
    private static final java.util.Set<String> RAIL_SHAPES = java.util.Set.of(
            "north_south", "east_west", "ascending_east", "ascending_west",
            "ascending_north", "ascending_south", "south_east", "south_west",
            "north_west", "north_east");

    /** Normalizes one tag reference: an optional {@code #} prefix and a default namespace. */
    private static String normalizeTag(String tag) {
        String id = tag.startsWith("#") ? tag.substring(1) : tag;
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
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
