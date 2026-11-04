package ink.tuanzi.neomacros.fabric.web.v1;

import java.util.List;

/** The script console replay response: requested entries plus the newest sequence. */
public record ConsoleResponse(List<ConsoleEntry> entries, long lastSeq) {
}
