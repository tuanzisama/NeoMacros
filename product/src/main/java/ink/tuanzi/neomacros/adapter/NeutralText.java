package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bidirectional mapping between vanilla components and the neutral chat
 * component model. The neutral form keeps render-equivalent tri-states: a
 * decoration that resolves to the inherited value is stored as inherited
 * (null), so the vanilla inheritance chain survives the round trip.
 */
final class NeutralText {

    private NeutralText() {
    }

    /** Maps one vanilla component tree onto the neutral model. */
    static ChatComponent toModel(Component component) {
        return toModel(component, ChatComponent.plain(""));
    }

    /** Maps one node, carrying the parent's resolved styles down the tree. */
    private static ChatComponent toModel(Component node, ChatComponent inherited) {
        Style style = node.getStyle();
        String text = node.getContents() instanceof PlainTextContents plain
                ? plain.text() : node.getString();
        String ownColor = style.getColor() != null ? style.getColor().serialize() : null;
        Boolean ownBold = own(style.isBold(), inherited.bold());
        Boolean ownItalic = own(style.isItalic(), inherited.italic());
        Boolean ownUnderlined = own(style.isUnderlined(), inherited.underlined());
        Boolean ownStrikethrough = own(style.isStrikethrough(), inherited.strikethrough());
        Boolean ownObfuscated = own(style.isObfuscated(), inherited.obfuscated());
        String color = ownColor != null ? ownColor : inherited.color();
        Boolean bold = ownBold != null ? ownBold : inherited.bold();
        Boolean italic = ownItalic != null ? ownItalic : inherited.italic();
        Boolean underlined = ownUnderlined != null ? ownUnderlined : inherited.underlined();
        Boolean strikethrough = ownStrikethrough != null ? ownStrikethrough : inherited.strikethrough();
        Boolean obfuscated = ownObfuscated != null ? ownObfuscated : inherited.obfuscated();
        // The record copies the children list at construction; map the
        // siblings before building the node.
        List<ChatComponent> children = new ArrayList<>();
        for (Component sibling : node.getSiblings()) {
            children.add(toModel(sibling, new ChatComponent(text, color, bold,
                    italic, underlined, strikethrough, obfuscated, List.of())));
        }
        return new ChatComponent(text, color, bold, italic, underlined,
                strikethrough, obfuscated, children);
    }

    /** Explicit tri-state: equal to the inherited value means inherited. */
    private static Boolean own(boolean resolved, Boolean inherited) {
        boolean inheritedValue = inherited != null && inherited;
        return resolved == inheritedValue ? null : resolved;
    }

    /** Rebuilds one vanilla component tree from the neutral model. */
    static Component toNative(ChatComponent component) {
        var out = Component.literal(component.text());
        Style style = Style.EMPTY;
        if (component.color() != null) {
            style = withColor(style, component.color());
        }
        if (component.bold() != null) {
            style = style.withBold(component.bold());
        }
        if (component.italic() != null) {
            style = style.withItalic(component.italic());
        }
        if (component.underlined() != null) {
            style = style.withUnderlined(component.underlined());
        }
        if (component.strikethrough() != null) {
            style = style.withStrikethrough(component.strikethrough());
        }
        if (component.obfuscated() != null) {
            style = style.withObfuscated(component.obfuscated());
        }
        var styled = out.withStyle(style);
        for (ChatComponent child : component.children()) {
            styled.append(toNative(child));
        }
        return styled;
    }

    /** Resolves a named formatting color or a #rrggbb hex color. */
    static Style withColor(Style style, String color) {
        // Named colors: ChatFormatting is an enum on every node; the lookup
        // accessor renames across versions, so try both spellings.
        try {
            var byName = net.minecraft.ChatFormatting.valueOf(color.toUpperCase(Locale.ROOT));
            return style.withColor(byName);
        } catch (IllegalArgumentException ignored) {
            // not a named formatting color
        }
        // Hex form: #rrggbb
        var parsed = TextColor.parseColor(color).result().orElse(null);
        return parsed != null ? style.withColor(parsed) : style;
    }
}
