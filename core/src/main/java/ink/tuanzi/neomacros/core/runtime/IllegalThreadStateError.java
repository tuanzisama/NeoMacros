package ink.tuanzi.neomacros.core.runtime;

/**
 * Raised when a call would deadlock the threading model: blocking waits from
 * the game main thread or from a thread already joined onto it (the Java
 * counterpart of the script surface's IllegalThreadStateError).
 */
public class IllegalThreadStateError extends IllegalStateException {

    /** Creates the error with a description of the illegal join. */
    public IllegalThreadStateError(String message) {
        super(message);
    }
}
