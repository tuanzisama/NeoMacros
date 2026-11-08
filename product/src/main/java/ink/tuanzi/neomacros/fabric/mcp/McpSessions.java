package ink.tuanzi.neomacros.fabric.mcp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The MCP session table: one entry per completed {@code initialize},
 * keyed by the opaque id the client echoes back in the {@code Mcp-Session-Id}
 * header. Entries idle past the timeout are swept lazily on the next
 * create, so an agent that walks away frees its slot without any timer.
 */
public final class McpSessions {

    /** How long a session may sit without a request before the sweep drops it. */
    public static final long IDLE_MILLIS = 30L * 60 * 1000;

    /** One live connection state. */
    public record Session(String id, String protocolVersion, long createdAtMs) {
    }

    private static final class Entry {
        final Session session;
        volatile long lastSeenMs;

        Entry(Session session, long now) {
            this.session = session;
            this.lastSeenMs = now;
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /** Creates the empty table. */
    public McpSessions() {
    }

    /** Sweeps idle entries, creates a session, and returns it. */
    public synchronized Session create(String protocolVersion, long now) {
        long deadline = now - IDLE_MILLIS;
        entries.values().removeIf(entry -> entry.lastSeenMs < deadline);
        Session session = new Session(newId(), protocolVersion, now);
        entries.put(session.id(), new Entry(session, now));
        return session;
    }

    /** The session for the id, refreshing its idle clock; null when unknown. */
    public Session get(String id) {
        if (id == null) {
            return null;
        }
        Entry entry = entries.get(id);
        if (entry == null) {
            return null;
        }
        entry.lastSeenMs = System.currentTimeMillis();
        return entry.session;
    }

    /** Drops the session; false when the id is unknown. */
    public boolean remove(String id) {
        return id != null && entries.remove(id) != null;
    }

    /** Drops every session (a token rotation invalidates all connections). */
    public synchronized void clear() {
        entries.clear();
    }

    /** The live session count (diagnostics). */
    public int count() {
        return entries.size();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
