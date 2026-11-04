package ink.tuanzi.neomacros.core.api;

/**
 * Marker contract for data-kind cells the script can construct directly
 * ({@code new Vec3(1, 2, 3)}): the engine routes the guest constructor's
 * raw arguments here to build the backing cell. Implementations must return
 * a fresh cell of their own type.
 */
public interface SelfConstructing {

    /** Builds a new cell from the raw guest constructor arguments. */
    Object construct(Object[] args);
}
