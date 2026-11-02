package ink.tuanzi.neomacros.fabric.web;

/**
 * One file read from the macros root: the exact bytes and the ETag
 * (base64url SHA-256) used for optimistic concurrency on write.
 */
public record FileContent(byte[] bytes, String etag) {
}
