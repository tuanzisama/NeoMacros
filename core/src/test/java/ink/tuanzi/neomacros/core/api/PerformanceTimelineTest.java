package ink.tuanzi.neomacros.core.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral tests for the per-script performance timeline: origin math,
 * mark/measure resolution in every accepted form, ring-buffer eviction,
 * clears, and query filters. Clocks are injected so timings are exact.
 */
class PerformanceTimelineTest {

    /** Controlled clock pair: both read one shared millisecond counter. */
    private static final class FakeClock {
        final AtomicLong millis = new AtomicLong();
        final long originWall;

        FakeClock(long originWall) {
            this.originWall = originWall;
        }

        long nanos() {
            return millis.get() * 1_000_000L;
        }

        long wall() {
            return originWall + millis.get();
        }

        void advance(double ms) {
            millis.addAndGet((long) ms);
        }
    }

    private PerformanceTimeline timeline(FakeClock clock) {
        return new PerformanceTimeline(clock::nanos, clock::wall);
    }

    @Test
    void originAnchorsWallClockAndNowStartsAtZero() {
        FakeClock clock = new FakeClock(1_000_000L);
        PerformanceTimeline t = timeline(clock);
        assertEquals(1_000_000.0, t.originMillis());
        assertEquals(0.0, t.now());
        clock.advance(250);
        assertEquals(250.0, t.now());
        assertEquals(1_000_250.0, t.originMillis() + t.now());
    }

    @Test
    void nowKeepsSubMillisecondPrecision() {
        FakeClock clock = new FakeClock(0);
        AtomicLong micros = new AtomicLong();
        PerformanceTimeline t = new PerformanceTimeline(
                () -> micros.get() * 1_000L, () -> micros.get() / 1_000L);
        micros.set(1_500); // 1.5 ms
        assertEquals(1.5, t.now());
    }

    @Test
    void markRecordsZeroDurationEntry() {
        FakeClock clock = new FakeClock(0);
        PerformanceTimeline t = timeline(clock);
        clock.advance(5);
        PerformanceApi.PerformanceEntry entry = t.mark("loop");
        assertEquals("loop", entry.name());
        assertEquals("mark", entry.entryType());
        assertEquals(5.0, entry.startTime());
        assertEquals(0.0, entry.duration());
        assertEquals(List.of(entry), t.entriesByType("mark"));
    }

    @Test
    void markRejectsEmptyNames() {
        PerformanceTimeline t = timeline(new FakeClock(0));
        assertThrows(IllegalArgumentException.class, () -> t.mark(null));
        assertThrows(IllegalArgumentException.class, () -> t.mark(""));
    }

    @Test
    void measureDefaultsSpanOriginToNow() {
        FakeClock clock = new FakeClock(0);
        PerformanceTimeline t = timeline(clock);
        clock.advance(40);
        PerformanceApi.PerformanceEntry entry = t.measure("boot", null, null);
        assertEquals(0.0, entry.startTime());
        assertEquals(40.0, entry.duration());
    }

    @Test
    void measureResolvesMarkNamesToTheirTimestamps() {
        FakeClock clock = new FakeClock(0);
        PerformanceTimeline t = timeline(clock);
        clock.advance(5);
        t.mark("a");
        clock.advance(5);
        t.mark("b");
        PerformanceApi.PerformanceEntry entry = t.measure("ab", "a", "b");
        assertEquals(5.0, entry.startTime());
        assertEquals(5.0, entry.duration());
    }

    @Test
    void measureAcceptsRawTimestampsAndMixedBounds() {
        FakeClock clock = new FakeClock(0);
        PerformanceTimeline t = timeline(clock);
        clock.advance(3);
        t.mark("a");
        PerformanceApi.PerformanceEntry numeric = t.measure("t", 2.0, 7.0);
        assertEquals(2.0, numeric.startTime());
        assertEquals(5.0, numeric.duration());
        PerformanceApi.PerformanceEntry mixed = t.measure("m", "a", 9.0);
        assertEquals(3.0, mixed.startTime());
        assertEquals(6.0, mixed.duration());
    }

