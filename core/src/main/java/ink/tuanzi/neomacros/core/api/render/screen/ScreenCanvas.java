package ink.tuanzi.neomacros.core.api.render.screen;

import ink.tuanzi.neomacros.port.model.DrawContext2d;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Short-lived drawing context handed to a screen's {@code onRender}
 * callback: four immediate-mode primitives (text/rect/outline/sprite)
 * straight onto the frame's neutral {@link DrawContext2d}, drawn above the
 * widgets. The canvas exists only for the duration of one callback — use
 * after it returns throws — and exposes no OpenGL or pipeline types to
 * scripts.
 *
 * <p>Colors are ARGB numbers; sprite ids are {@code namespace:path}.</p>
 *
 * @zh 交给屏幕 {@code onRender} 回调的短命绘图上下文：四个立即模式原语（text/rect/outline/sprite）直通当帧中立 {@link DrawContext2d}，画在控件之上。画布只在一次回调期间有效——回调返回后调用即 throw；不向脚本暴露任何 OpenGL/管线类型。颜色为 ARGB 数字，sprite id 为 {@code namespace:path}。
 */
@ApiClass(name = "ScreenCanvas", kind = ApiClassKind.HANDLE,
        wraps = "ink.tuanzi.neomacros.port.model.DrawContext2d")
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public final class ScreenCanvas implements ink.tuanzi.neomacros.core.api.ScriptHandle {

    private final DrawContext2d delegate;
    private volatile boolean active = true;

    /** Wraps one per-frame drawing delegate (internal). */
    public ScreenCanvas(DrawContext2d delegate) {
        this.delegate = delegate;
    }

    /** Ends the canvas lifetime; called by the facade after the callback. */
    void revoke() {
        active = false;
    }

    /** Draws a text string at (x, y) in white.
     * @zh 在 (x, y) 以白色绘制文本。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public void text(int x, int y, String text) {
        ensureActive();
        delegate.text(x, y, text);
    }

    /** Draws a text string at (x, y) with an ARGB color.
     * @zh 在 (x, y) 以 ARGB 颜色绘制文本。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public void text(int x, int y, String text, int color) {
        ensureActive();
        delegate.text(x, y, text, color);
    }

    /** Fills a rectangle (x, y, width, height) with an ARGB color.
     * @zh 以 ARGB 颜色填充矩形 (x, y, width, height)。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public void rect(int x, int y, int width, int height, int color) {
        ensureActive();
        delegate.rect(x, y, width, height, color);
    }

    /** Draws a 1-pixel rectangle outline.
     * @zh 绘制 1 像素矩形描边。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public void outline(int x, int y, int width, int height, int color) {
        ensureActive();
        delegate.outline(x, y, width, height, color);
    }

    /** Draws a texture scaled to the rectangle ({@code namespace:path}).
     * @zh 将纹理按矩形缩放绘制（{@code namespace:path}）。 */
    @ApiSince("0.5.0")
    @ApiLoader("*")
    @ApiMC("*")
    public void sprite(int x, int y, int width, int height, String textureId) {
        ensureActive();
        delegate.sprite(x, y, width, height, textureId);
    }

    private void ensureActive() {
        if (!active) {
            throw new IllegalStateException(
                    "ScreenCanvas is only valid inside the onRender callback");
        }
    }
}
