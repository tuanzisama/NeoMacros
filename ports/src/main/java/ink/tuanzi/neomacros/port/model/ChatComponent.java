package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable rich-text node of the neutral chat component model.
 *
 * <p>{@code color} is a CSS-style name or hex form ({@code "red"},
 * {@code "#ff5500"}) or {@code null} for inherited; the style flags are
 * {@code null} for inherited, otherwise an explicit on/off. The
 * {@code children} list nests further components; the constructor copies it
 * defensively and the stored list is unmodifiable.</p>
 */
public record ChatComponent(String text, String color,
                            Boolean bold, Boolean italic, Boolean underlined, Boolean strikethrough,
                            Boolean obfuscated, List<ChatComponent> children) {

    public ChatComponent {
        Objects.requireNonNull(text, "text cannot be null");
        children = List.copyOf(children);
    }

    /** Component with the given plain text and all styling inherited. */
    public static ChatComponent plain(String text) {
        return new ChatComponent(text, null, null, null, null, null, null, List.of());
    }
}
