package ink.tuanzi.neomacros.doc.api;

/**
 * Kind of a script-facing class declared with {@link ApiClass}.
 *
 * <p>{@link #HANDLE} marks a live-reference class whose getters read current
 * game state through the port layer in real time; {@link #DATA} marks an
 * immutable plain-data class (positions, item stacks, text) that is safe to
 * store, compare, and serialize.</p>
 */
public enum ApiClassKind {

    /** Live-reference handle: real-time getters, {@code snapshot()} copy, undefined-on-stale reads. */
    HANDLE,

    /** Immutable plain-data class: JSON-safe values with no live state. */
    DATA,
}
