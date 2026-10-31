package ink.tuanzi.neomacros.core.api.bindgen;

/**
 * Raised when a script calls a binding whose capability is not declared in
 * the script's {@code meta.permissions}; the script surface sees this as the
 * {@code PermissionError} error type.
 */
public class PermissionError extends RuntimeException {

    /** Creates the error for a denied capability call. */
    public PermissionError(String message) {
        super(message);
    }
}
