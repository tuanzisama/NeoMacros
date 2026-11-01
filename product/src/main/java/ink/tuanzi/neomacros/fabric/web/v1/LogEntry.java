package ink.tuanzi.neomacros.fabric.web.v1;

/** One line of the mod's own log output, with its ring sequence number. */
public record LogEntry(long seq, String level, String logger, String line) {
}
