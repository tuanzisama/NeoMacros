package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.port.model.ItemStackData;
import ink.tuanzi.neomacros.port.model.TextSegmentData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable styled text node: literal content plus optional color,
 * decorations, hover content, click action, and nested children. Built with
 * {@link Component} ({@code build()}); instances also arrive as the
 * {@code chat:receive} payload. {@code JSON.stringify} renders the vanilla
 * component tree ({@code {text, color, …, extra: [...]}}).
 *
 * @zh 不可变富文本节点：字面内容加可选颜色、装饰、悬停内容、点击动作与嵌套子节点。由 {@link Component} {@code build()} 产出；也作为 {@code chat:receive} 的事件载荷。{@code JSON.stringify} 输出原版组件树（{@code {text, color, …, extra: [...]}}）。
 * @zh-example const link = new Component("点我回城")
 *   .clickCommand("/home")
 *   .hover("传送到主城")
 *   .build();
 */
@ApiClass(name = "TextComponent", kind = ApiClassKind.DATA)
@ApiSince("0.4.0")
@ApiMC("*")
@ApiLoader("*")
public final class TextComponent implements ScriptHandle {

    // Package-visible finals: the Component builder in this package reads
    // the own (unresolved) style fields for copyFrom; the public script face
    // is the bean getters below.
    final String content;
    final String color;
    final Boolean bold;
    final Boolean italic;
    final Boolean underlined;
    final Boolean strikethrough;
    final Boolean obfuscated;
    final String hoverText;
    final ItemStackData hoverItem;
    final String clickAction;
    final String clickValue;
    final List<TextComponent> children;

    TextComponent(String content, String color, Boolean bold, Boolean italic,
            Boolean underlined, Boolean strikethrough, Boolean obfuscated,
            String hoverText, ItemStackData hoverItem, String clickAction,
            String clickValue, List<TextComponent> children) {
        this.content = content == null ? "" : content;
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
        this.hoverText = hoverText;
        this.hoverItem = hoverItem;
        this.clickAction = clickAction;
        this.clickValue = clickValue;
        this.children = List.copyOf(children);
    }

    /** Unstyled single node with the given text (host-internal convenience).
     * @zh 无样式的单节点文本（宿主内部便捷用）。 */
    @ApiInternal
    public static TextComponent of(String plain) {
        return new TextComponent(plain == null ? "" : plain, null, null, null,
                null, null, null, null, null, null, null, List.of());
    }

    /**
     * Maps one neutral chat component tree onto the script face: explicit
     * styles cross per node, inheritance resolves at read/flatten time.
     * Hover and click content is not carried (vanilla parsing hands out
     * leaf-only events).
     *
     * @zh 将中立聊天组件树映射到脚本面：样式逐节点携带，继承在读取/拍平时解析。悬停与点击内容不映射（原版解析只给叶子事件）。
     */
    @ApiInternal
    public static TextComponent from(ink.tuanzi.neomacros.port.model.ChatComponent component) {
        List<TextComponent> mapped = new ArrayList<>();
        for (ink.tuanzi.neomacros.port.model.ChatComponent child : component.children()) {
            mapped.add(from(child));
        }
        return new TextComponent(component.text(), component.color(),
                component.bold(), component.italic(), component.underlined(),
                component.strikethrough(), component.obfuscated(),
                null, null, null, null, mapped);
    }

    /** The literal content of this node.
     * @zh 本节点的字面内容。 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getContent() {
        return content;
    }

    /** The nested child nodes in order.
     * @zh 按序嵌套的子节点。 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public List<TextComponent> getChildren() {
        return children;
    }

    /** The plain concatenated text of the whole tree.
     * @zh 整棵树的纯文本拼接。 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getPlain() {
        StringBuilder out = new StringBuilder(content);
        for (TextComponent child : children) {
            out.append(child.getPlain());
        }
        return out.toString();
    }

    /** Flattens the tree into render segments; child nodes inherit this
     * node's color, decorations, hover, and click unless they set their own.
     * @zh 将树拍平为渲染分段；子节点继承本节点的颜色、装饰、悬停与点击，除非自行设置。 */
    @ApiInternal
    public List<TextSegmentData> flatten() {
        List<TextSegmentData> out = new ArrayList<>();
        flattenInto(this, null, null, null, null, null, null,
                null, null, null, null, out);
        return out;
    }

