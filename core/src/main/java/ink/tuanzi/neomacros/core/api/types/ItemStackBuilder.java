package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.SelfConstructing;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Builder for local {@link ItemStack} values; the single construction path
 * for script-made stacks. The product is local data: it enters the world only
 * through privileged writes (creative inventory) or stays comparison/display
 * data — survival mode has no "materialize items" path (server authority).
 * @zh 本地 {@link ItemStack} 的构建器；脚本造物的唯一构造路径。产物是本地数据——进入世界只能靠特权写（创造物品栏）或仅作比较/展示——生存模式没有"凭空给物品"的路径（服务器权威）。
 * @zh-example const stack = new ItemStackBuilder("diamond_sword")
 *   .name("Excalibur")
 *   .enchant("minecraft:sharpness", 5)
 *   .unbreakable()
 *   .count(1)
 *   .build();
 */
@ApiClass(name = "ItemStackBuilder", kind = ApiClassKind.DATA)
@ApiSince("0.2.0")
@ApiLoader("*")
@ApiMC("*")
public final class ItemStackBuilder implements ScriptHandle, SelfConstructing {

    private final String id;
    private final List<ink.tuanzi.neomacros.port.model.EnchantmentData> enchantments = new ArrayList<>();
    private final List<String> lore = new ArrayList<>();
    private int count = 1;
    private String name;
    private int damage;
    private int maxDamage;
    private boolean unbreakable;
    private final Map<String, Object> baseExtras = new LinkedHashMap<>();

    /** Creates the builder for one item id (namespace optional).
     * @zh 为某物品 id 创建构建器（命名空间可省略）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ItemStackBuilder(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("item id is required");
        }
        this.id = ItemStack.namespaced(itemId);
    }

    /** Creates the builder (engine factory instantiation; id resolved from args).
     * @zh 创建构建器（引擎工厂实例化用；id 取自参数）。 */
    public ItemStackBuilder() {
        this.id = null;
    }

