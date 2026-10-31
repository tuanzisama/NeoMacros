package ink.tuanzi.neomacros.core.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Turns script failures into a bounded recent-error list and a throttled
 * chat notification line, so a hot loop erroring every tick cannot spam the
 * chat while the player still learns about failures promptly.
 */
public final class ErrorPresenter {

    /** Maximum number of recent errors retained for the viewer screen. */
    private static final int RETAINED = 20;

    private final Consumer<String> chatLine;
    private final long throttleMillis;
    private final AtomicLong lastShownAt = new AtomicLong();
    private final Deque<String> recent = new ArrayDeque<>();

    /**
     * Creates the presenter; {@code chatLine} receives one line per shown
     * failure (already throttled).
     */
    public ErrorPresenter(Consumer<String> chatLine, long throttleMillis) {
        this.chatLine = chatLine;
        this.throttleMillis = throttleMillis;
    }

    /**
     * Records one failure: always retained for the viewer, and surfaced on
     * chat when the throttle window allows.
     */
    public synchronized void record(Throwable error) {
        String summary = summarize(error);
        synchronized (recent) {
            recent.addLast(summary);
            while (recent.size() > RETAINED) {
                recent.removeFirst();
            }
        }
        long now = System.currentTimeMillis();
        long last = lastShownAt.get();
        if (now - last >= throttleMillis && lastShownAt.compareAndSet(last, now)) {
            chatLine.accept(summary);
        }
    }

    /** Returns the most recent error summaries, oldest first. */
    public List<String> recent() {
        synchronized (recent) {
            return List.copyOf(recent);
        }
    }

    /** Flattens one throwable into a single chat-sized line. */
    private static String summarize(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        String line = message.replace('\n', ' ').strip();
        if (line.length() > 120) {
            line = line.substring(0, 117) + "...";
        }
        return line;
    }
}
