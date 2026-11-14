package ink.tuanzi.neomacros.port.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Point-in-time copy of one scoreboard objective as the sidebar would draw
 * it: identity, criteria, the per-owner scores, the team-formatted display
 * texts, and the scoreboard's tracked owners.
 *
 * @param name                     objective identifier
 * @param displayName              display title, plain text
 * @param criteria                 criteria identifier, e.g. {@code dummy}
 * @param renderType               render type identifier, e.g. {@code integer}
 * @param playerScores             raw owner name to score, hidden entries
 *                                 included
 * @param scoreToDisplayName       score to team-formatted owner text;
 *                                 owners with equal scores overwrite
 * @param texts                    team-formatted names of the visible
 *                                 entries, highest score first, capped at
 *                                 fifteen
 * @param knownPlayers             scoreboard-tracked owner names
 * @param knownPlayersDisplayNames display name per tracked owner, in
 *                                 {@code knownPlayers} order
 */
public record ScoreboardObjectiveData(String name, String displayName, String criteria,
        String renderType, Map<String, Integer> playerScores,
        Map<Integer, String> scoreToDisplayName, List<String> texts,
        List<String> knownPlayers, List<String> knownPlayersDisplayNames) {

    public ScoreboardObjectiveData {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(criteria, "criteria cannot be null");
        Objects.requireNonNull(renderType, "renderType cannot be null");
        playerScores = playerScores == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(playerScores));
        scoreToDisplayName = scoreToDisplayName == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(scoreToDisplayName));
        texts = texts == null ? List.of() : List.copyOf(texts);
        knownPlayers = knownPlayers == null ? List.of() : List.copyOf(knownPlayers);
        knownPlayersDisplayNames = knownPlayersDisplayNames == null ? List.of()
                : List.copyOf(knownPlayersDisplayNames);
    }
}
