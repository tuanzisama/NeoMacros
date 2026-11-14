package ink.tuanzi.neomacros.core.api.render.components;

import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

/**
 * Alignment contract of a two-dimensional element: the scaled-geometry
 * queries that alignment math reads (own box, sibling box, parent box) plus
 * the fluent alignment operations that position this element against its
 * parent box or a sibling element. {@link Draw2DElement} implements this
 * contract; scripts reach every member through an element handle, so this
 * type itself stays host-side and never appears as a script-visible
 * interface.
 *
 * <p>Alignment is pure geometry computed at call time: an accepted call
 * writes the new position through {@link #moveTo} and returns this element.
 * Nothing enters the per-frame render path, and an element wider than its
 * parent still resolves (the shift may be negative).</p>
 *
 * <p>Alignment tokens are case-insensitive. Parent-relative horizontal
 * tokens are {@code left}, {@code center} and {@code right}; vertical tokens
 * are {@code top}, {@code center} and {@code bottom} ({@code middle} is an
 * accepted synonym of {@code center} on both axes); a whole-number
 * percentage from {@code 0%} to {@code 100%} positions the element within
 * the space its own scaled size leaves unused. Sibling-relative tokens take
 * the form {@code <thisAnchor>On<otherAnchor>} — for example
 * {@code leftOnRight} or {@code centerOnCenter} — where a bare token aligns
 * the same anchor on both elements. Malformed sibling alignments throw,
 * while unsupported parent tokens do nothing (JsMacros parity).</p>
 *
 * @zh 二维元素的对齐契约：对齐数学读取的缩放几何查询（自身盒、兄弟盒、父盒），以及把本元素定位到父盒或兄弟元素的 fluent 对齐操作。{@link Draw2DElement} 实现本契约；脚本经元素句柄调用全部成员，因此本类型保持宿主侧，不作为脚本可见接口暴露。
 * @zh 对齐是调用时计算的纯几何：被接受的调用经 {@link #moveTo} 写入新位置并返回本元素。不进入每帧渲染路径；比父盒更宽的元素同样可求值（位移可为负）。
 * @zh 对齐词不区分大小写。父盒相对的水平词为 {@code left}、{@code center}、{@code right}；垂直词为 {@code top}、{@code center}、{@code bottom}（两轴均接受 {@code middle} 作为 {@code center} 的同义词）；{@code 0%} 到 {@code 100%} 的整数百分比把元素放进自身缩放尺寸用剩的空间。兄弟相对词形如 {@code <本元素锚>On<对方锚>}——例如 {@code leftOnRight} 或 {@code centerOnCenter}——裸词表示两侧同锚对齐。畸形的兄弟对齐抛错，父盒相对的不支持词则静默不动（与 JsMacros 一致）。
 */
public interface Alignable {

