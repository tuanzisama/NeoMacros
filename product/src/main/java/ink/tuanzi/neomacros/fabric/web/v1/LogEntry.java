package ink.tuanzi.neomacros.fabric.web.v1;

/**
 * One line of the mod's own log output, with its ring sequence number and
 * the originating log4j event time (epoch millis) so panel clients can
 * interleave log lines with script console output on one timeline.
 */
public record LogEntry(long seq, long ts, String level, String logger, String line) {
}
