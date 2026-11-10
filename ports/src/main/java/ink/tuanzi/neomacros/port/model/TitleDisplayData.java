package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * One title/subtitle display about to reach the HUD overlay.
 *
 * @zh 即将上屏的标题/副标题显示。
 * @zh-param kind 显示类型：title（大标题）或 subtitle（副标题）
 * @zh-param text 显示的文本
 */
public record TitleDisplayData(String kind, ChatComponent text) {

    /** The {@code kind} value of the main title line. */
    public static final String TITLE = "title";

    /** The {@code kind} value of the subtitle line. */
    public static final String SUBTITLE = "subtitle";

    public TitleDisplayData {
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(text, "text cannot be null");
    }
}
