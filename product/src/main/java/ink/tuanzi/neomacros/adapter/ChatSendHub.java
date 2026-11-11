package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.ChatSendVerdict;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Fan-out point for outgoing chat messages. The client network listener mixin
 * calls {@link #fire} when a chat message is about to go on the wire (player
 * sends and script {@code chat.say} sends alike); the chat port registers its
 * sink here. Sinks run on the game main thread in registration order; the
 * combined verdict cancels on any sink and takes the last non-null
 * replacement text.
 *
 * <p>Redispatching a rewritten message goes through {@link #muted}: the guard
 * short-circuits {@link #fire} so handler-initiated sends never re-enter the
 * interception chain (no feedback loops).</p>
 */
public final class ChatSendHub {

    private static final List<Function<String, ChatSendVerdict>> SINKS =
            new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Boolean> MUTED = ThreadLocal.withInitial(() -> false);

    private ChatSendHub() {
    }

    /** Registers a chat-send sink. */
    public static void register(Function<String, ChatSendVerdict> sink) {
        SINKS.add(sink);
    }

    /** Clears every sink (test isolation). */
    static void reset() {
        SINKS.clear();
    }

    /** Runs the action with the sinks muted (redispatched rewritten sends). */
    public static void muted(Runnable action) {
        MUTED.set(true);
        try {
            action.run();
        } finally {
            MUTED.set(false);
        }
    }

    /** Fires the chat-send sinks; the result drives the mixin's cancel path. */
    public static ChatSendVerdict fire(String message) {
        if (MUTED.get()) {
            return ChatSendVerdict.PASS;
        }
        boolean canceled = false;
        String replaced = null;
        for (Function<String, ChatSendVerdict> sink : SINKS) {
            ChatSendVerdict verdict = sink.apply(message);
            if (verdict == null) {
                continue;
            }
            canceled |= verdict.canceled();
            if (verdict.message() != null) {
                replaced = verdict.message();
            }
        }
        return new ChatSendVerdict(canceled, replaced);
    }
}
