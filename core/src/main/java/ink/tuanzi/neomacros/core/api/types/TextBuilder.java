package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.model.TextSegmentData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mutable builder producing immutable {@link Text} values. Created by
 * {@code chat.text(str, style?)}; chain the style members then
 * {@code build()}. Styles apply to the most recently appended segment.
 *
 * @zh 可变构建器，产出不可变 {@link Text}。由 {@code chat.text(str, style?)} 创建；链式设置样式后 {@code build()}。样式作用于最近一次追加的分段。
 */
@ApiClass(name = "TextBuilder", kind = ApiClassKind.HANDLE)
@ApiSince("0.2.0")
@ApiMC("*")
@ApiLoader("*")
public final class TextBuilder implements ScriptHandle {

    private final List<TextSegmentData> segments = new ArrayList<>();

    /** Creates the builder from one initial literal segment (engine-facing). */
    public TextBuilder(String text, Map<String, Object> style) {
        segments.add(styled(new TextSegmentData(text, null, null, null,
                null, null, null, null, null, null), style));
    }

    private TextSegmentData last() {
        return segments.get(segments.size() - 1);
    }

    private void replaceLast(TextSegmentData segment) {
        segments.set(segments.size() - 1, segment);
    }

    private static TextSegmentData styled(TextSegmentData base, Map<String, Object> style) {
        if (style == null || style.isEmpty()) {
            return base;
        }
        String color = style.get("color") != null ? String.valueOf(style.get("color")) : base.color();
        Boolean bold = merge(style, "bold", base.bold());
        Boolean italic = merge(style, "italic", base.italic());
        Boolean underlined = merge(style, "underlined", base.underlined());
        Boolean strikethrough = merge(style, "strikethrough", base.strikethrough());
        Boolean obfuscated = merge(style, "obfuscated", base.obfuscated());
        String hoverText = style.get("hoverText") != null ? String.valueOf(style.get("hoverText")) : base.hoverText();
        ink.tuanzi.neomacros.port.model.ItemStackData hoverItem = style.get("hoverItem") != null
                ? itemHover(String.valueOf(style.get("hoverItem"))) : base.hoverItem();
        String clickCommand = style.get("clickCommand") != null ? String.valueOf(style.get("clickCommand")) : base.clickCommand();
        String clickUrl = style.get("clickUrl") != null ? String.valueOf(style.get("clickUrl")) : base.clickUrl();
        return new TextSegmentData(base.text(), color, bold, italic, underlined,
                strikethrough, obfuscated, hoverText, hoverItem, clickCommand, clickUrl);
    }

    /** Builds a one-count item hover fact from an item id.
     * @zh 由物品 id 构建单计数的悬停物品事实。 */
    private static ink.tuanzi.neomacros.port.model.ItemStackData itemHover(String itemId) {
        return new ink.tuanzi.neomacros.port.model.ItemStackData(
                ItemStack.namespaced(itemId), 1, "", 0, 0, List.of(), List.of(), Map.of());
    }

    /** Sets the segment color (named color or #rrggbb).
     * @zh 设置分段颜色（命名色或 #rrggbb）。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextBuilder color(String color) {
        TextSegmentData last = last();
        replaceLast(new TextSegmentData(last.text(), color, last.bold(), last.italic(),
                last.underlined(), last.strikethrough(), last.obfuscated(),
                last.hoverText(), last.clickCommand(), last.clickUrl()));
        return this;
    }

    /** Applies the bold decoration.
     * @zh 应用粗体装饰。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextBuilder bold() {
        return flag("bold", true);
    }

    /** Applies the italic decoration.
     * @zh 应用斜体装饰。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextBuilder italic() {
        return flag("italic", true);
    }

    /** Applies the underline decoration.
     * @zh 应用下划线装饰。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextBuilder underlined() {
        return flag("underlined", true);
    }

    /** Applies the strikethrough decoration.
     * @zh 应用删除线装饰。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextBuilder strikethrough() {
        return flag("strikethrough", true);
    }

    /** Applies the obfuscated decoration.
     * @zh 应用乱码装饰。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextBuilder obfuscated() {
        return flag("obfuscated", true);
    }

    private TextBuilder flag(String key, boolean value) {
        TextSegmentData last = last();
        replaceLast(styled(last, Map.of(key, value)));
        return this;
    }

    /** Sets the show-text hover content.
     * @zh 设置悬停展示文本。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextBuilder hover(String hoverText) {
        TextSegmentData last = last();
        replaceLast(new TextSegmentData(last.text(), last.color(), last.bold(), last.italic(),
                last.underlined(), last.strikethrough(), last.obfuscated(),
                hoverText, last.clickCommand(), last.clickUrl()));
        return this;
    }

    /**
     * Sets the show-item hover content from an item id (one-count stack).
     *
     * @zh 由物品 id 设置悬停展示物品（单计数）。
     */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextBuilder hoverItem(String itemId) {
        TextSegmentData last = last();
        replaceLast(new TextSegmentData(last.text(), last.color(), last.bold(), last.italic(),
                last.underlined(), last.strikethrough(), last.obfuscated(),
                last.hoverText(), itemHover(itemId), last.clickCommand(), last.clickUrl()));
        return this;
    }

    /** Sets the run-command click action.
     * @zh 设置运行命令的点击动作。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextBuilder clickCommand(String command) {
        TextSegmentData last = last();
        replaceLast(new TextSegmentData(last.text(), last.color(), last.bold(), last.italic(),
                last.underlined(), last.strikethrough(), last.obfuscated(),
                last.hoverText(), command, last.clickUrl()));
        return this;
    }

    /** Sets the open-url click action.
     * @zh 设置打开链接的点击动作。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextBuilder clickUrl(String url) {
        TextSegmentData last = last();
        replaceLast(new TextSegmentData(last.text(), last.color(), last.bold(), last.italic(),
                last.underlined(), last.strikethrough(), last.obfuscated(),
                last.hoverText(), last.clickCommand(), url));
        return this;
    }

    /** Appends one more segment, optionally styled ({@code {color, bold, ...}}).
     * @zh 追加一个分段，可选样式（{@code {color, bold, ...}}）。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextBuilder append(String text,
            @ApiOptional @ApiType("TextStyle") Map<String, Object> style) {
        segments.add(styled(new TextSegmentData(text, null, null, null,
                null, null, null, null, null, null), style));
        return this;
    }

    /** Appends one unstyled segment.
     * @zh 追加一个无样式分段。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextBuilder append(String text) {
        return append(text, null);
    }

    /** Produces the immutable styled text.
     * @zh 产出不可变的富文本。 */
    @ApiSince("0.2.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Text build() {
        return new Text(List.copyOf(segments));
    }

    /** The segments appended so far (read face for compat).
     * @zh 目前已追加的分段（兼容读取面）。 */
    @ApiReadOnly
    public List<TextSegmentData> getSegments() {
        return List.copyOf(segments);
    }

    /** Merges one style entry over the base value, tolerating null base. */
    private static Boolean merge(Map<String, Object> style, String key, Boolean base) {
        Object value = style.get(key);
        if (value == null) {
            return base;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}