package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import ink.tuanzi.neomacros.fabric.web.api.NmNullable;

/**
 * A script failure with its source location, so the web editor can mark the
 * exact spot. Line and column are null when the engine could not locate the
 * failure.
 *
 * @param script the entry source path, relative to the macros root
 * @param line   the 1-based line, or null when unknown
 * @param column the 1-based column, or null when unknown
 * @param message the failure message
 */
public record LocatedError(
        String script,
        @NmNullable Integer line,
        @NmNullable Integer column,
        String message) {
}
