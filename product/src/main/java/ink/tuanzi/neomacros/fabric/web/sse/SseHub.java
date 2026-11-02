package ink.tuanzi.neomacros.fabric.web.sse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/**
 * The server-sent-events hub: issues the one-time tickets the stream
 * endpoint consumes, holds the connected writers, and broadcasts named
 * events with JSON payloads to all of them. Dead connections are dropped on
 * their first failed write; a periodic heartbeat keeps half-open sockets
 * from lingering unnoticed.
 */
public final class SseHub {

    /** Ticket validity in milliseconds. */
    public static final long TICKET_TTL_MILLIS = 30_000;

    /** Maximum simultaneously outstanding tickets. */
    private static final int MAX_TICKETS = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Long> tickets = new ConcurrentHashMap<>();
    private final List<SseConnection> connections = new CopyOnWriteArrayList<>();
    private final LongSupplier nowMillis;

    /** Creates the hub; {@code nowMillis} is injectable for tests. */
    public SseHub(LongSupplier nowMillis) {
        this.nowMillis = nowMillis;
    }

    /** Issues a single-use ticket (base64url, 24 bytes of entropy). */
    public synchronized String issueTicket() {
        long now = nowMillis.getAsLong();
        tickets.values().removeIf(expiresAt -> now >= expiresAt);
        while (tickets.size() >= MAX_TICKETS) {
            tickets.remove(tickets.keySet().iterator().next());
        }
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tickets.put(ticket, now + TICKET_TTL_MILLIS);
        return ticket;
    }

    /**
     * Consumes a ticket; returns false when it is missing, expired, or
     * already used. Consuming happens before the stream opens so the error
     * path can still answer with a normal JSON body.
     */
    public boolean consumeTicket(String ticket) {
        if (ticket == null) {
            return false;
        }
        Long expiresAt = tickets.remove(ticket);
        return expiresAt != null && nowMillis.getAsLong() < expiresAt;
    }

    /** Opens the exchange output as a live event stream (ticket already consumed). */
    public SseConnection open(java.io.OutputStream out, String remote) throws java.io.IOException {
        byte[] raw = new byte[2];
        RANDOM.nextBytes(raw);
        SseConnection connection = new SseConnection(
                "ses_" + java.util.HexFormat.of().formatHex(raw), remote,
                nowMillis.getAsLong(), out);
        connections.add(connection);
        return connection;
    }

    /** One live session as the command surface lists it. */
    public record SessionInfo(String id, String remote, long connectedAtMillis) {
    }

    /** The live sessions, oldest first. */
    public List<SessionInfo> sessions() {
        List<SessionInfo> out = new java.util.ArrayList<>();
        for (SseConnection connection : connections) {
            out.add(new SessionInfo(connection.id, connection.remote, connection.connectedAt));
        }
        return out;
    }

    /** Closes the session with the given id; false when unknown. */
    public boolean disconnect(String id) {
        for (SseConnection connection : connections) {
            if (connection.id.equals(id)) {
                connection.close();
                connections.remove(connection);
                return true;
            }
        }
        return false;
    }

    /** Sends one named event with a JSON payload to every connected client. */
    public void broadcast(String event, String jsonData) {
        sendToAll("event: " + event + "\ndata: " + jsonData + "\n\n");
    }

    /** Sends an SSE comment heartbeat to every connected client. */
    public void heartbeat() {
        sendToAll(": hb\n\n");
    }

    /** Writes one frame to everyone, dropping connections that failed. */
    private void sendToAll(String frame) {
        List<SseConnection> dead = new java.util.ArrayList<>();
        for (SseConnection connection : connections) {
            if (!connection.send(frame)) {
                dead.add(connection);
            }
        }
        connections.removeAll(dead);
    }

    /** The number of live connections (diagnostics). */
    public int connectionCount() {
        return connections.size();
    }

    /** Closes every connection (service shutdown after the goodbye event). */
    public void closeAll() {
        for (SseConnection connection : connections) {
            connection.close();
        }
        connections.clear();
    }

    /** One live event stream: writes frames to the exchange output. */
    public static final class SseConnection {

        private final String id;
        private final String remote;
        private final long connectedAt;
        private final OutputStream out;
        private volatile boolean closed;

        /** A connection bound to an exchange output stream. */
        private SseConnection(String id, String remote, long connectedAt,
                              OutputStream out) throws IOException {
            this.id = id;
            this.remote = remote;
            this.connectedAt = connectedAt;
            this.out = out;
            out.write("retry: 3000\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        /** Writes one frame; false when the connection is dead. */
        public boolean send(String frame) {
            if (closed) {
                return false;
            }
            try {
                out.write(frame.getBytes(StandardCharsets.UTF_8));
                out.flush();
                return true;
            } catch (IOException e) {
                close();
                return false;
            }
        }

        /** Closes the underlying stream (ends the exchange). */
        public void close() {
            if (!closed) {
                closed = true;
                try {
                    out.close();
                } catch (IOException ignored) {
                    // the client is gone; nothing to report
                }
            }
        }
    }
}
