package ink.tuanzi.neomacros.port;

import ink.tuanzi.neomacros.port.model.DrawContext2d;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * HUD overlay rendering: z-ordered layers drawn on top of the vanilla HUD.
 *
 * <p>Layers register with a stable identifier for replay and z-order
 * management. The render callback fires every frame on the game main
 * thread. Implementations must be safe for concurrent use: the facade
 * hops registration/removal/z changes onto the main thread, while element
 * state inside a painter may be published from any script thread.</p>
 */
public interface RenderPort {

    /**
     * Registers (or re-registers) a HUD layer at the given z-order. Layers
     * render after the vanilla HUD; higher z renders on top. Re-registering
     * the same id replaces its painter and z without creating a second
     * backing element. The painter is a host-side (core) callback invoked on
     * the game main thread once per frame with a tick count and a neutral
     * draw context; it is never a script function.
     *
     * @param id stable identifier for this layer (replay/management)
     * @param z z-order (higher renders on top)
     * @param painter draw callback (every frame, main thread); first arg =
     *                tick count, second arg = draw context
     */
    void addLayer(String id, int z, BiConsumer<Integer, DrawContext2d> painter);

    /** Removes a previously registered layer; unknown ids are ignored. */
    void removeLayer(String id);

    /**
     * Changes a registered layer's z-order without re-registering it or
     * disturbing its painter. Unknown ids are ignored. Implementations must
     * keep this safe to call from any thread and apply the new order to the
     * next frame.
     *
     * @param id stable layer identifier from {@link #addLayer}
     * @param z new z-order (higher renders on top)
     */
    void setLayerZ(String id, int z);

    /** Returns all registered layer identifiers in render order. */
    Supplier<List<String>> layerIds();

    /**
     * Creates or replaces a GPU-resident texture from ARGB pixels. The full
     * texture is re-uploaded on every call, so callers batch their drawing
     * and upload once afterwards. Registration and upload happen on the game
     * main thread; the returned future settles there. Re-registering an
     * identifier replaces the backing texture without duplicating it.
     *
     * <p>The implementation takes ownership of {@code argb}: the caller must
     * not mutate the array after handing it over. Pixels are row-major
     * {@code width * height} entries in ARGB order.</p>
     *
     * @param id     texture identifier ({@code namespace:path}, e.g.
     *               {@code neomacros:image/badge})
     * @param width  texture width in pixels (positive)
     * @param height texture height in pixels (positive)
     * @param argb   row-major ARGB pixel snapshot
     * @return future completing once the texture is registered and uploaded
     */
    default CompletableFuture<Void> putTexture(String id, int width, int height, int[] argb) {
        return CompletableFuture.failedFuture(new UnsupportedPortFeature("texture"));
    }

    /**
     * Returns every texture identifier registered through
     * {@link #putTexture} on this port, in registration order.
     *
     * <p>Defaulted so adapters written before the texture face keep
     * compiling and answer with an empty list.</p>
     */
    default Supplier<List<String>> textureIds() {
        return java.util.List::of;
    }

    /**
     * Font width of one string in scaled pixels — the HUD text metric the
     * {@code Draw2DText} component caches when its text is set (G-001 §5.2;
     * the measurement runs off the render path, the same way
     * {@code ChatPort.textWidth} already reads the font). The default is
     * the neutral {@code length * 6} estimate so adapters written before
     * the metric keep compiling (and the E2E stub stays assertion-stable);
     * the Fabric adapter overrides it with the real font measurement.
     *
     * @param text the string to measure (null measures as empty)
     * @return the advance width in scaled pixels
     * @zh 字体宽度度量（缩放像素）——{@code Draw2DText} 组件在文本被设置时缓存该度量（G-001 §5.2；度量发生在渲染路径之外，与既有 {@code ChatPort.textWidth} 读字体同先例）。默认为中立的 {@code length * 6} 估算，使先于该面编写的适配器保持可编译（E2E 桩的断言因此稳定）；Fabric 适配器以真实字体度量覆写。
     * @zh-param text 待度量字符串（null 按空串度量）
     * @zh-return 缩放像素下的前进宽度
     */
    default int textWidth(String text) {
        return text == null ? 0 : text.length() * 6;
    }

    /**
     * The font line height in scaled pixels — the unscaled height of a
     * {@code Draw2DText} element (G-001 §5.2). Defaulted to the vanilla
     * default-font line height so adapters written before the metric keep
     * compiling; the Fabric adapter overrides it with the live font.
     *
     * @return one text line's height in scaled pixels
     * @zh 字体行高（缩放像素）——{@code Draw2DText} 元素的未缩放高度（G-001 §5.2）。默认取原版默认字体行高，使先于该面编写的适配器保持可编译；Fabric 适配器以实时字体覆写。
     * @zh-return 单行文本高度（缩放像素）
     */
    default int textLineHeight() {
        return 8;
    }
}
