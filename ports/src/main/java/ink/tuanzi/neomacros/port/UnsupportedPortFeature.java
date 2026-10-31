package ink.tuanzi.neomacros.port;

/**
 * Thrown by a port's {@code default} method when the runtime's adapter
 * implementation predates the feature.
 *
 * <p>The port evolution convention (see package javadoc) keeps old milestone
 * nodes compiling across port upgrades: new methods arrive with default
 * bodies that either degrade gracefully or raise this exception, and the
 * caller probes capabilities instead of assuming them.</p>
 */
public class UnsupportedPortFeature extends RuntimeException {

    /** Constructs the exception with a description of the missing feature. */
    public UnsupportedPortFeature(String feature) {
        super("Port feature not supported by this adapter: " + feature);
    }
}
