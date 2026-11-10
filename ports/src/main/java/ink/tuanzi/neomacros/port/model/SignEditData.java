package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;

/**
 * The sign-edit editor about to open: block position, face, and the current
 * four lines of text as shown to the player.
 *
 * @zh 即将打开的告示牌编辑界面：方块坐标、正面/背面与当前四行文本。
 */
public record SignEditData(int x, int y, int z, boolean front, List<String> lines) {

    public SignEditData {
        Objects.requireNonNull(lines, "lines cannot be null");
        lines = List.copyOf(lines);
    }
}
