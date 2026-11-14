package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.port.model.ItemStackData;

import java.util.Locale;

/**
 * Immutable state snapshot of one HUD element: an id-keyed scene cell
 * publishes the current spec through a volatile field and the render thread
 * re-projects it every frame (G-001 §2.2 write-then-publish). Geometry
 * coordinates may be absolute scaled pixels or a viewport percentage; they
 * resolve against the current viewport at paint time.
 *
 * <p>The record is host-side data and never crosses the script boundary.
 * The base handle reads and republishes it through
 * {@link Draw2DElement.Backend}; component tasks (G-034 / G-034B) extend
 * it with their own fields plus {@code with*} copy methods, keeping the
 * base-class API stable while component state grows alongside it.</p>
 *
 * <p><b>G-034 component fields</b>: {@code shadow} (text drop shadow),
 * {@code stroke} (line thickness — the base {@code width} face stays the
 * line's horizontal extent, see {@code Draw2DLine}), and
 * {@code flipX}/{@code flipY} (corner/endpoint order: the stored box is
 * always the min-corner origin with non-negative size, and the flags say
 * whether the element's <i>first</i> corner sits on the far edge).</p>
 *
 * <p><b>G-034B component fields</b>: {@code texture} plus the
 * {@code imageX/imageY/regionWidth/regionHeight/textureWidth/textureHeight}
 * source-rect face of an image element (the {@code Draw2DImage} texture
 * view), and {@code stack}/{@code overlay}/{@code overlayText} of an item
 * icon ({@code Draw2DItem}). Kinds that do not use a group keep its
 * neutral defaults ({@code null} texture/stack, zero region, no overlay).</p>
 *
 * @zh 单个 HUD 元素的不可变状态快照：以 id 为键的场景单元经 volatile 字段发布当前 spec，渲染线程每帧重新投影（G-001 §2.2 写入即发布）。几何坐标可为缩放像素绝对值或视口百分比，绘制时按当前视口求值。
 * @zh 该 record 为宿主侧数据，不跨越脚本边界。基类句柄经 {@link Draw2DElement.Backend} 读取并重新发布；组件任务（G-034 / G-034B）为其扩展自有字段与 {@code with*} 拷贝方法，基类 API 保持稳定、组件状态随之生长。
 * @zh <b>G-034 组件字段</b>：{@code shadow}（文本投影）、{@code stroke}（线宽——基类 {@code width} 面保持为线的横向跨度，见 {@code Draw2DLine}）、{@code flipX}/{@code flipY}（角点/端点次序：存储盒恒为最小角原点 + 非负尺寸，标志指示<i>第一</i>角是否落在远端）。
 * @zh <b>G-034B 组件字段</b>：{@code texture} 与图像元素的 {@code imageX/imageY/regionWidth/regionHeight/textureWidth/textureHeight} 源矩形面（{@code Draw2DImage} 纹理视图），以及物品图标的 {@code stack}/{@code overlay}/{@code overlayText}（{@code Draw2DItem}）。不使用某组的 kind 保持其中性默认值（纹理/堆为 {@code null}、区域为 0、无覆盖层）。
 */
