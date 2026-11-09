package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * One styled text segment: literal text plus optional color, decorations,
 * hover content, and click action. Neutral render instruction consumed by
 * the chat port; color is a named formatting color or {@code #rrggbb}.
 *
 * <p>{@code clickAction} uses the vanilla action vocabulary
 * ({@code run_command}, {@code open_url}, {@code suggest_command},
 * {@code copy_to_clipboard}); {@code clickValue} carries its target.</p>
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
 * @param clickAction   vanilla click action name, null when absent
 * @param clickValue    click action target, null when absent
 */
public record TextSegmentData(String text, String color, Boolean bold, Boolean italic,
        Boolean underlined, Boolean strikethrough, Boolean obfuscated,
        String hoverText, ItemStackData hoverItem, String clickAction, String clickValue) {

    public TextSegmentData {
        Objects.requireNonNull(text, "text cannot be null");
    }
}
