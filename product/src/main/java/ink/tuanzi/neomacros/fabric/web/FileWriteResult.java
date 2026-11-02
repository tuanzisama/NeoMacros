package ink.tuanzi.neomacros.fabric.web;

import ink.tuanzi.neomacros.fabric.web.v1.LocatedError;

/**
 * The outcome of one file write: either the saved state — {@code error} is
 * the owner macro's structured reload error, null when it reloaded cleanly
 * or no macro owns the path — or the conflict shape carrying the current
 * text and its hash for the client's If-Match retry.
 */
public record FileWriteResult(LocatedError error, String conflictContent, String currentEtag) {

    /** Whether this outcome is an If-Match conflict rather than a save. */
    public boolean conflict() {
        return conflictContent != null;
    }

    /** The conflict shape for a file that does not exist yet. */
    public static FileWriteResult conflict(String content, String etag) {
        return new FileWriteResult(null, content, etag);
    }

    /** The saved shape. */
    public static FileWriteResult saved(LocatedError error) {
        return new FileWriteResult(error, null, null);
    }
}
