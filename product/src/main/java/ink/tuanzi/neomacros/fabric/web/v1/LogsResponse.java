package ink.tuanzi.neomacros.fabric.web.v1;

import java.util.List;

/** The log replay response: the requested entries plus the newest sequence. */
public record LogsResponse(List<LogEntry> entries, long lastSeq) {
}
