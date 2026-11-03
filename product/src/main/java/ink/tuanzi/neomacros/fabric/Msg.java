package ink.tuanzi.neomacros.fabric;

/**
 * Unified chat formatting: every printed line opens with one gold
 * {@code [NeoMacros]} tag, and the embedded details carry accent colors —
 * aqua for identifiers (scripts, sessions, devices), yellow for values
 * (counts, keys, durations), red for error reasons. The accents are legacy
 * formatting codes, so they survive {@link UiText#tr} substitution and
 * render identically in chat and screen status lines.
 */
public final class Msg {

    /** The mod tag every chat line starts with. */
    public static final String TAG = "§6[NeoMacros]§r ";

    private Msg() {
    }

    /** Aqua accent for an identifier: script, session, or device name. */
    public static String name(Object value) {
        return "§b" + value + "§r";
    }

    /** Yellow accent for a value: count, key name, duration, or algorithm. */
    public static String num(Object value) {
        return "§e" + value + "§r";
    }

    /** Red accent for an error reason. */
    public static String err(Object value) {
        return "§c" + value + "§r";
    }

    /** Green accent for a positive state word. */
    public static String ok(Object value) {
        return "§a" + value + "§r";
    }

    /** Gray accent for secondary detail (version, author, stopped state). */
    public static String dim(Object value) {
        return "§7" + value + "§r";
    }

    /** Prepends the mod tag to an already-resolved line. */
    public static String line(String message) {
        return TAG + message;
    }
}
