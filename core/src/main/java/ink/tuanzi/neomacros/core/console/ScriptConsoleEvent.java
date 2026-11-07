package ink.tuanzi.neomacros.core.console;

import java.util.List;

/**
 * One console emission from a script: identity and location plus the
 * structured argument tree the engine's formatter produced. {@code args}
 * holds neutral node maps (see the engine's {@code ConsoleValueFormatter});
 * {@code kind}/{@code level} follow browser console semantics, and the
 * trailing fields carry the per-kind extras ({@code label}/{@code count}
 * for count, {@code ms} for time/timeLog/timeEnd, {@code stack} for
 * trace/assert failures).
 *
 * @param macro    the manifest name of the emitting script
 * @param kind     log | trace | dir | table | count | time | timeEnd |
 *                 group | groupEnd | clear | assert
 * @param level    log | info | warn | error | debug
 * @param file     script-relative POSIX path of the call site, null when
 *                 the frame could not be resolved
 * @param line     call-site line, null when unresolved
 * @param column   call-site column, null when unresolved
 * @param depth    group nesting level of the call site
 * @param args     structured argument nodes (never null)
 * @param label    count/time channel label, null when unused
 * @param count    current counter value for count, null otherwise
 * @param ms       elapsed milliseconds for time/timeLog/timeEnd, null otherwise
 * @param stack    guest stack capture for trace/assert, null otherwise
 * @param timestampMillis emission wall-clock time
 */
public record ScriptConsoleEvent(String macro, String kind, String level,
                                 String file, Integer line, Integer column, int depth,
                                 List<Object> args,
                                 String label, Integer count, Double ms, String stack,
                                 long timestampMillis) {

    /** Event with the resolved location and extras; args never null. */
    public ScriptConsoleEvent {
        args = args == null ? List.of() : List.copyOf(args);
    }
}
