package ink.tuanzi.neomacros.fabric.web.v1;

/**
 * One JVM heap snapshot from the {@code MemoryMXBean}: the game instance's
 * real heap usage at sample time. All values are bytes.
 *
 * @param used      heap currently in use (last GC-aware estimate of the JVM)
 * @param committed heap committed to the JVM by the OS
 * @param max       the maximum heap the JVM may grow to (-1 when unbounded)
 */
public record JvmMemory(long used, long committed, long max) {
}
