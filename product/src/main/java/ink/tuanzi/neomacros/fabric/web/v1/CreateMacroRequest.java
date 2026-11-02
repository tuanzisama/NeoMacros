package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;
import ink.tuanzi.neomacros.fabric.web.api.NmNullable;

/**
 * The macro scaffold request: a category directory, the macro name
 * ({@code [A-Za-z0-9_-]}, unique across categories), and an optional
 * description for the generated manifest.
 */
public record CreateMacroRequest(
        @NmDesc("The category directory: keypress, event, or services") String category,
        @NmDesc("The macro name ([A-Za-z0-9_-], unique across categories)") String name,
        @NmDesc("Optional description for the generated manifest") @NmNullable String description) {
}
