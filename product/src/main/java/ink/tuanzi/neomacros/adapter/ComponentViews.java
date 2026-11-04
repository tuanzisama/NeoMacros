package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts MC chat components into the neutral rich-text model the engine's
 * console formatter serves to the panel: per-node literal text with resolved
 * style (color as a CSS-style name or hex form, explicit style flags). This
 * class compiles inside the product node, so the game type never leaks into
 * the version-independent layers — they only ever see the neutral record.
 */
public final class ComponentViews {

    private ComponentViews() {
    }

    /** The neutral rich-text view of a host value; null for non-components. */
    public static ChatComponent toChatView(Object value) {
        return value instanceof Component component ? view(component) : null;
    }

    private static ChatComponent view(Component component) {
        List<ChatComponent> children = new ArrayList<>();
        for (Component child : component.getSiblings()) {
            children.add(view(child));
        }
        Style style = component.getStyle();
        return new ChatComponent(literalText(component), color(style),
                style.isBold(), style.isItalic(), style.isUnderlined(),
                style.isStrikethrough(), style.isObfuscated(), children);
    }

    /**
     * Per-node text: the literal fast path keeps nested colors distinct;
     * translatable and other dynamic contents fall back to the flattened
     * subtree text (siblings' share is stripped when it cleanly suffixes).
     */
    private static String literalText(Component component) {
        if (component.getContents() instanceof PlainTextContents.LiteralContents literal) {
            return literal.text();
        }
        String full = component.getString();
        String head = full;
        for (Component sibling : component.getSiblings()) {
            String tail = sibling.getString();
            if (head.endsWith(tail) && head.length() >= tail.length()) {
                head = head.substring(0, head.length() - tail.length());
            } else {
                return full;
            }
        }
        return head;
    }

    /** The CSS-style color of one node's style, or null for inherited. */
    private static String color(Style style) {
        TextColor textColor = style.getColor();
        return textColor == null ? null : textColor.serialize();
    }
}
