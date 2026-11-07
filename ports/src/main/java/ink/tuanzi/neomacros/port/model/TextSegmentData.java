package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * One styled text segment: literal text plus optional color, decorations,
 * hover content, and click action. Neutral value object; color is a named
 * formatting color or {@code #rrggbb}.
 *
 * @param text          literal content
 * @param color         named color or {@code #rrggbb}, null for inherited
 * @param bold          bold flag, null for inherited
 * @param italic        italic flag, null for inherited
 * @param underlined    underline flag, null for inherited
 * @param strikethrough strike flag, null for inherited
 * @param obfuscated    obfuscation flag, null for inherited
 * @param hoverText     show-text hover content, null when absent
 * @param hoverItem     show-item hover content, null when absent
 * @param clickCommand  run-command action, null when absent
 * @param clickUrl      open-url action, null when absent
 */
public record TextSegmentData(String text, String color, Boolean bold, Boolean italic,
        Boolean underlined, Boolean strikethrough, Boolean obfuscated,
        String hoverText, ItemStackData hoverItem, String clickCommand, String clickUrl) {

    public TextSegmentData {
        Objects.requireNonNull(text, "text cannot be null");
    }

    /** Convenience constructor for segments without an item hover. */
    public TextSegmentData(String text, String color, Boolean bold, Boolean italic,
            Boolean underlined, Boolean strikethrough, Boolean obfuscated,
            String hoverText, String clickCommand, String clickUrl) {
        this(text, color, bold, italic, underlined, strikethrough, obfuscated,
                hoverText, null, clickCommand, clickUrl);
    }
}
