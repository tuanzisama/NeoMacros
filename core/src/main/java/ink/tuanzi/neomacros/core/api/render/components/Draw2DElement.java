package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

import java.util.function.BooleanSupplier;

/**
 * Handle to one element living inside a HUD layer. Obtained from
 * {@code layer.text(...)} / {@code layer.rect(...)} upserts and from
 * {@code layer.element(id)} / {@code layer.elements}.
 *
 * <p>This wave lands the minimal container-side identity face only: the
 * element id and the disposal flag. Geometry, fluent setters, scale /
 * rotation, alignment and the {@code layer}/{@code parent} back-references
 * belong to the Draw2DElement base-class task (G-032B); component-specific
 * properties (text, color, texture, stack) belong to the component tasks
 * (G-034 / G-034B).</p>
 *
 * <p>A handle turns disposed when its element is removed, replaced by a
 * different component kind, cleared from the layer, or the layer itself is
 * removed; reading {@link #getId()} afterwards yields {@code undefined}.</p>
 *
 * @zh HUD 图层内单个元素的句柄，由 {@code layer.text(...)} / {@code layer.rect(...)} upsert 与 {@code layer.element(id)} / {@code layer.elements} 返回。
 * @zh 本波只落容器侧的最小标识面：元素 id 与失效标记。几何、fluent setter、缩放/旋转、对齐与 {@code layer}/{@code parent} 反向引用归 Draw2DElement 基类任务（G-032B）；组件专有属性（文本、颜色、纹理、物品栈）归组件任务（G-034 / G-034B）。
 * @zh 元素被移除、被不同组件类型替换、随层 clear 或整层移除后句柄即失效；此后读取 {@link #getId()} 返回 {@code undefined}。
 */
@ApiClass(name = "Draw2DElement", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public final class Draw2DElement implements ScriptHandle {

    private final String id;
    private final BooleanSupplier disposed;

    /**
     * Host-side constructor wiring the handle to its scene cell; scripts
     * receive instances from the layer face and never build their own.
     *
     * @param id       element id inside the owning layer
     * @param disposed live disposal probe (cell removed or scene disposed)
     */
    public Draw2DElement(String id, BooleanSupplier disposed) {
        this.id = id;
        this.disposed = disposed;
    }

    /**
     * The element id inside its layer; {@code undefined} once disposed.
     *
     * @zh 元素在其层内的 id；失效后为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String getId() {
        return disposed.getAsBoolean() ? null : id;
    }

    /**
     * Whether this handle has been invalidated (element removed, replaced,
     * cleared, or the layer disposed). Always readable.
     *
     * @zh 句柄是否已失效（元素被移除、替换、clear 或整层失效）。永远可读。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isDisposed() {
        return disposed.getAsBoolean();
    }
}
