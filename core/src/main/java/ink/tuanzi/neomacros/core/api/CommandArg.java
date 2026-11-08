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

/**
 * One typed argument slot of a registered command. Slots build through the
 * dynamic {@code command.arg} factory ({@code int() float() bool() word()
 * string() greedy() player() pos()}) and chain {@code optional()} and
 * {@code default(v)}. Parsing walks slots in declaration order against the
 * raw remainder; {@code greedy} consumes everything that is left.
 *
 * @zh 注册命令的一个类型化参数槽。槽位经动态 {@code command.arg} 工厂构建（{@code int() float() bool() word() string() greedy() player() pos()}），可链式 {@code optional()} 与 {@code default(v)}。解析按声明顺序对原始余串进行；{@code greedy} 吞掉剩余全部。
 */
@ApiClass(name = "CommandArg", kind = ApiClassKind.HANDLE)
@ApiSince("0.1.0")
@ApiMC("*")
@ApiLoader("*")
public final class CommandArg {

    /** The supported slot shapes. */
    public enum Kind {
        INT, FLOAT, BOOL, WORD, STRING, GREEDY, PLAYER, POS
    }

    /** Member names the {@code command.arg} factory answers to. */
    public static final List<String> KIND_NAMES = List.of(
            "int", "float", "bool", "word", "string", "greedy", "player", "pos");

    private final Kind kind;
    private final boolean optional;
    private final Object fallback;

    private CommandArg(Kind kind, boolean optional, Object fallback) {
        this.kind = kind;
        this.optional = optional;
        this.fallback = fallback;
    }

    /** Builds one slot from a factory member name; unknown names raise. */
    @ApiInternal
    public static CommandArg of(String memberName) {
        Kind kind = switch (memberName.toLowerCase(Locale.ROOT)) {
            case "int" -> Kind.INT;
            case "float" -> Kind.FLOAT;
            case "bool" -> Kind.BOOL;
            case "word" -> Kind.WORD;
            case "string" -> Kind.STRING;
            case "greedy" -> Kind.GREEDY;
            case "player" -> Kind.PLAYER;
            case "pos" -> Kind.POS;
            default -> throw new IllegalArgumentException("unknown argument kind: "
                    + memberName);
        };
        return new CommandArg(kind, false, null);
    }

    /** Marks the slot optional (missing input falls back, never raises).
     * @zh 将槽位标记为可选（输入缺失时回退，不抛错）。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CommandArg optional() {
        return new CommandArg(kind, true, fallback);
    }

    /** Supplies the fallback value for an optional slot.
     * @zh 为可选槽位提供回退值。 */
    @ApiSince("0.1.0")
    @ApiMC("*")
    @ApiLoader("*")
    public CommandArg defaultTo(@ApiType("number | string | boolean") Object value) {
        return new CommandArg(kind, true, value);
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
     * missing required slots and malformed values; greedy consumes the rest.
     */
    @ApiInternal
    public static Map<String, Object> parseAll(Map<String, CommandArg> slots,
                                               String remainder) {
        List<String> tokens = remainder == null || remainder.isBlank()
                ? List.of() : List.of(remainder.trim().split("\\s+"));
        Map<String, Object> out = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, CommandArg> entry : slots.entrySet()) {
            CommandArg slot = entry.getValue();
            if (slot.kind == Kind.GREEDY) {
                List<String> restTokens = tokens.subList(
                        Math.min(index, tokens.size()), tokens.size());
                String rest = String.join(" ", restTokens);
                out.put(entry.getKey(), rest.isEmpty() && slot.fallback != null
                        ? slot.fallback : rest);
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
            String token = tokens.get(index++);
            try {
                out.put(entry.getKey(), slot.parse(token));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("argument '" + entry.getKey()
                        + "' does not accept '" + token + "'");
            }
        }
        return out;
    }

    private Object parse(String token) {
        return switch (kind) {
            case INT -> Integer.parseInt(token);
            case FLOAT -> Double.parseDouble(token);
            case BOOL -> parseBool(token);
            default -> token;
        };
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
}
