package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.TextSegmentData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable styled text: an ordered list of segments, each carrying literal
 * content and optional color, decorations, hover text, and click action.
 * Instances come only from {@code chat.text(...).build()}; there is no
 * direct constructor. {@code JSON.stringify} renders the segment list.
 *
 * @zh 不可变富文本：按序的分段列表，每段携带字面内容与可选颜色、装饰、悬停文本、点击动作。实例只来自 {@code chat.text(...).build()}；不提供直接构造器。{@code JSON.stringify} 输出分段列表。
 */
@ApiClass(name = "Text", kind = ApiClassKind.DATA)
@ApiSince("0.2.0")
@ApiMC("*")
@ApiLoader("*")
public final class Text implements ScriptHandle {

    private final List<TextSegmentData> segments;

    Text(List<TextSegmentData> segments) {
        this.segments = List.copyOf(segments);
    }

    /** Creates an unstyled single-segment text (compat/tests). */
    public static Text of(String plain) {
        return new Text(List.of(new TextSegmentData(plain, null, null, null,
                null, null, null, null, null, null)));
    }

    /** The styled segments in order.
     * @zh 按序的样式分段。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<TextSegmentData> getSegments() {
        return segments;
    }

    /** The plain concatenated text without styling.
     * @zh 去样式后的纯文本。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getPlain() {
        StringBuilder out = new StringBuilder();
        for (TextSegmentData segment : segments) {
            out.append(segment.text());
        }
        return out.toString();
    }

    /** Plain-object rendering used by {@code JSON.stringify}.
     * @zh {@code JSON.stringify} 的纯对象渲染。 */
    public List<Map<String, Object>> toJSON() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TextSegmentData segment : segments) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("text", segment.text());
            if (segment.color() != null) {
                view.put("color", segment.color());
            }
            if (segment.bold() != null) {
                view.put("bold", segment.bold());
            }
            if (segment.italic() != null) {
                view.put("italic", segment.italic());
            }
            if (segment.underlined() != null) {
                view.put("underlined", segment.underlined());
            }
            if (segment.strikethrough() != null) {
                view.put("strikethrough", segment.strikethrough());
            }
            if (segment.obfuscated() != null) {
                view.put("obfuscated", segment.obfuscated());
            }
            if (segment.hoverText() != null) {
                view.put("hoverText", segment.hoverText());
            }
            if (segment.clickCommand() != null) {
                view.put("clickCommand", segment.clickCommand());
            }
            if (segment.clickUrl() != null) {
                view.put("clickUrl", segment.clickUrl());
            }
            out.add(view);
        }
        return out;
    }

    @Override
    public String toString() {
        return "Text(" + getPlain() + ")";
    }
}
