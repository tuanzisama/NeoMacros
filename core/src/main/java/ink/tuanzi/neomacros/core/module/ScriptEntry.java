package ink.tuanzi.neomacros.core.module;

import ink.tuanzi.neomacros.core.module.manifest.MacroManifest;
import ink.tuanzi.neomacros.core.module.manifest.ScriptCategory;

import java.nio.file.Path;

/**
 * One discovered script folder: its category, folder, parsed manifest, and
 * resolved entry file. Discovery produces these; loading consumes them.
 *
 * @param category  the trigger category folder the script lives in
 * @param folder    the script folder below the category directory
 * @param manifest  the parsed {@code neomacro.*} declaration
 * @param entryFile the resolved entry ({@code index.js} or the manifest {@code main})
 */
public record ScriptEntry(ScriptCategory category, Path folder, MacroManifest manifest, Path entryFile) {

    /** The script identity (the manifest name). */
    public String name() {
        return manifest.name();
    }

    /** The module metadata: manifest fields with the category-default lifecycle applied. */
    public ScriptMeta meta() {
        return new ScriptMeta(manifest.name(), manifest.version(),
                manifest.lifecycleOr(category), manifest.permissions());
    }

    /**
     * The effective key binding: the central keymapping table wins (including
     * an empty-string entry, which is a persisted unbind), otherwise the
     * manifest's suggested {@code config.keypress.default-key}.
     */
    public String effectiveKey(java.util.Map<String, String> overrides) {
        return overrides.getOrDefault(manifest.name(),
                manifest.configOrEmpty().keypress() == null
                        ? "" : manifest.configOrEmpty().keypress().defaultKeyOrEmpty());
    }
}
