package ink.tuanzi.neomacros.core.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Per-script timeline backing the {@code neo.performance} module: a
 * monotonic high-resolution clock anchored at the session origin plus a
 * bounded ring of mark and measure entries. Each script owns one timeline;
 * all access happens on that script's loop thread, and methods are
 * synchronized only to keep the shared deque consistent under defensive
 * scheduling.
 */
final class PerformanceTimeline {

    /** Newest-kept entry count; older entries are evicted from the front. */
    static final int MAX_ENTRIES = 4096;

    private final LongSupplier nanos;
    private final LongSupplier millis;
    private final long originNanos;
    private final double originMillis;
    private final ArrayDeque<PerformanceApi.PerformanceEntry> entries = new ArrayDeque<>();

    /** Creates a timeline anchored at the current instant. */
    PerformanceTimeline() {
        this(System::nanoTime, System::currentTimeMillis);
    }

    /** Creates a timeline with injectable clocks (tests). */
    PerformanceTimeline(LongSupplier nanos, LongSupplier millis) {
        this.nanos = nanos;
        this.millis = millis;
        this.originNanos = nanos.getAsLong();
        this.originMillis = millis.getAsLong();
    }

    /** Wall-clock time of the session origin, in milliseconds. */
    synchronized double originMillis() {
        return originMillis;
    }

    /** Monotonic milliseconds since the origin, microsecond precision. */
    synchronized double now() {
        return Math.max(0.0, (nanos.getAsLong() - originNanos) / 1_000_000.0);
    }

    /** Records a zero-duration mark; the name must be non-empty. */
    synchronized PerformanceApi.PerformanceEntry mark(String name) {
        requireName(name);
        return push(new PerformanceApi.PerformanceEntry(name, "mark", now(), 0.0));
    }

    /**
     * Records a measure spanning {@code start} to {@code end}; each bound is
     * a mark name (resolving to that mark's most recent start time), a raw
     * timestamp, or {@code null} for the default ({@code 0} for the start,
     * the current time for the end).
     */
    synchronized PerformanceApi.PerformanceEntry measure(String name, Object start, Object end) {
        requireName(name);
        double from = resolveBound(start, 0.0);
        double to = resolveBound(end, now());
        return push(new PerformanceApi.PerformanceEntry(name, "measure", from, to - from));
    }

    /** Removes marks — all of them when {@code name} is null. */
    synchronized void clearMarks(String name) {
        clear("mark", name);
    }

    /** Removes measures — all of them when {@code name} is null. */
    synchronized void clearMeasures(String name) {
        clear("measure", name);
    }

    /** Snapshot of every kept entry in insertion order. */
    synchronized List<PerformanceApi.PerformanceEntry> entries() {
        return new ArrayList<>(entries);
    }

    /** Snapshot filtered by name and, when {@code entryType} is non-null, by type. */
    synchronized List<PerformanceApi.PerformanceEntry> entriesByName(String name, String entryType) {
        List<PerformanceApi.PerformanceEntry> out = new ArrayList<>();
        for (PerformanceApi.PerformanceEntry entry : entries) {
            if (entry.name().equals(name)
                    && (entryType == null || entry.entryType().equals(entryType))) {
                out.add(entry);
            }
        }
        return out;
    }

    /** Snapshot filtered by entry type ({@code mark} or {@code measure}). */
    synchronized List<PerformanceApi.PerformanceEntry> entriesByType(String entryType) {
        List<PerformanceApi.PerformanceEntry> out = new ArrayList<>();
        for (PerformanceApi.PerformanceEntry entry : entries) {
            if (entry.entryType().equals(entryType)) {
                out.add(entry);
            }
        }
        return out;
    }

    private PerformanceApi.PerformanceEntry push(PerformanceApi.PerformanceEntry entry) {
        entries.addLast(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
        return entry;
    }

    private void clear(String entryType, String name) {
        entries.removeIf(entry -> entry.entryType().equals(entryType)
                && (name == null || entry.name().equals(name)));
    }

    private double resolveBound(Object bound, double fallback) {
        if (bound == null) {
            return fallback;
        }
        if (bound instanceof String markName) {
            PerformanceApi.PerformanceEntry mark = latestMark(markName);
            if (mark == null) {
                throw new IllegalArgumentException("no mark named '" + markName
                        + "' on the timeline (never marked, cleared, or already evicted)");
            }
            return mark.startTime();
        }
        if (bound instanceof Number timestamp) {
            return timestamp.doubleValue();
        }
        throw new IllegalArgumentException(
                "measure bounds must be a mark name or a timestamp, got: " + bound);
    }

    private PerformanceApi.PerformanceEntry latestMark(String name) {
        Iterator<PerformanceApi.PerformanceEntry> it = entries.descendingIterator();
        while (it.hasNext()) {
            PerformanceApi.PerformanceEntry entry = it.next();
            if ("mark".equals(entry.entryType()) && entry.name().equals(name)) {
                return entry;
            }
        }
        return null;
    }

    private static void requireName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("performance mark/measure requires a non-empty name");
        }
    }
}
