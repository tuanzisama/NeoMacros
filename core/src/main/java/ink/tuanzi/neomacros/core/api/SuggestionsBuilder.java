package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.types.BlockPos;
import ink.tuanzi.neomacros.core.api.types.TextComponent;
import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiReadOnly;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The completion builder handed to a slot's {@code suggests} callback while
 * the player tabs through a command. It reads as a flat handle: {@code input}
 * is the command line typed so far, {@code start} is the offset of the token
 * being completed, and {@code remaining} is the unfinished tail from that
 * offset. The {@code suggest*} methods queue completions for that token and
 * return the same builder for chaining; queued entries reach the game's
 * suggestion list when the callback returns.
 *
 * <p>This is the single completion surface of {@code command.register};
 * no parallel builder grows here.
 *
 * @zh 交给参数槽 {@code suggests} 回调的补全构建器（玩家 Tab 补全命令时）。以扁平句柄呈现：{@code input} 为已输入的命令行，{@code start} 为正在补全的 token 起点，{@code remaining} 为该起点之后未完成的尾串。{@code suggest*} 系列方法为当前 token 排入候选并返回同一构建器以供链式调用；回调返回后排入项进入游戏建议列表。
 * @zh 这是 {@code command.register} 唯一的补全面，不在此生长第二套 builder。
 */
@ApiClass(name = "SuggestionsBuilder", kind = ApiClassKind.HANDLE)
@ApiSince("0.5.0")
@ApiMC("*")
@ApiLoader("*")
public final class SuggestionsBuilder implements ScriptHandle {

    /**
     * One queued completion: the text offered plus an optional tooltip shown
     * beside it. Host-side transport for the adapter's replay step.
     *
     * @zh 一条排入的候选项：候选文本加可选悬浮提示。供适配器回放使用的宿主侧载体。
     */
    @ApiInternal
    public record Entry(String text, TextComponent tooltip) {
    }

    private final String input;
    private final int start;
    private final List<Entry> entries = new ArrayList<>();

    /** Creates a builder over one command line and completion offset. */
    SuggestionsBuilder(String input, int start) {
        this.input = input == null ? "" : input;
        this.start = Math.max(0, Math.min(start, this.input.length()));
    }

