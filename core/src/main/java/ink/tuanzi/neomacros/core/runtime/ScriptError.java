package ink.tuanzi.neomacros.core.runtime;

/**
 * Failure raised by the script engine layer: carries optional source
 * location for the error presentation chain (chat summary and editor jump).
 */
public class ScriptError extends RuntimeException {

    private final String sourceName;
    private final Integer line;
    private final Integer column;

    /** Creates a script error without source location. */
    public ScriptError(String message) {
        this(message, null, null, null, null);
    }

    /** Creates a script error wrapping an engine exception. */
    public ScriptError(String message, Throwable cause) {
        this(message, cause, null, null, null);
    }

    /** Creates a script error with full source location. */
    public ScriptError(String message, Throwable cause, String sourceName, Integer line, Integer column) {
        super(message, cause);
        this.sourceName = sourceName;
        this.line = line;
        this.column = column;
    }

    /** Source file the error originated from, or null when unknown. */
    public String getSourceName() {
        return sourceName;
    }

    /** 1-based line number, or null when unknown. */
    public Integer getLine() {
        return line;
    }

    /** 1-based column number, or null when unknown. */
    public Integer getColumn() {
        return column;
    }
}
