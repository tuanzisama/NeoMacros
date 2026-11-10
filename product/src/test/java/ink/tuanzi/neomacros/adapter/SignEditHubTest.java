package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.SignEditData;
import ink.tuanzi.neomacros.port.model.SignEditVerdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sign-edit hub verdict combination: cancel and closeScreen OR across sinks,
 * the last replacement lines win, and pass-through keeps the original text.
 */
class SignEditHubTest {

    @AfterEach
    void resetHub() {
        SignEditHub.reset();
    }

    private static SignEditData data() {
        return new SignEditData(1, 64, 2, true, List.of("", "", "", ""));
    }

    @Test
    void cancelOrsAcrossSinks() {
        SignEditHub.register(d -> SignEditVerdict.PASS);
        SignEditHub.register(d -> SignEditVerdict.CANCEL);
        assertTrue(SignEditHub.fire(data()).canceled());
    }

    @Test
    void closeScreenOrsAcrossSinks() {
        SignEditHub.register(d -> new SignEditVerdict(false, null, true));
        var verdict = SignEditHub.fire(data());
        assertFalse(verdict.canceled());
        assertTrue(verdict.closeScreen());
    }

    @Test
    void replacementLinesAreLastWriterWins() {
        SignEditHub.register(d -> new SignEditVerdict(false, List.of("a", "", "", ""), false));
        SignEditHub.register(d -> new SignEditVerdict(false, List.of("b", "", "", ""), false));

        var verdict = SignEditHub.fire(data());
        assertEquals(List.of("b", "", "", ""), verdict.lines());
    }

    @Test
    void noSinksKeepsTheOriginalText() {
        var verdict = SignEditHub.fire(data());
        assertFalse(verdict.canceled());
        assertFalse(verdict.closeScreen());
        assertNull(verdict.lines());
    }
}
