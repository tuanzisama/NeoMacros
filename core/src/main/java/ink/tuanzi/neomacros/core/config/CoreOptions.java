package ink.tuanzi.neomacros.core.config;

import java.util.List;
import java.util.Map;

/**
 * The {@code core} config section: runtime behavior knobs and the script
 * trust table. Field defaults are the shipped values; {@code options.json}
 * overrides them per install.
 */
public final class CoreOptions implements ConfigSection {

    /** Upper bound in milliseconds for synchronous event dispatch and main-thread waits. */
    @Option(description = "Sync watchdog budget in milliseconds", min = 50, max = 10_000)
    public int maxSyncTime = 500;

    /** Whether changed script files are reloaded automatically while the game runs. */
    @Option(description = "Reload changed scripts automatically")
    public boolean scriptWatch = true;

    /** Number of idle watch ticks between script directory scans. */
    @Option(description = "Ticks between script watch scans", min = 20, max = 600)
    public int scriptWatchInterval = 40;

    /** Extra events excluded from wildcard subscriptions (added to the built-in list). */
    @Option(description = "Additional events skipped by wildcard subscriptions")
    public List<String> wildcardIgnoresExtra = List.of();

    /** Per-script trusted capability sets, keyed by content hash. */
    @Option(description = "Trusted scripts by content hash")
    public Map<String, List<String>> trust = Map.of();

    public Map<String, List<String>> getTrust() {
        return trust;
    }

    public void setTrust(Map<String, List<String>> value) {
        trust = value;
    }

    /** Script enable/disable state keyed by manifest name (management panel store). */
    @Option(description = "Script enable state by name")
    public Map<String, Boolean> autoload = Map.of();

    /**
     * Central key binding table keyed by script name; the value is a vanilla
     * key name, or the empty string for an explicit unbind that must survive
     * restarts. Absent names fall back to the manifest's {@code key} field.
     */
    @Option(description = "Key binding overrides by script name")
    public Map<String, String> keybinds = Map.of();

    /**
     * Macros directory override; empty uses {@code config/neomacros/macros}
     * (with its {@code keypress/event/services} category folders) next to the
     * game's config folder.
     */
    @Option(description = "Macros directory override")
    public String macrosDir = "";

    @Override
    public String key() {
        return "core";
    }

    @Override
    public int schemaVersion() {
        return ConfigManager.TARGET_VERSION;
    }
}
