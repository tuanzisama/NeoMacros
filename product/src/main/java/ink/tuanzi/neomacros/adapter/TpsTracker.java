package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.TpsData;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Estimates the remote tick rate from time sync packet cadence: each packet
 * covers 20 server ticks, so the interval between consecutive packets yields
 * the instantaneous rate, and sliding tick windows yield the rolling minute
 * averages.
 */
public final class TpsTracker {

    private static final long WINDOW_MILLIS_1 = 60_000L;
    private static final long WINDOW_MILLIS_5 = 300_000L;
    private static final long WINDOW_MILLIS_15 = 900_000L;
    private static final double TICKS_PER_PACKET = 20.0;
    private static final double MAX_TPS = 1000.0;

    private static final Deque<Sample> SAMPLES = new ArrayDeque<>();
    private static volatile long lastPacketMillis;
    private static volatile double instantTps;

    private TpsTracker() {
    }

    private record Sample(long atMillis, double ticks) {
    }

    /** Records one time sync packet arrival. */
    public static synchronized void recordPacket() {
        long now = System.currentTimeMillis();
        long last = lastPacketMillis;
        lastPacketMillis = now;
        if (last != 0) {
            long delta = Math.max(1, now - last);
            instantTps = Math.max(0.0, Math.min(MAX_TPS,
                    TICKS_PER_PACKET * 1000.0 / delta));
        }
        SAMPLES.addLast(new Sample(now, TICKS_PER_PACKET));
        prune(now);
    }

    /** The current estimate, or null before the first packet. */
    public static synchronized TpsData snapshot() {
        if (lastPacketMillis == 0) {
            return null;
        }
        long now = System.currentTimeMillis();
        prune(now);
        return new TpsData(instantTps,
                windowTps(now, WINDOW_MILLIS_1),
                windowTps(now, WINDOW_MILLIS_5),
                windowTps(now, WINDOW_MILLIS_15));
    }

    /** Drops samples older than the largest window. */
    private static void prune(long now) {
        long oldest = now - WINDOW_MILLIS_15;
        while (!SAMPLES.isEmpty() && SAMPLES.peekFirst().atMillis() < oldest) {
            SAMPLES.removeFirst();
        }
    }

    /** Average ticks per second over one sliding window. */
    private static double windowTps(long now, long windowMillis) {
        long from = now - windowMillis;
        double ticks = 0;
        for (Sample sample : SAMPLES) {
            if (sample.atMillis() >= from) {
                ticks += sample.ticks();
            }
        }
        return ticks / (windowMillis / 1000.0);
    }
}
