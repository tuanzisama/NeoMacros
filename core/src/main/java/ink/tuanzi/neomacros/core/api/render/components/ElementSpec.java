package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.doc.api.ApiInternal;

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
 * @zh 单个 HUD 元素的不可变状态快照：以 id 为键的场景单元经 volatile 字段发布当前 spec，渲染线程每帧重新投影（G-001 §2.2 写入即发布）。几何坐标可为缩放像素绝对值或视口百分比，绘制时按当前视口求值。
 * @zh 该 record 为宿主侧数据，不跨越脚本边界。基类句柄经 {@link Draw2DElement.Backend} 读取并重新发布；组件任务（G-034 / G-034B）为其扩展自有字段与 {@code with*} 拷贝方法，基类 API 保持稳定、组件状态随之生长。
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
        boolean rotateCenter) {

    /**
     * Component kinds hosted by the container face. Component tasks append
     * their own kinds (line, image, item) as they land.
     *
     * @zh 容器面承载的组件类型。组件任务落地时追加各自类型（line、image、item）。
     */
    public enum Kind {
        TEXT,
        RECT
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
                visible, scale, rotation, rotateCenter);
    }

    /** Copy replacing the y position. */
    public ElementSpec withY(Coord value) {
        return new ElementSpec(kind, x, value, w, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter);
    }

    /** Copy replacing the box width. */
    public ElementSpec withW(Coord value) {
        return new ElementSpec(kind, x, y, value, h, color, text, zIndex,
                visible, scale, rotation, rotateCenter);
    }

    /** Copy replacing the box height. */
    public ElementSpec withH(Coord value) {
        return new ElementSpec(kind, x, y, w, value, color, text, zIndex,
                visible, scale, rotation, rotateCenter);
    }

    /** Copy replacing the element z-order (a z change re-sorts the layer). */
    public ElementSpec withZIndex(int value) {
        return new ElementSpec(kind, x, y, w, h, color, text, value,
                visible, scale, rotation, rotateCenter);
    }

    /** Copy replacing the visibility flag (paint skips invisible elements). */
    public ElementSpec withVisible(boolean value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                value, scale, rotation, rotateCenter);
    }

    /** Copy replacing the render scale (applied by the transform primitive). */
    public ElementSpec withScale(float value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, value, rotation, rotateCenter);
    }

    /** Copy replacing the rotation in degrees, normalized to {@code [-180, 180)}. */
    public ElementSpec withRotation(float value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, normalizeRotation(value), rotateCenter);
    }

    /** Copy replacing whether rotation pivots about the element center. */
    public ElementSpec withRotateCenter(boolean value) {
        return new ElementSpec(kind, x, y, w, h, color, text, zIndex,
                visible, scale, rotation, value);
    }

    /**
     * Normalizes degrees into {@code [-180, 180)} the same way the JsMacros
     * setters do, so {@code setRotation(270)} reads back as {@code -90}.
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
}
