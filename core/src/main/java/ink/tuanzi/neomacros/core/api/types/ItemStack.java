package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ItemStackData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable copy of one item stack. Instances come only from queries
 * (inventory slots, event payloads) or the {@link ItemStackBuilder}; there is
 * no direct constructor. {@code JSON.stringify} renders the plain fields.
 * @zh 不可变的物品堆副本。实例只来自查询（容器槽位、事件载荷）或 {@link ItemStackBuilder}；不提供直接构造器。{@code JSON.stringify} 输出纯字段。
 */
@ApiClass(name = "ItemStack", kind = ApiClassKind.DATA)
@ApiSince("0.2.0")
@ApiLoader("*")
@ApiMC("*")
public final class ItemStack implements ScriptHandle {

    private final String id;
    private final int count;
    private final String name;
    private final int damage;
    private final int maxDamage;
    private final List<String> lore;
    private final List<Enchantment> enchantments;
    private final boolean unbreakable;
    private final Map<String, Object> extras;

    ItemStack(String id, int count, String name, int damage, int maxDamage,
            List<String> lore, List<Enchantment> enchantments, boolean unbreakable,
            Map<String, Object> extras) {
        this.id = namespaced(id);
        this.count = count;
        this.name = name;
        this.damage = damage;
        this.maxDamage = maxDamage;
        this.lore = List.copyOf(lore);
        this.enchantments = List.copyOf(enchantments);
        this.unbreakable = unbreakable;
        this.extras = Map.copyOf(extras == null ? Map.of() : extras);
    }

    /** Builds the script stack from one neutral port snapshot.
     * @zh 由中立端口快照构建脚本物品堆。 */
    public static ItemStack of(ItemStackData data) {
        List<Enchantment> enchants = new ArrayList<>();
        for (ink.tuanzi.neomacros.port.model.EnchantmentData entry : data.enchantments()) {
            enchants.add(new Enchantment(namespaced(entry.id()), entry.level()));
        }
        return new ItemStack(data.id(), data.count(), data.name(), data.damage(),
                data.maxDamage(), data.lore(), enchants, false, data.extras());
    }

    /** Reads one neutral item fact from the port snapshot.
     * @zh 读取端口快照中的一项中性物品事实。 */
    private Object fact(String key) {
        return extras.get(key);
    }

    /** The neutral port snapshot of this stack.
     * @zh 本物品堆的中立端口快照。 */
    @ApiInternal
    public ItemStackData toData() {
        List<ink.tuanzi.neomacros.port.model.EnchantmentData> entries = new ArrayList<>();
        for (Enchantment entry : enchantments) {
            entries.add(new ink.tuanzi.neomacros.port.model.EnchantmentData(entry.id(), entry.level()));
        }
        return new ItemStackData(id, count, name, damage, maxDamage, lore, entries);
    }

    /** Appends the namespace to a bare identifier ({@code minecraft:} default).
     * @zh 为裸标识补全命名空间（默认 {@code minecraft:}）。 */
    @ApiInternal
    public static String namespaced(String id) {
        if (id == null) {
            return "minecraft:air";
        }
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }

