package ink.tuanzi.neomacros.core.api.types;

import ink.tuanzi.neomacros.core.api.SelfConstructing;
import ink.tuanzi.neomacros.core.api.ScriptHandle;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;
import ink.tuanzi.neomacros.port.model.ItemStackData;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for immutable {@link TextComponent} values; the single
 * construction path for script-made styled text. Style setters mutate the
 * draft in place and return the same builder; {@code build()} is
 * non-destructive, so the draft stays reusable for further chaining.
 * Click actions and hover content are mutually exclusive — the latest call
 * wins.
 *
 * @zh 不可变 {@link TextComponent} 的构建器；脚本富文本的唯一构造路径。样式方法就地修改草稿并返回同一 builder；{@code build()} 非破坏——草稿可继续链式修改后再次产出。点击动作与悬停内容互斥，后设的覆盖先设的。
 * @zh-example const comp = new Component("警告：")
 *   .color("red")
 *   .bold(true)
 *   .append(new Component("库存不足").underlined())
 *   .build();
 * chat.log(comp);
 */
@ApiClass(name = "Component", kind = ApiClassKind.DATA)
@ApiSince("0.4.0")
@ApiMC("*")
@ApiLoader("*")
public final class Component implements ScriptHandle, SelfConstructing {

    private String content;
    private String color;
    private Boolean bold;
    private Boolean italic;
    private Boolean underlined;
    private Boolean strikethrough;
    private Boolean obfuscated;
    private String hoverText;
    private ItemStackData hoverItem;
    private String clickAction;
    private String clickValue;
    private final List<TextComponent> children = new ArrayList<>();

    /** Creates the builder with one literal root content.
     * @zh 以一段字面根内容创建构建器。 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component(String text) {
        this.content = text == null ? "" : text;
    }

    /** Creates the builder (engine factory instantiation; content from args).
     * @zh 创建构建器（引擎工厂实例化用；内容取自参数）。 */
    public Component() {
        this.content = "";
    }

    /** Sets the color: a vanilla formatting name or {@code #rrggbb}.
     * @zh 设置颜色：原版格式色名或 {@code #rrggbb}。
     * @zh-param value 色名或十六进制颜色 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component color(String value) {
        this.color = value;
        return this;
    }

    /** Sets bold on (default when called without arguments).
     * @zh 设为粗体（无参调用默认 true）。
     * @zh-param value true/false；缺省视为 true */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component bold(@ApiOptional Boolean value) {
        this.bold = value == null ? Boolean.TRUE : value;
        return this;
    }

    /** Sets italic on or off.
     * @zh 设置斜体的开或关。
     * @zh-param value true/false；缺省视为 true */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component italic(@ApiOptional Boolean value) {
        this.italic = value == null ? Boolean.TRUE : value;
        return this;
    }

    /** Sets underline on or off.
     * @zh 设置下划线的开或关。
     * @zh-param value true/false；缺省视为 true */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component underlined(@ApiOptional Boolean value) {
        this.underlined = value == null ? Boolean.TRUE : value;
        return this;
    }

    /** Sets strikethrough on or off.
     * @zh 设置删除线的开或关。
     * @zh-param value true/false；缺省视为 true */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component strikethrough(@ApiOptional Boolean value) {
        this.strikethrough = value == null ? Boolean.TRUE : value;
        return this;
    }

    /** Sets obfuscated on or off.
     * @zh 设置混淆字符的开或关。
     * @zh-param value true/false；缺省视为 true */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component obfuscated(@ApiOptional Boolean value) {
        this.obfuscated = value == null ? Boolean.TRUE : value;
        return this;
    }

