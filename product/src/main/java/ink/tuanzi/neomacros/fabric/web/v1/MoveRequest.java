package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;

/**
 * The file-tree move request: both paths are macros-root-relative and must
 * stay inside one macro folder.
 */
public record MoveRequest(
        @NmDesc("The macros-root-relative source path (must exist)") String from,
        @NmDesc("The macros-root-relative target path (must not exist)") String to) {
}
