package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.core.api.render.HudLayer;
import ink.tuanzi.neomacros.core.api.types.ItemStack;
import ink.tuanzi.neomacros.core.api.types.ItemStackBuilder;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.model.ItemStackData;

/**
 * Handle to one item-icon element living inside a HUD layer, obtained
 * from {@code layer.item(id, …)} upserts. The G-034B component face of
 * {@link Draw2DElement}: the stack view ({@code setItem}/{@code item})
 * and the game decorations ({@code overlay} plus the overlay label) —
 * the fixed 16-by-16 box, transform, z-order, alignment and lifecycle
 * are inherited without re-declaration (G-034C contract).
 *
 * <p><b>Box and scale</b>: the element box is the icon's 16-by-16
 * GUI-pixel footprint (seeded by the upsert), so {@code getScaledWidth}
 * answers {@code 16 &times; scale}; the icon draws
 * through the neutral {@code item} primitive, decorations off unless
 * {@link #setOverlay(boolean)} enables them.</p>
 *
 * <p><b>Element is its own builder</b> (拍板②): every setter is fluent
 * and returns this handle.</p>
 *
 * @zh HUD 图层内单个物品图标元素的句柄，由 {@code layer.item(id, …)} upsert 返回。{@link Draw2DElement} 的 G-034B 组件面：堆视图（{@code setItem}/{@code item}）与游戏装饰（{@code overlay} 加覆盖标签）——固定 16×16 盒、变换、z 序、对齐、生命周期全部继承且不重声明（G-034C 契约）。
 * @zh <b>盒与缩放</b>：元素盒为图标的 16×16 GUI 像素占位（由 upsert 播种），因此 {@code getScaledWidth} 返回 {@code 16 × scale}；图标经中立 {@code item} 原语绘制，装饰默认关闭，除非 {@link #setOverlay(boolean)} 开启。
 * @zh <b>元素即 Builder</b>（拍板②）：全部 setter 为 fluent 并返回本句柄。
 */
