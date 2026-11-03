package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;

/**
 * The macro move request: one macro folder changing its category. The
 * manifest name (and with it the persisted autoload/keybinds state) stays
 * the same; only the category directory changes.
 */
public record MoveMacroRequest(
        @NmDesc("The macro id category/name (a bare name works too)") String from,
        @NmDesc("The target category: keypress, event, or services") String to) {
}
