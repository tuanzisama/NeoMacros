package ink.tuanzi.neomacros.core.runtime;

import java.util.Map;

/**
 * Result of one REPL expression evaluation (DevTools console): either a
 * formatted value node (the neutral console node model shared with the
 * editor) or the details of a guest-level failure. Transport-level trouble
 * (busy loop, unsupported engine) is not carried here — it surfaces as
 * {@link ScriptError} from the eval call itself.
 */
public record EvalOutcome(
        Map<String, Object> value,
        String errorName,
        String errorMessage,
        String errorStack,
        long durationMs) {

    /** Outcome for a value-producing evaluation ({@code value} is the node map). */
    public static EvalOutcome ofValue(Map<String, Object> value, long durationMs) {
        return new EvalOutcome(value, null, null, null, durationMs);
    }

    /** Outcome for an evaluation whose expression threw (or a rejected promise). */
    public static EvalOutcome ofError(String name, String message, String stack, long durationMs) {
        return new EvalOutcome(null, name, message, stack, durationMs);
    }

    /** True when the expression failed at guest level. */
    public boolean threw() {
        return value == null;
    }
}