    /** The namespaced item identifier, e.g. {@code minecraft:diamond_sword}.
     * @zh 命名空间物品标识，如 {@code minecraft:diamond_sword}。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public String getId() {
        return id;
    }

    /** The stack size.
     * @zh 物品数量。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public int getCount() {
        return count;
    }

    /** The maximum stack size for this item.
     * @zh 该物品的最大堆叠数。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public int getMaxStackSize() {
        Object value = fact("maxStackSize");
        return value instanceof Integer i ? i : 1;
    }

    /** Whether the item takes durability damage at all.
     * @zh 该物品是否会损耗耐久。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public boolean isDamageable() {
        return Boolean.TRUE.equals(fact("damageable"));
    }

    /** Whether the item can receive enchantments in an enchanting table.
     * @zh 该物品是否可被附魔台附魔。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public boolean isEnchantable() {
        return Boolean.TRUE.equals(fact("enchantable"));
    }

    /** Whether the item currently carries enchantments.
     * @zh 该物品当前是否带附魔。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public boolean isEnchanted() {
        return Boolean.TRUE.equals(fact("enchanted"));
    }

    /** Whether the item is food.
     * @zh 该物品是否为食物。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public boolean isEdible() {
        return Boolean.TRUE.equals(fact("edible"));
    }

    /** The anvil repair cost added by prior anvil work (0 when untouched).
     * @zh 铁砧累积的修复费用（未动过为 0）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public int getRepairCost() {
        Object value = fact("repairCost");
        return value instanceof Integer i ? i : 0;
    }

    /** The display name (styled text flattened).
     * @zh 显示名（样式文本平铺）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public String getName() {
        return name;
    }

    /** The current durability damage (0 for undamageable items).
     * @zh 当前耐久损伤（无耐久物品为 0）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public int getDamage() {
        return damage;
    }

    /** The maximum durability (0 for undamageable items).
     * @zh 最大耐久（无耐久物品为 0）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public int getMaxDamage() {
        return maxDamage;
    }

    /** The lore lines, in order.
     * @zh 按序排列的 Lore 行。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public List<String> getLore() {
        return lore;
    }

    /** The enchantments as {@code {id, level}} entries, in stored order.
     * @zh 按 {id, level} 形态的附魔列表，按存储顺序。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    @ApiReadOnly
    public List<Map<String, Object>> getEnchantments() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Enchantment entry : enchantments) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", entry.id());
            view.put("level", entry.level());
            out.add(view);
        }
        return out;
    }

    /** Whether the stack represents nothing (air or a zero count).
     * @zh 物品堆是否为空（空气或数量为零）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean getIsEmpty() {
        return count <= 0 || id.equals("minecraft:air");
    }

    /** Whether the stack's item id equals the identifier (namespace optional).
     * @zh 物品标识是否等于给定 id（命名空间可省略）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean is(String itemId) {
        return id.equals(namespaced(itemId));
    }

    /** Whether the other stack holds the same item, ignoring count, damage,
     * and components.
     * @zh 另一物品堆是否为同类物品（忽略数量、耐久与组件）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean isSameItem(Object other) {
        String otherId = itemIdOf(other);
        return otherId != null && id.equals(otherId);
    }

    /** Whether the other stack is a full copy of this one.
     * @zh 另一物品堆是否与本堆完全相同。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean same(Object other) {
        return equals(other);
    }

    /** JSON form so {@code JSON.stringify} renders the stack directly.
     * @zh JSON 形式，使 {@code JSON.stringify} 直接输出物品堆。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public Map<String, Object> toJSON() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("count", count);
        out.put("name", name);
        if (maxDamage > 0) {
            out.put("damage", damage);
            out.put("maxDamage", maxDamage);
        }
        if (!lore.isEmpty()) {
            out.put("lore", lore);
        }
        if (!enchantments.isEmpty()) {
            out.put("enchantments", getEnchantments());
        }
        return out;
    }

    @Override
    public String toString() {
        return "ItemStack(" + id + " x" + count + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ItemStack stack)) {
            return false;
        }
        return id.equals(stack.id) && count == stack.count && damage == stack.damage
                && maxDamage == stack.maxDamage && unbreakable == stack.unbreakable
                && lore.equals(stack.lore) && enchantments.equals(stack.enchantments)
                && java.util.Objects.equals(name, stack.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, count, name, damage, maxDamage, lore, enchantments,
                unbreakable);
    }

    /** Reads the item id of another stack-shaped value (stack or plain object). */
    private static String itemIdOf(Object other) {
        if (other instanceof ItemStack stack) {
            return stack.id;
        }
        return null;
    }

    /** One enchantment entry of the stack. */
    static final class Enchantment {

        private final String id;
        private final int level;

        Enchantment(String id, int level) {
            this.id = id;
            this.level = level;
        }

        String id() {
            return id;
        }

        int level() {
            return level;
        }
    }
}
