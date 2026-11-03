package ink.tuanzi.neomacros.fabric.web.v1;

import ink.tuanzi.neomacros.fabric.web.api.NmDesc;

import java.util.List;

/** One macro folder's file listing. */
public record FilesResponse(
        @NmDesc("Every file in the macro folder as folder-relative POSIX paths,"
                + " the manifest first") List<String> files,
        @NmDesc("Every subdirectory as folder-relative POSIX paths (empty ones"
                + " included, symlinks excluded)") List<String> dirs) {
}
