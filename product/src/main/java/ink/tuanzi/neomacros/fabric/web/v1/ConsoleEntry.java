package ink.tuanzi.neomacros.fabric.web.v1;

import java.util.List;

/**
 * One script console emission as the panel sees it: identity and call site
 * plus the structured argument tree the engine produced. {@code kind} and
 * {@code level} follow browser console semantics; the trailing fields carry
 * the per-kind extras (label/count for count, ms for timeLog/timeEnd,
 * stack for trace and failed asserts). Nulls mean "not applicable".
 */
public record ConsoleEntry(long seq, long ts, String macro, String kind, String level,
                           String file, Integer line, Integer col, int depth,
                           List<Object> args,
                           String label, Integer count, Double ms, String stack) {
}
