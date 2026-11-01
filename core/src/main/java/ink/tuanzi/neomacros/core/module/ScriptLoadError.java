package ink.tuanzi.neomacros.core.module;

import java.nio.file.Path;

/**
 * A script folder rejected by discovery (missing/invalid manifest, missing
 * entry, duplicate identity). Surfaced through the manager's {@code errors()}
 * view so the panel and command surface can show why a folder was skipped.
 *
 * @param folder  the rejected script folder
 * @param message the concrete rejection reason
 */
public record ScriptLoadError(Path folder, String message) {
}
