package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;

/**
 * Immutable raster image: dimensions plus PNG-encoded bytes, produced by
 * screenshots (and later by texture drawing). Instances come only from
 * queries; there is no direct constructor. The byte content is compatible
 * with {@code fs.writeBytes}.
 *
 * @zh 不可变位图：尺寸加 PNG 编码字节，由截图（以及后续的纹理绘制）产生。实例只来自查询，不提供直接构造器。字节内容可直接交给 {@code fs.writeBytes}。
 */
@ApiClass(name = "Image", kind = ApiClassKind.DATA)
@ApiSince("0.2.0")
@ApiLoader("*")
@ApiMC("*")
public final class Image implements ScriptHandle {

    private final int width;
    private final int height;
    private final byte[] bytes;

    /** Builds one image from PNG-encoded content (engine-facing). */
    public Image(int width, int height, byte[] bytes) {
        this.width = width;
        this.height = height;
        this.bytes = bytes.clone();
    }

    /** The image width in pixels.
     * @zh 图片宽度（像素）。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public int getWidth() {
        return width;
    }

    /** The image height in pixels.
     * @zh 图片高度（像素）。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public int getHeight() {
        return height;
    }

    /** The PNG-encoded image content.
     * @zh PNG 编码的图片内容。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public byte[] getBytes() {
        return bytes.clone();
    }

    /** Plain-object rendering used by {@code JSON.stringify}. */
    public java.util.Map<String, Object> toJSON() {
        java.util.Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("width", width);
        view.put("height", height);
        view.put("bytes", bytes.length + " bytes");
        return view;
    }

    @Override
    public String toString() {
        return "Image(" + width + "x" + height + ")";
    }
}
