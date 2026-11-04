package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.SelfConstructing;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builder for local {@link ItemStack} values; the single construction path
 * for script-made stacks. The product is local data: it enters the world only
 * through privileged writes (creative inventory) or stays comparison/display
 * data — survival mode has no "materialize items" path (server authority).
 * @zh 本地 {@link ItemStack} 的构建器；脚本造物的唯一构造路径。产物是本地数据——进入世界只能靠特权写（创造物品栏）或仅作比较/展示——生存模式没有"凭空给物品"的路径（服务器权威）。
 */
@ApiClass(name = "ItemStackBuilder", kind = ApiClassKind.DATA)
@ApiMC("*")
public final class ItemStackBuilder implements ScriptHandle, SelfConstructing {

    private final String id;
    private final List<ItemStack.Enchantment> enchantments = new ArrayList<>();
    private final List<String> lore = new ArrayList<>();
    private int count = 1;
    private String name;
    private int damage;
    private int maxDamage;
    private boolean unbreakable;

    /** Creates the builder for one item id (namespace optional).
     * @zh 为某物品 id 创建构建器（命名空间可省略）。 */
    @ApiSince("0.2.0")
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
    @ApiMC("*")
    public ItemStackBuilder count(int size) {
        this.count = size;
        return this;
    }

    /** Sets the current durability damage.
     * @zh 设置当前耐久损伤。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public ItemStackBuilder damage(int value) {
        this.damage = value;
        return this;
    }

    /** Sets the maximum durability.
     * @zh 设置最大耐久。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public ItemStackBuilder maxDamage(int value) {
        this.maxDamage = value;
        return this;
    }

    /** Sets a custom display name.
     * @zh 设置自定义显示名。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public ItemStackBuilder name(String value) {
        this.name = value;
        return this;
    }

    /** Appends lore lines (varargs form).
     * @zh 追加 Lore 行（变参形态）。 */
    @ApiSince("0.2.0")
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
    @ApiMC("*")
    public ItemStackBuilder enchant(String enchantId, int level) {
        this.enchantments.add(new ItemStack.Enchantment(ItemStack.namespaced(enchantId), level));
        return this;
    }

    /** Marks the stack unbreakable (default true when called without arguments).
     * @zh 标记为不可破坏（无参调用默认 true）。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public ItemStackBuilder unbreakable() {
        return unbreakable(true);
    }

    /** Marks the stack unbreakable on or off.
     * @zh 标记不可破坏的开或关。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public ItemStackBuilder unbreakable(boolean on) {
        this.unbreakable = on;
        return this;
    }

    /** Copies all fields from an existing stack as the base draft, then lets
     * chained calls override.
     * @zh 以现有物品堆为底稿拷贝全部字段，随后链式调用逐项覆盖。 */
    @ApiSince("0.2.0")
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
        for (Map<String, Object> entry : stack.getEnchantments()) {
            this.enchantments.add(new ItemStack.Enchantment(
                    String.valueOf(entry.get("id")),
                    entry.get("level") instanceof Number n ? n.intValue() : 0));
        }
        return this;
    }

    /** Validates and produces the immutable stack.
     * @zh 校验并产出不可变物品堆。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    public ItemStack build() {
        if (id == null) {
            throw new IllegalStateException("item id is required");
        }
        return new ItemStack(id, count, name == null ? "" : name, damage, maxDamage,
                lore, enchantments, unbreakable);
    }

    @Override
    public String toString() {
        return "ItemStackBuilder(" + id + ")";
    }

    @Override
    public Object construct(Object[] args) {
        String itemId = args != null && args.length > 0 && args[0] != null
                ? args[0].toString() : null;
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("item id is required");
        }
        return new ItemStackBuilder(itemId);
    }
}
