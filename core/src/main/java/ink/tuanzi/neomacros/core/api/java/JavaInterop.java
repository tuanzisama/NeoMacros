package ink.tuanzi.neomacros.core.api.java;

import java.util.function.Function;

/**
 * The engine-facing contract of a Java-interop object: the engine renders it
 * as a dynamic guest proxy whose member reads resolve through these methods,
 * so scripts reach unwrapped Java members under the closed host-access
 * policy. A member read either yields a marshalled value or a dispatch
 * function the engine calls with converted arguments.
 */
public interface JavaInterop {

    /** The runtime class name of the wrapped object or class. */
    String className();

    /** Whether any field or method carries the member name. */
    boolean hasMember(String name);

    /**
     * Reads one member: a field value, or a dispatch function for methods
     * (including the {@code new} construction member on classes).
     */
    Function<Object[], Object> readCallable(String name);

    /** Reads a plain-value member (fields only), or null when absent. */
    Object readValue(String name);

    /** Dispatches one method by name with already-converted Java arguments. */
    Object invoke(String name, Object[] args);

    /** Writes a public field; throws when no writable field carries the name. */
    void write(String name, Object value);

    /** Whether the wrapped object is an instance of the named class. */
    boolean instanceOf(String className);

    /**
     * The raw host value behind this proxy (the wrapped instance or class);
     * the engine's hidden {@code __neoRaw} member probes it to unwrap
     * interop proxies crossing back into host code.
     */
    Object rawValue();

    /**
     * Marshals one interop result for the guest: scalars pass through,
     * already-interop values pass through, and every other host object
     * (collections included, per the escape-layer marshalling contract)
     * wraps as a {@link JavaObject} handle.
     */
    static Object wrap(Object value) {
        if (value == null || value instanceof JavaInterop
                || value instanceof String || value instanceof Boolean
                || value instanceof Number || value instanceof Character) {
            return value;
        }
        return new JavaObject(value);
    }
}
