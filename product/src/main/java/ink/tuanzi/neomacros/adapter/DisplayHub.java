package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.ActionbarDisplayData;
import ink.tuanzi.neomacros.port.model.ChatComponent;
import ink.tuanzi.neomacros.port.model.TextVerdict;
import ink.tuanzi.neomacros.port.model.TitleDisplayData;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Fan-out point for the HUD title/subtitle/action-bar displays. The packet
 * mixins call the fire methods with the component about to reach the HUD;
 * the chat port registers its display sinks here. Sinks run on the game main
 * thread in registration order; the combined verdict cancels on any sink and
 * takes the last non-null replacement text.
 */
public final class DisplayHub {

    private static final List<Function<TitleDisplayData, TextVerdict>> TITLE_SINKS =
            new CopyOnWriteArrayList<>();
    private static final List<Function<ActionbarDisplayData, TextVerdict>> ACTIONBAR_SINKS =
            new CopyOnWriteArrayList<>();

    private DisplayHub() {
    }

    /** Registers a title/subtitle display sink. */
    public static void registerTitle(Function<TitleDisplayData, TextVerdict> sink) {
        TITLE_SINKS.add(sink);
    }

    /** Registers an action-bar display sink. */
    public static void registerActionbar(Function<ActionbarDisplayData, TextVerdict> sink) {
        ACTIONBAR_SINKS.add(sink);
    }

    /** Clears every sink (test isolation). */
    static void reset() {
        TITLE_SINKS.clear();
        ACTIONBAR_SINKS.clear();
    }

    /**
     * Fires the title sinks for one title/subtitle display. The result is
     * null when suppressed, otherwise the original or replaced component.
     */
    public static Component fireTitle(String kind, Component original) {
        TitleDisplayData display = new TitleDisplayData(kind, NeutralText.toModel(original));
        boolean canceled = false;
        ChatComponent replaced = null;
        for (Function<TitleDisplayData, TextVerdict> sink : TITLE_SINKS) {
            TextVerdict verdict = sink.apply(display);
            if (verdict == null) {
                continue;
            }
            canceled |= verdict.canceled();
            if (verdict.text() != null) {
                replaced = verdict.text();
            }
        }
        if (canceled) {
            return null;
        }
        return replaced != null ? NeutralText.toNative(replaced) : original;
    }

    /**
     * Fires the action-bar sinks for one overlay message (server packet or
     * local overlay). The result is null when suppressed, otherwise the
     * original or replaced component.
     */
    public static Component fireActionbar(String source, Component original) {
        ActionbarDisplayData display =
                new ActionbarDisplayData(NeutralText.toModel(original), source);
        boolean canceled = false;
        ChatComponent replaced = null;
        for (Function<ActionbarDisplayData, TextVerdict> sink : ACTIONBAR_SINKS) {
            TextVerdict verdict = sink.apply(display);
            if (verdict == null) {
                continue;
            }
            canceled |= verdict.canceled();
            if (verdict.text() != null) {
                replaced = verdict.text();
            }
        }
        if (canceled) {
            return null;
        }
        return replaced != null ? NeutralText.toNative(replaced) : original;
    }
}
