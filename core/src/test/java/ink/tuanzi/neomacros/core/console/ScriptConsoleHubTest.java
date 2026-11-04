package ink.tuanzi.neomacros.core.console;

import ink.tuanzi.neomacros.port.model.ChatComponent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The console hub contract: ordered fan-out to installed dispatchers,
 * reference-equal removal, a broken sink never breaking the rest, and the
 * component-view hook with its safe null/error fallbacks.
 */
class ScriptConsoleHubTest {

    private static ScriptConsoleEvent event(String macro) {
        return new ScriptConsoleEvent(macro, "log", "info", null, null, null, 0,
                List.of(), null, null, null, null, 0);
    }

    @Test
    void dispatchFansOutToEveryInstalledDispatcher() throws Exception {
        LinkedBlockingQueue<ScriptConsoleEvent> a = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<ScriptConsoleEvent> b = new LinkedBlockingQueue<>();
        ScriptConsole.installDispatcher(a::add);
        ScriptConsole.installDispatcher(b::add);
        try {
            ScriptConsole.dispatch(event("m"));
            assertEquals("m", a.poll(2, TimeUnit.SECONDS).macro());
            assertEquals("m", b.poll(2, TimeUnit.SECONDS).macro());
        } finally {
            ScriptConsole.removeDispatcher(a::add);
            ScriptConsole.removeDispatcher(b::add);
        }
    }

    @Test
    void removalStopsTheFanOut() throws Exception {
        LinkedBlockingQueue<ScriptConsoleEvent> received = new LinkedBlockingQueue<>();
        java.util.function.Consumer<ScriptConsoleEvent> dispatcher = received::add;
        ScriptConsole.installDispatcher(dispatcher);
        ScriptConsole.removeDispatcher(dispatcher);
        ScriptConsole.dispatch(event("gone"));
        Thread.sleep(100);
        assertNull(received.poll(), "a removed dispatcher must not receive events");
    }

    @Test
    void aBrokenSinkNeverBreaksTheOthers() throws Exception {
        LinkedBlockingQueue<ScriptConsoleEvent> healthy = new LinkedBlockingQueue<>();
        java.util.function.Consumer<ScriptConsoleEvent> broken = event -> {
            throw new IllegalStateException("sink exploded");
        };
        ScriptConsole.installDispatcher(broken);
        ScriptConsole.installDispatcher(healthy::add);
        try {
            ScriptConsole.dispatch(event("still"));
            assertEquals("still", healthy.poll(2, TimeUnit.SECONDS).macro());
        } finally {
            ScriptConsole.removeDispatcher(broken);
            ScriptConsole.removeDispatcher(healthy::add);
        }
    }

    @Test
    void componentViewFallsBackSafely() {
        ScriptConsole.installComponentView(null);
        assertNull(ScriptConsole.componentView("anything"));

        ScriptConsole.installComponentView(value -> value instanceof String s
                ? ChatComponent.plain(s)
                : null);
        try {
            assertEquals("text", ScriptConsole.componentView("text").text());
            assertNull(ScriptConsole.componentView(42));
            assertNull(ScriptConsole.componentView(null));
        } finally {
            ScriptConsole.installComponentView(null);
        }
    }

    @Test
    void aThrowingViewFallsBackToNull() {
        ScriptConsole.installComponentView(value -> {
            throw new IllegalStateException("renderer exploded");
        });
        try {
            assertNull(ScriptConsole.componentView("anything"));
        } finally {
            ScriptConsole.installComponentView(null);
        }
    }

    @Test
    void eventsCarryImmutableArgs() {
        ScriptConsoleEvent original = new ScriptConsoleEvent("m", "log", "info", null,
                null, null, 0, new java.util.ArrayList<>(List.of("x")),
                null, null, null, null, 0);
        assertTrue(original.args().size() == 1);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> original.args().add("y"));
    }
}
