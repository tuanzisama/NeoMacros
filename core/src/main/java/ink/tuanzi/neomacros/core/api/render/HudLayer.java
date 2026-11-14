package ink.tuanzi.neomacros.core.api.render;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.core.api.render.components.Draw2DElement;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
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
        this.scene = new HudScene(window);
        CompletableFuture.runAsync(() -> port.addLayer(key, zIndex, scene::paint),
                mainExecutor);
    }

    /**
     * The public layer id, without the owner namespace prefix;
     * {@code undefined} once the layer is disposed.
     *
     * @zh 公开层 id（不含 owner 前缀）；层失效后为 {@code undefined}。
     */
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
     *             default white), {@code zIndex} (default {@code 0}),
     *             {@code visible} (default {@code true})
     * @return the live element handle
     * @zh 按 id 插入或更新文本元素：同 id 原地更新（插入位与句柄不变）；同 id 不同组件类型则替换并使旧句柄失效。
     * @zh-param id 元素 id（不得以 {@code $} 开头）
     * @zh-param text 要绘制的文本
     * @zh-param opts 文本选项：{@code x}、{@code y}（数字或百分比字符串，默认 {@code 0}）、{@code color}（ARGB 数字或 {@code '#rgb'}/{@code '#rrggbbaa'}/原版颜色名，默认白色）、{@code zIndex}（默认 {@code 0}）、{@code visible}（默认 {@code true}）
     * @zh-return 存活的元素句柄
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement text(String id, String text,
            @ApiOptional @ApiType("TextOptions") Map<String, Object> opts) {
        ensureAlive();
        requireElementId(id);
        Map<String, Object> options = opts == null ? Map.of() : opts;
        HudScene.Spec spec = new HudScene.Spec(HudScene.Kind.TEXT,
                HudScene.Coord.parse(options.getOrDefault("x", 0), "text x"),
                HudScene.Coord.parse(options.getOrDefault("y", 0), "text y"),
                HudScene.Coord.at(0),
                HudScene.Coord.at(0),
                colorOf(options.get("color"), 0xFFFFFFFF),
                text == null ? "" : text,
                intOf(options.get("zIndex"), 0),
                boolOf(options.get("visible"), true));
        return scene.upsert(id, HudScene.Kind.TEXT, spec);
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
     *             color name, default white), {@code zIndex} (default
     *             {@code 0}), {@code visible} (default {@code true})
     * @return the live element handle
     * @zh 按 id 插入或更新填充矩形元素：同 id 语义同 {@link #text}。选项包必须给出矩形（{@code x}、{@code y}、{@code w}、{@code h}）。
     * @zh-param id 元素 id（不得以 {@code $} 开头）
     * @zh-param opts 矩形选项：{@code x}、{@code y}、{@code w}、{@code h}（数字或百分比字符串，必填）、{@code color}（ARGB 数字或 {@code '#rgb'}/{@code '#rrggbbaa'}/原版颜色名，默认白色）、{@code zIndex}（默认 {@code 0}）、{@code visible}（默认 {@code true}）
     * @zh-return 存活的元素句柄
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Draw2DElement rect(String id,
            @ApiType("RectOptions") Map<String, Object> opts) {
        ensureAlive();
        requireElementId(id);
        if (opts == null) {
            throw new IllegalArgumentException("rect requires x, y, w and h options");
        }
        HudScene.Spec spec = new HudScene.Spec(HudScene.Kind.RECT,
                requiredCoord(opts, "x"),
                requiredCoord(opts, "y"),
                requiredCoord(opts, "w"),
                requiredCoord(opts, "h"),
                colorOf(opts.get("color"), 0xFFFFFFFF),
                "",
                intOf(opts.get("zIndex"), 0),
                boolOf(opts.get("visible"), true));
        return scene.upsert(id, HudScene.Kind.RECT, spec);
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
     * preserves every element (the JsMacros semantics for an overlay
     * without an init callback).
     *
     * @zh 提交覆盖层。保留式模型在 {@code neo.hud.layer(id)} 创建时即注册图层并跨帧保留元素状态，因此在未配置 init 回调时本方法为保留全部元素的空操作（即 JsMacros 无 init 回调分支的语义）。
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

    private static HudScene.Coord requiredCoord(Map<String, Object> opts, String name) {
        Object value = opts.get(name);
        if (value == null) {
            throw new IllegalArgumentException("rect option '" + name + "' is required");
        }
        return HudScene.Coord.parse(value, "rect " + name);
    }

    /**
     * Color normalization (拍板④ double form): numbers are ARGB ints,
     * strings are {@code #rgb} / {@code #rrggbbaa} hex or a vanilla
     * formatting color name.
     */
    private static int colorOf(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return (int) number.longValue();
        }
        if (value instanceof String text) {
            String trimmed = text.trim().toLowerCase(java.util.Locale.ROOT);
            Integer named = NAMED_COLORS.get(trimmed);
            if (named != null) {
                return named;
            }
            String hex = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
            switch (hex.length()) {
                case 3 -> {
                    int r = Character.digit(hex.charAt(0), 16);
                    int g = Character.digit(hex.charAt(1), 16);
                    int b = Character.digit(hex.charAt(2), 16);
                    if (r >= 0 && g >= 0 && b >= 0) {
                        return 0xFF000000 | (r << 20) | (r << 16)
                                | (g << 12) | (g << 8) | (b << 4) | b;
                    }
                }
                case 6 -> {
                    try {
                        return 0xFF000000 | (int) Long.parseLong(hex, 16);
                    } catch (NumberFormatException ignored) {
                        // fall through to the rejection below
                    }
                }
                case 8 -> {
                    try {
                        return (int) Long.parseLong(hex, 16);
                    } catch (NumberFormatException ignored) {
                        // fall through to the rejection below
                    }
                }
                default -> {
                    // fall through to the rejection below
                }
            }
            throw new IllegalArgumentException("unknown color: " + text);
        }
        throw new IllegalArgumentException("color must be a number or a color string");
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

    /** Vanilla formatting colors, opaque ARGB (the Text COLOR list). */
    private static final Map<String, Integer> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", 0xFF000000),
            Map.entry("dark_blue", 0xFF0000AA),
            Map.entry("dark_green", 0xFF00AA00),
            Map.entry("dark_aqua", 0xFF00AAAA),
            Map.entry("dark_red", 0xFFAA0000),
            Map.entry("dark_purple", 0xFFAA00AA),
            Map.entry("gold", 0xFFFFAA00),
            Map.entry("gray", 0xFFAAAAAA),
            Map.entry("dark_gray", 0xFF555555),
            Map.entry("blue", 0xFF5555FF),
            Map.entry("green", 0xFF55FF55),
            Map.entry("aqua", 0xFF55FFFF),
            Map.entry("red", 0xFFFF5555),
            Map.entry("light_purple", 0xFFFF55FF),
            Map.entry("yellow", 0xFFFFFF55),
            Map.entry("white", 0xFFFFFFFF));
}