    /**
     * Sets this element's position; the single-axis helpers keep the other
     * axis. Implementations accept absolute scaled pixels or a percentage
     * string.
     *
     * @param x new x position
     * @param y new y position
     * @return this element for chaining
     * @zh 设置本元素位置；单轴助手保留另一轴。实现接受缩放像素绝对值或百分比字符串。
     * @zh-param x 新 x 位置
     * @zh-param y 新 y 位置
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Draw2DElement moveTo(@ApiType("number | string") Object x,
            @ApiType("number | string") Object y);

    /**
     * The scaled width alignment math uses as this element's extent.
     *
     * @return width after percentage resolution and scale, or {@code null}
     *         once the handle is disposed
     * @zh 对齐计算使用的缩放宽度。
     * @zh-return 百分比求值并乘缩放后的宽度；句柄失效后为 {@code null}
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Integer getScaledWidth();

    /**
     * The parent-box width that anchors parent-relative alignment.
     *
     * @return parent width in scaled pixels, or {@code null} once disposed
     * @zh 锚定父盒相对对齐的父盒宽度。
     * @zh-return 父盒宽度（缩放像素）；失效后为 {@code null}
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Integer getParentWidth();

    /**
     * The scaled height alignment math uses as this element's extent.
     *
     * @return height after percentage resolution and scale, or {@code null}
     *         once the handle is disposed
     * @zh 对齐计算使用的缩放高度。
     * @zh-return 百分比求值并乘缩放后的高度；句柄失效后为 {@code null}
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Integer getScaledHeight();

    /**
     * The parent-box height that anchors parent-relative alignment.
     *
     * @return parent height in scaled pixels, or {@code null} once disposed
     * @zh 锚定父盒相对对齐的父盒高度。
     * @zh-return 父盒高度（缩放像素）；失效后为 {@code null}
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Integer getParentHeight();

    /**
     * The left edge alignment uses as this element's x position; scaling
     * does not move it.
     *
     * @return resolved x in scaled pixels, or {@code null} once disposed
     * @zh 对齐用作本元素 x 位置的左边缘；缩放不移动它。
     * @zh-return 求值后的 x（缩放像素）；失效后为 {@code null}
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Integer getScaledLeft();

    /**
     * The top edge alignment uses as this element's y position; scaling
     * does not move it.
     *
     * @return resolved y in scaled pixels, or {@code null} once disposed
     * @zh 对齐用作本元素 y 位置的上边缘；缩放不移动它。
     * @zh-return 求值后的 y（缩放像素）；失效后为 {@code null}
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Integer getScaledTop();

    /**
     * The right edge: the left edge plus the scaled width.
     *
     * @return resolved right edge, or {@code null} once disposed
     * @zh 右边缘：左边缘加缩放宽度。
     * @zh-return 求值后的右边缘；失效后为 {@code null}
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Integer getScaledRight();

    /**
     * The bottom edge: the top edge plus the scaled height.
     *
     * @return resolved bottom edge, or {@code null} once disposed
     * @zh 下边缘：上边缘加缩放高度。
     * @zh-return 求值后的下边缘；失效后为 {@code null}
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Integer getScaledBottom();

    /**
     * Moves this element to {@code x}, retaining its current y coordinate.
     *
     * @param x the new x position
     * @return this element for chaining
     * @zh 把本元素移到 {@code x}，保留当前 y 坐标。
     * @zh-param x 新 x 位置
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Draw2DElement moveToX(@ApiType("number | string") Object x);

    /**
     * Moves this element to {@code y}, retaining its current x coordinate.
     *
     * @param y the new y position
     * @return this element for chaining
     * @zh 把本元素移到 {@code y}，保留当前 x 坐标。
     * @zh-param y 新 y 位置
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Draw2DElement moveToY(@ApiType("number | string") Object y);

    /**
     * Aligns this element horizontally within its parent box, or against a
     * sibling element when the first argument is an element.
     *
     * @param alignmentOrParentOffset parent form: {@code left},
     *        {@code center}, {@code right} or a whole-number percentage
     * @param alignmentOrOffset sibling form: the alignment (for example
     *        {@code leftOnRight}); parent form: pixels added to the result
     * @param offset sibling form: pixels added to the resulting x position
     * @return this element for chaining
     * @zh 在父盒内水平对齐本元素；首参为元素时改为相对兄弟元素对齐。
     * @zh-param alignmentOrParentOffset 父盒形：{@code left}、{@code center}、{@code right} 或整数百分比
     * @zh-param alignmentOrOffset 兄弟形：对齐词（如 {@code leftOnRight}）；父盒形：加到结果上的像素
     * @zh-param offset 兄弟形：加到结果 x 位置上的像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Draw2DElement alignHorizontally(
            @ApiType("string | Draw2DElement") Object alignmentOrParentOffset,
            @ApiOptional @ApiType("string | number") Object alignmentOrOffset,
            @ApiOptional @ApiType("number") Object offset);

    /**
     * Aligns this element vertically within its parent box, or against a
     * sibling element when the first argument is an element.
     *
     * @param alignmentOrParentOffset parent form: {@code top},
     *        {@code center}, {@code bottom} or a whole-number percentage
     * @param alignmentOrOffset sibling form: the alignment (for example
     *        {@code topOnBottom}); parent form: pixels added to the result
     * @param offset sibling form: pixels added to the resulting y position
     * @return this element for chaining
     * @zh 在父盒内垂直对齐本元素；首参为元素时改为相对兄弟元素对齐。
     * @zh-param alignmentOrParentOffset 父盒形：{@code top}、{@code center}、{@code bottom} 或整数百分比
     * @zh-param alignmentOrOffset 兄弟形：对齐词（如 {@code topOnBottom}）；父盒形：加到结果上的像素
     * @zh-param offset 兄弟形：加到结果 y 位置上的像素
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Draw2DElement alignVertically(
            @ApiType("string | Draw2DElement") Object alignmentOrParentOffset,
            @ApiOptional @ApiType("string | number") Object alignmentOrOffset,
            @ApiOptional @ApiType("number") Object offset);

    /**
     * Aligns this element on both axes within its parent box, or against a
     * sibling element when the first argument is an element. Four call
     * shapes are accepted: {@code align(horizontal, vertical)},
     * {@code align(horizontal, horizontalOffset, vertical, verticalOffset)},
     * {@code align(other, horizontal, vertical)} and
     * {@code align(other, horizontal, horizontalOffset, vertical, verticalOffset)}.
     *
     * @param horizontalOrOther parent form: the horizontal alignment;
     *        sibling form: the element to align against
     * @param alignmentOrOffset parent form {@code align(h, v)}: the vertical
     *        alignment; sibling form {@code align(other, h, v)}: the
     *        horizontal alignment; offset forms: the horizontal offset
     * @param verticalOrHorizontalOffset parent offset form: the vertical
     *        alignment; sibling offset form: the horizontal offset; sibling
     *        {@code align(other, h, v)}: the vertical alignment
     * @param verticalOrVerticalOffset sibling offset form: the vertical
     *        alignment; parent offset form: the vertical offset
     * @param verticalOffset sibling offset form: the vertical offset
     * @return this element for chaining
     * @zh 在父盒内双轴对齐本元素；首参为元素时改为相对兄弟元素对齐。接受四种调用形：{@code align(水平, 垂直)}、{@code align(水平, 水平偏移, 垂直, 垂直偏移)}、{@code align(other, 水平, 垂直)} 与 {@code align(other, 水平, 水平偏移, 垂直, 垂直偏移)}。
     * @zh-param horizontalOrOther 父盒形：水平对齐词；兄弟形：作为对齐基准的元素
     * @zh-param alignmentOrOffset 父盒形 {@code align(h, v)}：垂直对齐词；兄弟形 {@code align(other, h, v)}：水平对齐词；带偏移形：水平偏移
     * @zh-param verticalOrHorizontalOffset 父盒带偏移形：垂直对齐词；兄弟带偏移形：水平偏移；兄弟 {@code align(other, h, v)}：垂直对齐词
     * @zh-param verticalOrVerticalOffset 兄弟带偏移形：垂直对齐词；父盒带偏移形：垂直偏移
     * @zh-param verticalOffset 兄弟带偏移形：垂直偏移
     * @zh-return 本元素（可链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    Draw2DElement align(
            @ApiType("string | Draw2DElement") Object horizontalOrOther,
            @ApiType("string | number") Object alignmentOrOffset,
            @ApiOptional @ApiType("string | number") Object verticalOrHorizontalOffset,
            @ApiOptional @ApiType("string | number") Object verticalOrVerticalOffset,
            @ApiOptional @ApiType("number") Object verticalOffset);

    /**
     * Parses an integer percentage with a trailing {@code %}. Values outside
     * {@code 0} to {@code 100} and strings without the suffix return
     * {@code -1}; a suffixed non-integer throws
     * {@link NumberFormatException}. Host-side helper: parameter
     * normalization owns percentage parsing, so this static never crosses
     * the script boundary.
     *
     * @param string the string to parse
     * @return the percentage in {@code [0, 100]}, or {@code -1}
     * @throws NumberFormatException if the suffixed body is not an integer
     * @zh 解析带尾随 {@code %} 的整数百分比。超出 {@code 0} 到 {@code 100} 的值与不带后缀的字符串返回 {@code -1}；带后缀但非整数抛 {@link NumberFormatException}。宿主侧助手：百分比解析归参数归一所有，本静态方法永不跨越脚本边界。
     * @zh-param string 待解析字符串
     * @zh-return {@code [0, 100]} 内的百分比，或 {@code -1}
     * @zh-throws NumberFormatException 后缀主体不是整数时
     */
    @ApiInternal
    public static int parsePercentage(String string) {
        if (string == null || !string.endsWith("%")) {
            return -1;
        }
        int value = Integer.parseInt(
                string.substring(0, string.length() - 1));
        return (value >= 0 && value <= 100) ? value : -1;
    }
}
