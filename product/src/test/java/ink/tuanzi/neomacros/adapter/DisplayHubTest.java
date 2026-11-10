package ink.tuanzi.neomacros.adapter;

import ink.tuanzi.neomacros.port.model.ChatComponent;
import ink.tuanzi.neomacros.port.model.TextVerdict;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Display hub verdict combination: the cancel verdict ORs across sinks, the
 * last replacement text wins, and vanilla components round-trip the neutral
 * model (styles preserved).
 */
class DisplayHubTest {

    @AfterEach
    void resetHubs() {
        DisplayHub.reset();
    }

    @Test
    void cancelOrsAcrossSinks() {
        DisplayHub.registerTitle(d -> TextVerdict.PASS);
        DisplayHub.registerTitle(d -> TextVerdict.CANCEL);
        DisplayHub.registerTitle(d -> new TextVerdict(false, ChatComponent.plain("swapped")));

        assertNull(DisplayHub.fireTitle("title", Component.literal("original")),
                "any canceling sink suppresses the display");
    }

    @Test
    void replacementIsLastWriterWins() {
        DisplayHub.registerTitle(d -> new TextVerdict(false, ChatComponent.plain("first")));
        DisplayHub.registerTitle(d -> new TextVerdict(false, ChatComponent.plain("second")));

        var result = DisplayHub.fireTitle("subtitle", Component.literal("original"));
        assertEquals("second", result.getString());
    }

    @Test
    void passThroughKeepsTheOriginalComponent() {
        DisplayHub.registerActionbar(d -> TextVerdict.PASS);
        var original = Component.literal("keep");
        assertEquals(original, DisplayHub.fireActionbar("server", original));
    }

    @Test
    void vanillaComponentRoundTripsWithStyles() {
        var original = Component.literal("head").withStyle(style -> style.withBold(true))
                .append(Component.literal("tail").withStyle(style -> style.withColor(
                        net.minecraft.ChatFormatting.RED)));
        var neutral = NeutralText.toModel(original);
        assertEquals("head", neutral.text());
        assertEquals(Boolean.TRUE, neutral.bold());
        assertEquals(1, neutral.children().size());
        String childColor = neutral.children().get(0).color();
        assertTrue(childColor.equals("red") || childColor.equals("#FF5555"),
                "named or hex form, got: " + childColor);

        var rebuilt = NeutralText.toNative(neutral);
        assertEquals("headtail", rebuilt.getString());
        assertTrue(rebuilt.getStyle().isBold());
        assertTrue(rebuilt.getSiblings().get(0).getStyle().getColor() != null);
    }

    @Test
    void replacementTextReachesTheVerdict() {
        DisplayHub.registerActionbar(d -> new TextVerdict(false, ChatComponent.plain("nope")));
        var result = DisplayHub.fireActionbar("local", Component.literal("original"));
        assertEquals("nope", result.getString());
    }
}
