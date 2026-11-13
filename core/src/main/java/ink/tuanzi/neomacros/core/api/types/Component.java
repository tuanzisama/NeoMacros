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
import ink.tuanzi.neomacros.port.model.EntityHoverData;
import ink.tuanzi.neomacros.port.model.ItemStackData;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for immutable {@link TextComponent} values; the single
 * construction path for script-made styled text. Style setters mutate the
 * draft in place and return the same builder; {@code build()} is
 * non-destructive, so the draft stays reusable for further chaining.
 * Hover content is single-valued: the text, item, and entity hovers replace
 * one another, latest call wins.
 *
 * @zh 不可变 {@link TextComponent} 的构建器；脚本富文本的唯一构造路径。样式方法就地修改草稿并返回同一 builder；{@code build()} 非破坏——草稿可继续链式修改后再次产出。悬停内容单值：文本/物品/实体悬停互相覆盖，后设的覆盖先设的。
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
    private EntityHoverData hoverEntity;
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

    /** Sets a show-text hover; replaces any item or entity hover.
     * @zh 设置文本悬停；覆盖已有的物品与实体悬停。
     * @zh-param text 悬停文本 */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component hover(String text) {
        this.hoverText = text;
        this.hoverItem = null;
        this.hoverEntity = null;
        return this;
    }

    /** Sets a show-item hover; replaces any text or entity hover. A string is the item
     * id (count 1); an ItemStack rides its full snapshot.
     * @zh 设置物品悬停；覆盖已有的文本与实体悬停。字符串按物品 id（数量 1）；ItemStack 按完整快照。
     * @zh-param item 物品 id 或 ItemStack */
    @ApiSince("0.4.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component hoverItem(@ApiType("ItemStack | string") Object item) {
        if (item instanceof ItemStack stack) {
            this.hoverItem = stack.toData();
            this.hoverText = null;
            this.hoverEntity = null;
            return this;
        }
        if (item instanceof String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("item id is required");
            }
            this.hoverItem = new ItemStackData(ItemStack.namespaced(id), 1, "");
            this.hoverText = null;
            this.hoverEntity = null;
            return this;
        }
        throw new IllegalArgumentException("hoverItem expects an ItemStack or item id");
    }

    /**
     * Sets a show-entity hover; replaces any text or item hover. The entity
     * type and display name are captured now, so a later unload keeps the
     * tooltip the hover was built with.
     *
     * @zh 设置实体悬停；覆盖已有的文本与物品悬停。实体类型与显示名在此刻捕获，之后实体卸载也保留建立悬停时的提示。
     * @zh-param entity 实体句柄 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component hoverEntity(ink.tuanzi.neomacros.core.api.entity.EntityApi.Entity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("hoverEntity expects an entity handle");
        }
        String type = entity.getType();
        if (type == null) {
            throw new IllegalArgumentException(
                    "hoverEntity needs an entity present in the world (type read at call time)");
        }
        String uuid = entity.getUuid();
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("hoverEntity needs an entity with a UUID");
        }
        String name = entity.getName();
        this.hoverEntity = new EntityHoverData(uuid, type, name == null ? "" : name);
        this.hoverText = null;
        this.hoverItem = null;
        return this;
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

    /**
     * Sets any built-in click action by name; the action is matched
     * case-insensitively and the value is checked against the action.
     * Supported: {@code open_url}, {@code run_command},
     * {@code suggest_command}, {@code copy_to_clipboard},
     * {@code change_page} (decimal page number), {@code open_file}.
     *
     * @zh 按名称设置任一内置点击动作；动作名大小写不敏感，取值按动作校验。支持 {@code open_url}、{@code run_command}、{@code suggest_command}、{@code copy_to_clipboard}、{@code change_page}（十进制页码）、{@code open_file}。
     * @zh-param action 原版点击动作名
     * @zh-param value 动作目标 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component click(String action, String value) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("click action is required");
        }
        if (value == null) {
            throw new IllegalArgumentException("click action value is required");
        }
        String normalized = action.toLowerCase(java.util.Locale.ROOT);
        switch (normalized) {
            case "open_url", "run_command", "suggest_command", "copy_to_clipboard",
                    "open_file" -> {
                // value carries the target as-is
            }
            case "change_page" -> {
                try {
                    Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "change_page expects a decimal page number, got: " + value);
                }
            }
            default -> throw new IllegalArgumentException("unsupported click action: " + action);
        }
        this.clickAction = normalized;
        this.clickValue = value;
        return this;
    }

    /**
     * Replaces this section's whole style — color, decorations, hover, and
     * click — with the style of an existing value; content and children are
     * untouched.
     *
     * @zh 用现有组件的样式整体替换本节样式——颜色、装饰、悬停与点击——内容与子节点不变。
     * @zh-param source 提供样式的组件 */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public Component style(TextComponent source) {
        if (source == null) {
            throw new IllegalArgumentException("style source is required");
        }
        this.color = source.color;
        this.bold = source.bold;
        this.italic = source.italic;
        this.underlined = source.underlined;
        this.strikethrough = source.strikethrough;
        this.obfuscated = source.obfuscated;
        this.hoverText = source.hoverText;
        this.hoverItem = source.hoverItem;
        this.hoverEntity = source.hoverEntity;
        this.clickAction = source.clickAction;
        this.clickValue = source.clickValue;
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
        this.hoverEntity = other.hoverEntity;
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
                strikethrough, obfuscated, hoverText, hoverItem, hoverEntity,
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
