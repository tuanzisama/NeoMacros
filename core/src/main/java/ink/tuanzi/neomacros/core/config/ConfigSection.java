package ink.tuanzi.neomacros.core.config;

/**
 * One root-key section of {@code options.json}: a holder object whose
 * {@link Option}-annotated fields are loaded and saved by the
 * {@link ConfigManager} through reflection.
 */
public interface ConfigSection {

    /** The root key of this section (for example {@code "core"}). */
    String key();

    /** The schema version this section class reads and writes. */
    int schemaVersion();
}
