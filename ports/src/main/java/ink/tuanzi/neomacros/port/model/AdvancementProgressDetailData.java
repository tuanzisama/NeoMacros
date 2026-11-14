package ink.tuanzi.neomacros.port.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable client-known detail of one advancement's progress: the completion
 * times of the obtained criteria and the vanilla progress text.
 *
 * <p>Criterion timestamps travel with the server's progress packet, so the
 * client reads them without a server round-trip.</p>
 *
 * @param criterionDates obtained criterion names to their completion time in
 *                       milliseconds since the epoch; unobtained criteria are
 *                       omitted
 * @param fraction       the vanilla {@code n/m} progress text, or null when
 *                       the advancement has no criteria or at most one
 *                       requirement group
 */
public record AdvancementProgressDetailData(Map<String, Long> criterionDates,
        String fraction) {

    public AdvancementProgressDetailData {
        Objects.requireNonNull(criterionDates, "criterionDates cannot be null");
        criterionDates = Collections.unmodifiableMap(
                new LinkedHashMap<>(criterionDates));
    }
}
