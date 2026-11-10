package ink.tuanzi.neomacros.port.model;

/**
 * Combined verdict of the display sinks: suppress the display
 * ({@code canceled}), and optionally replace the text with {@code text}.
 * A null {@code text} keeps the original component.
 *
 * @zh 显示源的综合裁决：{@code canceled} 抑制显示；{@code text} 非空时替换文本。
 */
public record TextVerdict(boolean canceled, ChatComponent text) {

    /** The pass-through verdict: display the original text. */
    public static final TextVerdict PASS = new TextVerdict(false, null);

    /** The cancel verdict: suppress the display. */
    public static final TextVerdict CANCEL = new TextVerdict(true, null);
}
