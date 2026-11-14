package ink.tuanzi.neomacros.adapter;

import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;

import java.util.Locale;

/**
 * Legacy formatting metadata the script face reads off colors: the packed
 * RGB value, the lowercase name, the legacy color index, and the team
 * colour lookup. Minecraft 26.2 dropped the colour fields from
 * {@link ChatFormatting} (values moved to {@code TextColor}, team colours to
 * {@code TeamColor}), so the gated branches below keep the pre-26.2 shape on
 * every node.
 */
public final class FormattingValues {

    private FormattingValues() {
    }

    /**
     * The packed RGB value of one of the sixteen colors; modifiers and
     * {@code RESET} answer -1.
     */
    public static int colorValue(ChatFormatting formatting) {
        //? if >=26.2 {
        var color = net.minecraft.network.chat.TextColor.fromLegacyFormat(formatting);
        return color == null ? -1 : color.getValue();
        //?} else {
        /* Integer color = formatting.getColor();
        return color == null ? -1 : color; */
        //?}
    }

    /** The lowercase name of one formatting, e.g. {@code dark_purple}. */
    public static String colorName(ChatFormatting formatting) {
        return formatting.name().toLowerCase(Locale.ROOT);
    }

    /** The legacy color index 0-15, or -1 for a modifier or {@code RESET}. */
    public static int colorIndex(ChatFormatting formatting) {
        return formatting.ordinal() < 16 ? formatting.ordinal() : -1;
    }

    /**
     * The team's colour as a legacy formatting; a team without a colour
     * answers {@code RESET} (the pre-26.2 shape the script face keeps).
     */
    public static ChatFormatting teamColor(PlayerTeam team) {
        //? if >=26.2 {
        return team.getColor()
                .map(color -> ChatFormatting.valueOf(color.name()))
                .orElse(ChatFormatting.RESET);
        //?} else {
        /* return team.getColor() == null ? ChatFormatting.RESET : team.getColor(); */
        //?}
    }
}
