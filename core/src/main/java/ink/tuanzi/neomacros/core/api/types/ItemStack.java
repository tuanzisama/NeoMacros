package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
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
    private final List<ink.tuanzi.neomacros.port.model.EnchantmentData> enchantments;
    private final boolean unbreakable;
    private final Map<String, Object> extras;

    ItemStack(String id, int count, String name, int damage, int maxDamage,
            List<String> lore, List<ink.tuanzi.neomacros.port.model.EnchantmentData> enchantments,
            boolean unbreakable, Map<String, Object> extras) {
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
    @ApiInternal
    public static ItemStack of(ItemStackData data) {
        List<ink.tuanzi.neomacros.port.model.EnchantmentData> enchants = new ArrayList<>();
        for (ink.tuanzi.neomacros.port.model.EnchantmentData entry : data.enchantments()) {
            enchants.add(new ink.tuanzi.neomacros.port.model.EnchantmentData(
                    namespaced(entry.id()), entry.level()));
        }
        return new ItemStack(data.id(), data.count(), data.name(), data.damage(),
                data.maxDamage(), data.lore(), enchants, false, data.extras());
    }

    /** Reads one neutral item fact from the port snapshot.
     * @zh 读取端口快照中的一项中性物品事实。 */
    private Object fact(String key) {
        return extras.get(key);
    }

    /** Reads one boolean fact; absent facts answer undefined.
     * @zh 读取一项布尔事实；事实缺席为 undefined。 */
    private Boolean factBoolean(String key) {
        return extras.get(key) instanceof Boolean b ? b : null;
    }

    /** Copies one string-list fact; absent facts answer undefined.
     * @zh 拷贝一项字符串列表事实；事实缺席为 undefined。 */
    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof String text) {
                out.add(text);
            }
        }
        return out;
    }

    /** Resolves a Block or BlockState argument to a state (a Block answers
     * with its default state); undefined targets answer null and other
     * argument types are rejected.
     * @zh 将方块或方块状态参数解析为状态（Block 取默认状态）；undefined 目标返回 null，其他参数类型拒绝。 */
    private static BlockState resolveState(Object blockOrState) {
        if (blockOrState == null) {
            return null;
        }
        if (blockOrState instanceof BlockState state) {
            return state;
        }
        if (blockOrState instanceof Block block) {
            return block.getDefaultState();
        }
        throw new IllegalArgumentException("expected Block or BlockState");
    }

    /** The neutral port snapshot of this stack, facts included.
     * @zh 本物品堆的中立端口快照（含物品事实）。 */
    @ApiInternal
    public ItemStackData toData() {
        return new ItemStackData(id, count, name, damage, maxDamage, lore, enchantments, extras);
    }

    /** The neutral fact map, for the builder's copy path (package access).
     * @zh 中立事实表（构建器拷贝路径用，包内访问）。 */
    Map<String, Object> extrasView() {
        return extras;
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

    /** The enchantments on this stack, in stored order.
     * @zh 该物品堆上的附魔列表，按存储顺序。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<Enchantment> getEnchantments() {
        List<Enchantment> out = new ArrayList<>(enchantments.size());
        for (ink.tuanzi.neomacros.port.model.EnchantmentData entry : enchantments) {
            out.add(new Enchantment(entry.id(), entry.level()));
        }
        return out;
    }

    /** The neutral enchantment entries, for the builder's copy path
     * (package access).
     * @zh 中立附魔条目（构建器拷贝路径用，包内访问）。 */
    List<ink.tuanzi.neomacros.port.model.EnchantmentData> enchantmentData() {
        return enchantments;
    }

    // ---- G-027 item face: item-type facts and evaluations ----

    /** Whether the item carries a tool component (mining rules and speeds).
     * @zh 该物品是否带工具组件（挖掘规则与速度）。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isTool() {
        return factBoolean("tool");
    }

    /** Whether the item carries an equippable component (wearable slots).
     * @zh 该物品是否带可装备组件（可穿戴槽位）。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isWearable() {
        return factBoolean("wearable");
    }

    /** Whether the item resists fire damage; undefined without item facts.
     * @zh 该物品是否抗火；无物品事实时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isFireproof() {
        return factBoolean("fireproof");
    }

    /** Whether the item places a block (block item family).
     * @zh 该物品是否为可放置的方块物品。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isBlockItem() {
        return factBoolean("blockItem");
    }

    /** The block represented by this block item as its registered default
     * state, or undefined for a non-block item or an unknown block type.
     * @zh 方块物品对应的方块（注册默认状态）；非方块物品或未知方块类型为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public BlockState getBlock() {
        if (!(fact("blockId") instanceof String blockId)) {
            return null;
        }
        return ScriptApiEnvironment.adapter().world().blockType(blockId)
                .map(data -> new BlockState(data.id(), data.defaultState()))
                .orElse(null);
    }

    /** Whether the item fits inside container items such as bundles and
     * shulker boxes; undefined without item facts.
     * @zh 该物品是否可放入收纳袋等容器物品；无物品事实时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean canFitInsideContainerItems() {
        return factBoolean("canFitInsideContainerItems");
    }

    /** The item's enchantability component value (0 when the item accepts
     * no enchantments); undefined without item facts.
     * @zh 该物品的附魔力组件值（不可附魔为 0）；无物品事实时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Integer getEnchantability() {
        return fact("enchantability") instanceof Integer i ? i : null;
    }

    /** Whether enchantments are hidden in the tooltip display.
     * @zh 提示框显示中是否隐藏附魔。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isEnchantmentsHidden() {
        return factBoolean("enchantmentsHidden");
    }

    /** Whether attribute modifiers are hidden in the tooltip display.
     * @zh 提示框显示中是否隐藏属性修饰符。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isModifiersHidden() {
        return factBoolean("modifiersHidden");
    }

    /** Whether the unbreakable flag is hidden in the tooltip display.
     * @zh 提示框显示中是否隐藏不可破坏标记。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isUnbreakableHidden() {
        return factBoolean("unbreakableHidden");
    }

    /** Whether the can-destroy restriction is hidden in the tooltip display.
     * @zh 提示框显示中是否隐藏禁破坏限制。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isCanDestroyHidden() {
        return factBoolean("canDestroyHidden");
    }

    /** Whether the can-place-on restriction is hidden in the tooltip display.
     * @zh 提示框显示中是否隐藏禁放置限制。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isCanPlaceHidden() {
        return factBoolean("canPlaceHidden");
    }

    /** Whether the dyed-color component is hidden in the tooltip display.
     * @zh 提示框显示中是否隐藏染色组件。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isDyeHidden() {
        return factBoolean("dyeHidden");
    }

    /** Whether the stack carries a can-destroy adventure restriction;
     * undefined without item facts.
     * @zh 物品堆是否带禁破坏冒险限制；无物品事实时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean hasDestroyRestrictions() {
        return factBoolean("hasDestroyRestrictions");
    }

    /** Whether the stack carries a can-place-on adventure restriction;
     * undefined without item facts.
     * @zh 物品堆是否带禁放置冒险限制；无物品事实时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean hasPlaceRestrictions() {
        return factBoolean("hasPlaceRestrictions");
    }

    /** The block identifiers the can-destroy restriction accepts, or
     * undefined when the stack carries no restriction.
     * @zh 禁破坏限制接受的方块标识列表；物品堆不带限制时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<String> getDestroyRestrictions() {
        return stringList(fact("destroyRestrictions"));
    }

    /** The block identifiers the can-place-on restriction accepts, or
     * undefined when the stack carries no restriction.
     * @zh 禁放置限制接受的方块标识列表；物品堆不带限制时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<String> getPlaceRestrictions() {
        return stringList(fact("placeRestrictions"));
    }

    /** Display names of the creative tabs containing this item, in tab
     * order; undefined without the item port or for an unknown item.
     * @zh 包含该物品的创造标签页显示名（按标签页顺序）；无物品端口或未知物品时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<String> getCreativeTab() {
        return ScriptApiEnvironment.adapter().item().creativeTabs(id).orElse(null);
    }

    /** The icon stacks of the creative tabs containing this item, in the
     * same order as {@link #getCreativeTab()}; undefined without the item
     * port or for an unknown item.
     * @zh 包含该物品的创造标签页图标堆（与 creativeTab 同序）；无物品端口或未知物品时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<ItemStack> getGroupIcon() {
        return ScriptApiEnvironment.adapter().item().groupIcons(id)
                .map(list -> {
                    List<ItemStack> out = new ArrayList<>();
                    for (ink.tuanzi.neomacros.port.model.ItemStackData data : list) {
                        out.add(of(data));
                    }
                    return out;
                })
                .orElse(null);
    }

    /** The full registry identifiers of the item tags this item belongs
     * to; undefined without the item port or for an unknown item.
     * @zh 该物品所属全部物品标签的完整标识；无物品端口或未知物品时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<String> getTags() {
        return ScriptApiEnvironment.adapter().item().itemTags(id).orElse(null);
    }

    /** A fresh default stack of this item (count 1), or undefined without
     * the item port or for an unknown item.
     * @zh 该物品的新默认物品堆（数量 1）；无物品端口或未知物品时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public ItemStack getDefaultStack() {
        return ScriptApiEnvironment.adapter().item().defaultStack(id)
                .map(ItemStack::of).orElse(null);
    }

    /** The crafting remainder of this item's type (one-count stack), or
     * undefined when the item has no remainder.
     * @zh 该物品的合成剩余物（数量 1）；物品无剩余物时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public ItemStack getRecipeRemainder() {
        return ScriptApiEnvironment.adapter().item().recipeRemainder(id)
                .map(ItemStack::of).orElse(null);
    }

    /** Whether this item's type has a crafting remainder; undefined
     * without the item port or for an unknown item.
     * @zh 该物品类型是否有合成剩余物；无物品端口或未知物品时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean hasRecipeRemainder() {
        return ScriptApiEnvironment.adapter().item().hasRecipeRemainder(id).orElse(null);
    }

    /** The main-hand attack damage this item yields with the local
     * player's base attribute; undefined without a player.
     * @zh 该物品配合本地玩家基础属性的主手攻击伤害；无玩家时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Double getAttackDamage() {
        return ScriptApiEnvironment.adapter().item().attackDamage(id).orElse(null);
    }

    /** Whether the local player currently has a cooldown for this item's
     * cooldown group; undefined without a player.
     * @zh 本地玩家当前是否处于该物品的冷却组冷却中；无玩家时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isOnCooldown() {
        return ScriptApiEnvironment.adapter().item().onCooldown(id).orElse(null);
    }

    /** The remaining cooldown fraction (1 at the start, 0 when expired or
     * absent); undefined without a player.
     * @zh 冷却剩余比例（开始时为 1，到期或无冷却为 0）；无玩家时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Float getCooldownProgress() {
        return ScriptApiEnvironment.adapter().item().cooldownProgress(id).orElse(null);
    }

    /**
     * Whether this item is the correct tool for drops against the given
     * block or block state (a {@code Block} answers against its default
     * state); undefined when the target cannot be resolved.
     *
     * @zh 给定方块或方块状态下该物品是否为正确的掉落工具（Block 按其默认状态判定）；目标无法解析时为 undefined。
     * @zh-param blockOrState 方块句柄或方块状态
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean isCorrectToolForDrops(@ApiType("Block | BlockState") Object blockOrState) {
        BlockState state = resolveState(blockOrState);
        if (state == null) {
            return null;
        }
        return ScriptApiEnvironment.adapter().item()
                .correctToolForDrops(id, state.getId(), state.getProps()).orElse(null);
    }

    /**
     * The mining-speed multiplier this item yields against the given block
     * or block state (a {@code Block} answers against its default state);
     * undefined when the target cannot be resolved.
     *
     * @zh 给定方块或方块状态下该物品的挖掘速度倍率（Block 按其默认状态判定）；目标无法解析时为 undefined。
     * @zh-param blockOrState 方块句柄或方块状态
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Float getDestroySpeed(@ApiType("Block | BlockState") Object blockOrState) {
        BlockState state = resolveState(blockOrState);
        if (state == null) {
            return null;
        }
        return ScriptApiEnvironment.adapter().item()
                .destroySpeed(id, state.getId(), state.getProps()).orElse(null);
    }

    /**
     * Whether a stack of the material item repairs stacks of this item at
     * an anvil; undefined when either side cannot be resolved.
     *
     * @zh 材料物品堆是否能在铁砧上修复本物品堆；任一侧无法解析时为 undefined。
     * @zh-param material 可能的修复材料物品堆
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean canBeRepairedWith(ItemStack material) {
        if (material == null) {
            throw new IllegalArgumentException("material stack is required");
        }
        return ScriptApiEnvironment.adapter().item()
                .canBeRepairedWith(id, material.getId()).orElse(null);
    }

    /** The food component of this stack's item type (hunger, saturation,
     * always-edible); undefined for a non-food item or an unknown item.
     * @zh 该物品堆所属物品类型的食物组件（饥饿、饱和度、可随时食用）；非食物物品或未知物品为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public ink.tuanzi.neomacros.port.model.FoodData getFood() {
        return ScriptApiEnvironment.adapter().item().food(id).orElse(null);
    }

    /** The enchantments that can be applied to this item type (level 0),
     * in registry order; undefined without the item port or for an
     * unknown item.
     * @zh 可应用于该物品类型的附魔（等级 0），按注册表顺序；无物品端口或物品未知时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<Enchantment> getPossibleEnchantments() {
        return possibleEnchantments(false);
    }

    /** The subset of {@link #getPossibleEnchantments()} offered by an
     * enchanting table (level 0), in registry order; undefined without the
     * item port or for an unknown item.
     * @zh {@link #getPossibleEnchantments()} 中附魔台会给出的子集（等级 0），按注册表顺序；无物品端口或物品未知时为 undefined。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<Enchantment> getPossibleEnchantmentsFromTable() {
        return possibleEnchantments(true);
    }

    /**
     * Whether the given enchantment fits this stack: its item type is
     * supported and it combines with every enchantment already present;
     * undefined when either side cannot be resolved.
     *
     * @zh 给定附魔是否适配本物品堆：物品类型受支持且与已有全部附魔可叠加；任一侧无法解析时为 undefined。
     * @zh-param enchantment 要检查的附魔
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Boolean canBeApplied(Enchantment enchantment) {
        if (enchantment == null) {
            return null;
        }
        return enchantment.canBeApplied(this);
    }

    /** A builder draft seeded with this stack, for component editing; the
     * draft is local data and enters the world only through a privileged
     * creative write.
     * @zh 以本物品堆为底稿的构建器草稿（用于组件编辑）；草稿是本地数据，只能经特权创造写入进入世界。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public ItemStackBuilder getCreative() {
        return new ItemStackBuilder(id).copyFrom(this);
    }

    /** Resolves one possible-enchantment query through the item port. */
    private List<Enchantment> possibleEnchantments(boolean enchantingTable) {
        List<String> ids = ScriptApiEnvironment.adapter().item()
                .possibleEnchantments(id, enchantingTable).orElse(null);
        if (ids == null) {
            return null;
        }
        List<Enchantment> out = new ArrayList<>(ids.size());
        for (String enchantmentId : ids) {
            out.add(new Enchantment(enchantmentId, 0));
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
    public boolean isSameItem(@ApiType("ItemStack") Object other) {
        String otherId = itemIdOf(other);
        return otherId != null && id.equals(otherId);
    }

    /** Whether the other stack is a full copy of this one.
     * @zh 另一物品堆是否与本堆完全相同。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public boolean same(@ApiType("ItemStack") Object other) {
        return equals(other);
    }

    /** JSON string so {@code JSON.stringify} renders the stack directly.
     * @zh JSON 字符串，使 {@code JSON.stringify} 直接输出物品堆。 */
    @ApiSince("0.3.0")
    @ApiLoader("*")
    @ApiMC("*")
    public String toJSON() {
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
            List<Map<String, Object>> entries = new ArrayList<>();
            for (ink.tuanzi.neomacros.port.model.EnchantmentData entry : enchantments) {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("id", entry.id());
                view.put("level", entry.level());
                entries.add(view);
            }
            out.put("enchantments", entries);
        }
        return ink.tuanzi.neomacros.core.util.JsonLite.write(out);
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
}
