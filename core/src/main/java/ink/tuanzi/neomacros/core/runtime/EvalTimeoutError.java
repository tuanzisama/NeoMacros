package ink.tuanzi.neomacros.core.runtime;

/**
 * The context loop did not answer within the engine's loop budget while
 * evaluating a REPL expression (busy module or hung promise). Web routes
 * map this to a 504 {@code eval_timeout} response.
 */
public class EvalTimeoutError extends ScriptError {

    /** Creates the timeout failure with a descriptive message. */
    public EvalTimeoutError(String message) {
        super(message);
    }
}
