package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;

/** The directory-creation request: a macros-root-relative folder path. */
public record MkdirRequest(
        @NmDesc("The macros-root-relative directory path (created recursively)") String path) {
}