@ApiInternal
public record ElementSpec(
        Kind kind,
        Coord x,
        Coord y,
        Coord w,
        Coord h,
        int color,
        String text,
        int zIndex,
        boolean visible,
        float scale,
        float rotation,
        boolean rotateCenter,
        boolean shadow,
        float stroke,
        boolean flipX,
        boolean flipY,
        String texture,
        int imageX,
        int imageY,
        int regionWidth,
        int regionHeight,
        int textureWidth,
        int textureHeight,
        ItemStackData stack,
        boolean overlay,
        String overlayText) {

    /**
     * Component kinds hosted by the container face. Component tasks append
     * their own kinds (image, item) as they land.
     *
     * @zh 容器面承载的组件类型。组件任务落地时追加各自类型（image、item）。
     */
    public enum Kind {
        TEXT,
        RECT,
        LINE,
        IMAGE,
        ITEM
    }

    /**
     * A screen coordinate: either absolute scaled pixels or a percentage of
     * the parent box (the layer viewport) resolved every frame.
     *
     * @zh 屏幕坐标：缩放像素绝对值，或每帧按父盒（层视口）求值的百分比。
     */
    public static final class Coord {
        private final int abs;
        private final double pct;
        private final boolean percent;

        private Coord(int abs, double pct, boolean percent) {
            this.abs = abs;
            this.pct = pct;
            this.percent = percent;
        }

        /** Absolute pixel coordinate. */
        public static Coord at(int value) {
            return new Coord(value, 0, false);
        }

        /**
         * Parses a script coordinate: a number is absolute pixels, a string
         * of the form {@code "50%"} is a viewport percentage.
         *
         * @throws IllegalArgumentException on any other form
         */
        public static Coord parse(Object value, String what) {
            if (value instanceof Number number) {
                return at(number.intValue());
            }
            if (value instanceof String text) {
                String trimmed = text.trim();
                if (trimmed.endsWith("%")) {
                    try {
                        return new Coord(0, Double.parseDouble(trimmed.substring(0,
                                trimmed.length() - 1)), true);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                what + " is not a percentage: " + text, e);
                    }
                }
                throw new IllegalArgumentException(
                        what + " must be a number or a percentage string: " + text);
            }
            throw new IllegalArgumentException(
                    what + " must be a number or a percentage string");
        }

        /**
         * Resolves against the given parent extent in pixels.
         *
         * @param parent parent extent (viewport or parent box) in scaled pixels
         * @return absolute pixels for this coordinate
         */
        public int resolve(int parent) {
            return percent ? (int) Math.round(parent * pct / 100.0) : abs;
        }

        /**
         * The raw script-facing form for round-tripping: the integer pixels
         * for absolute coordinates, or the original percentage string
         * ({@code "50%"}) for viewport percentages.
         *
         * @return the raw coordinate as a number or percentage string
         * @zh 脚本面往返用的原始形态：绝对坐标为整数像素，视口百分比为原百分比字符串（{@code "50%"}）。
         * @zh-return 原始坐标（数字或百分比字符串）
         */
        public Object raw() {
            if (!percent) {
                return abs;
            }
            long whole = (long) pct;
            return (pct == whole) ? whole + "%" : pct + "%";
        }
    }

    /** Copy replacing the x position. */
    public ElementSpec withX(Coord value) {
        return new ElementSpec(kind, value, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the y position. */
    public ElementSpec withY(Coord value) {
        return new ElementSpec(kind, x, value, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the box width. */
    public ElementSpec withW(Coord value) {
        return new ElementSpec(kind, x, y, value, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the box height. */
    public ElementSpec withH(Coord value) {
        return new ElementSpec(kind, x, y, w, value, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the packed ARGB color. */
    public ElementSpec withColor(int value) {
        return new ElementSpec(kind, x, y, w, h, value, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the displayed text string. */
    public ElementSpec withText(String value) {
        return new ElementSpec(kind, x, y, w, h, color, value, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the element z-order (a z change re-sorts the layer). */
    public ElementSpec withZIndex(int value) {
        return new ElementSpec(kind, x, y, w, h, color, text, value,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the visibility flag (paint skips invisible elements). */
    public ElementSpec withVisible(boolean value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                value, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the render scale (applied by the transform primitive). */
    public ElementSpec withScale(float value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, value, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the rotation in degrees, normalized to {@code [-180, 180)}. */
    public ElementSpec withRotation(float value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, normalizeRotation(value), rotateCenter,
                shadow, stroke, flipX, flipY, texture, imageX, imageY,
                regionWidth, regionHeight, textureWidth, textureHeight,
                stack, overlay, overlayText);
    }

    /** Copy replacing whether rotation pivots about the element center. */
    public ElementSpec withRotateCenter(boolean value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, value, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the text drop-shadow flag (text elements). */
    public ElementSpec withShadow(boolean value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, value, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the line stroke thickness in screen pixels. */
    public ElementSpec withStroke(float value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, value,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing whether the first corner sits on the far x edge. */
    public ElementSpec withFlipX(boolean value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                value, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing whether the first corner sits on the far y edge. */
    public ElementSpec withFlipY(boolean value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, value, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the texture identifier an image element blits from. */
    public ElementSpec withTexture(String value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, value, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the source x offset inside the texture, in texels. */
    public ElementSpec withImageX(int value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, value, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the source y offset inside the texture, in texels. */
    public ElementSpec withImageY(int value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, value, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the sampled region width in texels ({@code 0} = derive from the destination). */
    public ElementSpec withRegionWidth(int value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, value,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the sampled region height in texels ({@code 0} = derive from the destination). */
    public ElementSpec withRegionHeight(int value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                value, textureWidth, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the whole-texture width used to normalize texel offsets. */
    public ElementSpec withTextureWidth(int value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, value, textureHeight, stack, overlay,
                overlayText);
    }

    /** Copy replacing the whole-texture height used to normalize texel offsets. */
    public ElementSpec withTextureHeight(int value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, value, stack, overlay,
                overlayText);
    }

    /** Copy replacing the item stack an item element draws ({@code null} = nothing). */
    public ElementSpec withStack(ItemStackData value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, value, overlay,
                overlayText);
    }

    /** Copy replacing whether the item draws its game decorations. */
    public ElementSpec withOverlay(boolean value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, value,
                overlayText);
    }

    /** Copy replacing the overlay label ({@code null} draws the stack count). */
    public ElementSpec withOverlayText(String value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter, shadow, stroke,
                flipX, flipY, texture, imageX, imageY, regionWidth,
                regionHeight, textureWidth, textureHeight, stack, overlay,
                value);
    }

    /**
     * Normalizes degrees into {@code [-180, 180)}, so
     * {@code setRotation(270)} reads back as {@code -90}.
     *
     * @param degrees raw rotation in degrees
     * @return the equivalent angle inside {@code [-180, 180)}
     */
    public static float normalizeRotation(float degrees) {
        float normalized = (degrees + 180f) % 360f;
        if (normalized < 0) {
            normalized += 360f;
        }
        return normalized - 180f;
    }

    /**
     * Resolves a texture identifier: a bare name takes the
     * {@code minecraft} namespace, an empty or null name means
     * "no texture" (null).
     *
     * @param id script texture id (namespace optional) or null
     * @return the {@code namespace:path} identifier, or null when absent
     * @zh 解析纹理标识：裸名取 {@code minecraft} 命名空间，空名/null 表示"无纹理"（返回 null）。
     * @zh-param id 脚本纹理 id（命名空间可省）或 null
     * @zh-return {@code namespace:path} 标识；缺席时为 null
     */
    public static String textureIdentifier(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }

    /**
     * Color normalization (拍板④ double form): numbers are ARGB ints,
     * strings are {@code #rgb} / {@code #rrggbbaa} hex or a vanilla
     * formatting color name; {@code null} answers {@code fallback}.
     *
     * @param value    script color ({@code number | string}) or null
     * @param fallback value used when {@code value} is null
     * @return the packed ARGB color
     * @zh 颜色归一（拍板④ 双形）：数字为 ARGB 整型，字符串为 {@code #rgb}/{@code #rrggbbaa} 十六进制或原版颜色名；{@code null} 返回 {@code fallback}。
     * @zh-param value 脚本颜色（{@code number | string}）或 null
     * @zh-param fallback {@code value} 为 null 时的回退值
     * @zh-return 打包后的 ARGB 颜色
     */
    public static int parseColor(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return (int) number.longValue();
        }
        if (value instanceof String text) {
            String trimmed = text.trim().toLowerCase(Locale.ROOT);
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

    /**
     * Folded {@code setColor} resolution for the component handles: one
     * script member carries every call shape (G-033 overload-fold
     * rule — the engine keeps one binding per name), dispatched by arity:
     * {@code setColor(color)}, {@code setColor(color, alpha)},
     * {@code setColor(r, g, b)} and {@code setColor(r, g, b, a)}. The alpha
     * replaces the packed top byte (only its low eight bits survive,
     * without clamping); the rgb forms are opaque unless an alpha
     * channel is given. The color itself accepts the 拍板④ double
     * form (ARGB number or color string).
     *
     * @param color  packed color (number or string), or the red channel in rgb forms
     * @param alpha  optional alpha byte (color+alpha form), or the green channel in rgb forms
     * @param blue   optional blue channel (rgb form), absent for rgba-less calls
     * @param alpha2 optional alpha channel of the rgba form
     * @return the packed ARGB color
     * @zh 组件句柄的折叠 {@code setColor} 解析：一个脚本成员承载全部调用形（G-033 重载折叠铁律——引擎每名只留一个绑定），按元数分派：{@code setColor(color)}、{@code setColor(color, alpha)}、{@code setColor(r, g, b)}、{@code setColor(r, g, b, a)}。alpha 替换打包色顶字节（仅低八位生效，不钳制）；rgb 形默认不透明。颜色本身接受拍板④ 双形。
     * @zh-param color 打包色（数字或字符串），rgb 形中为红通道
     * @zh-param alpha 可选 alpha 字节（color+alpha 形），rgb 形中为绿通道
     * @zh-param blue 可选蓝通道（rgb 形）
     * @zh-param alpha2 rgba 形的可选 alpha 通道
     * @zh-return 打包后的 ARGB 颜色
     */
    public static int parseColor(Object color, Object alpha, Object blue,
            Object alpha2) {
        if (color == null) {
            throw new IllegalArgumentException("color is required");
        }
        if (blue != null) {
            int a = alpha2 == null ? 0xFF : channel(alpha2, "alpha");
            return a << 24 | channel(color, "red") << 16
                    | channel(alpha, "green") << 8 | channel(blue, "blue");
        }
        int argb = parseColor(color, 0xFFFFFFFF);
        if (alpha == null) {
            return argb;
        }
        return channel(alpha, "alpha") << 24 | (argb & 0x00FFFFFF);
    }

    /** A 0-255 channel argument of the folded color forms. */
    private static int channel(Object value, String what) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    what + " channel must be a number: " + value);
        }
        int channel = number.intValue();
        if (channel < 0 || channel > 255) {
            throw new IllegalArgumentException(
                    what + " channel must be in 0..255: " + value);
        }
        return channel;
    }

    /** Vanilla formatting colors, opaque ARGB (the Text COLOR list). */
    private static final java.util.Map<String, Integer> NAMED_COLORS =
            java.util.Map.ofEntries(
                    java.util.Map.entry("black", 0xFF000000),
                    java.util.Map.entry("dark_blue", 0xFF0000AA),
                    java.util.Map.entry("dark_green", 0xFF00AA00),
                    java.util.Map.entry("dark_aqua", 0xFF00AAAA),
                    java.util.Map.entry("dark_red", 0xFFAA0000),
                    java.util.Map.entry("dark_purple", 0xFFAA00AA),
                    java.util.Map.entry("gold", 0xFFFFAA00),
                    java.util.Map.entry("gray", 0xFFAAAAAA),
                    java.util.Map.entry("dark_gray", 0xFF555555),
                    java.util.Map.entry("blue", 0xFF5555FF),
                    java.util.Map.entry("green", 0xFF55FF55),
                    java.util.Map.entry("aqua", 0xFF55FFFF),
                    java.util.Map.entry("red", 0xFFFF5555),
                    java.util.Map.entry("light_purple", 0xFFFF55FF),
                    java.util.Map.entry("yellow", 0xFFFFFF55),
                    java.util.Map.entry("white", 0xFFFFFFFF));
}
