package ink.tuanzi.neomacros.port.model;

import java.util.List;

/**
 * Combined verdict of the sign-edit sinks: skip opening the editor
 * ({@code canceled} or {@code closeScreen}), and optionally replace the text
 * with {@code lines}. A null {@code lines} keeps the original text.
 *
 * @zh 告示牌编辑的综合裁决：{@code canceled}/{@code closeScreen} 跳过打开编辑界面；{@code lines} 非空时替换文本。
 */
public record SignEditVerdict(boolean canceled, List<String> lines, boolean closeScreen) {

    /** The pass-through verdict: open the editor with the original text. */
    public static final SignEditVerdict PASS = new SignEditVerdict(false, null, false);

    /** The cancel verdict: skip the editor and submit the (possibly replaced) text. */
    public static final SignEditVerdict CANCEL = new SignEditVerdict(true, null, false);
}
