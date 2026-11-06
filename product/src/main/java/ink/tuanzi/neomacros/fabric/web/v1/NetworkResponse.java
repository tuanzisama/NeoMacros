package ink.tuanzi.neomacros.fabric.web.v1;

import java.util.List;

/**
 * Replay page of the network ring: the entries after the client's cursor,
 * oldest first, plus the newest sequence for the next incremental pull.
 */
public record NetworkResponse(List<NetworkEntry> entries, long lastSeq) {
}
