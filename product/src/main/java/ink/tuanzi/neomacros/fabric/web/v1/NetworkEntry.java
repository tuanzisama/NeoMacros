package ink.tuanzi.neomacros.fabric.web.v1;

import java.util.Map;

/**
 * One recorded {@code neo.net} request as the panel sees it: identity and
 * timing plus request/response headers and size-capped body previews. Full
 * bodies are not carried here — only the {@code hasRequestBody}/
 * {@code hasResponseBody} flags; the content itself is served per entry by
 * the {@code /network/{seq}/body} endpoint until it is evicted. Failure
 * rows carry {@code error} and leave {@code status}/{@code size} null;
 * success rows carry them and leave {@code error} null. Phase timings
 * ({@code waitMs} = start until response headers, {@code downloadMs} =
 * headers until settle) are null when the failure happened before headers.
 * Binary responses carry only {@code size} ({@code responseBodyPreview}
 * null, {@code responseBinary} true). Nulls mean "not applicable".
 */
public record NetworkEntry(long seq, long ts, String method, String url, String initiator,
                           String initiatorFile,
                           Map<String, String> requestHeaders,
                           String requestBodyPreview, boolean hasRequestBody,
                           boolean requestBodyTruncated,
                           Integer status, Map<String, String> responseHeaders,
                           String responseBodyPreview, boolean hasResponseBody,
                           boolean responseBodyTruncated,
                           boolean responseBinary, Long size, Long durationMs,
                           Long waitMs, Long downloadMs, String error) {
}
