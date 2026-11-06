package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;

/**
 * Point-in-time copy of the client scoreboard: every objective and all known
 * player scores under them.
 *
 * @param objectives all objectives on the client scoreboard
 * @param scores     player scores, one per objective/owner pair
 */
public record ScoreboardData(List<ObjectiveData> objectives, List<ScoreEntryData> scores) {

    public ScoreboardData {
        Objects.requireNonNull(objectives, "objectives cannot be null");
        Objects.requireNonNull(scores, "scores cannot be null");
        objectives = List.copyOf(objectives);
        scores = List.copyOf(scores);
    }

    /** One scoreboard objective. */
    public record ObjectiveData(String name, String displayName, String criteria,
            String renderType) {

        public ObjectiveData {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(displayName, "displayName cannot be null");
            Objects.requireNonNull(criteria, "criteria cannot be null");
            Objects.requireNonNull(renderType, "renderType cannot be null");
        }
    }

    /** One player score under one objective. */
    public record ScoreEntryData(String objective, String owner, int value) {

        public ScoreEntryData {
            Objects.requireNonNull(objective, "objective cannot be null");
            Objects.requireNonNull(owner, "owner cannot be null");
        }
    }
}
