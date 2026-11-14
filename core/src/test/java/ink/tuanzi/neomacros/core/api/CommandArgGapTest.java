package ink.tuanzi.neomacros.core.api;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-026: the declarative slot vocabulary behind the CommandBuilder
 * members — the new long/uuid/identifier/time/literal kinds, numeric
 * {@code range(min, max)}, quote-aware {@code string} slots and the raw
 * greedy slice.
 */
class CommandArgGapTest {

    private static Map<String, CommandArg> slots(Object... pairs) {
        Map<String, CommandArg> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), (CommandArg) pairs[i + 1]);
        }
        return map;
    }

    @Test
    void newKindsParseTheirTokens() {
        Map<String, Object> parsed = CommandArg.parseAll(slots(
                "count", CommandArg.of("long"),
                "id", CommandArg.of("uuid"),
                "block", CommandArg.of("identifier"),
                "delay", CommandArg.of("time")),
                "9007199254740993 069a79f4-44e9-4726-a5be-fca90e38aaf5"
                        + " minecraft:gold_block 2s");

        assertEquals(9007199254740993L, parsed.get("count"),
                "long slots keep 64-bit precision");
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", parsed.get("id"),
                "uuid slots normalize to the lowercase canonical text");
        assertEquals("minecraft:gold_block", parsed.get("block"));
        assertEquals(40, parsed.get("delay"), "2s is 40 ticks");
    }

    @Test
    void timeUnitsFollowTheVanillaSyntax() {
        assertEquals(5, CommandArg.parseAll(
                slots("d", CommandArg.of("time")), "5t").get("d"));
        assertEquals(24000 * 3, CommandArg.parseAll(
                slots("d", CommandArg.of("time")), "3d").get("d"));
        assertThrows(IllegalArgumentException.class, () -> CommandArg.parseAll(
                slots("d", CommandArg.of("time")), "5"), "the unit is required");
        assertThrows(IllegalArgumentException.class, () -> CommandArg.parseAll(
                slots("d", CommandArg.of("time")), "5m"), "unknown units are rejected");
    }

    @Test
    void invalidTokensAreRejectedWithTheSlotName() {
        var failure = assertThrows(IllegalArgumentException.class, () ->
                CommandArg.parseAll(slots("id", CommandArg.of("uuid")), "not-a-uuid"));
        assertTrue(failure.getMessage().contains("id"), failure.getMessage());
        assertTrue(failure.getMessage().contains("not-a-uuid"), failure.getMessage());

        assertThrows(IllegalArgumentException.class, () -> CommandArg.parseAll(
                slots("b", CommandArg.of("identifier")), "Minecraft:Gold"),
                "identifiers stay lowercase namespace:path");
        assertThrows(IllegalArgumentException.class, () -> CommandArg.parseAll(
                slots("n", CommandArg.of("int")), "3.5"));
    }

    @Test
    void rangeBoundsAcceptTheEdgesAndRejectTheRest() {
        Map<String, CommandArg> bounded = slots(
                "amount", CommandArg.of("int").range(1, 64),
                "ratio", CommandArg.of("float").range(0.0, 1.0),
                "tick", CommandArg.of("long").range(0L, 72000L));

        assertEquals(1, CommandArg.parseAll(bounded, "1 0.5 600").get("amount"));
        assertEquals(64, CommandArg.parseAll(bounded, "64 1.0 72000").get("amount"));
        assertEquals(0.5, CommandArg.parseAll(bounded, "32 0.5 600").get("ratio"));

        var tooBig = assertThrows(IllegalArgumentException.class, () ->
                CommandArg.parseAll(bounded, "65 0.5 600"));
        assertTrue(tooBig.getMessage().contains("out of range [1, 64]"),
                tooBig.getMessage());
        assertThrows(IllegalArgumentException.class, () ->
                CommandArg.parseAll(bounded, "32 1.5 600"));
        assertThrows(IllegalArgumentException.class, () ->
                CommandArg.parseAll(bounded, "32 0.5 -1"));
    }

    @Test
    void rangeIsOnlyForNumericSlotsAndRejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> CommandArg.of("word").range(1, 10),
                "range() must not attach to non-numeric slots");
        assertThrows(IllegalArgumentException.class,
                () -> CommandArg.of("int").range(10, 1),
                "min must not exceed max");
        assertThrows(IllegalArgumentException.class,
                () -> CommandArg.of("int").range(null, 10));
    }

    @Test
    void quotedStringSlotsKeepSpacesWhileWordSlotsRejectQuotes() {
        Map<String, CommandArg> quoted = slots(
                "title", CommandArg.of("string"),
                "rest", CommandArg.of("greedy"));
        Map<String, Object> parsed = CommandArg.parseAll(quoted,
                "\"hello world\" and   the rest");
        assertEquals("hello world", parsed.get("title"),
                "quotes are stripped, spaces survive");
        assertEquals("and   the rest", parsed.get("rest"),
                "greedy hands back the raw slice with its spacing");

        assertThrows(IllegalArgumentException.class, () -> CommandArg.parseAll(
                slots("w", CommandArg.of("word")), "\"two words\""),
                "a word is one unquoted token");
        assertThrows(IllegalArgumentException.class, () -> CommandArg.parseAll(
                slots("s", CommandArg.of("string")), "\"unterminated"),
                "unterminated quotes are rejected");
    }

    @Test
    void literalSlotsMatchTheKeyOrTheExplicitWord() {
        Map<String, Object> byKey = CommandArg.parseAll(
                slots("set", CommandArg.of("literal")), "set");
        assertEquals("set", byKey.get("set"));

        Map<String, CommandArg> explicit = slots(
                "verb", CommandArg.of("literal", new Object[]{"show"}),
                "amount", CommandArg.of("int"));
        assertEquals("show", CommandArg.parseAll(explicit, "show 7").get("verb"));

        var mismatch = assertThrows(IllegalArgumentException.class, () ->
                CommandArg.parseAll(explicit, "hide 7"));
        assertTrue(mismatch.getMessage().contains("expected literal 'show'"),
                mismatch.getMessage());
        // The mismatch must not be swallowed by the slot wrapper.
        assertTrue(mismatch.getMessage().contains("verb"), mismatch.getMessage());
    }

    @Test
    void optionalAndDefaultSurviveRangeAndKindChanges() {
        Map<String, Object> parsed = CommandArg.parseAll(slots(
                "times", CommandArg.of("int").range(1, 8).defaultTo(2)), "");
        assertEquals(2, parsed.get("times"), "default applies within a range");
        assertThrows(IllegalArgumentException.class, () -> CommandArg.parseAll(
                slots("times", CommandArg.of("int").range(1, 8).defaultTo(9)), "9"),
                "the default itself is still validated against the range");
    }

    @Test
    void theFactoryAnswersTheExtendedVocabulary() {
        CommandApi.CommandArgFactory factory = new CommandApi.CommandArgFactory();
        assertTrue(factory.hasMember("literal"));
        assertTrue(factory.hasMember("uuid"));
        assertTrue(factory.hasMember("long"));
        assertFalse(factory.hasMember("createCommandBuilder"),
                "the factory never grows foreign builder names");

        assertEquals(java.util.List.of("int", "long", "float", "bool", "word",
                        "string", "greedy", "player", "pos", "uuid", "identifier",
                        "time", "literal"),
                CommandApi.argKindNames());

        Object literal = factory.invoke("literal", new Object[]{"set"});
        assertTrue(literal instanceof ink.tuanzi.neomacros.core.api.java.JavaObject);
        Object slot = ((ink.tuanzi.neomacros.core.api.java.JavaObject) literal).raw();
        assertTrue(slot instanceof CommandArg);
        assertEquals("set", CommandArg.parseAll(
                slots("verb", (CommandArg) slot), "set").get("verb"));
    }

    @Test
    void slotsFromStillRequiresFactoryBuiltSlots() {
        assertThrows(IllegalArgumentException.class, () ->
                CommandArg.slotsFrom(Map.of("x", "not-a-slot")));
        assertEquals(0, CommandArg.slotsFrom(null).size());
    }
}
