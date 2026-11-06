package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.types.Text;
import ink.tuanzi.neomacros.core.api.types.TextBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rich text surface: chat.text factory, the builder chain, and the
 * immutable Text guest (plain extraction and JSON rendering).
 */
class TextGapTest {

    @Test
    void builderChainProducesStyledSegments() {
        Text text = new ChatApi().text("hello", null)
                .color("red").bold()
                .append(" world", null).underlined()
                .build();
        assertEquals(2, text.getSegments().size());
        assertEquals("hello", text.getSegments().get(0).text());
        assertEquals("red", text.getSegments().get(0).color());
        assertEquals(Boolean.TRUE, text.getSegments().get(0).bold());
        assertEquals(" world", text.getSegments().get(1).text());
        assertEquals(Boolean.TRUE, text.getSegments().get(1).underlined());
        assertEquals("hello world", text.getPlain());
    }

    @Test
    void hoverAndClickRideTheSegment() {
        Text text = new ChatApi().text("click", null)
                .hover("details").clickCommand("/help").clickUrl("https://example.com")
                .build();
        assertEquals("details", text.getSegments().get(0).hoverText());
        assertEquals("/help", text.getSegments().get(0).clickCommand());
        assertEquals("https://example.com", text.getSegments().get(0).clickUrl());
    }

    @Test
    void styleMapSeedsTheFirstSegment() {
        TextBuilder builder = new TextBuilder("styled", Map.of("color", "gold", "bold", true));
        Text text = builder.build();
        assertEquals("gold", text.getSegments().get(0).color());
        assertEquals(Boolean.TRUE, text.getSegments().get(0).bold());
        assertTrue(text.toJSON().get(0).containsKey("text"));
    }

    @Test
    void appendWithoutStyleStaysUnstyled() {
        Text text = new TextBuilder("a", null).append("b", null).build();
        assertNull(text.getSegments().get(1).color());
        assertNull(text.getSegments().get(1).bold());
        assertEquals("ab", text.getPlain());
    }
}
