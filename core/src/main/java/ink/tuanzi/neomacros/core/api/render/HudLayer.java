package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.core.api.render.components.Draw2DElement;
import ink.tuanzi.neomacros.core.api.render.components.Draw2DImage;
import ink.tuanzi.neomacros.core.api.render.components.Draw2DItem;
import ink.tuanzi.neomacros.core.api.render.components.Draw2DLine;
import ink.tuanzi.neomacros.core.api.render.components.Draw2DRect;
import ink.tuanzi.neomacros.core.api.render.components.Draw2DText;
import ink.tuanzi.neomacros.core.api.render.components.ElementSpec;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.RenderPort;
import ink.tuanzi.neomacros.port.model.WindowData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * A persistent HUD overlay layer: the container node of the retained scene
 * graph behind {@code neo.hud.layer(id)}. The layer registers a single
 * painter with {@link RenderPort} on creation; elements added through the
 * upsert face ({@link #text}, {@link #rect}) live in core state and are
 * re-projected every frame by the engine — no script callback runs on the
 * render path.
 *
 * <p>Draw order: layer {@link #getZIndex()} ascending across layers, then
 * element z ascending inside the layer, then insertion order for equal z.
 * Coordinates are scaled GUI pixels (or viewport percentage strings such as
 * {@code "50%"}); {@link #getWidth()} / {@link #getHeight()} answer the
 * current viewport.</p>
 *
 * <p>After the layer is removed ({@code hud.removeLayer}, module unload, or
 * {@code layer.dispose()} host-side) every read property answers
 * {@code undefined} except {@link #isDisposed()}, and every method throws.</p>
 *
 * @zh 持久 HUD 覆盖层：{@code neo.hud.layer(id)} 背后保留式场景图的容器节点。层在创建时向 {@link RenderPort} 注册单个 painter；通过 upsert 面（{@link #text}、{@link #rect}）加入的元素存活于 core 状态，由引擎每帧重新投影——渲染路径上永不进入脚本回调。
 * @zh 绘制序：跨层按 {@link #getZIndex()} 升序，层内按元素 z 升序，同 z 按插入序。坐标为缩放后 GUI 像素（或 {@code "50%"} 形式的视口百分比字符串）；{@link #getWidth()} / {@link #getHeight()} 返回当前视口尺寸。
 * @zh 层被移除（{@code hud.removeLayer}、模块卸载或宿主侧 {@code layer.dispose()}）后，除 {@link #isDisposed()} 外所有读属性返回 {@code undefined}，所有方法抛错。
 */
@ApiClass(name = "HudLayer", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public final class HudLayer implements ScriptHandle {

    /** The element-id prefix reserved for host-side sugar layers. */
    static final String RESERVED_PREFIX = "$";

    private static final String DISPOSED_MESSAGE = "layer no longer valid";

    private final String id;
    private final String key;
    private final RenderPort port;
    private final Executor mainExecutor;
    private final HudScene scene;
    /** Content-addressed ids already uploaded for {@code image:} snapshots. */
    private final java.util.Set<String> uploadedSnapshots =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile int zIndex;
    private volatile boolean disposed;

    /**
     * Host-side constructor: builds the scene and schedules the painter
     * registration on the main thread. Scripts receive instances from
     * {@code neo.hud.layer(id)} only.
     *
     * @param id           public layer id (without owner prefix)
     * @param key          owner-qualified port key ({@code <owner>:<id>})
     * @param zIndex       initial cross-layer z-order
     * @param port         render port receiving the layer painter
     * @param mainExecutor executor hopping port calls onto the main thread
     * @param window       viewport supplier (scaled window pixels)
     */
    public HudLayer(String id, String key, int zIndex, RenderPort port,
            Executor mainExecutor, Supplier<WindowData> window) {
        this.id = id;
        this.key = key;
        this.zIndex = zIndex;
        this.port = port;
        this.mainExecutor = mainExecutor;
        this.scene = new HudScene(this, window);
        CompletableFuture.runAsync(() -> port.addLayer(key, zIndex, scene::paint),
                mainExecutor);
    }

    /**
     * The public layer id, without the owner namespace prefix;
     * {@code undefined} once the layer is disposed.
     *
     * @zh 公开层 id（不含 owner 前缀）；层失效后为 {@code undefined}。
     */
    @ApiReadOnly
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String getId() {
        return disposed ? null : id;
    }

    /**
     * The layer's draw order against other layers; lower values draw first.
     * Defaults to {@code 0}.
     *
     * @zh 层相对其他层的绘制序；数值小者先画。默认 {@code 0}。
     */
    @ApiReadOnly
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getZIndex() {
        return disposed ? null : zIndex;
    }

    /**
     * Changes the layer's draw order against other layers without
     * re-registering it; takes effect on the next frame. Element order
     * inside the layer is unaffected.
     *
     * @param zIndex new z-order (lower draws first; negatives allowed)
     * @zh 改变层相对其他层的绘制序且不重新注册；下一帧生效。层内元素顺序不受影响。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void setZIndex(int zIndex) {
        ensureAlive();
        if (this.zIndex == zIndex) {
            return;
        }
        this.zIndex = zIndex;
        CompletableFuture.runAsync(() -> port.setLayerZ(key, zIndex), mainExecutor);
    }

    /**
     * Whether the layer renders. An invisible layer keeps its registration
     * and elements; it simply paints nothing. Defaults to {@code true}.
     *
     * @zh 层是否渲染。不可见层保留注册与元素，只是不绘制。默认 {@code true}。
     */
    @ApiReadOnly
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Boolean isVisible() {
        return disposed ? null : scene.isVisible();
    }

    /**
     * Shows or hides the layer. Pure core state: the port is not touched.
     *
     * @param visible {@code false} to hide the layer
     * @zh 显示或隐藏层。纯 core 状态：不触碰端口。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void setVisible(boolean visible) {
        ensureAlive();
        scene.setVisible(visible);
    }

    /**
     * Current viewport width in scaled GUI pixels ({@code 0} before the
     * window exists); {@code undefined} once the layer is disposed.
     *
     * @zh 当前视口宽度（缩放后 GUI 像素，窗口未就绪时为 {@code 0}）；层失效后为 {@code undefined}。
     */
    @ApiReadOnly
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getWidth() {
        return disposed ? null : scene.width();
    }

    /**
     * Current viewport height in scaled GUI pixels ({@code 0} before the
     * window exists); {@code undefined} once the layer is disposed.
     *
     * @zh 当前视口高度（缩放后 GUI 像素，窗口未就绪时为 {@code 0}）；层失效后为 {@code undefined}。
     */
    @ApiReadOnly
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Integer getHeight() {
        return disposed ? null : scene.height();
    }

    /**
     * Whether the layer has been removed. Always readable.
     *
     * @zh 层是否已移除。永远可读。
     */
    @ApiReadOnly
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Inserts or updates a text element under {@code id}: same id updates
     * the live element in place (stable insertion slot, handle kept); the
     * same id with a different component kind replaces it and disposes the
     * previous handle.
     *
     * @param id   element id (must not start with {@code $})
     * @param text the string to draw
     * @param opts text options: {@code x}, {@code y} (number or percentage
     *             string, default {@code 0}), {@code color} (ARGB number or
     *             {@code '#rgb'}/{@code '#rrggbbaa'}/vanilla color name,
     *             default white), {@code scale} (default {@code 1}),
     *             {@code shadow} (drop shadow, default {@code false}),
     *             {@code rotation} (degrees, default {@code 0}),
     *             {@code zIndex} (default {@code 0}),
     *             {@code visible} (default {@code true}). The font-measured
     *             width and line height seed the element's box.
     * @return the live text element handle
     * @zh 按 id 插入或更新文本元素：同 id 原地更新（插入位与句柄不变）；同 id 不同组件类型则替换并使旧句柄失效。
     * @zh-param id 元素 id（不得以 {@code $} 开头）
     * @zh-param text 要绘制的文本
     * @zh-param opts 文本选项：{@code x}、{@code y}（数字或百分比字符串，默认 {@code 0}）、{@code color}（ARGB 数字或 {@code '#rgb'}/{@code '#rrggbbaa'}/原版颜色名，默认白色）、{@code scale}（默认 {@code 1}）、{@code shadow}（投影，默认 {@code false}）、{@code rotation}（度，默认 {@code 0}）、{@code zIndex}（默认 {@code 0}）、{@code visible}（默认 {@code true}）。字体度量的宽度与行高作为元素盒的初值。
     * @zh-return 存活的文本元素句柄
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DText text(String id, String text,
            @ApiOptional @ApiType("TextOptions") Map<String, Object> opts) {
        ensureAlive();
        requireElementId(id);
        Map<String, Object> options = opts == null ? Map.of() : opts;
        String value = text == null ? "" : text;
        ElementSpec spec = new ElementSpec(ElementSpec.Kind.TEXT,
                ElementSpec.Coord.parse(options.getOrDefault("x", 0), "text x"),
                ElementSpec.Coord.parse(options.getOrDefault("y", 0), "text y"),
                ElementSpec.Coord.at(port.textWidth(value)),
                ElementSpec.Coord.at(port.textLineHeight()),
                colorOf(options.get("color"), 0xFFFFFFFF),
                value,
                intOf(options.get("zIndex"), 0),
                boolOf(options.get("visible"), true),
                scaleOf(options.get("scale")),
                rotationOf(options.get("rotation")),
                false,
                boolOf(options.get("shadow"), false),
                1f,
                false,
                false,
                null, 0, 0, 0, 0, 0, 0, null, false, null);
        return (Draw2DText) scene.upsert(id, ElementSpec.Kind.TEXT, spec);
    }

    /**
     * Inserts or updates a filled rectangle element under {@code id}:
     * same-id semantics as {@link #text}. The options bag must carry the
     * box ({@code x}, {@code y}, {@code w}, {@code h}).
     *
     * @param id   element id (must not start with {@code $})
     * @param opts rect options: {@code x}, {@code y}, {@code w}, {@code h}
     *             (numbers or percentage strings, required), {@code color}
     *             (ARGB number or {@code '#rgb'}/{@code '#rrggbbaa'}/vanilla
     *             color name, default white), {@code scale} (default
     *             {@code 1}), {@code rotation} (degrees, default
     *             {@code 0}), {@code zIndex} (default {@code 0}),
     *             {@code visible} (default {@code true})
     * @return the live rect element handle
     * @zh 按 id 插入或更新填充矩形元素：同 id 语义同 {@link #text}。选项包必须给出矩形（{@code x}、{@code y}、{@code w}、{@code h}）。
     * @zh-param id 元素 id（不得以 {@code $} 开头）
     * @zh-param opts 矩形选项：{@code x}、{@code y}、{@code w}、{@code h}（数字或百分比字符串，必填）、{@code color}（ARGB 数字或 {@code '#rgb'}/{@code '#rrggbbaa'}/原版颜色名，默认白色）、{@code scale}（默认 {@code 1}）、{@code rotation}（度，默认 {@code 0}）、{@code zIndex}（默认 {@code 0}）、{@code visible}（默认 {@code true}）
     * @zh-return 存活的矩形元素句柄
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DRect rect(String id,
            @ApiType("RectOptions") Map<String, Object> opts) {
        ensureAlive();
        requireElementId(id);
        if (opts == null) {
            throw new IllegalArgumentException("rect requires x, y, w and h options");
        }
        ElementSpec spec = new ElementSpec(ElementSpec.Kind.RECT,
                requiredCoord(opts, "x", "rect"),
                requiredCoord(opts, "y", "rect"),
                requiredCoord(opts, "w", "rect"),
                requiredCoord(opts, "h", "rect"),
                colorOf(opts.get("color"), 0xFFFFFFFF),
                "",
                intOf(opts.get("zIndex"), 0),
                boolOf(opts.get("visible"), true),
                scaleOf(opts.get("scale")),
                rotationOf(opts.get("rotation")),
                false,
                false,
                1f,
                false,
                false,
                null, 0, 0, 0, 0, 0, 0, null, false, null);
        return (Draw2DRect) scene.upsert(id, ElementSpec.Kind.RECT, spec);
    }

    /**
     * Inserts or updates a straight line element under {@code id}:
     * same-id semantics as {@link #text}. The options bag must carry both
     * endpoints ({@code x1}, {@code y1}, {@code x2}, {@code y2} — plain
     * pixel numbers); corners may be given in either order (the stored box
     * is the min-corner extent, the endpoint order stays observable through
     * the handle). The G-032 {@code addLine} overloads fold into this one
     * member (G-033 overload-fold rule).
     *
     * @param id   element id (must not start with {@code $})
     * @param opts line options: {@code x1}, {@code y1}, {@code x2},
     *             {@code y2} (pixel numbers, required), {@code color}
     *             (ARGB number or {@code '#rgb'}/{@code '#rrggbbaa'}/vanilla
     *             color name, default white), {@code width} (stroke
     *             thickness in pixels, default {@code 1}), {@code rotation}
     *             (degrees, default {@code 0}), {@code zIndex} (default
     *             {@code 0}), {@code visible} (default {@code true})
     * @return the live line element handle
     * @zh 按 id 插入或更新直线元素：同 id 语义同 {@link #text}。选项包必须给出两端点（{@code x1}、{@code y1}、{@code x2}、{@code y2}——纯像素数字）；端点顺序可任意（存储盒为最小角跨度，端点次序经句柄仍可观测）。G-032 的 {@code addLine} 重载组折叠进本成员（G-033 重载折叠铁律）。
     * @zh-param id 元素 id（不得以 {@code $} 开头）
     * @zh-param opts 直线选项：{@code x1}、{@code y1}、{@code x2}、{@code y2}（像素数字，必填）、{@code color}（ARGB 数字或 {@code '#rgb'}/{@code '#rrggbbaa'}/原版颜色名，默认白色）、{@code width}（笔画粗细像素，默认 {@code 1}）、{@code rotation}（度，默认 {@code 0}）、{@code zIndex}（默认 {@code 0}）、{@code visible}（默认 {@code true}）
     * @zh-return 存活的直线元素句柄
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DLine line(String id,
            @ApiType("LineOptions") Map<String, Object> opts) {
        ensureAlive();
        requireElementId(id);
        if (opts == null) {
            throw new IllegalArgumentException(
                    "line requires x1, y1, x2 and y2 options");
        }
        int x1 = intOf(opts.get("x1"), Integer.MIN_VALUE);
        int y1 = intOf(opts.get("y1"), Integer.MIN_VALUE);
        int x2 = intOf(opts.get("x2"), Integer.MIN_VALUE);
        int y2 = intOf(opts.get("y2"), Integer.MIN_VALUE);
        if (x1 == Integer.MIN_VALUE || y1 == Integer.MIN_VALUE
                || x2 == Integer.MIN_VALUE || y2 == Integer.MIN_VALUE) {
            throw new IllegalArgumentException(
                    "line option 'x1', 'y1', 'x2' and 'y2' are required");
        }
        ElementSpec spec = new ElementSpec(ElementSpec.Kind.LINE,
                ElementSpec.Coord.at(Math.min(x1, x2)),
                ElementSpec.Coord.at(Math.min(y1, y2)),
                ElementSpec.Coord.at(Math.abs(x2 - x1)),
                ElementSpec.Coord.at(Math.abs(y2 - y1)),
                colorOf(opts.get("color"), 0xFFFFFFFF),
                "",
                intOf(opts.get("zIndex"), 0),
                boolOf(opts.get("visible"), true),
                scaleOf(opts.get("scale")),
                rotationOf(opts.get("rotation")),
                false,
                false,
                strokeOf(opts.get("width")),
                x1 > x2,
                y1 > y2,
                null, 0, 0, 0, 0, 0, 0, null, false, null);
        return (Draw2DLine) scene.upsert(id, ElementSpec.Kind.LINE, spec);
    }

    /**
     * Inserts or updates a texture-image element under {@code id}: same-id
     * semantics as {@link #text}. The options bag must carry the
     * destination box ({@code x}, {@code y}, {@code w}, {@code h}) and
     * names its source through {@code texture} (a {@code namespace:path}
     * identifier — {@code img.identifier} of an uploaded
     * {@code CustomImage} is the G-030 consumption form) or through
     * {@code image} (an immutable PNG snapshot, decoded and uploaded once
     * under a content-addressed id; {@code texture} wins when both are
     * given). The G-032 {@code addImage} overload group folds into this
     * one member (G-033 overload-fold rule).
     *
     * @param id   element id (must not start with {@code $})
     * @param opts image options: {@code x}, {@code y}, {@code w},
     *             {@code h} (numbers or percentage strings, required),
     *             {@code texture} (identifier, namespace optional),
     *             {@code image} (PNG snapshot uploaded on first use),
     *             {@code color} (ARGB number or {@code '#rgb'}/
     *             {@code '#rrggbbaa'}/vanilla color name, default white),
     *             {@code region} ({@code {x, y, w, h}} source rect in
     *             texels, default the full destination extent),
     *             {@code textureWidth}/{@code textureHeight} (whole
     *             texture size normalizing the texel offsets, default
     *             {@code 256}), {@code scale} (default {@code 1}),
     *             {@code rotation} (degrees, default {@code 0}),
     *             {@code zIndex} (default {@code 0}), {@code visible}
     *             (default {@code true})
     * @return the live image element handle
     * @zh 按 id 插入或更新纹理图像元素：同 id 语义同 {@link #text}。选项包必须给出目标盒（{@code x}、{@code y}、{@code w}、{@code h}），并经 {@code texture}（{@code namespace:path} 标识——已上传 {@code CustomImage} 的 {@code img.identifier} 即 G-030 消费位）或 {@code image}（不可变 PNG 快照，首次使用时解码并以内容寻址 id 上传；两者同给时 {@code texture} 优先）指定来源。G-032 的 {@code addImage} 重载组折叠进本成员（G-033 重载折叠铁律）。
     * @zh-param id 元素 id（不得以 {@code $} 开头）
     * @zh-param opts 图像选项：{@code x}、{@code y}、{@code w}、{@code h}（数字或百分比字符串，必填）、{@code texture}（标识，命名空间可省）、{@code image}（PNG 快照，首次使用即上传）、{@code color}（ARGB 数字或 {@code '#rgb'}/{@code '#rrggbbaa'}/原版颜色名，默认白色）、{@code region}（{@code {x, y, w, h}} 源矩形，单位 texel，默认取整个目标尺寸）、{@code textureWidth}/{@code textureHeight}（归一化 texel 偏移的整纹理尺寸，默认 {@code 256}）、{@code scale}（默认 {@code 1}）、{@code rotation}（度，默认 {@code 0}）、{@code zIndex}（默认 {@code 0}）、{@code visible}（默认 {@code true}）
     * @zh-return 存活的图像元素句柄
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DImage image(String id,
            @ApiType("ImageOptions") Map<String, Object> opts) {
        ensureAlive();
        requireElementId(id);
        if (opts == null) {
            throw new IllegalArgumentException(
                    "image requires x, y, w and h options");
        }
        Map<String, Object> region = bag(opts.get("region"));
        TextureSource source = textureSource(opts);
        ElementSpec spec = new ElementSpec(ElementSpec.Kind.IMAGE,
                requiredCoord(opts, "x", "image"),
                requiredCoord(opts, "y", "image"),
                requiredCoord(opts, "w", "image"),
                requiredCoord(opts, "h", "image"),
                colorOf(opts.get("color"), 0xFFFFFFFF),
                "",
                intOf(opts.get("zIndex"), 0),
                boolOf(opts.get("visible"), true),
                scaleOf(opts.get("scale")),
                rotationOf(opts.get("rotation")),
                false,
                false,
                1f,
                false,
                false,
                source.id(),
                intOf(region.get("x"), 0),
                intOf(region.get("y"), 0),
                intOf(region.get("w"), 0),
                intOf(region.get("h"), 0),
                source.width(),
                source.height(),
                null, false, null);
        return (Draw2DImage) scene.upsert(id, ElementSpec.Kind.IMAGE, spec);
    }

    /**
     * Inserts or updates an item-icon element under {@code id}: same-id
     * semantics as {@link #text}. The stack may be an {@link ItemStack}
     * (its neutral snapshot is stored) or a bare item id (a one-item
     * default stack; a bare name takes the {@code minecraft} namespace);
     * {@code null} draws nothing until {@code setItem} runs. The box is the
     * fixed 16-by-16 GUI-pixel icon scaled by {@code scale}. The G-032
     * {@code addItem} overload group folds into this one member (G-033
     * overload-fold rule); passing only an options bag in the stack slot
     * works as the stackless form.
     *
     * @param id    element id (must not start with {@code $})
     * @param stack the stack to draw: an {@link ItemStack}, a bare item id,
     *              or {@code null}/omitted for an empty icon
     * @param opts  item options: {@code x}, {@code y} (numbers or
     *              percentage strings, default {@code 0}), {@code overlay}
     *              (game decorations, default {@code false}),
     *              {@code overlayText} (label replacing the stack count),
     *              {@code scale} (default {@code 1}), {@code rotation}
     *              (degrees, default {@code 0}), {@code zIndex} (default
     *              {@code 0}), {@code visible} (default {@code true})
     * @return the live item element handle
     * @zh 按 id 插入或更新物品图标元素：同 id 语义同 {@link #text}。堆可为 {@link ItemStack}（存储其中立快照）或裸物品 id（默认单件堆；裸名取 {@code minecraft} 命名空间）；{@code null} 在 {@code setItem} 调用前不绘制。盒为固定的 16×16 GUI 像素图标并按 {@code scale} 缩放。G-032 的 {@code addItem} 重载组折叠进本成员（G-033 重载折叠铁律）；堆槽位只给选项包时按无堆形态处理。
     * @zh-param id 元素 id（不得以 {@code $} 开头）
     * @zh-param stack 要绘制的堆：{@link ItemStack}、裸物品 id，或 {@code null}/省略表示空图标
     * @zh-param opts 物品选项：{@code x}、{@code y}（数字或百分比字符串，默认 {@code 0}）、{@code overlay}（游戏装饰，默认 {@code false}）、{@code overlayText}（替代堆数量的标签）、{@code scale}（默认 {@code 1}）、{@code rotation}（度，默认 {@code 0}）、{@code zIndex}（默认 {@code 0}）、{@code visible}（默认 {@code true}）
     * @zh-return 存活的物品元素句柄
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DItem item(String id,
            @ApiOptional @ApiType("ItemStack | string") Object stack,
            @ApiOptional @ApiType("ItemOptions") Map<String, Object> opts) {
        ensureAlive();
        requireElementId(id);
        Map<String, Object> options;
        if (opts == null && stack instanceof Map<?, ?>) {
            // layer.item(id, opts): the stack slot carries the bag.
            options = bag(stack);
            stack = null;
        } else {
            options = opts == null ? Map.of() : opts;
        }
        ElementSpec spec = new ElementSpec(ElementSpec.Kind.ITEM,
                optionalCoord(options.get("x"), "item x"),
                optionalCoord(options.get("y"), "item y"),
                ElementSpec.Coord.at(16),
                ElementSpec.Coord.at(16),
                0xFFFFFFFF,
                "",
                intOf(options.get("zIndex"), 0),
                boolOf(options.get("visible"), true),
                scaleOf(options.get("scale")),
                rotationOf(options.get("rotation")),
                false,
                false,
                1f,
                false,
                false,
                null, 0, 0, 0, 0, 0, 0,
                stackData(stack),
                boolOf(options.get("overlay"), false),
                overlayTextOf(options.get("overlayText")));
        return (Draw2DItem) scene.upsert(id, ElementSpec.Kind.ITEM, spec);
    }

    /**
     * Resolves the texture source of an {@link #image} upsert: an explicit
     * {@code texture} id wins (its optional dims default to
     * {@code 256}&times;{@code 256}), otherwise a
     * {@code image} snapshot is decoded and uploaded once under a
     * content-addressed id carrying its own dimensions. A port without the
     * texture face simply never draws the sprite (the upload future is
     * observed, not awaited).
     */
    private TextureSource textureSource(Map<String, Object> options) {
        int width = intOf(options.get("textureWidth"), 256);
        int height = intOf(options.get("textureHeight"), 256);
        if (options.get("texture") instanceof String name && !name.isEmpty()) {
            return new TextureSource(ElementSpec.textureIdentifier(name),
                    width, height);
        }
        if (options.get("image") instanceof ink.tuanzi.neomacros.core.api.types.Image snapshot) {
            return uploadSnapshot(snapshot);
        }
        return new TextureSource(null, width, height);
    }

    /**
     * Decodes one immutable PNG snapshot and uploads it (once per distinct
     * content) through {@link RenderPort#putTexture}; identical snapshots
     * across re-upserts share the id, so a per-frame re-upsert of the same
     * image never re-uploads.
     */
    private TextureSource uploadSnapshot(
            ink.tuanzi.neomacros.core.api.types.Image snapshot) {
        byte[] bytes = snapshot.getBytes();
        String id = "neomacros:image/hud/" + contentHash(bytes);
        if (uploadedSnapshots.add(id)) {
            java.awt.image.BufferedImage surface;
            try {
                surface = javax.imageio.ImageIO.read(
                        new java.io.ByteArrayInputStream(bytes));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(
                        "failed to decode the image snapshot", e);
            }
            if (surface == null) {
                throw new IllegalArgumentException(
                        "image snapshot is not a readable PNG");
            }
            int width = surface.getWidth();
            int height = surface.getHeight();
            int[] argb = surface.getRGB(0, 0, width, height, null, 0, width);
            // Observed, not awaited: a port without the texture face keeps
            // compiling and simply draws nothing (UnsupportedPortFeature).
            port.putTexture(id, width, height, argb).exceptionally(e -> null);
            return new TextureSource(id, width, height);
        }
        return new TextureSource(id, snapshot.getWidth(), snapshot.getHeight());
    }

    /** First 32 hex chars of the SHA-256 of the snapshot bytes. */
    private static String contentHash(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(bytes);
            return java.util.HexFormat.of().formatHex(digest, 0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Resolves the stack slot of {@link #item}: snapshot, id, or none. */
    private static ink.tuanzi.neomacros.port.model.ItemStackData stackData(
            Object stack) {
        if (stack == null) {
            return null;
        }
        if (stack instanceof ink.tuanzi.neomacros.core.api.types.ItemStack itemStack) {
            return itemStack.toData();
        }
        if (stack instanceof String itemId) {
            return new ink.tuanzi.neomacros.core.api.types.ItemStackBuilder(
                    itemId).count(1).build().toData();
        }
        throw new IllegalArgumentException(
                "item stack must be an ItemStack or an item id: " + stack);
    }

    /** One resolved image source: texture id ({@code null} = none) and dims. */
    private record TextureSource(String id, int width, int height) {
    }

    /** Narrow one neutral options bag; anything else reads as empty. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> bag(Object value) {
        return value instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
    }

    /** An optional coordinate: absent means the absolute origin. */
    private static ElementSpec.Coord optionalCoord(Object value, String what) {
        return value == null ? ElementSpec.Coord.at(0)
                : ElementSpec.Coord.parse(value, what);
    }

    /** The overlay label option: a string, or null for the stack count. */
    private static String overlayTextOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("expected a string, got: " + value);
    }

    /**
     * Font width of one string on this layer's port — the measurement a
     * {@code Draw2DText} caches when its text is set. Host-side only.
     *
     * @param text the string to measure
     * @return the advance width in scaled pixels
     * @zh 本层端口上的字体宽度度量——{@code Draw2DText} 设置文本时缓存的值。仅宿主侧。
     * @zh-param text 待度量字符串
     * @zh-return 缩放像素下的前进宽度
     */
    @ApiInternal
    public int measureTextWidth(String text) {
        return disposed ? 0 : port.textWidth(text);
    }

    /**
     * The font line height on this layer's port — the unscaled height of
     * a {@code Draw2DText} element. Host-side only.
     *
     * @return one text line's height in scaled pixels
     * @zh 本层端口上的字体行高——{@code Draw2DText} 元素的未缩放高度。仅宿主侧。
     * @zh-return 单行文本高度（缩放像素）
     */
    @ApiInternal
    public int measureTextLineHeight() {
        return disposed ? 0 : port.textLineHeight();
    }

    /**
     * The element under {@code id}, or {@code undefined} when absent (or
     * once the layer is disposed).
     *
     * @param id element id
     * @zh 指定 id 的元素；缺席（或层已失效）时为 {@code undefined}。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement element(String id) {
        return disposed ? null : scene.element(id);
    }

    /**
     * All live element handles in draw order: element z ascending, then
     * insertion order for equal z; {@code undefined} once the layer is
     * disposed.
     *
     * @zh 全部存活元素句柄，按绘制序（元素 z 升序，同 z 按插入序）；层失效后为 {@code undefined}。
     */
    @ApiReadOnly
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public List<Draw2DElement> getElements() {
        return disposed ? null : scene.elements();
    }

    /**
     * All live element ids, parallel to {@link #getElements()};
     * {@code undefined} once the layer is disposed.
     *
     * @zh 全部存活元素 id，与 {@link #getElements()} 平行；层失效后为 {@code undefined}。
     */
    @ApiReadOnly
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public List<String> getIds() {
        return disposed ? null : scene.ids();
    }

    /**
     * Removes one element; its handle becomes disposed. Unknown ids are
     * ignored.
     *
     * @param id element id
     * @zh 移除一个元素；其句柄随即失效。未知 id 忽略。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void remove(String id) {
        ensureAlive();
        scene.remove(id);
    }

    /**
     * Removes every element from the layer; outstanding handles become
     * disposed. The layer itself stays registered.
     *
     * @zh 移除层内全部元素；未失效句柄随即失效。层本身保持注册。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void clear() {
        ensureAlive();
        scene.clear();
    }

    /**
     * Commits the overlay. The retained model registers the layer at
     * {@code neo.hud.layer(id)} creation time and keeps element state across
     * frames, so with no init callback configured this is a no-op that
     * preserves every element.
     *
     * @zh 提交覆盖层。保留式模型在 {@code neo.hud.layer(id)} 创建时即注册图层并跨帧保留元素状态，因此在未配置 init 回调时本方法为保留全部元素的空操作。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public void init() {
        ensureAlive();
    }

    /**
     * Host-side teardown: disposes the scene (every element handle dies)
     * and drops the port registration. Driven by {@code hud.removeLayer}
     * and module unload; scripts cannot reach it (internal surface).
     *
     * @zh 宿主侧拆除：失效场景（全部元素句柄死亡）并注销端口注册。由 {@code hud.removeLayer} 与模块卸载驱动；脚本不可达（内部面）。
     */
    @ApiInternal
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        scene.dispose();
        CompletableFuture.runAsync(() -> port.removeLayer(key), mainExecutor);
    }

    private void ensureAlive() {
        if (disposed) {
            throw new IllegalStateException(DISPOSED_MESSAGE);
        }
    }

    private static void requireElementId(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("element id must not be empty");
        }
        if (id.startsWith(RESERVED_PREFIX)) {
            throw new IllegalArgumentException(
                    "element ids must not start with '" + RESERVED_PREFIX + "'");
        }
    }

    private static ElementSpec.Coord requiredCoord(Map<String, Object> opts,
            String name, String owner) {
        Object value = opts.get(name);
        if (value == null) {
            throw new IllegalArgumentException(
                    owner + " option '" + name + "' is required");
        }
        return ElementSpec.Coord.parse(value, owner + " " + name);
    }

    /**
     * Scale option: a number greater than zero (zero and negative values
     * throw), default {@code 1}. The value is stored on the element state;
     * the render transform that applies it ships with the component tasks
     * (G-034).
     */
    private static float scaleOf(Object value) {
        if (value == null) {
            return 1f;
        }
        if (value instanceof Number number) {
            float scale = number.floatValue();
            if (!(scale > 0)) {
                throw new IllegalArgumentException(
                        "scale must be greater than zero: " + value);
            }
            return scale;
        }
        throw new IllegalArgumentException("expected a number, got: " + value);
    }

    /**
     * Rotation option in degrees, normalized to {@code [-180, 180)} the
     * same way {@code Draw2DElement.setRotation} does; default {@code 0}.
     */
    private static float rotationOf(Object value) {
        if (value == null) {
            return 0f;
        }
        if (value instanceof Number number) {
            return ElementSpec.normalizeRotation(number.floatValue());
        }
        throw new IllegalArgumentException("expected a number, got: " + value);
    }

    /**
     * Color normalization (拍板④ double form): numbers are ARGB ints,
     * strings are {@code #rgb} / {@code #rrggbbaa} hex or a vanilla
     * formatting color name. Delegates to the shared element-side parser.
     */
    private static int colorOf(Object value, int fallback) {
        return ElementSpec.parseColor(value, fallback);
    }

    /**
     * Stroke-thickness option of {@link #line}: a number of screen pixels,
     * default {@code 1}. Non-positive values are stored as-is (a
     * non-positive stroke simply draws nothing).
     */
    private static float strokeOf(Object value) {
        if (value == null) {
            return 1f;
        }
        if (value instanceof Number number) {
            return number.floatValue();
        }
        throw new IllegalArgumentException("expected a number, got: " + value);
    }

    private static int intOf(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("expected a number, got: " + value);
    }

    private static boolean boolOf(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw new IllegalArgumentException("expected a boolean, got: " + value);
    }
}
