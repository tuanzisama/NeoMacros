package ink.tuanzi.neomacros.core.module.manifest;

/**
 * A script folder's manifest is missing or invalid. The message names the
 * folder and the concrete reason; the folder is skipped by discovery.
 */
public class ManifestParseException extends RuntimeException {

    public ManifestParseException(String message) {
        super(message);
    }

    public ManifestParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