    @Test
    void measurePrefersTheMostRecentMarkWithTheName() {
        FakeClock clock = new FakeClock(0);
        PerformanceTimeline t = timeline(clock);
        clock.advance(1);
        t.mark("loop");
        clock.advance(4);
        t.mark("loop");
        PerformanceApi.PerformanceEntry entry = t.measure("span", "loop", null);
        assertEquals(5.0, entry.startTime());
    }

    @Test
    void measureRejectsUnknownMarkNames() {
        PerformanceTimeline t = timeline(new FakeClock(0));
        assertThrows(IllegalArgumentException.class, () -> t.measure("m", "ghost", null));
        assertThrows(IllegalArgumentException.class,
                () -> t.measure("m", 42, "also-ghost"));
    }

    @Test
    void clearedMarksStopResolving() {
        FakeClock clock = new FakeClock(0);
        PerformanceTimeline t = timeline(clock);
        t.mark("a");
        t.measure("span", "a", null);
        t.clearMarks("a");
        assertThrows(IllegalArgumentException.class, () -> t.measure("again", "a", null));
    }

    @Test
    void clearsAreScopedToNameWhenGiven() {
        FakeClock clock = new FakeClock(0);
        PerformanceTimeline t = timeline(clock);
        t.mark("a");
        t.mark("b");
        t.measure("a", null, null);
        t.clearMarks("a");
        assertEquals(1, t.entriesByType("mark").size());
        assertEquals("b", t.entriesByType("mark").get(0).name());
        assertEquals(1, t.entriesByType("measure").size());
    }

    @Test
    void clearsWithoutNameRemoveEverythingOfThatType() {
        FakeClock clock = new FakeClock(0);
        PerformanceTimeline t = timeline(clock);
        t.mark("a");
        t.mark("b");
        t.measure("m", null, null);
        t.clearMarks(null);
        t.clearMeasures(null);
        assertEquals(0, t.entries().size());
    }

    @Test
    void queriesFilterByNameAndType() {
        FakeClock clock = new FakeClock(0);
        PerformanceTimeline t = timeline(clock);
        t.mark("x");
        t.measure("x", null, null);
        t.measure("y", null, null);
        assertEquals(3, t.entries().size());
        assertEquals(2, t.entriesByName("x", null).size());
        assertEquals(1, t.entriesByName("x", "mark").size());
        assertEquals(2, t.entriesByType("measure").size());
        assertEquals(0, t.entriesByType("paint").size());
    }

    @Test
    void evictionKeepsTheNewestRing() {
        FakeClock clock = new FakeClock(0);
        PerformanceTimeline t = timeline(clock);
        int extra = 5;
        for (int i = 0; i < PerformanceTimeline.MAX_ENTRIES + extra; i++) {
            t.mark("m" + i);
            clock.advance(1);
        }
        List<PerformanceApi.PerformanceEntry> entries = t.entries();
        assertEquals(PerformanceTimeline.MAX_ENTRIES, entries.size());
        assertEquals("m" + extra, entries.get(0).name());
        assertEquals("m" + (PerformanceTimeline.MAX_ENTRIES + extra - 1),
                entries.get(entries.size() - 1).name());
    }

    @Test
    void evictedMarksStopResolvingToo() {
        FakeClock clock = new FakeClock(0);
        PerformanceTimeline t = timeline(clock);
        t.mark("old");
        for (int i = 0; i < PerformanceTimeline.MAX_ENTRIES; i++) {
            t.mark("filler" + i);
        }
        assertThrows(IllegalArgumentException.class, () -> t.measure("m", "old", null));
    }

    @Test
    void monotonicClockNeverReadsNegative() {
        // A clock stepping backward must not surface negative timestamps.
        AtomicLong backwards = new AtomicLong(1_000_000L);
        PerformanceTimeline t = new PerformanceTimeline(
                () -> backwards.get(), backwards::get);
        backwards.set(0);
        assertEquals(0.0, t.now());
    }
}
