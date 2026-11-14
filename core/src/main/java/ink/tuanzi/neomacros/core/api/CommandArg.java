package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.doc.api.ApiClass;
import ink.tuanzi.neomacros.doc.api.ApiClassKind;
import ink.tuanzi.neomacros.doc.api.ApiInternal;
import ink.tuanzi.neomacros.doc.api.ApiLoader;
import ink.tuanzi.neomacros.doc.api.ApiMC;
import ink.tuanzi.neomacros.doc.api.ApiOptional;
import ink.tuanzi.neomacros.doc.api.ApiSince;
import ink.tuanzi.neomacros.doc.api.ApiType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One typed argument slot of a registered command. Slots build through the
 * dynamic {@code command.arg} factory ({@code int() long() float() bool()
 * word() string() greedy() player() pos() uuid() identifier() time()
 * literal()}) and chain {@code optional()}, {@code default(v)} and the
 * numeric {@code range(min, max)}. Parsing walks slots in declaration order
 * against the raw remainder; quoted {@code string} slots accept spaces,
 * {@code greedy} consumes everything that is left, and {@code literal}
 * requires its fixed word.
 *
 * <p>This is the single argument vocabulary of the declarative
 * {@code command.register} surface; no parallel builder grows here.
 *
 * @zh 注册命令的一个类型化参数槽。槽位经动态 {@code command.arg} 工厂构建（{@code int() long() float() bool() word() string() greedy() player() pos() uuid() identifier() time() literal()}），可链式 {@code optional()}、{@code default(v)} 与数值 {@code range(min, max)}。解析按声明顺序对原始余串进行；带引号的 {@code string} 槽可含空格，{@code greedy} 吞掉剩余全部，{@code literal} 要求固定词完全一致。
 * @zh 这是声明式 {@code command.register} 面唯一的参数词汇表，不再生长第二套 builder。
 */
