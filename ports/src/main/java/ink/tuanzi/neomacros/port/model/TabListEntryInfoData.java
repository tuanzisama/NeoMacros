package ink.tuanzi.neomacros.port.model;

import java.util.Objects;

/**
 * Point-in-time copy of one tab-list player entry: profile identity, the
 * row the server configured, the skin facts the client holds for the
 * profile, the chat-session public key when present, and the team the
 * player is on in the loaded scoreboard.
 *
 * @param uuid         account UUID in string form
 * @param name         account name
 * @param latency      last measured latency in milliseconds
 * @param gamemode     game mode word the game uses, e.g. {@code survival}
 * @param displayName  tab-list display name, plain text, null when unset
 * @param publicKey    encoded chat-session public key, null when the entry
 *                     has no session
 * @param skinTexture  body skin texture path, null when unknown
 * @param skinUrl      body skin download URL, null when unavailable
 * @param capeTexture  cape texture path, null when the entry has no cape
 * @param elytraTexture elytra texture path, null when unknown
 * @param cape         whether the entry has a cape texture
 * @param slimModel    whether the entry uses the slim skin model
 * @param team         the player's scoreboard team, null when on none
 */
public record TabListEntryInfoData(String uuid, String name, int latency, String gamemode,
        String displayName, byte[] publicKey, String skinTexture, String skinUrl,
        String capeTexture, String elytraTexture, boolean cape, boolean slimModel,
        TeamData team) {

    public TabListEntryInfoData {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(gamemode, "gamemode cannot be null");
    }
}
