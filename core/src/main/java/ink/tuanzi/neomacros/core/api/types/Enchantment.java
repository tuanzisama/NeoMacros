package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import ink.tuanzi.neomacros.port.model.EnchantmentRelations;
import ink.tuanzi.neomacros.port.model.EnchantmentSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable enchantment entry: an enchantment identifier together with the
 * level it carries on a stack (0 for a registry-only entry). Instances come
 * from {@link ItemStack#getEnchantments()} or from another enchantment's
 * conflict and compatibility lists; the definition reads (level bounds,
 * weight, curse, treasure, supported items, relations) resolve through the
 * item port on every access, so an unknown identifier or a missing
 * connection answers {@code undefined}.
 *
 * @zh 不可变附魔条目：附魔标识 + 它在物品堆上的等级（纯注册表条目为 0）。实例来自 {@link ItemStack#getEnchantments()} 或其他附魔的冲突/兼容列表；定义读取（等级上下限、权重、诅咒、宝藏、支持物品、相容关系）每次访问经物品端口解析，因此未知标识或无连接时为 undefined。
 */
@ApiClass(name = "Enchantment", kind = ApiClassKind.DATA)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public final class Enchantment implements ScriptHandle {

    private final String id;
    private final int level;

    /** Builds one entry from a neutral identifier and level. */
    Enchantment(String id, int level) {
        this.id = ItemStack.namespaced(id);
        this.level = level;
    }

    /** The registry identifier of this enchantment, e.g. {@code minecraft:sharpness}.
     * @zh 该附魔的注册标识，如 {@code minecraft:sharpness}。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getId() {
        return id;
    }

    /** The level this entry carries, or 0 for a registry-only entry.
     * @zh 该条目携带的等级；纯注册表条目为 0。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public int getLevel() {
        return level;
    }

    /** The lowest level the enchantment defines; undefined without the
     * item port or for an unknown enchantment.
     * @zh 该附魔定义的最低等级；无物品端口或附魔未知时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getMinLevel() {
        EnchantmentSpec spec = spec();
        return spec == null ? null : spec.minLevel();
    }

    /** The highest level the enchantment defines; undefined without the
     * item port or for an unknown enchantment.
     * @zh 该附魔定义的最高等级；无物品端口或附魔未知时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getMaxLevel() {
        EnchantmentSpec spec = spec();
        return spec == null ? null : spec.maxLevel();
    }

    /** The selection weight the enchanting table uses; undefined without
     * the item port or for an unknown enchantment.
     * @zh 附魔台选择该附魔所用的权重；无物品端口或附魔未知时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getWeight() {
        EnchantmentSpec spec = spec();
        return spec == null ? null : spec.weight();
    }

    /** Whether the enchantment carries the curse tag; undefined without
     * the item port or for an unknown enchantment.
     * @zh 该附魔是否带诅咒标签；无物品端口或附魔未知时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isCursed() {
        EnchantmentSpec spec = spec();
        return spec == null ? null : spec.cursed();
    }

    /** Whether the enchantment carries the treasure tag; undefined without
     * the item port or for an unknown enchantment.
     * @zh 该附魔是否带宝藏标签；无物品端口或附魔未知时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isTreasure() {
        EnchantmentSpec spec = spec();
        return spec == null ? null : spec.treasure();
    }

    /** The styled display name at this entry's level (roman numeral
     * suffix, curses coloured red); undefined without the item port.
     * @zh 本条目等级的样式化显示名（罗马数字后缀，诅咒为红色）；无物品端口时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public TextComponent getRomanLevelName() {
        return getRomanLevelName(level);
    }

    /**
     * The styled display name at the given level (roman numeral suffix,
     * curses coloured red); undefined without the item port.
     *
     * @zh 给定等级的样式化显示名（罗马数字后缀，诅咒为红色）；无物品端口时为 undefined。
     * @zh-param level 要命名的等级
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public TextComponent getRomanLevelName(int level) {
        ChatComponent model = ScriptApiEnvironment.adapter().item()
                .enchantmentLevelName(id, level).orElse(null);
        return model == null ? null : TextComponent.from(model);
    }

    /** The registry entries this enchantment conflicts with, including
     * itself; undefined without the item port.
     * @zh 与该附魔冲突的注册表条目（含其自身）；无物品端口时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<Enchantment> getConflictingEnchantments() {
        EnchantmentRelations relations = relations();
        return relations == null ? null : wrap(relations.conflictIds());
    }

    /** The registry entries this enchantment combines with, excluding
     * itself; undefined without the item port.
     * @zh 与该附魔可叠加的注册表条目（不含其自身）；无物品端口时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<Enchantment> getCompatibleEnchantments() {
        EnchantmentRelations relations = relations();
        return relations == null ? null : wrap(relations.compatibleIds());
    }

    /** The item identifiers this enchantment supports; undefined without
     * the item port.
     * @zh 该附魔支持的物品标识列表；无物品端口时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<String> getAcceptableItems() {
        EnchantmentSpec spec = spec();
        return spec == null ? null : spec.acceptableItemIds();
    }

    /**
     * Whether this enchantment supports the item type of the argument and,
     * for a stack, is compatible with every enchantment already on it;
     * undefined when either side cannot be resolved.
     *
     * @zh 该附魔是否支持参数物品的类型，且（对物品堆而言）与其上已有附魔全部相容；任一侧无法解析时为 undefined。
     * @zh-param item 物品堆或物品标识
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean canBeApplied(@ApiType("ItemStack | string") Object item) {
        EnchantmentSpec spec = spec();
        if (spec == null) {
            return null;
        }
        if (item == null) {
            return null;
        }
        String itemId;
        if (item instanceof ItemStack stack) {
            itemId = stack.getId();
        } else if (item instanceof String text) {
            itemId = ItemStack.namespaced(text);
        } else {
            throw new IllegalArgumentException("expected ItemStack or item id");
        }
        if (!spec.acceptableItemIds().contains(itemId)) {
            return false;
        }
        if (item instanceof ItemStack stack) {
            for (Enchantment other : stack.getEnchantments()) {
                Boolean compatible = compatibleWith(other.getId());
                if (compatible == null) {
                    return null;
                }
                if (!compatible) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether this enchantment combines with the given one; an enchantment
     * never combines with itself, and an unknown identifier answers
     * undefined.
     *
     * @zh 该附魔是否与给定附魔可叠加；附魔与自身永不相容，未知标识为 undefined。
     * @zh-param other 另一附魔或附魔标识
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isCompatible(@ApiType("Enchantment | string") Object other) {
        String otherId = enchantmentIdOf(other);
        if (otherId == null) {
            return null;
        }
        return compatibleWith(otherId);
    }

    /**
     * Whether the given enchantment conflicts with this one; a conflict
     * holds against itself, and an unknown identifier answers undefined.
     *
     * @zh 给定附魔是否与该附魔冲突；附魔与自身冲突，未知标识为 undefined。
     * @zh-param other 另一附魔或附魔标识
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean conflictsWith(@ApiType("Enchantment | string") Object other) {
        Boolean compatible = isCompatible(other);
        return compatible == null ? null : !compatible;
    }

    /** The definition snapshot, or null when the port cannot answer. */
    private EnchantmentSpec spec() {
        return ScriptApiEnvironment.adapter().item().enchantmentSpec(id).orElse(null);
    }

    /** The relation snapshot, or null when the port cannot answer. */
    private EnchantmentRelations relations() {
        return ScriptApiEnvironment.adapter().item().enchantmentRelations(id).orElse(null);
    }

    /** Pairwise compatibility against the registry, or null when unknown. */
    private Boolean compatibleWith(String otherId) {
        return ScriptApiEnvironment.adapter().item()
                .enchantmentsCompatible(id, otherId).orElse(null);
    }

    /** Wraps neutral identifiers as level-zero entries. */
    private static List<Enchantment> wrap(List<String> ids) {
        List<Enchantment> out = new ArrayList<>(ids.size());
        for (String other : ids) {
            out.add(new Enchantment(other, 0));
        }
        return out;
    }

    /** Reads the identifier of an enchantment-shaped argument. */
    private static String enchantmentIdOf(Object other) {
        if (other == null) {
            return null;
        }
        if (other instanceof Enchantment enchantment) {
            return enchantment.id;
        }
        if (other instanceof String text) {
            return ItemStack.namespaced(text);
        }
        throw new IllegalArgumentException("expected Enchantment or enchantment id");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Enchantment enchantment
                && id.equals(enchantment.id) && level == enchantment.level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, level);
    }

    @Override
    public String toString() {
        return "Enchantment(" + id + " x" + level + ")";
    }
}