    /** Sets the stack size.
     * @zh 设置物品数量。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ItemStackBuilder count(int size) {
        this.count = size;
        return this;
    }

    /** Sets the current durability damage.
     * @zh 设置当前耐久损伤。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ItemStackBuilder damage(int value) {
        this.damage = value;
        return this;
    }

    /** Sets the maximum durability.
     * @zh 设置最大耐久。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ItemStackBuilder maxDamage(int value) {
        this.maxDamage = value;
        return this;
    }

    /** Sets a custom display name.
     * @zh 设置自定义显示名。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ItemStackBuilder name(String value) {
        this.name = value;
        return this;
    }

    /** Appends lore lines (varargs form).
     * @zh 追加 Lore 行（变参形态）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ItemStackBuilder lore(String... lines) {
        for (String line : lines) {
            this.lore.add(line);
        }
        return this;
    }

    /** Replaces lore lines from an array form.
     * @zh 以数组形态替换 Lore 行。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ItemStackBuilder loreLines(List<String> lines) {
        this.lore.clear();
        if (lines != null) {
            this.lore.addAll(lines);
        }
        return this;
    }

    /** Adds one enchantment (chainable, repeats allowed).
     * @zh 添加一条附魔（可链式重复）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ItemStackBuilder enchant(String enchantId, int level) {
        this.enchantments.add(new ink.tuanzi.neomacros.port.model.EnchantmentData(
                ItemStack.namespaced(enchantId), level));
        return this;
    }

    /** Marks the stack unbreakable (default true when called without arguments).
     * @zh 标记为不可破坏（无参调用默认 true）。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ItemStackBuilder unbreakable() {
        return unbreakable(true);
    }

    /** Marks the stack unbreakable on or off.
     * @zh 标记不可破坏的开或关。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ItemStackBuilder unbreakable(boolean on) {
        this.unbreakable = on;
        return this;
    }

    // ---- creative write face ----
    // Every write applies to the draft immediately and settles with the
    // builder itself, so `await` keeps the chain alive.

    /** Sets the remaining durability (the item's maximum damage minus this
     * value); items without maximum damage take no damage.
     * @zh 设置剩余耐久（物品最大耐久减去该值）；无最大耐久的物品不受损伤。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<ItemStackBuilder>")
    public CompletableFuture<ItemStackBuilder> setDurability(int durability) {
        this.damage = this.maxDamage > 0 ? Math.max(0, this.maxDamage - durability) : 0;
        return CompletableFuture.completedFuture(this);
    }

    /**
     * Adds or upgrades one enchantment: an identifier with an explicit
     * level, or an {@link Enchantment} handle carrying its own level.
     * Existing entries of the same enchantment are replaced.
     *
     * @zh 添加或升级一条附魔：带显式等级的标识，或自带等级的 {@link Enchantment} 句柄；同名附魔的既有条目会被替换。
     * @zh-param enchantment 附魔句柄或附魔标识
     * @zh-param level 等级（传入附魔句柄时可省略）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<ItemStackBuilder>")
    public CompletableFuture<ItemStackBuilder> addEnchantment(
            @ApiType("Enchantment | string") Object enchantment,
            @ApiOptional Integer level) {
        if (enchantment == null) {
            throw new IllegalArgumentException("enchantment is required");
        }
        String enchantId;
        int resolved;
        if (enchantment instanceof Enchantment handle) {
            enchantId = handle.getId();
            resolved = level == null ? handle.getLevel() : level;
        } else if (enchantment instanceof String text) {
            if (level == null) {
                throw new IllegalArgumentException("level is required for an enchantment id");
            }
            enchantId = ItemStack.namespaced(text);
            resolved = level;
        } else {
            throw new IllegalArgumentException("expected Enchantment or enchantment id");
        }
        this.enchantments.removeIf(entry -> entry.id().equals(enchantId));
        this.enchantments.add(new ink.tuanzi.neomacros.port.model.EnchantmentData(
                enchantId, resolved));
        return CompletableFuture.completedFuture(this);
    }

    /** Removes every enchantment from the draft.
     * @zh 移除草稿上的全部附魔。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<ItemStackBuilder>")
    public CompletableFuture<ItemStackBuilder> clearEnchantments() {
        this.enchantments.clear();
        return CompletableFuture.completedFuture(this);
    }

    /**
     * Removes one enchantment, keeping the others.
     *
     * @zh 移除一条附魔，保留其余附魔。
     * @zh-param enchantment 要移除的附魔句柄或附魔标识
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<ItemStackBuilder>")
    public CompletableFuture<ItemStackBuilder> removeEnchantment(
            @ApiType("Enchantment | string") Object enchantment) {
        String enchantId;
        if (enchantment instanceof Enchantment handle) {
            enchantId = handle.getId();
        } else if (enchantment instanceof String text) {
            enchantId = ItemStack.namespaced(text);
        } else if (enchantment == null) {
            throw new IllegalArgumentException("enchantment is required");
        } else {
            throw new IllegalArgumentException("expected Enchantment or enchantment id");
        }
        this.enchantments.removeIf(entry -> entry.id().equals(enchantId));
        return CompletableFuture.completedFuture(this);
    }

    /** Clears every lore line from the draft.
     * @zh 清空草稿上的全部 Lore 行。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<ItemStackBuilder>")
    public CompletableFuture<ItemStackBuilder> clearLore() {
        this.lore.clear();
        return CompletableFuture.completedFuture(this);
    }

    /**
     * Appends one lore line, or every line of an array of lines; values are
     * rendered with their string form (integral numbers without decimals).
     *
     * @zh 追加一行 Lore，或一个数组中的全部 Lore 行；值按其字符串形式渲染（整数不带小数）。
     * @zh-param line 一行文本，或文本/数值数组
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<ItemStackBuilder>")
    public CompletableFuture<ItemStackBuilder> addLore(
            @ApiType("string | number | Array<string | number>") Object line) {
        if (line == null) {
            return CompletableFuture.completedFuture(this);
        }
        if (line instanceof Iterable<?> many) {
            for (Object one : many) {
                if (one != null) {
                    this.lore.add(loreLine(one));
                }
            }
        } else {
            this.lore.add(loreLine(line));
        }
        return CompletableFuture.completedFuture(this);
    }

    /** Renders one lore value the way a guest string conversion would. */
    private static String loreLine(Object value) {
        if (value instanceof Number number) {
            double decimal = number.doubleValue();
            if (!Double.isInfinite(decimal) && decimal == Math.floor(decimal)) {
                return String.valueOf(number.longValue());
            }
        }
        return String.valueOf(value);
    }

