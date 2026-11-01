package ink.tuanzi.neomacros.fabric.web.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory ring of the mod's own log lines with a monotonically increasing
 * sequence. Web clients replay incrementally with {@code GET /logs?after=}
 * and subscribe to the live tail through SSE; both read from here.
 */
public final class LogRing {

    /** One log line as the panel sees it. */
    public record Entry(long seq, String level, String logger, String line) {
    }

    private final int capacity;
    private final ArrayDeque<Entry> entries;
    private long nextSeq;

    /** Creates the ring with the given capacity. */
    public LogRing(int capacity) {
        this.capacity = capacity;
        this.entries = new ArrayDeque<>(capacity);
    }

    /** Appends one line, evicting the oldest when full. */
    public synchronized void append(String level, String logger, String line) {
        Entry entry = new Entry(++nextSeq, level, logger, line);
        entries.addLast(entry);
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
    }

    /** The entries with a sequence greater than {@code after}, oldest first. */
    public synchronized List<Entry> after(long after) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.seq() > after) {
                out.add(entry);
            }
        }
        return out;
    }

    /** The newest sequence number (0 for an empty ring). */
    public synchronized long lastSeq() {
        return nextSeq;
    }
}
