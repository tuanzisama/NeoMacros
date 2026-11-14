package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Ordering contract of a renderable two-dimensional element: one z-index
 * value that decides when the element paints inside its layer — lower
 * values draw first, equal values keep insertion order. This is the
 * element's own ordering, distinct from the layer's ordering against other
 * layers. {@link Draw2DElement} implements this contract; scripts read the
 * value as the {@code zIndex} property of an element handle.
 *
 * <p>A per-frame drawing callback is deliberately not part of this
 * contract: elements publish immutable state and the core painter replays
 * it every frame, so neither a raw graphics type nor a script hop enters
 * the render path.</p>
 *
 * @zh 可绘制二维元素的排序契约：一个 z-index 值决定元素在层内的绘制时机——数值小者先画，同值保持插入序。这是元素自身的排序，区别于层与层之间的排序。{@link Draw2DElement} 实现本契约；脚本以元素句柄的 {@code zIndex} 属性读取该值。
 * @zh 每帧绘制回调刻意不属本契约：元素发布不可变状态、由 core painter 每帧重放，因此原始图形类型与脚本跳转都不进入渲染路径。
 */
public interface RenderElement {

    /**
     * This element's ordering value on its layer: lower values draw first,
     * ties keep insertion order; defaults to {@code 0}.
     *
     * @return the z-index of this element, or {@code null} once the handle
     *         is disposed
     * @zh 元素在层上的绘制序：数值小者先画，同值保持插入序；默认 {@code 0}。
     * @zh-return 元素的 z-index；句柄失效后为 {@code null}
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Integer getZIndex();
}
