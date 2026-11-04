package ink.tuanzi.neomacros.fabric.web.console;

import ink.tuanzi.neomacros.core.console.ScriptConsoleEvent;
import ink.tuanzi.neomacros.fabric.web.v1.ConsoleEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * In-memory ring of script console emissions with a monotonically increasing
 * sequence, mirroring the {@link ink.tuanzi.neomacros.fabric.web.log.LogRing}
 * shape: web clients replay incrementally with {@code GET /console?after=}
 * and subscribe to the live tail through the SSE {@code console} event; both
 * read from here. Appends arrive on the core console pipeline thread (one
 * ordered thread off the script loops), so seq order matches emission order.
 */
public final class ConsoleRing {

    private final int capacity;
    private final ArrayDeque<ConsoleEntry> entries;
    private long nextSeq;
    private Consumer<ConsoleEntry> listener;

    /** Creates the ring with the given capacity. */
    public ConsoleRing(int capacity) {
        this.capacity = capacity;
        this.entries = new ArrayDeque<>(capacity);
    }

    /** Sets the live-tail listener (the SSE console feed). */
    public synchronized void addListener(Consumer<ConsoleEntry> listener) {
        this.listener = listener;
    }

    /**
     * Converts one core console event into the payload record, appends it,
     * and returns the record (with its freshly assigned sequence) so direct
     * appenders (the DevTools eval route) can echo the entry back.
     */
    public synchronized ConsoleEntry append(ScriptConsoleEvent event) {
        ConsoleEntry entry = new ConsoleEntry(++nextSeq, event.timestampMillis(),
                event.macro(), event.kind(), event.level(),
                event.file(), event.line(), event.column(), event.depth(),
                event.args(), event.label(), event.count(), event.ms(), event.stack());
        entries.addLast(entry);
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
        if (listener != null) {
            listener.accept(entry);
        }
        return entry;
    }

    /** The entries with a sequence greater than {@code after}, oldest first. */
    public synchronized List<ConsoleEntry> after(long after) {
        List<ConsoleEntry> out = new ArrayList<>();
        for (ConsoleEntry entry : entries) {
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
