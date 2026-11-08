package ink.tuanzi.neomacros.fabric.web.v1;

import java.util.List;

/**
 * The memory replay page: samples after the {@code after} cursor, oldest
 * first, plus the ring's newest sequence for the next cursor.
 *
 * @param samples the replayed samples, oldest first
 * @param lastSeq the newest sequence in the ring (0 for an empty ring)
 */
public record MemoryResponse(List<MemorySample> samples, long lastSeq) {
}
