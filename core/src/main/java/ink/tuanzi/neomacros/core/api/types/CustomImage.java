package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptApiEnvironment;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.core.api.SelfConstructing;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * A script-owned pixel canvas: an in-memory ARGB image the script draws on
 * and hands to the game as a texture. Drawing calls change only the
 * in-memory pixels; {@link #update()} uploads those pixels to the texture
 * registered under {@link #getIdentifier()}. Updates upload the whole
 * image, so batch several drawing calls and upload once afterwards.
 *
 * <p>Images register under {@code neomacros:image/<name>}; creating another
 * image with the same name reuses that identifier. The script gets one
 * through {@code new CustomImage(width, height, name)} for a blank canvas or
 * {@code new CustomImage(path, name)} for one loaded from a file below the
 * NeoMacros config directory.</p>
 *
 * @zh 脚本自有的像素画布：脚本在其上绘制、再交给游戏作为纹理的内存 ARGB 图像。绘制调用只改动内存像素；{@link #update()} 把像素上传到 {@link #getIdentifier()} 对应的纹理。上传始终是整图，可在多次绘制后批量上传一次。
 * @zh-example const img = new neo.CustomImage(16, 16, "badge");
 *   img.setGraphicsColor(0xFF555555).fillRect(0, 0, 16, 16);
 *   img.setGraphicsColor(0xFFFFFFFF).drawString(2, 11, "hi");
 *   await img.update();
 *   chat.log(`${img.identifier} is ${img.width} by ${img.height}`);
 */
@ApiClass(name = "CustomImage", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiLoader("*")
@ApiMC("*")
public final class CustomImage implements ScriptHandle, SelfConstructing {

    /** Identifier namespace/path prefix; the script name appends to it. */
    private static final String IDENTIFIER_PREFIX = "neomacros:image/";

    /**
     * Sandbox root for {@link #loadImage} and {@link #saveImage}: paths
     * resolve below the NeoMacros config directory (the analog of the
     * JsMacros config folder). Installed once by the bootstrap.
     */
    private static volatile ink.tuanzi.neomacros.core.api.FsApi.FsBridge fileRoot;

    private final String name;
    private final String identifier;
    private final BufferedImage surface;
    private final java.awt.Graphics2D graphics;

    /**
     * Engine factory shell (never handed to scripts): the self-constructing
     * path instantiates this no-arg cell and routes raw guest constructor
     * arguments through {@link #construct}.
     *
     * @zh 引擎工厂壳（不会交给脚本）：自构造路径用此无参实例承载，脚本构造参数经 {@link #construct} 分发。
     */
    public CustomImage() {
        this.name = "";
        this.identifier = "";
        this.surface = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        this.graphics = this.surface.createGraphics();
    }

    private CustomImage(String name, BufferedImage surface) {
        this.name = name;
        this.identifier = IDENTIFIER_PREFIX + name;
        this.surface = surface;
        this.graphics = surface.createGraphics();
        uploadBestEffort();
    }

    /**
     * Installs the file root backing {@link #loadImage} and
     * {@link #saveImage} (bootstrap-provided; config-directory relative).
     *
     * @zh 安装 {@link #loadImage} 与 {@link #saveImage} 的文件根（启动时注入；相对配置目录）。
     */
    @ApiInternal
    public static void installRoot(ink.tuanzi.neomacros.core.api.FsApi.FsBridge bridge) {
        fileRoot = bridge;
    }

    /**
     * Builds one image from raw guest constructor arguments:
     * {@code (width, height, name)} for a blank canvas or {@code (path, name)}
     * for one loaded from a file.
     *
     * @zh 由脚本构造器原始参数构建图像：{@code (width, height, name)} 建空白画布，{@code (path, name)} 从文件加载。
     */
    @Override
    @ApiInternal
    public Object construct(Object[] args) {
        if (args != null && args.length == 3 && args[0] instanceof Number width
                && args[1] instanceof Number height && args[2] instanceof String name) {
            return createBlank(width.intValue(), height.intValue(), name);
        }
        if (args != null && args.length == 2 && args[0] instanceof String path
                && args[1] instanceof String name) {
            return createFromFile(path, name);
        }
        throw new IllegalArgumentException(
                "expected new CustomImage(width, height, name) or new CustomImage(path, name)");
    }

    private static CustomImage createBlank(int width, int height, String name) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "image dimensions must be positive: " + width + "x" + height);
        }
        return new CustomImage(validateName(name),
                new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB));
    }

    private static CustomImage createFromFile(String path, String name) {
        if (path == null) {
            throw new IllegalArgumentException("image path is required");
        }
        BufferedImage loaded = readPng(resolveFile(path));
        if (loaded == null) {
            throw new IllegalArgumentException("file is not a readable image: " + path);
        }
        return new CustomImage(validateName(name), loaded);
    }

    /** Rejects names that would not form a valid texture identifier path. */
    private static String validateName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("image name is required");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '/' || c == '.' || c == '_' || c == '-';
            if (!valid) {
                throw new IllegalArgumentException(
                        "image name must be a lowercase identifier path: " + name);
            }
        }
        return name;
    }

    /** Resolves a script path against the installed config-directory root. */
    private static Path resolveFile(String path) {
        ink.tuanzi.neomacros.core.api.FsApi.FsBridge root = fileRoot;
        if (root == null) {
            throw new IllegalStateException("image file root is not installed");
        }
        if (path == null) {
            throw new IllegalArgumentException("image path is required");
        }
        return root.resolve(path);
    }

    /**
     * Uploads the current pixels at construction time, best-effort: the
     * awaited upload contract is {@link #update()}, and construction keeps
     * working in environments without a render port (tests, embedders).
     */
    private void uploadBestEffort() {
        try {
            ScriptApiEnvironment.adapter().render()
                    .putTexture(identifier, surface.getWidth(), surface.getHeight(), pixels());
        } catch (RuntimeException ignored) {
            // Deferred to update(), which surfaces the failure to the script.
        }
    }

    /** Row-major ARGB snapshot of the whole canvas (fresh array). */
    private int[] pixels() {
        int width = surface.getWidth();
        return surface.getRGB(0, 0, width, surface.getHeight(), null, 0, width);
    }

    private void checkBounds(int x, int y) {
        if (x < 0 || y < 0 || x >= surface.getWidth() || y >= surface.getHeight()) {
            throw new IndexOutOfBoundsException("pixel (" + x + ", " + y + ") outside "
                    + surface.getWidth() + "x" + surface.getHeight());
        }
    }

    /**
     * The registration name supplied at construction, without the
     * {@code neomacros:image/} prefix.
     *
     * @zh 构造时提供的注册名（不含 {@code neomacros:image/} 前缀）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getName() {
        return name;
    }

    /**
     * The registered texture identifier of this image; images with the same
     * name share the identifier.
     *
     * @zh 本图像的纹理标识；同名图像共享同一标识。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getIdentifier() {
        return identifier;
    }

    /**
     * The image width in pixels; fixed at construction.
     *
     * @zh 图像宽度（像素），构造时固定。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public int getWidth() {
        return surface.getWidth();
    }

    /**
     * The image height in pixels; fixed at construction.
     *
     * @zh 图像高度（像素），构造时固定。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public int getHeight() {
        return surface.getHeight();
    }

    /**
     * Uploads the current pixels to this image's texture so changes become
     * visible; the full image is copied and uploaded on the game main
     * thread. Batch drawing changes and call this once afterward.
     *
     * @zh 把当前像素上传到本图像的纹理使改动可见；整图复制并在游戏主线程上传。可批量绘制后只调用一次。
     * @return promise resolving to this image for chaining
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<CustomImage>")
    public CompletableFuture<CustomImage> update() {
        return ScriptApiEnvironment.adapter().render()
                .putTexture(identifier, surface.getWidth(), surface.getHeight(), pixels())
                .thenApply(v -> this);
    }

    /**
     * Saves the current pixels as a PNG below the NeoMacros config
     * directory; {@code .png} is appended to {@code fileName} and an
     * existing file is overwritten. This saves the in-memory image whether
     * or not {@link #update()} has uploaded recent changes.
     *
     * @zh 把当前像素存为配置目录下的 PNG；{@code fileName} 追加 {@code .png}，同名文件被覆盖。无论 {@link #update()} 是否已上传，保存的都是内存图像。
     * @zh-param path 相对配置目录的目录路径
     * @zh-param fileName 不含扩展名的文件名
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage saveImage(String path, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("file name is required");
        }
        Path directory = resolveFile(path);
        Path target = directory.resolve(fileName + ".png");
        try {
            Files.createDirectories(directory);
            ImageIO.write(surface, "png", target.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to save image " + target, e);
        }
        return this;
    }

    /**
     * Loads an image from a path below the NeoMacros config directory;
     * returns {@code undefined} if the file cannot be read or no image
     * reader recognizes it. Pass {@code x}, {@code y}, {@code width} and
     * {@code height} to receive only that crop; a crop running outside the
     * image throws.
     *
     * @zh 从配置目录下的路径加载图片；文件不可读或格式不认识时返回 undefined。传入 {@code x}/{@code y}/{@code width}/{@code height} 可只取该区域；越界裁剪抛错。
     * @zh-param path 相对配置目录的文件路径
     * @param x      crop left (defaults to 0 when cropping)
     * @param y      crop top (defaults to 0 when cropping)
     * @param width  crop width; omit both {@code width} and {@code height}
     *               for the whole image
     * @param height crop height
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Image loadImage(String path, @ApiOptional Integer x, @ApiOptional Integer y,
            @ApiOptional Integer width, @ApiOptional Integer height) {
        BufferedImage source = readPng(resolveFile(path));
        if (source == null) {
            return null;
        }
        if (width == null && height == null) {
            return toImage(source);
        }
        if (width == null || height == null) {
            throw new IllegalArgumentException("a crop needs both width and height");
        }
        int left = x == null ? 0 : x;
        int top = y == null ? 0 : y;
        if (left < 0 || top < 0 || width <= 0 || height <= 0
                || left + width > source.getWidth() || top + height > source.getHeight()) {
            throw new IndexOutOfBoundsException("crop " + left + "," + top + "," + width + "x"
                    + height + " outside " + source.getWidth() + "x" + source.getHeight());
        }
        return toImage(source.getSubimage(left, top, width, height));
    }

    /**
     * Java-side shortcut for the whole-file form; the script face is the
     * single {@code loadImage(path, x?, y?, width?, height?)} member.
     *
     * @zh 整文件形式的 Java 侧捷径；脚本面只有 {@code loadImage(path, x?, y?, width?, height?)} 单成员。
     */
    @ApiInternal
    public Image loadImage(String path) {
        return loadImage(path, null, null, null, null);
    }

    /**
     * A PNG-encoded snapshot of the current pixels; changes made through
     * this image's drawing members are included, changes made to the
     * snapshot are not (use {@link #drawImage} to composite it back).
     *
     * @zh 当前像素的 PNG 编码快照；本图像绘制成员的改动会包含其中，对快照的改动不会反向生效（用 {@link #drawImage} 合成回去）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public Image getImage() {
        return toImage(surface);
    }

    /**
     * The pixel at {@code (x, y)} as an ARGB integer; coordinates outside
     * the image throw. Opaque pixels cross the script boundary as signed
     * 32-bit values ({@code pixel | 0} normalizes a full-range literal
     * before comparing).
     *
     * @zh {@code (x, y)} 处像素的 ARGB 整型值；越界坐标抛错。不透明像素以有符号 32 位值跨脚本边界（比较前用 {@code pixel | 0} 归一到全域写法）。
     * @zh-param x 像素 X
     * @zh-param y 像素 Y
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getPixel(int x, int y) {
        checkBounds(x, y);
        return surface.getRGB(x, y);
    }

    /**
     * Sets one pixel from an ARGB integer; call {@link #update()} to upload
     * the change to its texture. This does not change the graphics drawing
     * color. Coordinates outside the image throw. The value is a 32-bit
     * pattern: full-range literals like {@code 0xFF112233} and signed
     * forms like {@code 0xFF112233 | 0} both land on the same pixels.
     *
     * @zh 用 ARGB 整型值设置一个像素；调用 {@link #update()} 才会上传到纹理。不会改动图形绘制色。越界坐标抛错。取值按 32 位位型处理：{@code 0xFF112233} 全域写法与 {@code 0xFF112233 | 0} 有符号写法落点相同。
     * @zh-param x 像素 X
     * @zh-param y 像素 Y
     * @zh-param argb 要写入的 ARGB 值
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage setPixel(int x, int y, long argb) {
        checkBounds(x, y);
        surface.setRGB(x, y, (int) argb);
        return this;
    }

    /**
     * Draws the source image into this image at {@code (x, y)}, scaled to
     * {@code width} x {@code height}. With the optional source rectangle
     * only that region of the source is drawn; omitted source values default
     * to the whole source image. Changes stay in memory until
     * {@link #update()} uploads them.
     *
     * @zh 把源图像绘制到本图像 {@code (x, y)} 处并缩放到 {@code width} x {@code height}。给出可选源矩形时只绘制源图的该区域；省略的源参数默认取整幅源图。改动在 {@link #update()} 上传前只存在于内存。
     * @zh-param img 要绘制的图像
     * @zh-param x 目标 X
     * @zh-param y 目标 Y
     * @zh-param width 目标宽度
     * @zh-param height 目标高度
     * @param sourceX     source region left (default 0)
     * @param sourceY     source region top (default 0)
     * @param sourceWidth source region width (default: to the source's right edge)
     * @param sourceHeight source region height (default: to the source's bottom edge)
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage drawImage(Image img, int x, int y, int width, int height,
            @ApiOptional Integer sourceX, @ApiOptional Integer sourceY,
            @ApiOptional Integer sourceWidth, @ApiOptional Integer sourceHeight) {
        if (img == null) {
            throw new IllegalArgumentException("image is required");
        }
        BufferedImage source = decode(img);
        if (source == null) {
            throw new IllegalArgumentException("image content is not a readable png");
        }
        int sx = sourceX == null ? 0 : sourceX;
        int sy = sourceY == null ? 0 : sourceY;
        int sw = sourceWidth == null ? source.getWidth() - sx : sourceWidth;
        int sh = sourceHeight == null ? source.getHeight() - sy : sourceHeight;
        // The ten-argument drawImage takes rectangle corners; the public
        // face is origin-plus-size, so convert both rectangles here.
        graphics.drawImage(source, x, y, x + width, y + height,
                sx, sy, sx + sw, sy + sh, null);
        return this;
    }

    /**
     * Java-side shortcut for the whole-image form; the script face is the
     * single {@code drawImage(img, x, y, width, height, sourceX?, …)} member.
     *
     * @zh 整图形式的 Java 侧捷径；脚本面只有 {@code drawImage(img, x, y, width, height, sourceX?, …)} 单成员。
     */
    @ApiInternal
    public CustomImage drawImage(Image img, int x, int y, int width, int height) {
        return drawImage(img, x, y, width, height, null, null, null, null);
    }

    /**
     * The current graphics drawing color as an opaque ARGB integer, signed
     * across the script boundary like {@link #getPixel}.
     *
     * @zh 当前图形绘制色（不透明 ARGB 整型值），与 {@link #getPixel} 一样以有符号形式跨脚本边界。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public int getGraphicsColor() {
        return graphics.getColor().getRGB();
    }

    /**
     * Sets the opaque drawing color used by drawing operations; the alpha
     * bits of {@code color} are ignored. This does not set the background
     * used by {@link #clearRect(int, int, int, int)}. Full-range literals
     * such as {@code 0xFFFF00FF} are accepted as 32-bit patterns.
     *
     * @zh 设置绘制操作使用的不透明绘制色；{@code color} 的 alpha 位被忽略。不会改动 {@link #clearRect(int, int, int, int)} 使用的背景色。{@code 0xFFFF00FF} 等全域写法按 32 位位型接受。
     * @zh-param color RGB 绘制色
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage setGraphicsColor(long color) {
        graphics.setColor(new Color((int) color, false));
        return this;
    }

    /**
     * Translates the graphics origin for subsequent drawing operations; the
     * translation is cumulative and does not move existing pixels.
     *
     * @zh 平移后续绘制操作的图形原点；平移累积，不会移动已有像素。
     * @zh-param x 原点 X
     * @zh-param y 原点 Y
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage translate(int x, int y) {
        graphics.translate(x, y);
        return this;
    }

    /**
     * Intersects the current clip with the given rectangle, restricting
     * subsequent drawing to the intersection; the clip follows the graphics
     * transform, including translations.
     *
     * @zh 把当前裁剪区与给定矩形求交，后续绘制被限制在交集内；裁剪区跟随图形变换（含平移）。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage clipRect(int x, int y, int width, int height) {
        graphics.clipRect(x, y, width, height);
        return this;
    }

    /**
     * Replaces the current clip with the given rectangle rather than
     * intersecting it as {@link #clipRect} does.
     *
     * @zh 用给定矩形替换当前裁剪区（而非 {@link #clipRect} 的求交）。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage setClip(int x, int y, int width, int height) {
        graphics.setClip(x, y, width, height);
        return this;
    }

    /**
     * Restores ordinary paint mode after {@link #setXorMode}.
     *
     * @zh 恢复 {@link #setXorMode} 之前的普通绘制模式。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage setPaintMode() {
        graphics.setPaintMode();
        return this;
    }

    /**
     * Sets XOR paint mode with {@code color} as the XOR color; the mode
     * remains active until {@link #setPaintMode} is called.
     *
     * @zh 以 {@code color} 为异或色进入 XOR 绘制模式；在调用 {@link #setPaintMode} 前一直生效。
     * @zh-param color 异或运算使用的颜色
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage setXorMode(long color) {
        graphics.setXORMode(new Color((int) color, false));
        return this;
    }

    /**
     * The bounds of the current clip as {@code {x, y, width, height}}, or
     * {@code undefined} when no clip is set.
     *
     * @zh 当前裁剪区的边界 {@code {x, y, width, height}}；未设置裁剪区时为 undefined。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public ClipBounds getClipBounds() {
        java.awt.Rectangle bounds = graphics.getClipBounds();
        return bounds == null ? null
                : new ClipBounds(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /**
     * Copies a region of this image to another location in the same image;
     * {@code dx} and {@code dy} are offsets from the source region's
     * origin and overlapping regions are supported.
     *
     * @zh 把本图像的一个区域复制到同图另一位置；{@code dx}/{@code dy} 是相对源区域原点的偏移，支持重叠区域。
     * @zh-param x 源区域 X
     * @zh-param y 源区域 Y
     * @zh-param width 源区域宽
     * @zh-param height 源区域高
     * @zh-param dx X 方向偏移
     * @zh-param dy Y 方向偏移
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage copyArea(int x, int y, int width, int height, int dx, int dy) {
        graphics.copyArea(x, y, width, height, dx, dy);
        return this;
    }

    /**
     * Draws a rectangle outline in the current graphics color.
     *
     * @zh 用当前绘制色画一个矩形轮廓。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage drawRect(int x, int y, int width, int height) {
        graphics.drawRect(x, y, width, height);
        return this;
    }

    /**
     * Fills a rectangle in the current graphics color.
     *
     * @zh 用当前绘制色填充一个矩形。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage fillRect(int x, int y, int width, int height) {
        graphics.fillRect(x, y, width, height);
        return this;
    }

    /**
     * Clears the rectangle using the graphics background color, which is
     * separate from the drawing color; pass {@code color} to choose the
     * background for this operation only.
     *
     * @zh 用图形背景色清空矩形（背景色与绘制色相互独立）；传入 {@code color} 可仅为本次操作指定背景。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     * @param color optional RGB fill for this clear only (restored afterwards)
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage clearRect(int x, int y, int width, int height,
            @ApiOptional Long color) {
        if (color == null) {
            graphics.clearRect(x, y, width, height);
            return this;
        }
        Color previous = graphics.getBackground();
        graphics.setBackground(new Color(color.intValue(), false));
        graphics.clearRect(x, y, width, height);
        graphics.setBackground(previous);
        return this;
    }

    /**
     * Java-side shortcut for the optional-color form; the script face is
     * the single {@code clearRect(x, y, width, height, color?)} member.
     *
     * @zh 可选背景色形式的 Java 侧捷径；脚本面只有 {@code clearRect(x, y, width, height, color?)} 单成员。
     */
    @ApiInternal
    public CustomImage clearRect(int x, int y, int width, int height) {
        return clearRect(x, y, width, height, null);
    }

    /**
     * Draws a rounded-rectangle outline; {@code arcWidth} and
     * {@code arcHeight} are the corner arc diameters.
     *
     * @zh 画圆角矩形轮廓；{@code arcWidth}/{@code arcHeight} 是四角圆弧直径。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     * @zh-param arcWidth 水平圆弧直径
     * @zh-param arcHeight 垂直圆弧直径
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage drawRoundRect(int x, int y, int width, int height,
            int arcWidth, int arcHeight) {
        graphics.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
        return this;
    }

    /**
     * Fills a rounded rectangle in the current graphics color.
     *
     * @zh 用当前绘制色填充圆角矩形。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     * @zh-param arcWidth 水平圆弧直径
     * @zh-param arcHeight 垂直圆弧直径
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage fillRoundRect(int x, int y, int width, int height,
            int arcWidth, int arcHeight) {
        graphics.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
        return this;
    }

    /**
     * Draws a 3D-effect rectangle outline: the drawing color supplies the
     * line and the graphics background supplies the shading;
     * {@code raised} selects raised or etched shading.
     *
     * @zh 画立体效果的矩形轮廓：绘制色作边线、图形背景色作明暗；{@code raised} 选择凸起或蚀刻效果。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     * @zh-param raised 是否凸起
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage draw3DRect(int x, int y, int width, int height, boolean raised) {
        graphics.draw3DRect(x, y, width, height, raised);
        return this;
    }

    /**
     * Fills a 3D-effect rectangle in the current graphics color; the
     * graphics background supplies the shading and {@code raised} selects
     * raised or etched shading.
     *
     * @zh 填充立体效果的矩形：绘制色作填充、图形背景色作明暗；{@code raised} 选择凸起或蚀刻效果。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     * @zh-param raised 是否凸起
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage fill3DRect(int x, int y, int width, int height, boolean raised) {
        graphics.fill3DRect(x, y, width, height, raised);
        return this;
    }

    /**
     * Draws the outline of an oval bounded by the given rectangle in the
     * current graphics color.
     *
     * @zh 用当前绘制色画给定矩形内接椭圆的轮廓。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage drawOval(int x, int y, int width, int height) {
        graphics.drawOval(x, y, width, height);
        return this;
    }

    /**
     * Fills an oval bounded by the given rectangle in the current graphics
     * color.
     *
     * @zh 用当前绘制色填充给定矩形内接椭圆。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage fillOval(int x, int y, int width, int height) {
        graphics.fillOval(x, y, width, height);
        return this;
    }

    /**
     * Draws an unfilled arc of the oval bounded by the given rectangle;
     * angles are degrees, {@code 0} is at three o'clock and positive
     * angles proceed anticlockwise.
     *
     * @zh 画给定矩形内接椭圆的一段弧线；角度以度为单位，{@code 0} 在三点方向，正角度沿逆时针。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     * @zh-param startAngle 起始角
     * @zh-param arcAngle 相对起始角的角度范围
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage drawArc(int x, int y, int width, int height,
            int startAngle, int arcAngle) {
        graphics.drawArc(x, y, width, height, startAngle, arcAngle);
        return this;
    }

    /**
     * Fills the arc segment of the oval bounded by the given rectangle;
     * angles follow {@link #drawArc}.
     *
     * @zh 填充给定矩形内接椭圆的一段弧；角度语义同 {@link #drawArc}。
     * @zh-param x 矩形 X
     * @zh-param y 矩形 Y
     * @zh-param width 矩形宽
     * @zh-param height 矩形高
     * @zh-param startAngle 起始角
     * @zh-param arcAngle 相对起始角的角度范围
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage fillArc(int x, int y, int width, int height,
            int startAngle, int arcAngle) {
        graphics.fillArc(x, y, width, height, startAngle, arcAngle);
        return this;
    }

    /**
     * Draws an open polyline through the corresponding entries of the two
     * coordinate arrays; entries beyond the shorter array are ignored and
     * the last point joins the first only when the supplied points do so.
     *
     * @zh 沿两坐标数组对应项画一条折线；超出较短数组的项被忽略，末点与首点仅在给定坐标重合时相连。
     * @zh-param pointsX 全部点的 X 坐标数组
     * @zh-param pointsY 全部点的 Y 坐标数组
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage drawPolygonLine(int[] pointsX, int[] pointsY) {
        int count = Math.min(length(pointsX), length(pointsY));
        if (count > 0) {
            graphics.drawPolyline(pointsX, pointsY, count);
        }
        return this;
    }

    /**
     * Draws a closed polygon outline through the corresponding entries of
     * the two coordinate arrays; entries beyond the shorter array are
     * ignored.
     *
     * @zh 沿两坐标数组对应项画闭合多边形轮廓；超出较短数组的项被忽略。
     * @zh-param pointsX 全部点的 X 坐标数组
     * @zh-param pointsY 全部点的 Y 坐标数组
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage drawPolygon(int[] pointsX, int[] pointsY) {
        int count = Math.min(length(pointsX), length(pointsY));
        if (count > 0) {
            graphics.drawPolygon(pointsX, pointsY, count);
        }
        return this;
    }

    /**
     * Fills the polygon defined by the corresponding entries of the two
     * coordinate arrays; entries beyond the shorter array are ignored.
     *
     * @zh 填充两坐标数组对应项定义的多边形；超出较短数组的项被忽略。
     * @zh-param pointsX 全部点的 X 坐标数组
     * @zh-param pointsY 全部点的 Y 坐标数组
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage fillPolygon(int[] pointsX, int[] pointsY) {
        int count = Math.min(length(pointsX), length(pointsY));
        if (count > 0) {
            graphics.fillPolygon(pointsX, pointsY, count);
        }
        return this;
    }

    /**
     * Draws text in the current graphics color; {@code y} is the text
     * baseline, not its top.
     *
     * @zh 用当前绘制色绘制文字；{@code y} 是文字基线而非顶部。
     * @zh-param x 文字 X
     * @zh-param y 基线 Y
     * @zh-param text 要绘制的文本
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CustomImage drawString(int x, int y, String text) {
        if (text == null) {
            throw new IllegalArgumentException("text is required");
        }
        graphics.drawString(text, x, y);
        return this;
    }

    /**
     * The pixel width of a string in the current graphics font; the string
     * need not have been drawn first.
     *
     * @zh 当前图形字体下字符串的像素宽度；无需先绘制过该字符串。
     * @zh-param toAnalyze 要测量的文本
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int getStringWidth(String toAnalyze) {
        if (toAnalyze == null) {
            throw new IllegalArgumentException("text is required");
        }
        return graphics.getFontMetrics().stringWidth(toAnalyze);
    }

    /**
     * Swaps the red and blue bytes of an ARGB value (alpha and green stay);
     * a pixel-format helper that never modifies an image. The value is read
     * as a 32-bit pattern, so full-range and signed forms both work.
     *
     * @zh 交换 ARGB 值的红蓝字节（alpha 与绿不变）；纯像素格式辅助，不会改动图像。取值按 32 位位型读取，全域与有符号写法均可。
     * @zh-param argb 要变换的 ARGB 值
     * @return the value with red and blue exchanged
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public int nativeARGBFlip(long argb) {
        return (int) ((argb & 0xFF00FF00L) | ((argb >> 16) & 0xFFL) | ((argb & 0xFFL) << 16));
    }

    /** Array length tolerant of null (empty polygon coordinates). */
    private static int length(int[] values) {
        return values == null ? 0 : values.length;
    }

    /** Reads PNG content; unreadable files and unknown formats read as null. */
    private static BufferedImage readPng(Path file) {
        try {
            return ImageIO.read(file.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    /** Decodes a guest {@link Image}'s PNG bytes; unreadable content is null. */
    private static BufferedImage decode(Image img) {
        try {
            return ImageIO.read(new ByteArrayInputStream(img.getBytes()));
        } catch (IOException e) {
            return null;
        }
    }

    /** Encodes a buffer as a PNG-backed guest {@link Image} snapshot. */
    private static Image toImage(BufferedImage source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(source, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to encode image snapshot", e);
        }
        return new Image(source.getWidth(), source.getHeight(), out.toByteArray());
    }

    /**
     * Clip bounds of a canvas as {@code {x, y, width, height}}: the current
     * clip rectangle, in canvas coordinates.
     *
     * @zh 画布裁剪区边界 {@code {x, y, width, height}}：当前裁剪矩形（画布坐标）。
     * @param x      clip left
     * @param y      clip top
     * @param width  clip width
     * @param height clip height
     */
    public record ClipBounds(int x, int y, int width, int height) {
    }

}
