package ink.tuanzi.neomacros.core.config;

import java.util.List;
import java.util.Map;

/**
 * The {@code core} config section: runtime behavior knobs and the script
 * trust table. Field defaults are the shipped values; {@code config.json}
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

    /**
     * The shipped packet suppression list: high-traffic packet ids that are
     * never turned into {@code packet:receive} / {@code packet:send} events.
     * The config value replaces this list wholesale (an explicit empty list
     * lets every packet through), unlike {@link #wildcardIgnoresExtra} which
     * only appends.
     */
    public static final List<String> DEFAULT_PACKET_IGNORE = List.of(
            "minecraft:bundle_delimiter",
            "minecraft:move_entity_pos", "minecraft:move_entity_pos_rot",
            "minecraft:move_entity_rot", "minecraft:rotate_head",
            "minecraft:set_entity_motion", "minecraft:entity_position_sync",
            "minecraft:teleport", "minecraft:move_player");

    /**
     * Packet ids suppressed before either packet event is published. The
     * shipped value is {@link #DEFAULT_PACKET_IGNORE}; an explicit config
     * value replaces it wholesale and {@code []} publishes every packet.
     */
    @Option(description = "Packet ids suppressed before packet events publish (replaces the default list; [] allows all)")
    public List<String> packetIgnore = DEFAULT_PACKET_IGNORE;

    /**
     * Returns whether a packet id is suppressed by one ignore list. Patterns
     * follow the event-bus wildcard grammar: an exact id, {@code domain:*}
     * or {@code *}.
     *
     * @zh 判断包 id 是否被抑制名单命中；模式沿用事件总线通配语法：精确 id、{@code domain:*} 或 {@code *}。
     */
    public static boolean packetIgnored(java.util.Collection<String> ignore, String id) {
        if (ignore == null || ignore.isEmpty() || id == null) {
            return false;
        }
        for (String pattern : ignore) {
            if (pattern == null) {
                continue;
            }
            if (pattern.equals("*") || pattern.equals(id)) {
                return true;
            }
            if (pattern.endsWith(":*")
                    && id.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
        }
        return false;
    }

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

    /** The nested {@code web} subsection (web panel service settings). */
    @Option(description = "Web panel service settings")
    public WebSettings web = new WebSettings();

    /** The nested {@code mcp} subsection (MCP agent server settings). */
    @Option(description = "MCP agent server settings")
    public McpSettings mcp = new McpSettings();

    /** The nested {@code update} subsection (update checker settings). */
    @Option(description = "Update checker settings")
    public UpdateSettings update = new UpdateSettings();

    @Override
    public String key() {
        return "core";
    }

    @Override
    public int schemaVersion() {
        return ConfigManager.TARGET_VERSION;
    }
}
