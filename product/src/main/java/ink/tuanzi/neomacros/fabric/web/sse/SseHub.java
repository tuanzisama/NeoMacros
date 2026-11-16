package ink.tuanzi.neomacros.fabric.web.sse;

import ink.tuanzi.neomacros.fabric.web.json.Jsons;

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

    /**
     * The largest single SSE frame (event line + data) before a broadcast
     * splits into {@code segment} frames. Sized to keep one write well inside
     * ordinary socket buffers so a huge eval result never stalls the hub lock.
     */
    public static final int MAX_EVENT_BYTES = 64 * 1024;

    /**
     * Segment payload size in characters. Chunks this size stay under
     * {@link #MAX_EVENT_BYTES} for any {@code Jsons.stringify} output: compact
     * JSON carries no raw control characters (each re-escape is at most 3
     * UTF-8 bytes per char for non-ASCII, 2 for quotes/backslashes) plus the
     * small routing envelope.
     */
    private static final int SEGMENT_CHARS = 16 * 1024;

    /** Maximum simultaneously outstanding tickets. */
    private static final int MAX_TICKETS = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Long> tickets = new ConcurrentHashMap<>();
    private final List<SseConnection> connections = new CopyOnWriteArrayList<>();
    private final LongSupplier nowMillis;
    private final java.util.concurrent.atomic.AtomicLong segmentIds =
            new java.util.concurrent.atomic.AtomicLong();

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

    /**
     * Sends one named event with a JSON payload to every connected client.
     * Frames within {@link #MAX_EVENT_BYTES} go out as a single frame; bigger
     * payloads split into ordered {@code segment} frames (same envelope for
     * every broadcast event) that the editor reassembles before dispatch.
     * The whole frame group is written under one lock so segments of two
     * concurrent broadcasts never interleave — otherwise the editor's
     * seq-ordered stores could observe a later entry before an earlier one.
     */
    public void broadcast(String event, String jsonData) {
        String frame = "event: " + event + "\ndata: " + jsonData + "\n\n";
        // utf8Length never undercounts, so a char-length overflow already
        // proves the frame is over the cap (avoids scanning a huge payload
        // just to reach the same conclusion).
        if (jsonData.length() <= MAX_EVENT_BYTES
                && utf8Length(frame) <= MAX_EVENT_BYTES) {
            sendToAll(frame);
            return;
        }
        sendToAll(segmentFrames(event, jsonData));
    }

    /**
     * Splits one oversized payload into {@code segment} frames:
     * {@code {"ev":"<event>","id":<n>,"i":<idx>,"total":<n>,"data":"<slice>"}}.
     * Slices cut on char boundaries without splitting a surrogate pair (the
     * JSON text carries no raw control characters, so re-escaping a slice
     * costs at most 3 UTF-8 bytes per char — a slice stays inside the cap).
     */
    private List<String> segmentFrames(String event, String jsonData) {
        long id = segmentIds.incrementAndGet();
        List<String> chunks = new java.util.ArrayList<>();
        int from = 0;
        while (from < jsonData.length()) {
            int to = Math.min(from + SEGMENT_CHARS, jsonData.length());
            if (to < jsonData.length() && Character.isHighSurrogate(jsonData.charAt(to - 1))) {
                to--;
            }
            chunks.add(jsonData.substring(from, to));
            from = to;
        }
        List<String> frames = new java.util.ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String envelope = Jsons.stringify(java.util.Map.of(
                    "ev", event, "id", id, "i", i, "total", chunks.size(),
                    "data", chunks.get(i)));
            frames.add("event: segment\ndata: " + envelope + "\n\n");
        }
        return frames;
    }

    /** UTF-8 byte length of one frame without materializing the bytes. */
    private static int utf8Length(String text) {
        int bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                bytes++;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    /** Sends an SSE comment heartbeat to every connected client. */
    public void heartbeat() {
        sendToAll(": hb\n\n");
    }

    /**
     * Writes frames to everyone, dropping connections that failed.
     * Synchronized: broadcasts arrive from logging threads while the
     * heartbeat arrives from the scheduler, and interleaved partial writes
     * would corrupt the stream framing. A frame group (one broadcast's
     * segments) is written inside a single acquisition so groups stay
     * contiguous on the wire.
     */
    private synchronized void sendToAll(String frame) {
        sendToAll(List.of(frame));
    }

    /** Writes an ordered frame group under one lock (see {@link #sendToAll(String)}). */
    private synchronized void sendToAll(List<String> frames) {
        List<SseConnection> dead = new java.util.ArrayList<>();
        for (String frame : frames) {
            for (SseConnection connection : connections) {
                if (!connection.send(frame)) {
                    dead.add(connection);
                }
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
