package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Point-in-time copy of one client boss bar: the server-driven title and
 * progress plus the bar's style and derived color formatting.
 *
 * @param uuid        boss bar identifier
 * @param name        displayed title, plain text
 * @param progress    fill fraction from 0 to 1
 * @param color       bar color identifier, e.g. {@code purple}
 * @param style       overlay style identifier, e.g. {@code progress} or
 *                    {@code notched_10}
 * @param colorValue  packed RGB value of the bar color's formatting
 * @param colorFormat formatting name of the bar color, e.g. {@code dark_purple}
 */
public record BossBarInfoData(String uuid, String name, float progress, String color,
        String style, int colorValue, String colorFormat) {

    public BossBarInfoData {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(color, "color cannot be null");
        style = style == null ? "progress" : style;
        colorFormat = colorFormat == null ? color : colorFormat;
    }
}