    private static void flattenInto(TextComponent node,
            String color, Boolean bold, Boolean italic, Boolean underlined,
            Boolean strikethrough, Boolean obfuscated,
            String hoverText, ItemStackData hoverItem,
            String clickAction, String clickValue, List<TextSegmentData> out) {
        String ownColor = node.color != null ? node.color : color;
        Boolean ownBold = node.bold != null ? node.bold : bold;
        Boolean ownItalic = node.italic != null ? node.italic : italic;
        Boolean ownUnderlined = node.underlined != null ? node.underlined : underlined;
        Boolean ownStrikethrough = node.strikethrough != null ? node.strikethrough : strikethrough;
        Boolean ownObfuscated = node.obfuscated != null ? node.obfuscated : obfuscated;
        String ownHoverText = node.hoverText != null ? node.hoverText : hoverText;
        ItemStackData ownHoverItem = node.hoverItem != null ? node.hoverItem : hoverItem;
        String ownClickAction = node.clickAction != null ? node.clickAction : clickAction;
        String ownClickValue = node.clickValue != null ? node.clickValue : clickValue;
        if (!node.content.isEmpty()) {
            out.add(new TextSegmentData(node.content, ownColor, ownBold, ownItalic,
                    ownUnderlined, ownStrikethrough, ownObfuscated,
                    ownHoverText, ownHoverItem, ownClickAction, ownClickValue));
        }
        for (TextComponent child : node.children) {
            flattenInto(child, ownColor, ownBold, ownItalic, ownUnderlined,
                    ownStrikethrough, ownObfuscated, ownHoverText, ownHoverItem,
                    ownClickAction, ownClickValue, out);
        }
    }

    /** Vanilla component tree rendering used by {@code JSON.stringify};
     * unset styling keys are omitted.
     * @zh {@code JSON.stringify} 使用的原版组件树渲染；未设置的样式键省略。 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public String toJSON() {
        return ink.tuanzi.neomacros.core.util.JsonLite.write(jsonNode());
    }

    private Map<String, Object> jsonNode() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("text", content);
        if (color != null) {
            view.put("color", color);
        }
        if (bold != null) {
            view.put("bold", bold);
        }
        if (italic != null) {
            view.put("italic", italic);
        }
        if (underlined != null) {
            view.put("underlined", underlined);
        }
        if (strikethrough != null) {
            view.put("strikethrough", strikethrough);
        }
        if (obfuscated != null) {
            view.put("obfuscated", obfuscated);
        }
        if (hoverText != null) {
            Map<String, Object> hover = new LinkedHashMap<>();
            hover.put("action", "show_text");
            hover.put("contents", hoverText);
            view.put("hoverEvent", hover);
        }
        if (hoverItem != null) {
            Map<String, Object> contents = new LinkedHashMap<>();
            contents.put("id", hoverItem.id());
            contents.put("count", hoverItem.count());
            Map<String, Object> hover = new LinkedHashMap<>();
            hover.put("action", "show_item");
            hover.put("contents", contents);
            view.put("hoverEvent", hover);
        }
        if (clickAction != null) {
            Map<String, Object> click = new LinkedHashMap<>();
            click.put("action", clickAction);
            click.put("value", clickValue);
            view.put("clickEvent", click);
        }
        if (!children.isEmpty()) {
            List<Object> extra = new ArrayList<>();
            for (TextComponent child : children) {
                extra.add(child.jsonNode());
            }
            view.put("extra", extra);
        }
        return view;
    }

    @Override
    public String toString() {
        return "TextComponent(" + getPlain() + ")";
    }
}
