package ink.tuanzi.neomacros.fabric.update;

/**
 * One resolved "there is a newer build" finding, as presented to the player
 * and broadcast to connected editor panels.
 */
public record UpdateNotice(String channel, String currentVersion, String latestVersion,
        String notes, String date) {

    /** The dedupe/ignore key persisted in the config ({@code channel@version}). */
    public String key() {
        return channel + "@" + latestVersion;
    }
}
