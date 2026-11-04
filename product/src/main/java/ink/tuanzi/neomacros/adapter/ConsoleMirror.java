package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.core.console.ScriptConsole;
import ink.tuanzi.neomacros.core.console.ScriptConsoleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The always-on plain-text mirror of script console output into the mod's
 * logging tree: every emission becomes one line on the
 * {@code neomacros.console.<macro>} logger, so console output survives in
 * the game log file (and the existing log ring) even when the web panel is
 * stopped or no editor is connected. Installed at bootstrap and never
 * removed; the web service adds its own ring/SSE dispatcher on top.
 */
public final class ConsoleMirror {

    private static final Consumer<ScriptConsoleEvent> DISPATCHER = ConsoleMirror::accept;

    private ConsoleMirror() {
    }

    /** Registers the mirror dispatcher with the core console hub. */
    public static void install() {
        ScriptConsole.installDispatcher(DISPATCHER);
    }

    private static void accept(ScriptConsoleEvent event) {
        Logger log = LoggerFactory.getLogger("neomacros.console." + event.macro());
        StringBuilder line = new StringBuilder(where(event));
        for (Object arg : event.args()) {
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(flatten(arg));
        }
        switch (event.level()) {
            case "error" -> log.error("{}", line);
            case "warn" -> log.warn("{}", line);
            case "debug" -> log.debug("{}", line);
            default -> log.info("{}", line);
        }
    }

    /** The {@code [file:line]} prefix, empty when unresolved. */
    private static String where(ScriptConsoleEvent event) {
        if (event.file() == null) {
            return "";
        }
        return "[" + event.file() + (event.line() != null ? ":" + event.line() : "") + "] ";
    }

    /** One-line rendering of a neutral console node (log-file use). */
    private static String flatten(Object node) {
        if (!(node instanceof Map<?, ?> map) || map.get("t") == null) {
            return String.valueOf(node);
        }
        String name = text(map.get("name"));
        String pre = text(map.get("pre"));
        return switch (String.valueOf(map.get("t"))) {
            case "str", "num", "bool" -> text(map.get("v"));
            case "null" -> "null";
            case "undef" -> "undefined";
            case "fn" -> "ƒ " + name + "()";
            case "arr" -> "[" + pre + "]";
            case "obj" -> "{" + pre + "}";
            case "err" -> name + ": " + text(map.get("msg"));
            case "class" -> "class " + name;
            case "comp" -> flatRich(map.get("rich"));
            case "host" -> pre;
            default -> text(map.get("v"));
        };
    }

    /** Null-safe text of one map value. */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Flattened rich text of a comp node (style dropped, text kept). */
    private static String flatRich(Object rich) {
        if (!(rich instanceof Map<?, ?> map)) {
            return "";
        }
        StringBuilder out = new StringBuilder(map.get("text") == null ? "" : String.valueOf(map.get("text")));
        Object children = map.get("children");
        if (children instanceof List<?> list) {
            for (Object child : list) {
                out.append(flatRich(child));
            }
        }
        return out.toString();
    }
}