@ApiClass(name = "Draw2DItem", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public class Draw2DItem extends Draw2DElement {

    /**
     * Host-side constructor wiring the handle to its scene cell; scripts
     * receive instances from {@code layer.item(...)} only.
     *
     * @param id      element id inside the owning layer
     * @param layer   the layer hosting this element (v1 parent box)
     * @param backend scene bridge publishing and reading the element state
     * @zh 宿主侧构造：把句柄接到其场景单元；脚本只从 {@code layer.item(...)} 获得实例。
     * @zh-param id 元素在其层内的 id
     * @zh-param layer 承载本元素的层（v1 父盒）
     * @zh-param backend 发布与读取元素状态的场景桥
     */
    public Draw2DItem(String id, HudLayer layer, Backend backend) {
        super(id, layer, backend);
    }

    /**
     * Replaces the drawn stack, or clears the icon when the argument is
     * {@code null}. Folded member (G-033 overload-fold rule — one script
     * binding per name): an {@link ItemStack} is stored as its neutral
     * snapshot; a string is the item id whose default stack is built with
     * the given {@code count} (default {@code 1}, a bare name takes the
     * {@code minecraft} namespace — unknown ids draw nothing, the air
     * stack the registry answers).
     *
     * @param stackOrId an {@link ItemStack}, an item id, or {@code null} for nothing
     * @param count     stack size for the item-id form (default {@code 1})
     * @return this element for chaining
     * @zh 替换绘制的堆，参数为 {@code null} 时清除图标。折叠成员（G-033 重载折叠铁律——每名一个脚本绑定）：{@link ItemStack} 按其中立快照存储；字符串为物品 id，按给定 {@code count}（默认 {@code 1}，裸名取 {@code minecraft} 命名空间）构建默认堆——未知 id 绘制注册表回答的空气堆（不画）。
     * @zh-param stackOrId {@link ItemStack}、物品 id，或 {@code null} 表示无
     * @zh-param count 物品 id 形的堆数量（默认 {@code 1}）
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DItem setItem(
            @ApiType("ItemStack | string") Object stackOrId,
            @ApiOptional Object count) {
        requireAlive();
        backend().publish(backend().spec().withStack(
                resolveStack(stackOrId, count)));
        return this;
    }

    /**
     * The stack this icon draws, as a fresh {@link ItemStack} snapshot;
     * {@code undefined} when no stack is set or once disposed.
     *
     * @zh 本图标绘制的堆，以新建的 {@link ItemStack} 快照返回；未设置堆或失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public ItemStack getItem() {
        if (backend().disposed()) {
            return null;
        }
        ItemStackData data = backend().spec().stack();
        return data == null ? null : ItemStack.of(data);
    }

    /**
     * Enables or disables the game decorations (durability bar, cooldown
     * overlay, and stack count or the custom label); off by default.
     *
     * @param overlay {@code true} to draw the decorations
     * @return this element for chaining
     * @zh 开启或关闭游戏装饰（耐久条、冷却覆盖与堆数量或自定义标签）；默认关闭。
     * @zh-param overlay {@code true} 表示绘制装饰
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DItem setOverlay(boolean overlay) {
        requireAlive();
        backend().publish(backend().spec().withOverlay(overlay));
        return this;
    }

    /**
     * Whether the game decorations are enabled — a method, not a
     * property ({@code should*} stays a method name, the {@code hasShadow}
     * precedent); {@code undefined} once disposed.
     *
     * @zh 游戏装饰是否开启——是方法而非属性（{@code should*} 保持方法名，{@code hasShadow} 先例）；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Boolean shouldShowOverlay() {
        return backend().disposed() ? null : backend().spec().overlay();
    }

    /**
     * Sets the label drawn over the icon; this does not enable the
     * overlay (the Builder fold enables it alongside). A non-null value
     * replaces the stack count when the overlay is on; {@code null} falls
     * back to the count, an empty string draws no label.
     *
     * @param overlayText the label, or {@code null} for the stack count
     * @return this element for chaining
     * @zh 设置绘制在图标上的标签；本方法不开启覆盖层（Builder 折叠会同时开启）。覆盖层开启时非 null 值替代堆数量；{@code null} 回退为数量，空串不绘制标签。
     * @zh-param overlayText 标签，或 {@code null} 表示堆数量
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DItem setOverlayText(String overlayText) {
        requireAlive();
        backend().publish(backend().spec().withOverlayText(overlayText));
        return this;
    }

    /**
     * The overlay label; {@code undefined} when none is set or once
     * disposed.
     *
     * @zh 覆盖标签；未设置或失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String getOverlayText() {
        return backend().disposed() ? null : backend().spec().overlayText();
    }

    // ---- host-side setItem overloads: the folded member above owns the
    // ---- script surface (one member per name); these mirror the task-card
    // ---- call shapes for Java callers and stay off it.

    /**
     * Java-side shortcut for {@link #setItem(Object, Object)} with a stack;
     * hidden from the script surface.
     *
     * @zh {@link #setItem(Object, Object)} 的 Java 捷径（堆形），不进脚本面。
     */
    @ApiInternal
    public Draw2DItem setItem(ItemStack stack) {
        return setItem((Object) stack, null);
    }

    /**
     * Java-side shortcut for {@link #setItem(Object, Object)} with an item
     * id and a count; hidden from the script surface.
     *
     * @zh {@link #setItem(Object, Object)} 的 Java 捷径（物品 id + 数量），不进脚本面。
     */
    @ApiInternal
    public Draw2DItem setItem(String id, int count) {
        return setItem((Object) id, (Object) count);
    }

    /** Resolves the folded {@code setItem} arguments to a neutral stack. */
    private static ItemStackData resolveStack(Object stackOrId, Object count) {
        if (stackOrId == null) {
            return null;
        }
        if (stackOrId instanceof ItemStack stack) {
            return stack.toData();
        }
        if (stackOrId instanceof String id) {
            int size;
            if (count == null) {
                size = 1;
            } else if (count instanceof Number number) {
                size = number.intValue();
            } else {
                throw new IllegalArgumentException(
                        "count must be a number: " + count);
            }
            return new ItemStackBuilder(id).count(size).build().toData();
        }
        throw new IllegalArgumentException(
                "expected an ItemStack or an item id: " + stackOrId);
    }

    /** Throws when the handle is no longer valid; used by every mutator. */
    private void requireAlive() {
        if (backend().disposed()) {
            throw new IllegalStateException("element no longer valid");
        }
    }
}
