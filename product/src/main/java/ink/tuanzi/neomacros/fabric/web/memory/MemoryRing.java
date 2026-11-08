package ink.tuanzi.neomacros.fabric.web.memory;

import ink.tuanzi.neomacros.fabric.web.v1.JvmMemory;
import ink.tuanzi.neomacros.fabric.web.v1.MemorySample;
import ink.tuanzi.neomacros.fabric.web.v1.ScriptMemory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * In-memory ring of memory samples with a monotonically increasing sequence,
 * shaped like the console/net rings: web clients replay incrementally with
 * {@code GET /api/v1/memory?after=} and subscribe to the live tail through
 * the SSE {@code memory:sample} event; both read from here. Appends arrive
 * on the sampler thread, so seq order matches sample order.
 */
public final class MemoryRing {

    private final int capacity;
    private final ArrayDeque<MemorySample> entries;
    private long nextSeq;
    private Consumer<MemorySample> listener;

    /** Creates the ring with the given capacity. */
    public MemoryRing(int capacity) {
        this.capacity = capacity;
        this.entries = new ArrayDeque<>(capacity);
    }

    /** Sets the live-tail listener (the SSE memory feed). */
    public synchronized void addListener(Consumer<MemorySample> listener) {
        this.listener = listener;
    }

    /**
     * Builds the sample record with its freshly assigned sequence, appends
     * it, and returns it for the SSE live tail.
     */
    public synchronized MemorySample append(long ts, JvmMemory jvm, long graaljsAllocated,
                                            List<ScriptMemory> scripts) {
        MemorySample entry = new MemorySample(++nextSeq, ts, jvm, graaljsAllocated,
                List.copyOf(scripts));
        entries.addLast(entry);
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
        if (listener != null) {
            listener.accept(entry);
        }
        return entry;
    }

    /** The samples with a sequence greater than {@code after}, oldest first. */
    public synchronized List<MemorySample> after(long after) {
        List<MemorySample> out = new ArrayList<>();
        for (MemorySample entry : entries) {
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