@ApiClass(name = "CommandArg", kind = ApiClassKind.HANDLE)
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class CommandArg {

    /** The supported slot shapes. */
    public enum Kind {
        INT, LONG, FLOAT, BOOL, WORD, STRING, GREEDY, PLAYER, POS,
        UUID, IDENTIFIER, TIME, LITERAL
    }

    /** Member names the {@code command.arg} factory answers to. */
    public static final List<String> KIND_NAMES = List.of(
            "int", "long", "float", "bool", "word", "string", "greedy",
            "player", "pos", "uuid", "identifier", "time", "literal");

    /** Resource-location shape: optional {@code namespace:} then the path. */
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+");

    /** Duration units accepted by the {@code time} slot (brigadier syntax). */
    private static final Map<String, Integer> TIME_UNITS = Map.of(
            "t", 1, "s", 20, "d", 24000);

    private final Kind kind;
    private final boolean optional;
    private final Object fallback;
    /** Inclusive bounds for numeric slots; {@code null} means unbounded. */
    private final Number rangeMin;
    private final Number rangeMax;
    /** Required word for {@code literal} slots; {@code null} = the slot key. */
    private final String literalWord;
    /**
     * Completion metadata: a candidate string list or a script callback
     * ({@code Consumer<Object[]>}); {@code null} means none.
     */
    private final Object suggestSpec;

    private CommandArg(Kind kind, boolean optional, Object fallback,
                       Number rangeMin, Number rangeMax, String literalWord,
                       Object suggestSpec) {
        this.kind = kind;
        this.optional = optional;
        this.fallback = fallback;
        this.rangeMin = rangeMin;
        this.rangeMax = rangeMax;
        this.literalWord = literalWord;
        this.suggestSpec = suggestSpec;
    }

    /** Builds one slot from a factory member name; unknown names raise. */
    @ApiInternal
    public static CommandArg of(String memberName) {
        return of(memberName, null);
    }

    /**
     * Builds one slot from a factory member name plus the factory arguments
     * the script passed ({@code literal('set')} carries its required word).
     */
    @ApiInternal
    public static CommandArg of(String memberName, Object[] factoryArgs) {
        Kind kind = switch (memberName.toLowerCase(Locale.ROOT)) {
            case "int" -> Kind.INT;
            case "long" -> Kind.LONG;
            case "float" -> Kind.FLOAT;
            case "bool" -> Kind.BOOL;
            case "word" -> Kind.WORD;
            case "string" -> Kind.STRING;
            case "greedy" -> Kind.GREEDY;
            case "player" -> Kind.PLAYER;
            case "pos" -> Kind.POS;
            case "uuid" -> Kind.UUID;
            case "identifier" -> Kind.IDENTIFIER;
            case "time" -> Kind.TIME;
            case "literal" -> Kind.LITERAL;
            default -> throw new IllegalArgumentException("unknown argument kind: "
                    + memberName);
        };
        String literalWord = null;
        if (kind == Kind.LITERAL && factoryArgs != null && factoryArgs.length > 0
                && factoryArgs[0] != null) {
            literalWord = String.valueOf(factoryArgs[0]);
        }
        return new CommandArg(kind, false, null, null, null, literalWord, null);
    }

    /** Marks the slot optional (missing input falls back, never raises).
     * @zh 将槽位标记为可选（输入缺失时回退，不抛错）。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CommandArg optional() {
        return new CommandArg(kind, true, fallback, rangeMin, rangeMax, literalWord,
                suggestSpec);
    }

    /** Supplies the fallback value for an optional slot.
     * @zh 为可选槽位提供回退值。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CommandArg defaultTo(@ApiType("number | string | boolean") Object value) {
        return new CommandArg(kind, true, value, rangeMin, rangeMax, literalWord,
                suggestSpec);
    }

    /**
     * Bounds a numeric slot ({@code int}, {@code long}, {@code float}) to the
     * inclusive {@code [min, max]} interval; out-of-range input is rejected
     * while parsing.
     *
     * @zh 将数值槽（{@code int} {@code long} {@code float}）限制在闭区间 {@code [min, max]}，越界输入在解析时被拒绝。
     * @zh-return 新的带边界槽位（原槽位不变）
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CommandArg range(@ApiType("number") Number min,
                            @ApiType("number") Number max) {
        if (kind != Kind.INT && kind != Kind.LONG && kind != Kind.FLOAT) {
            throw new IllegalArgumentException(
                    "range() applies to int/long/float slots, not " + kind);
        }
        if (min == null || max == null) {
            throw new IllegalArgumentException("range() needs both bounds");
        }
        if (compareBounds(min, max) > 0) {
            throw new IllegalArgumentException(
                    "range() min must not exceed max: " + min + " > " + max);
        }
        return new CommandArg(kind, optional, fallback, min, max, literalWord,
                suggestSpec);
    }

    /**
     * Attaches completion metadata to the slot: either a fixed candidate
     * list, or a callback receiving the {@link SuggestionsBuilder} while the
     * player tabs through this slot.
     *
     * @zh 为槽位挂载补全元数据：固定候选列表，或在玩家 Tab 到该槽位时收到 {@link SuggestionsBuilder} 的回调。
     * @zh-param value 候选字符串数组，或 {@code (builder) => void} 回调
     * @zh-return 新的带补全槽位（原槽位不变）
     * @zh-throws IllegalArgumentException 传入的不是字符串数组或回调
     */
    @ApiSince("0.5.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CommandArg suggests(
            @ApiType("string[] | ((builder: SuggestionsBuilder) => void)") Object value) {
        if (value instanceof java.util.List<?> list) {
            for (Object element : list) {
                if (!(element instanceof String)) {
                    throw new IllegalArgumentException(
                            "suggests(...) candidate lists take strings");
                }
            }
            return new CommandArg(kind, optional, fallback, rangeMin, rangeMax,
                    literalWord, List.copyOf(list));
        }
        if (value instanceof java.util.function.Consumer<?>) {
            return new CommandArg(kind, optional, fallback, rangeMin, rangeMax,
                    literalWord, value);
        }
        throw new IllegalArgumentException(
                "suggests(...) takes a string array or a callback");
    }

    /** The attached completion metadata (list or callback), or {@code null}. */
    @ApiInternal
    public Object suggestSpec() {
        return suggestSpec;
    }

    /**
     * Builds the completion for one command line: locates the token under
     * the cursor within the tail after {@code tailStart}, then queues the
     * slot's metadata for that position — a script callback receives the
     * builder, a fixed list is prefix-filtered, and a literal slot offers
     * its fixed word. Input beyond the declared slots queues nothing.
     *
     * @zh 构造一行命令的补全：定位 {@code tailStart} 之后光标处的 token，再按该位置排入槽位元数据——脚本回调收到构建器、固定列表按前缀过滤、literal 槽提供其固定词。超出已声明槽位的输入不排入任何候选。
     * @zh-param slots 声明顺序的槽位表
     * @zh-param input 已输入的命令行（截断至光标）
     * @zh-param tailStart 命令名之后余串的起始偏移
     * @zh-return 已排入候选的补全构建器
     */
    @ApiInternal
    public static SuggestionsBuilder suggestAt(Map<String, CommandArg> slots,
                                               String input, int tailStart) {
        String line = input == null ? "" : input;
        int from = Math.max(0, Math.min(tailStart, line.length()));
        String tail = line.substring(from);
        List<Token> tokens;
        try {
            tokens = tail.isBlank() ? List.of() : tokenize(tail);
        } catch (IllegalArgumentException malformed) {
            // An unterminated quote is the handler's problem to report;
            // completion stays silent instead of failing the whole request.
            return new SuggestionsBuilder(line, line.length());
        }
        int tokenStart;
        int tokenIndex;
        if (tail.isEmpty() || Character.isWhitespace(tail.charAt(tail.length() - 1))) {
            tokenStart = line.length();
            tokenIndex = tokens.size();
        } else {
            Token last = tokens.get(tokens.size() - 1);
            tokenStart = from + last.start();
            tokenIndex = tokens.size() - 1;
        }
        SuggestionsBuilder builder = new SuggestionsBuilder(line, tokenStart);
        if (slots == null || slots.isEmpty()) {
            return builder;
        }
        CommandArg slot = null;
        String slotKey = null;
        int index = 0;
        for (Map.Entry<String, CommandArg> entry : slots.entrySet()) {
            if (index++ == tokenIndex) {
                slot = entry.getValue();
                slotKey = entry.getKey();
                break;
            }
        }
        if (slot == null) {
            return builder;
        }
        if (slot.suggestSpec instanceof java.util.function.Consumer<?> consumer) {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<Object[]> callback =
                    (java.util.function.Consumer<Object[]>) consumer;
            callback.accept(new Object[]{builder});
            return builder;
        }
        if (slot.suggestSpec instanceof List<?> list) {
            List<String> values = new ArrayList<>(list.size());
            for (Object value : list) {
                values.add(String.valueOf(value));
            }
            return builder.suggestMatching(values);
        }
        if (slot.kind == Kind.LITERAL) {
            String word = slot.literalWord != null ? slot.literalWord : slotKey;
            if (word != null && word.toLowerCase(Locale.ROOT)
                    .startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(word);
            }
        }
        return builder;
    }

    /** Whether one string has the resource-location shape. */
    @ApiInternal
    static boolean isIdentifierText(String value) {
        return IDENTIFIER_PATTERN.matcher(value).matches();
    }

    /** Extracts the slot map from a raw binding value (engine-unwrapped). */
    @ApiInternal
    public static Map<String, CommandArg> slotsFrom(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, CommandArg> slots = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof CommandArg arg)) {
                throw new IllegalArgumentException("argument '" + entry.getKey()
                        + "' must come from command.arg.*");
            }
            slots.put(String.valueOf(entry.getKey()), arg);
        }
        return slots;
    }

    /**
     * Parses the raw remainder against the slot map, in order. Raises for
     * missing required slots, malformed values and trailing input beyond the
     * declared slots; greedy consumes the rest and closes the line.
     */
    @ApiInternal
    public static Map<String, Object> parseAll(Map<String, CommandArg> slots,
                                               String remainder) {
        List<Token> tokens = remainder == null || remainder.isBlank()
                ? List.of() : tokenize(remainder);
        Map<String, Object> out = new LinkedHashMap<>();
        int index = 0;
        boolean greedyClosed = false;
        for (Map.Entry<String, CommandArg> entry : slots.entrySet()) {
            CommandArg slot = entry.getValue();
            if (slot.kind == Kind.GREEDY) {
                // Raw substring: the remainder keeps its original spacing
                // and quoting instead of a re-joined token list.
                String rest = index >= tokens.size() ? ""
                        : remainder.substring(tokens.get(index).start());
                out.put(entry.getKey(), rest.isEmpty() && slot.fallback != null
                        ? slot.fallback : rest);
                greedyClosed = true;
                continue;
            }
            if (index >= tokens.size()) {
                if (slot.optional || slot.fallback != null) {
                    out.put(entry.getKey(), slot.fallback);
                    continue;
                }
                throw new IllegalArgumentException("missing required argument '"
                        + entry.getKey() + "'");
            }
            Token token = tokens.get(index++);
            try {
                out.put(entry.getKey(), slot.parse(token, entry.getKey()));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("argument '" + entry.getKey()
                        + "' does not accept '" + token.text() + "'"
                        + (error.getMessage() == null ? ""
                        : ": " + error.getMessage()));
            }
        }
        // The flat parser owns the whole line: trailing tokens beyond the
        // declared slots are rejected here so the handler, not the brigadier
        // tree, decides what input is acceptable.
        if (!greedyClosed && index < tokens.size()) {
            throw new IllegalArgumentException("unexpected input '"
                    + tokens.get(index).text() + "'");
        }
        return out;
    }

    /** One input token: raw text (quotes included when quoted) + offsets. */
    private record Token(String text, boolean quoted, int start, int end) {
    }

    /**
     * Splits the remainder on whitespace, keeping double-quoted runs as one
     * token (quotes preserved, {@code \"} escapes intact) and remembering
     * offsets so {@code greedy} can hand back the raw slice.
     */
    private static List<Token> tokenize(String remainder) {
        List<Token> out = new ArrayList<>();
        int index = 0;
        int length = remainder.length();
        while (index < length) {
            char current = remainder.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            int start = index;
            if (current == '"') {
                index++;
                boolean closed = false;
                while (index < length) {
                    char quoted = remainder.charAt(index);
                    if (quoted == '\\' && index + 1 < length) {
                        index += 2;
                        continue;
                    }
                    index++;
                    if (quoted == '"') {
                        closed = true;
                        break;
                    }
                }
                if (!closed) {
                    throw new IllegalArgumentException("unterminated quoted string");
                }
                out.add(new Token(remainder.substring(start, index), true,
                        start, index));
                continue;
            }
            while (index < length && !Character.isWhitespace(remainder.charAt(index))) {
                index++;
            }
            out.add(new Token(remainder.substring(start, index), false, start, index));
        }
        return out;
    }

    private Object parse(Token token, String slotKey) {
        String text = token.text();
        return switch (kind) {
            case INT -> bounded(Integer.parseInt(text));
            case LONG -> bounded(Long.parseLong(text));
            case FLOAT -> bounded(Double.parseDouble(text));
            case BOOL -> parseBool(text);
            case UUID -> parseUuid(text);
            case IDENTIFIER -> parseIdentifier(text);
            case TIME -> parseTime(text);
            case LITERAL -> parseLiteral(token, slotKey);
            case WORD -> parseWord(token);
            case STRING -> parseString(token);
            // GREEDY is consumed in parseAll; PLAYER/POS stay raw tokens.
            default -> text;
        };
    }

    /** Applies the inclusive bounds when the slot carries a range. */
    private Number bounded(Number value) {
        if (rangeMin == null) {
            return value;
        }
        if (compareBounds(value, rangeMin) < 0 || compareBounds(value, rangeMax) > 0) {
            throw new IllegalArgumentException("out of range ["
                    + rangeMin + ", " + rangeMax + "]");
        }
        return value;
    }

    /** Compares two bounds on the slot's own numeric domain. */
    private int compareBounds(Number left, Number right) {
        return kind == Kind.LONG
                ? Long.compare(left.longValue(), right.longValue())
                : Double.compare(left.doubleValue(), right.doubleValue());
    }

    private static boolean parseBool(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        if (lower.equals("true") || lower.equals("1")) {
            return true;
        }
        if (lower.equals("false") || lower.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("not a boolean: " + token);
    }

    /** UUID slots deliver the normalized lowercase text (neutral surface). */
    private static String parseUuid(String token) {
        if (!token.matches(
                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("not a UUID: " + token);
        }
        return UUID.fromString(token).toString();
    }

    private static String parseIdentifier(String token) {
        if (!IDENTIFIER_PATTERN.matcher(token).matches()) {
            throw new IllegalArgumentException("not a resource location: " + token);
        }
        return token;
    }

    /** {@code <count><unit>} with a required unit ({@code t}/{@code s}/{@code d}). */
    private static Integer parseTime(String token) {
        if (token.length() < 2) {
            throw new IllegalArgumentException("not a duration: " + token);
        }
        String unit = token.substring(token.length() - 1);
        Integer perUnit = TIME_UNITS.get(unit);
        if (perUnit == null) {
            throw new IllegalArgumentException("missing duration unit (t/s/d): " + token);
        }
        long count = Long.parseLong(token.substring(0, token.length() - 1));
        return Math.toIntExact(count * perUnit);
    }

    /** The fixed word is the explicit factory argument or the slot key. */
    private String parseLiteral(Token token, String slotKey) {
        String expected = literalWord != null ? literalWord : slotKey;
        String actual = token.quoted() ? unquote(token.text()) : token.text();
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("expected literal '" + expected + "'");
        }
        return actual;
    }

    /** A word is one unquoted token — quotes would hide the raw input. */
    private static String parseWord(Token token) {
        if (token.quoted()) {
            throw new IllegalArgumentException("word slots take one unquoted token");
        }
        return token.text();
    }

    /** Quoted strings drop their quotes and unescape {@code \"}/{@code \}. */
    private static String parseString(Token token) {
        return token.quoted() ? unquote(token.text()) : token.text();
    }

    private static String unquote(String text) {
        String body = text.substring(1, text.length() - 1);
        return body.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