    /**
     * The command line typed so far (truncated at the cursor).
     *
     * @zh 截至光标已输入的命令行。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getInput() {
        return input;
    }

    /**
     * The character offset in {@link #getInput()} where the token being
     * completed starts.
     *
     * @zh 正在补全的 token 在 {@link #getInput()} 中的起始偏移。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public int getStart() {
        return start;
    }

    /**
     * The unfinished text from {@link #getStart()} to the cursor.
     *
     * @zh 从 {@link #getStart()} 到光标之间未完成的文本。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getRemaining() {
        return input.substring(start);
    }

    /**
     * {@link #getRemaining()} lowercased, for prefix matching.
     *
     * @zh {@link #getRemaining()} 的小写形式，用于前缀匹配。
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    @ApiReadOnly
    public String getRemainingLowerCase() {
        return input.substring(start).toLowerCase(Locale.ROOT);
    }

    /**
     * Offers one completion: a string, or a number rendered in decimal.
     * A value identical to the remaining text is dropped, matching the game's
     * own suggestion filter.
     *
     * @zh 排入一条候选：字符串，或按十进制渲染的数字。与剩余文本完全相同的值会被丢弃，与游戏自身的建议过滤一致。
     * @zh-param value 候选文本或数字
     * @zh-return 同一构建器（供链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public SuggestionsBuilder suggest(@ApiType("string | number") Object value) {
        if (value instanceof Number number) {
            offer(formatNumber(number), null);
            return this;
        }
        if (value instanceof String text) {
            offer(text, null);
            return this;
        }
        throw new IllegalArgumentException(
                "suggest(...) takes a string or a number, not " + value);
    }

    /**
     * Offers one completion with a tooltip shown beside it.
     *
     * @zh 排入一条带悬浮提示的候选。
     * @zh-param value 候选文本或数字
     * @zh-param tooltip 悬浮提示文本
     * @zh-return 同一构建器（供链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public SuggestionsBuilder suggestWithTooltip(@ApiType("string | number") Object value,
                                                TextComponent tooltip) {
        if (tooltip == null) {
            throw new IllegalArgumentException("suggestWithTooltip(...) needs a tooltip");
        }
        String text = value instanceof Number number
                ? formatNumber(number) : String.valueOf(value);
        offer(text, tooltip);
        return this;
    }

    /**
     * Offers every candidate that starts with the text being completed.
     *
     * @zh 排入所有以当前输入为前缀的候选。
     * @zh-param values 候选字符串数组
     * @zh-return 同一构建器（供链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public SuggestionsBuilder suggestMatching(
            @ApiType("string[]") Collection<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("suggestMatching(...) needs candidates");
        }
        String prefix = getRemainingLowerCase();
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException("suggestMatching(...) takes strings");
            }
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                offer(value, null);
            }
        }
        return this;
    }

    /**
     * Offers every registry identifier that starts with the text being
     * completed; entries that are not resource-location shaped are skipped
     * instead of failing the batch.
     *
     * @zh 排入所有以当前输入为前缀的注册标识符；不符合资源位置形态的条目跳过，不使整批失败。
     * @zh-param values 标识符数组（如 {@code minecraft:stone}）
     * @zh-return 同一构建器（供链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public SuggestionsBuilder suggestIdentifier(
            @ApiType("string[]") Collection<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("suggestIdentifier(...) needs identifiers");
        }
        String prefix = getRemainingLowerCase();
        for (String value : values) {
            if (value == null || !CommandArg.isIdentifierText(value)) {
                continue;
            }
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                offer(value, null);
            }
        }
        return this;
    }

    /**
     * Offers block positions as {@code x y z} coordinate strings.
     *
     * @zh 以 {@code x y z} 坐标串的形式排入方块位置候选。
     * @zh-param positions 方块位置数组
     * @zh-return 同一构建器（供链式调用）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public SuggestionsBuilder suggestBlockPositions(Collection<BlockPos> positions) {
        if (positions == null) {
            throw new IllegalArgumentException("suggestBlockPositions(...) needs positions");
        }
        List<String> formatted = new ArrayList<>(positions.size());
        for (BlockPos position : positions) {
            if (position == null) {
                throw new IllegalArgumentException(
                        "suggestBlockPositions(...) takes positions, not null");
            }
            formatted.add(position.getIntX() + " " + position.getIntY()
                    + " " + position.getIntZ());
        }
        return suggestPositions(formatted);
    }

    /**
     * Offers coordinate strings, each with two or three whitespace-separated
     * components and a common component count across the call. Candidates are
     * filtered against the text being completed, so a partially typed
     * coordinate keeps matching.
     *
     * @zh 排入坐标串候选：每串为二或三个空白分隔的分量，且整批分量数一致。候选按当前输入前缀过滤，部分输入的坐标仍可命中。
     * @zh-param positions 坐标串数组（如 {@code "0 64 0"}）
     * @zh-return 同一构建器（供链式调用）
     * @zh-throws IllegalArgumentException 坐标分量数非法或整批不一致
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public SuggestionsBuilder suggestPositions(
            @ApiType("string[]") Collection<String> positions) {
        if (positions == null) {
            throw new IllegalArgumentException("suggestPositions(...) needs coordinates");
        }
        int dimensions = -1;
        List<String> validated = new ArrayList<>(positions.size());
        for (String position : positions) {
            if (position == null) {
                throw new IllegalArgumentException("suggestPositions(...) takes strings");
            }
            String[] components = position.trim().split("\\s+");
            if (components.length != 2 && components.length != 3) {
                throw new IllegalArgumentException(
                        "A position must have two or three coordinates");
            }
            if (dimensions == -1) {
                dimensions = components.length;
            } else if (components.length != dimensions) {
                throw new IllegalArgumentException(
                        "Positions must all have the same number of coordinates");
            }
            validated.add(String.join(" ", components));
        }
        String prefix = getRemainingLowerCase();
        for (String value : validated) {
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                offer(value, null);
            }
        }
        return this;
    }

    /**
     * The queued completions for this request. Host-side transport for the
     * adapter; scripts read suggestions only through their callback.
     *
     * @zh 本次请求排入的候选项。宿主侧载体；脚本只经回调读取建议。
     */
    @ApiInternal
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** Queues one text entry unless it equals the remaining input. */
    private void offer(String text, TextComponent tooltip) {
        if (text.isEmpty() || text.equals(getRemaining())) {
            return;
        }
        entries.add(new Entry(text, tooltip));
    }

    /** Renders integral numbers without a decimal tail; others as-is. */
    private static String formatNumber(Number number) {
        double value = number.doubleValue();
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf(number.longValue());
        }
        return String.valueOf(value);
    }
}
