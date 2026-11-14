package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;

/**
 * Point-in-time copy of one scoreboard team: identity, display text, the
 * color formatting the team assigns to its members, and the member list.
 *
 * @param name       registered team name
 * @param displayName display text, may differ from the name
 * @param colorName  lowercase color formatting name, e.g. {@code red};
 *                   {@code reset} when the team carries no color
 * @param colorIndex legacy color index 0-15, or -1 for no color
 * @param colorValue packed RGB value of the color, or -1 for no color
 * @param players    scoreboard names of the team members
 */
public record TeamData(String name, String displayName, String colorName, int colorIndex,
        int colorValue, List<String> players) {

    public TeamData {
        Objects.requireNonNull(name, "name cannot be null");
        displayName = displayName == null ? name : displayName;
        colorName = colorName == null ? "reset" : colorName;
        players = players == null ? List.of() : List.copyOf(players);
    }
}
