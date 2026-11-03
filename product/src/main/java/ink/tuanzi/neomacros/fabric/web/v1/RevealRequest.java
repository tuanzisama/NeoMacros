package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;

/**
 * The reveal request: open one macros-root-relative path (file or
 * directory) in the system file manager of the machine running the game.
 */
public record RevealRequest(
        @NmDesc("The macros-root-relative path (may contain slashes)") String path) {
}