    /** Hides or shows the enchantments in the tooltip without changing
     * them; does not remove the enchantments.
     * @zh 在提示框中隐藏或显示附魔（不改变附魔本身），不会移除附魔。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<ItemStackBuilder>")
    public CompletableFuture<ItemStackBuilder> hideEnchantments(boolean hide) {
        return CompletableFuture.completedFuture(hideFlag("enchantmentsHidden", hide));
    }

    /** Hides or shows the attribute modifiers in the tooltip.
     * @zh 在提示框中隐藏或显示属性修饰符。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<ItemStackBuilder>")
    public CompletableFuture<ItemStackBuilder> hideModifiers(boolean hide) {
        return CompletableFuture.completedFuture(hideFlag("modifiersHidden", hide));
    }

    /** Hides or shows the unbreakable flag in the tooltip without changing
     * whether the stack is unbreakable.
     * @zh 在提示框中隐藏或显示不可破坏标记（不改变物品堆是否不可破坏）。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<ItemStackBuilder>")
    public CompletableFuture<ItemStackBuilder> hideUnbreakable(boolean hide) {
        return CompletableFuture.completedFuture(hideFlag("unbreakableHidden", hide));
    }

    /** Hides or shows the can-destroy restriction in the tooltip without
     * changing the restriction itself.
     * @zh 在提示框中隐藏或显示禁破坏限制（不改变限制本身）。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<ItemStackBuilder>")
    public CompletableFuture<ItemStackBuilder> hideCanDestroy(boolean hide) {
        return CompletableFuture.completedFuture(hideFlag("canDestroyHidden", hide));
    }

    /** Hides or shows the can-place-on restriction in the tooltip without
     * changing the restriction itself.
     * @zh 在提示框中隐藏或显示禁放置限制（不改变限制本身）。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<ItemStackBuilder>")
    public CompletableFuture<ItemStackBuilder> hideCanPlace(boolean hide) {
        return CompletableFuture.completedFuture(hideFlag("canPlaceHidden", hide));
    }

    /** Hides or shows the dyed colour in the tooltip without changing the
     * colour itself.
     * @zh 在提示框中隐藏或显示染色（不改变颜色本身）。 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<ItemStackBuilder>")
    public CompletableFuture<ItemStackBuilder> hideDye(boolean hide) {
        return CompletableFuture.completedFuture(hideFlag("dyeHidden", hide));
    }

    /** Writes one tooltip-hidden flag into the draft's fact map. */
    private ItemStackBuilder hideFlag(String key, boolean hide) {
        this.baseExtras.put(key, hide);
        return this;
    }

    /** Copies all fields from an existing stack as the base draft, then lets
     * chained calls override.
     * @zh 以现有物品堆为底稿拷贝全部字段，随后链式调用逐项覆盖。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ItemStackBuilder copyFrom(ItemStack stack) {
        if (stack == null) {
            throw new IllegalArgumentException("stack to copy from is required");
        }
        this.count = stack.getCount();
        this.name = stack.getName();
        this.damage = stack.getDamage();
        this.maxDamage = stack.getMaxDamage();
        this.unbreakable = false;
        this.lore.clear();
        this.lore.addAll(stack.getLore());
        this.enchantments.clear();
        this.enchantments.addAll(stack.enchantmentData());
        this.baseExtras.clear();
        this.baseExtras.putAll(stack.extrasView());
        return this;
    }

    /** Validates and produces the immutable stack.
     * @zh 校验并产出不可变物品堆。 */
    @ApiSince("0.2.0")
    @ApiLoader("*")
    @ApiMC("*")
    public ItemStack build() {
        if (id == null) {
            throw new IllegalStateException("item id is required");
        }
        return new ItemStack(id, count, name == null ? "" : name, damage, maxDamage,
                lore, enchantments, unbreakable, baseExtras);
    }

    @Override
    public String toString() {
        return "ItemStackBuilder(" + id + ")";
    }

    @Override
    @ApiInternal
    public Object construct(Object[] args) {
        String itemId = args != null && args.length > 0 && args[0] != null
                ? args[0].toString() : null;
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("item id is required");
        }
        return new ItemStackBuilder(itemId);
    }
}
