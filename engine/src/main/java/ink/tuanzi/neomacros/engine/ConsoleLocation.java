package ink.tuanzi.neomacros.engine;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a console call site out of the guest {@code Error} stack the
 * GLUE wrapper captures. Glue frames (whose source name is the engine
 * glue) are console internals and skipped; the first remaining frame
 * carries {@code source:line:column}. A {@code file:} URI source is
 * relativized against the session's module root into the macro-relative
 * POSIX path the editor addresses files by; anything else (virtual
 * modules, plain source names) leaves the file unresolvable.
 */
final class ConsoleLocation {

    private static final Pattern FRAME = Pattern.compile("^(.*):(\\d+):(\\d+)$");

    /** Source name of the engine glue (see GraalJsEngine GLUE evaluation). */
    private static final String GLUE_SOURCE = "neomacros-engine-glue";

    /** Resolved call site; nulls when the frame could not be pinned down. */
    record Location(String file, Integer line, Integer column) {
        static final Location NONE = new Location(null, null, null);
    }

    private ConsoleLocation() {
    }

    /** Parses the first non-glue frame of the captured stack. */
    static Location parse(String stack, Path moduleRoot) {
        if (stack == null || stack.isEmpty()) {
            return Location.NONE;
        }
        for (String raw : stack.split("\n")) {
            String line = raw.trim();
            if (!line.startsWith("at ") || line.contains(GLUE_SOURCE)) {
                continue;
            }
            String body = line.substring(3).trim();
            int paren = body.lastIndexOf('(');
            if (paren >= 0 && body.endsWith(")")) {
                body = body.substring(paren + 1, body.length() - 1);
            }
            Matcher matcher = FRAME.matcher(body);
            if (!matcher.matches()) {
                continue;
            }
            try {
                Integer lineNo = Integer.valueOf(matcher.group(2));
                Integer column = Integer.valueOf(matcher.group(3));
                return new Location(relativize(matcher.group(1), moduleRoot), lineNo, column);
            } catch (NumberFormatException ignored) {
                return Location.NONE;
            }
        }
        return Location.NONE;
    }

    /**
     * Macro-root-relative POSIX path of a source location; null otherwise.
     * Module frames carry either a {@code file:} URI or the plain OS path
     * depending on how the module was loaded.
     */
    private static String relativize(String source, Path moduleRoot) {
        try {
            Path path = source.startsWith("file:")
                    ? Paths.get(URI.create(source))
                    : Paths.get(source);
            // Paths outside the module root (other macros, unrelated files)
            // stay unresolved rather than leaking as ../ escapes.
            if (!path.startsWith(moduleRoot)) {
                return null;
            }
            return moduleRoot.relativize(path).toString().replace('\\', '/');
        } catch (RuntimeException e) {
            return null;
        }
    }
}
