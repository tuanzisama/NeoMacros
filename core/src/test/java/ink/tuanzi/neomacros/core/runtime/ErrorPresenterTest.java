package ink.tuanzi.neomacros.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ErrorPresenter throttling and retention: every failure is retained for the
 * viewer while chat lines follow the throttle window.
 */
class ErrorPresenterTest {

    @Test
    void throttlesChatButRetainsEveryError() {
        List<String> lines = new ArrayList<>();
        ErrorPresenter presenter = new ErrorPresenter(lines::add, 10_000);

        presenter.record(new RuntimeException("first"));
        presenter.record(new RuntimeException("second"));
        presenter.record(new RuntimeException("third"));

        assertEquals(1, lines.size(), "only the first failure shows inside the window");
        assertEquals("first", lines.get(0));
        assertEquals(List.of("first", "second", "third"), presenter.recent());
    }

    @Test
    void showsAgainAfterWindowPasses() {
        List<String> lines = new ArrayList<>();
        ErrorPresenter presenter = new ErrorPresenter(lines::add, 0);

        presenter.record(new RuntimeException("a"));
        presenter.record(new RuntimeException("b"));

        assertEquals(2, lines.size(), "zero window shows every failure");
    }

    @Test
    void retainsBoundedHistoryAndTrimsLongLines() {
        List<String> lines = new ArrayList<>();
        ErrorPresenter presenter = new ErrorPresenter(lines::add, 60_000);
        String longMessage = "x".repeat(300);

        for (int i = 0; i < 30; i++) {
            presenter.record(new RuntimeException(longMessage + i));
        }

        assertEquals(20, presenter.recent().size(), "history is bounded at 20");
        assertTrue(presenter.recent().get(0).length() <= 120,
                "summary lines are chat-sized");
        assertTrue(presenter.recent().get(0).endsWith("..."));
    }
}
