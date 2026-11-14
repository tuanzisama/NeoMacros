package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.RenderPort;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * A registered texture slot handed out by {@code hud.texture(name, spec?)}:
 * the script-visible half of the minimal texture registry (G-001 拍板⑥).
 * The handle owns the GPU slot under {@code neomacros:image/<name>} —
 * {@link #getId()} is the identifier {@code layer.image}'s {@code texture}
 * field consumes — plus its lifecycle: {@link #update(Image)} re-uploads
 * pixels from a PNG snapshot, {@link #dispose()} unregisters the entry
 * and releases the GPU texture. Registration is namespaced per module
 * owner; unloading the module disposes every texture it registered.
 *
 * <p>Textures with the same name under one owner are one entry: repeated
     * {@code hud.texture(name)} calls return the same live handle. The
     * identifier itself is global by name, and it is shared with
     * {@link CustomImage} canvases of the same name.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * const badge = neo.hud.texture('badge', {width: 64, height: 64});
 * await badge.update(someImage);
 * layer.image('shot', {texture: badge.id, x: 0, y: 0, w: 64, h: 64});
 * badge.dispose();
 * }</pre>
 *
 * @zh {@code hud.texture(name, spec?)} 交出的已注册纹理槽：最小纹理注册表（G-001 拍板⑥）的脚本侧。句柄持有 {@code neomacros:image/<name>} 下的 GPU 槽位——{@link #getId()} 即 {@code layer.image} 的 {@code texture} 字段消费的标识——及其生命周期：{@link #update(Image)} 用 PNG 快照重传像素，{@link #dispose()} 注出注册表并释放 GPU 纹理。注册按模块 owner 命名空间隔离；模块卸载会失效其注册的全部纹理。
     * @zh 同 owner 同名纹理只有一条登记：重复 {@code hud.texture(name)} 返回同一存活句柄。标识本身按名字全局，并与同名 {@link CustomImage} 画布共享。
 */
@ApiClass(name = "Texture", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public final class Texture implements ScriptHandle {

    /** Identifier namespace/path prefix; the registry name appends to it. */
    private static final String IDENTIFIER_PREFIX = "neomacros:image/";

    private final String name;
    private final String identifier;
    private final RenderPort render;
    /** Drops this handle's registry entry; host-side, set by the facade. */
    private final Runnable onDispose;

    private volatile int width;
    private volatile int height;
    private volatile boolean disposed;

    /**
     * Builds one registry entry (host-side, {@code hud.texture} only —
     * the class is not self-constructing, so scripts never call this).
     *
     * @param name     registry name (a lowercase identifier path)
     * @param width    initial texture width in pixels
     * @param height   initial texture height in pixels
     * @param render   render port receiving uploads/removals
     * @param onDispose registry unregistration hook (idempotent)
     * @zh 构建一条注册表登记（仅 {@code hud.texture} 的宿主侧调用——类非自构造，脚本不会调用此构造器）。
     */
    public Texture(String name, int width, int height, RenderPort render, Runnable onDispose) {
        this.name = requireValidName(name);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "texture dimensions must be positive: " + width + "x" + height);
        }
        if (render == null || onDispose == null) {
            throw new IllegalArgumentException("texture host wiring is required");
        }
        this.identifier = IDENTIFIER_PREFIX + name;
        this.width = width;
        this.height = height;
        this.render = render;
        this.onDispose = onDispose;
    }

    /**
     * Rejects names that would not form a valid texture identifier path.
     * Returns the name for chaining.
     *
     * @throws IllegalArgumentException when the name is empty or contains
     *                                  characters outside {@code [a-z0-9/._-]}
     * @zh 拒绝无法构成合法纹理标识路径的名字。通过时原样返回。
     */
    @ApiInternal
    public static String requireValidName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("texture name is required");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '/' || c == '.' || c == '_' || c == '-';
            if (!valid) {
                throw new IllegalArgumentException(
                        "texture name must be a lowercase identifier path: " + name);
            }
        }
        return name;
    }

    /**
     * The registry name, without the {@code neomacros:image/} prefix.
     *
     * @zh 注册名（不含 {@code neomacros:image/} 前缀）。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getName() {
        return name;
    }

    /**
     * The registered texture identifier; {@code layer.image}'s
     * {@code texture} field consumes this value directly. Images with the
     * same name share the identifier.
     *
     * @zh 已注册的纹理标识；{@code layer.image} 的 {@code texture} 字段直接消费此值。同名图像共享同一标识。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getId() {
        return identifier;
    }

    /**
     * The texture width in pixels: the registered size, refreshed to the
     * last {@link #update(Image)} upload.
     *
     * @zh 纹理宽度（像素）：注册时的尺寸，随最近一次 {@link #update(Image)} 上传刷新。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public int getWidth() {
        return width;
    }

    /**
     * The texture height in pixels: the registered size, refreshed to the
     * last {@link #update(Image)} upload.
     *
     * @zh 纹理高度（像素）：注册时的尺寸，随最近一次 {@link #update(Image)} 上传刷新。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public int getHeight() {
        return height;
    }

    /**
     * Whether this handle has been disposed (explicitly or by module
     * unload); disposed handles stay readable but reject
     * {@link #update(Image)}.
     *
     * @zh 句柄是否已失效（显式调用或模块卸载）；失效后仍可读，但 {@link #update(Image)} 拒绝执行。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Syncs the registered dimensions to a adopting canvas of the same
     * identifier (host-side, {@code hud} facade only — the canvas already
     * uploaded its own pixels).
     *
     * @param width  the canvas width in pixels
     * @param height the canvas height in pixels
     * @zh 把注册尺寸同步给同标识的并入画布（仅 {@code hud} 门面的宿主侧调用——画布已自行上传像素）。
     */
    @ApiInternal
    public void adopt(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "texture dimensions must be positive: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
    }

    /**
     * Uploads a PNG snapshot (e.g. a {@code CustomImage}'s {@code image})
     * as this texture's pixels; the full texture is re-uploaded on the
     * game main thread and {@link #getWidth()}/{@link #getHeight()}
     * refresh to the snapshot's dimensions. Decoding and argument errors
     * throw synchronously; upload failures reject the promise.
     *
     * @param image PNG-encoded snapshot to upload
     * @return promise resolving to this texture for chaining
     * @zh 把 PNG 快照（如 {@code CustomImage} 的 {@code image}）作为本纹理的像素上传；整图在游戏主线程重传，{@link #getWidth()}/{@link #getHeight()} 刷新为快照尺寸。解码与入参错误同步抛出；上传失败使 promise 拒绝。
     * @zh-param image 待上传的 PNG 编码快照
     * @zh-return 解析为本纹理的 promise，可链式调用
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiType("Promise<Texture>")
    public CompletableFuture<Texture> update(Image image) {
        if (disposed) {
            throw new IllegalStateException("texture is disposed: " + name);
        }
        if (image == null) {
            throw new IllegalArgumentException("image is required");
        }
        BufferedImage decoded = decode(image);
        int newWidth = decoded.getWidth();
        int newHeight = decoded.getHeight();
        int[] pixels = decoded.getRGB(0, 0, newWidth, newHeight, null, 0, newWidth);
        return render.putTexture(identifier, newWidth, newHeight, pixels)
                .thenApply(v -> {
                    this.width = newWidth;
                    this.height = newHeight;
                    return this;
                });
    }

    /**
     * Unregisters this texture: the entry leaves the registry and the
     * GPU texture under {@link #getId()} is released (unknown ids are
     * ignored by the port). Idempotent — disposing twice has no further
     * effect. The identifier is global by name, so disposal also releases
     * a same-named {@link CustomImage} canvas's GPU slot; that canvas
     * re-registers itself on its next {@code update()}.
     *
     * @zh 注销本纹理：登记离开注册表，{@link #getId()} 下的 GPU 纹理被释放（端口忽略未知 id）。幂等——重复失效无进一步影响。标识按名字全局，因此注销也会释放同名 {@link CustomImage} 画布的 GPU 槽位；该画布在下一次 {@code update()} 时会自行重新注册。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        try {
            onDispose.run();
        } finally {
            render.removeTexture(identifier);
        }
    }

    /** Decodes a PNG snapshot to pixels; rejects unreadable content. */
    private static BufferedImage decode(Image image) {
        try {
            BufferedImage decoded = ImageIO.read(
                    new ByteArrayInputStream(image.getBytes()));
            if (decoded == null) {
                throw new IllegalArgumentException("image bytes are not a readable PNG");
            }
            return decoded;
        } catch (IOException e) {
            throw new IllegalArgumentException("image bytes are not a readable PNG", e);
        }
    }

    @Override
    public String toString() {
        return "Texture(" + name + ", " + width + "x" + height
                + (disposed ? ", disposed" : "") + ")";
    }
}
