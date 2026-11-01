package ink.tuanzi.neomacros.fabric.web.v1;

/**
 * A one-time SSE stream ticket. EventSource cannot send headers, so the
 * signed ticket request exchanges a short-lived query parameter instead.
 */
public record TicketResponse(String ticket, int expiresIn) {
}
