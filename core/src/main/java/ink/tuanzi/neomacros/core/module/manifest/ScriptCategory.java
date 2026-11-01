package ink.tuanzi.neomacros.core.module.manifest;

import ink.tuanzi.neomacros.core.module.ScriptMeta;

/**
 * The three trigger categories a script folder can live in. The category
 * directory decides where GUI and commands look for candidates, and supplies
 * the default lifecycle a manifest may override.
 */
public enum ScriptCategory {

    /** Key-press triggered scripts; the bindable key lives in the manifest or the central binding table. */
    KEYPRESS("keypress", ScriptMeta.Lifecycle.WORLD),

    /** Event-driven scripts that subscribe themselves through the events API. */
    EVENT("event", ScriptMeta.Lifecycle.WORLD),

    /** Long-running scripts pulled up at game start and stopped at quit. */
    SERVICES("services", ScriptMeta.Lifecycle.ALWAYS);

    private final String dirName;
    private final ScriptMeta.Lifecycle defaultLifecycle;

    ScriptCategory(String dirName, ScriptMeta.Lifecycle defaultLifecycle) {
        this.dirName = dirName;
        this.defaultLifecycle = defaultLifecycle;
    }

    /** The directory name below the macros root. */
    public String dirName() {
        return dirName;
    }

    /** The lifecycle used when the manifest does not declare one. */
    public ScriptMeta.Lifecycle defaultLifecycle() {
        return defaultLifecycle;
    }

    /** Resolves a directory name back to its category; null when unknown. */
    public static ScriptCategory fromDirName(String name) {
        for (ScriptCategory category : values()) {
            if (category.dirName.equals(name)) {
                return category;
            }
        }
        return null;
    }
}
