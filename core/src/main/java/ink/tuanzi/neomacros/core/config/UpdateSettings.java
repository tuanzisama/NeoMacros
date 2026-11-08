package ink.tuanzi.neomacros.core.config;

import java.util.List;

/**
 * The {@code core.update} section: the automatic update checker. Defaults
 * keep the checker on and quiet (twelve-hour cadence); local development
 * builds never auto-check regardless of these values, and {@code /neomacros
 * update check} always works manually.
 */
public final class UpdateSettings {

    /** Whether automatic update checks run while the game is open. */
    @Option(description = "Automatic update checks enabled")
    public boolean enabled = true;

    /** Wall-clock hours between automatic checks. */
    @Option(description = "Hours between update checks", min = 1, max = 168)
    public int intervalHours = 12;

    /**
     * Update channel to compare against: {@code auto} follows the running
     * build's own channel (local builds read as release), or an explicit
     * {@code release}/{@code dev}/{@code canary}.
     */
    @Option(description = "Update channel: auto, release, dev, canary")
    public String channel = "auto";

    /** Whether a newer version is announced in game (toast + chat card). */
    @Option(description = "Announce updates in game")
    public boolean notifyInGame = true;

    /** Whether connected editor panels receive the update banner event. */
    @Option(description = "Announce updates to connected editor panels")
    public boolean notifyEditor = true;

    /**
     * Replacement source chain for update data (checked in order, first
     * success wins); empty uses the built-in self-hosted manifest and the
     * GitHub API fallback. Entries are URLs.
     */
    @Option(description = "Custom update source URLs; empty uses the built-ins")
    public List<String> sources = List.of();

    /** The {@code channel@version} the player chose to skip. */
    @Option(description = "Update the player ignored")
    public String ignoredVersion = "";

    /** The last {@code channel@version} the player was notified about; dedupes restarts. */
    @Option(description = "Last notified channel@version")
    public String lastNotified = "";
}
