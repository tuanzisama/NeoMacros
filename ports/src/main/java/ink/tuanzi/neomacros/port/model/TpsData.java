package ink.tuanzi.neomacros.port.model;

/**
 * Client-side estimate of the remote tick rate, sampled from time sync
 * packet cadence: the instantaneous rate plus rolling minute averages.
 *
 * @param instant ticks per second over the latest packet interval
 * @param m1      rolling average over the last minute
 * @param m5      rolling average over the last five minutes
 * @param m15     rolling average over the last fifteen minutes
 */
public record TpsData(double instant, double m1, double m5, double m15) {
}
