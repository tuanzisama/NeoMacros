package ink.tuanzi.neomacros.core.net;

import java.util.Map;

/**
 * One recorded {@code neo.net} request, emitted once when the promise
 * settles (success or failure). Body previews are size-capped UTF-8
 * excerpts; full bodies are retained alongside the event only when small
 * enough ({@code requestBody}/{@code responseBody}, null when oversize or
 * recording off). Binary responses carry only their length. Phase timings
 * ({@code waitMs} = start until response headers, {@code downloadMs} =
 * headers until settle) are null when the failure happened before headers.
 * Failure fields are null/absent when the request succeeded, and vice
 * versa.
 *
 * @zh 一条 {@code neo.net} 请求记录，Promise 落定时发出（成功或失败各一条）。Body 预览为限量 UTF-8 截取；完整内容仅在小体积时随事件保留（超限或未开启记录为 null）；二进制响应只记大小。阶段计时 {@code waitMs}（发起到响应头）/ {@code downloadMs}（响应头到落定），头前失败为 null。失败字段与成功字段互斥。
 */
public record NetTraceEvent(
        long ts,
        String method,
        String url,
        String initiator,
        String initiatorFile,
        Map<String, String> requestHeaders,
        String requestBodyPreview,
        String requestBody,
        boolean requestBodyTruncated,
        Integer status,
        Map<String, String> responseHeaders,
        String responseBodyPreview,
        byte[] responseBody,
        boolean responseBodyTruncated,
        boolean responseBinary,
        Long size,
        Long durationMs,
        Long waitMs,
        Long downloadMs,
        String error) {
}