    /** Sets a show-text hover; replaces any item hover.
     * @zh 设置文本悬停；覆盖已有的物品悬停。
     * @zh-param text 悬停文本 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component hover(String text) {
        this.hoverText = text;
        this.hoverItem = null;
        return this;
    }

    /** Sets a show-item hover; replaces any text hover. A string is the item
     * id (count 1); an ItemStack rides its full snapshot.
     * @zh 设置物品悬停；覆盖已有的文本悬停。字符串按物品 id（数量 1）；ItemStack 按完整快照。
     * @zh-param item 物品 id 或 ItemStack */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component hoverItem(@ApiType("ItemStack | string") Object item) {
        if (item instanceof ItemStack stack) {
            this.hoverItem = stack.toData();
            this.hoverText = null;
            return this;
        }
        if (item instanceof String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("item id is required");
            }
            this.hoverItem = new ItemStackData(ItemStack.namespaced(id), 1, "");
            this.hoverText = null;
            return this;
        }
        throw new IllegalArgumentException("hoverItem expects an ItemStack or item id");
    }

    /** Click opens the URL in the browser (vanilla confirmation applies).
     * @zh 点击在浏览器打开链接（原版确认提示照常）。
     * @zh-param url 目标链接 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component clickUrl(String url) {
        return click("open_url", url);
    }

    /** Click runs the command as the local player.
     * @zh 点击以本地玩家身份执行命令。
     * @zh-param command 命令 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component clickCommand(String command) {
        return click("run_command", command);
    }

    /** Click fills the command into the chat input without sending.
     * @zh 点击把命令填入聊天输入框而不发送。
     * @zh-param command 命令 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component clickSuggestCommand(String command) {
        return click("suggest_command", command);
    }

    /** Click copies the text to the clipboard.
     * @zh 点击把文本复制到剪贴板。
     * @zh-param text 要复制的文本 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component clickCopyToClipboard(String text) {
        return click("copy_to_clipboard", text);
    }

    private Component click(String action, String value) {
        this.clickAction = action;
        this.clickValue = value;
        return this;
    }

    /** Appends one child: another draft (built immediately), a built value,
     * or a plain string. Children inherit this node's styling unless they
     * set their own.
     * @zh 追加一个子节点：另一个草稿（立即 build）、已建成值或纯文本。子节点继承本节点样式，除非自行设置。
     * @zh-param child 子草稿、子组件或纯文本 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component append(@ApiType("Component | TextComponent | string") Object child) {
        if (child instanceof Component draft) {
            this.children.add(draft.build());
            return this;
        }
        if (child instanceof TextComponent value) {
            this.children.add(value);
            return this;
        }
        if (child instanceof String text) {
            this.children.add(TextComponent.of(text));
            return this;
        }
        throw new IllegalArgumentException(
                "append expects a Component, TextComponent, or string");
    }

    /** Copies content, own styling, and children from an existing value as
     * the base draft, then lets chained calls override.
     * @zh 以现有组件为底稿拷贝内容、自有样式与子节点，随后链式调用逐项覆盖。
     * @zh-param other 要拷贝的组件 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component copyFrom(TextComponent other) {
        if (other == null) {
            throw new IllegalArgumentException("component to copy from is required");
        }
        this.content = other.content;
        this.color = other.color;
        this.bold = other.bold;
        this.italic = other.italic;
        this.underlined = other.underlined;
        this.strikethrough = other.strikethrough;
        this.obfuscated = other.obfuscated;
        this.hoverText = other.hoverText;
        this.hoverItem = other.hoverItem;
        this.clickAction = other.clickAction;
        this.clickValue = other.clickValue;
        this.children.clear();
        this.children.addAll(other.children);
        return this;
    }

    /** Produces the immutable value; the draft stays usable.
     * @zh 产出不可变组件；草稿仍可继续使用。 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public TextComponent build() {
        return new TextComponent(content, color, bold, italic, underlined,
                strikethrough, obfuscated, hoverText, hoverItem,
                clickAction, clickValue, children);
    }

    @Override
    public String toString() {
        return "Component(" + content + ")";
    }

    @Override
    @ApiInternal
    public Object construct(Object[] args) {
        String text = args != null && args.length > 0 && args[0] != null
                ? args[0].toString() : null;
        if (text == null) {
            throw new IllegalArgumentException("component text is required");
        }
        return new Component(text);
    }
}
