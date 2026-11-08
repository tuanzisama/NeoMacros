package ink.tuanzi.neomacros.port.model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable client-known progress of one advancement: which criteria are
 * obtained and which are still open.
 *
 * @param done      whether every requirement is satisfied
 * @param obtained  criterion names already granted
 * @param remaining criterion names not granted yet
 */
public record AdvancementProgressData(boolean done, List<String> obtained,
        List<String> remaining) {

    public AdvancementProgressData {
        Objects.requireNonNull(obtained, "obtained cannot be null");
        Objects.requireNonNull(remaining, "remaining cannot be null");
        obtained = List.copyOf(obtained);
        remaining = List.copyOf(remaining);
    }
}
