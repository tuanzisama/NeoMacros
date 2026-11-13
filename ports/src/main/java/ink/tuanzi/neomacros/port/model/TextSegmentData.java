package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * One styled text segment: literal text plus optional color, decorations,
 * hover content, and click action. Neutral render instruction consumed by
 * the chat port; color is a named formatting color or {@code #rrggbb}.
 *
 * <p>{@code clickAction} uses the vanilla action vocabulary
 * ({@code run_command}, {@code open_url}, {@code suggest_command},
 * {@code copy_to_clipboard}, {@code change_page}, {@code open_file});
 * {@code clickValue} carries its target.</p>
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
 * @param hoverEntity   show-entity hover capture, null when absent
 */
public record TextSegmentData(String text, String color, Boolean bold, Boolean italic,
        Boolean underlined, Boolean strikethrough, Boolean obfuscated,
        String hoverText, ItemStackData hoverItem, String clickAction, String clickValue,
        EntityHoverData hoverEntity) {

    public TextSegmentData {
        Objects.requireNonNull(text, "text cannot be null");
    }

    /** Segment without a show-entity hover (callers predating that field).
     * @zh 无 show-entity 悬停的分段（该字段之前的老调用方）。 */
    public TextSegmentData(String text, String color, Boolean bold, Boolean italic,
            Boolean underlined, Boolean strikethrough, Boolean obfuscated,
            String hoverText, ItemStackData hoverItem, String clickAction, String clickValue) {
        this(text, color, bold, italic, underlined, strikethrough, obfuscated,
                hoverText, hoverItem, clickAction, clickValue, null);
    }
}
