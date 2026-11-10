package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.SignEditData;
import ink.tuanzi.neomacros.port.model.SignEditVerdict;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Fan-out point for the sign-edit editor open. The LocalPlayer mixin calls
 * {@link #fire} when the server's sign-editor packet is about to open the
 * editor; the player port registers its sink here. Sinks run on the game
 * main thread in registration order; the combined verdict cancels (or
 * closes the screen) on any sink and takes the last non-null replacement
 * lines.
 */
public final class SignEditHub {

    private static final List<Function<SignEditData, SignEditVerdict>> SINKS =
            new CopyOnWriteArrayList<>();

    private SignEditHub() {
    }

    /** Registers a sign-edit sink. */
    public static void register(Function<SignEditData, SignEditVerdict> sink) {
        SINKS.add(sink);
    }

    /** Clears every sink (test isolation). */
    static void reset() {
        SINKS.clear();
    }

    /** Fires the sign-edit sinks; the result drives the mixin's cancel path. */
    public static SignEditVerdict fire(SignEditData data) {
        boolean canceled = false;
        boolean closeScreen = false;
        List<String> replaced = null;
        for (Function<SignEditData, SignEditVerdict> sink : SINKS) {
            SignEditVerdict verdict = sink.apply(data);
            if (verdict == null) {
                continue;
            }
            canceled |= verdict.canceled();
            closeScreen |= verdict.closeScreen();
            if (verdict.lines() != null) {
                replaced = verdict.lines();
            }
        }
        return new SignEditVerdict(canceled, replaced, closeScreen);
    }
}
