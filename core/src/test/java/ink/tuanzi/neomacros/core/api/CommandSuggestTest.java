package ink.tuanzi.neomacros.core.api;

import ink.tuanzi.neomacros.core.api.types.BlockPos;
import ink.tuanzi.neomacros.core.api.types.TextComponent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-026B completion surface: slot metadata routing through
 * {@link CommandArg#suggestAt}, the {@link SuggestionsBuilder} member face,
 * and the flat parser's ownership of the whole line.
 */
class CommandSuggestTest {

    private static Map<String, CommandArg> slots(Object... pairs) {
        Map<String, CommandArg> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), (CommandArg) pairs[i + 1]);
        }
        return map;
    }

    private static List<String> texts(SuggestionsBuilder builder) {
        return builder.entries().stream().map(SuggestionsBuilder.Entry::text).toList();
    }

    // ---- routing ----------------------------------------------------------

    @Test
    void literalSlotsOfferTheirFixedWordForTheFirstToken() {
        Map<String, CommandArg> slots = slots("verb", CommandArg.of("literal", new Object[]{"set"}));

        assertEquals(List.of("set"),
                texts(CommandArg.suggestAt(slots, "g026 se", "g026 ".length())));
        assertTrue(texts(CommandArg.suggestAt(slots, "g026 hide",
                "g026 ".length())).isEmpty(),
                "a mismatched literal prefix must not offer the word");
    }

    @Test
    void aTrailingSpaceMovesCompletionToTheNextSlot() {
        Map<String, CommandArg> slots = slots(
                "verb", CommandArg.of("literal", new Object[]{"set"}),
                "mode", CommandArg.of("word").suggests(List.of("peaceful", "easy")));

        assertEquals(List.of("peaceful", "easy"),
                texts(CommandArg.suggestAt(slots, "g026 set ",
                        "g026 ".length())));
        assertEquals(List.of("peaceful"),
                texts(CommandArg.suggestAt(slots, "g026 set pe",
                        "g026 ".length())));
    }

    @Test
    void pastTheDeclaredSlotsNothingIsQueued() {
        Map<String, CommandArg> slots = slots("verb", CommandArg.of("literal", new Object[]{"set"}));

        assertTrue(texts(CommandArg.suggestAt(slots, "g026 set extra",
                "g026 ".length())).isEmpty());
        assertTrue(texts(CommandArg.suggestAt(Map.of(), "g026 x",
                "g026 ".length())).isEmpty());
    }

    @Test
    void unterminatedQuotesStaySilent() {
        Map<String, CommandArg> slots = slots("note", CommandArg.of("string"));

        assertTrue(texts(CommandArg.suggestAt(slots, "g026 \"open",
                "g026 ".length())).isEmpty());
    }

    @Test
    void theCallbackReceivesTheBuilderAtTheToken() {
        SuggestionsBuilder[] seen = new SuggestionsBuilder[1];
        Map<String, CommandArg> slots = slots("name", CommandArg.of("word")
                .suggests((Consumer<Object[]>) args -> {
                    SuggestionsBuilder builder = (SuggestionsBuilder) args[0];
                    seen[0] = builder;
                    builder.suggestMatching(List.of("alpha", "beta"))
                            .suggest("gamma");
                }));

        assertEquals(List.of("alpha", "gamma"),
                texts(CommandArg.suggestAt(slots, "g026 a", "g026 ".length())),
                "matching candidates must be prefix-filtered, raw ones pass");
        assertNotNull(seen[0]);
        assertEquals("g026 a", seen[0].getInput());
        assertEquals("g026 ".length(), seen[0].getStart());
        assertEquals("a", seen[0].getRemaining());
        assertEquals("a", seen[0].getRemainingLowerCase());
        assertEquals(List.of("alpha", "beta", "gamma"),
                texts(CommandArg.suggestAt(slots, "g026 ", "g026 ".length())));
    }

    // ---- slot metadata ----------------------------------------------------

    @Test
    void suggestsValidatesItsArgumentAndStaysImmutable() {
        CommandArg base = CommandArg.of("word");
        assertThrows(IllegalArgumentException.class, () -> base.suggests("nope"));
        assertThrows(IllegalArgumentException.class, () -> base.suggests(List.of("a", 1)));
        assertThrows(IllegalArgumentException.class, () -> base.suggests(42));

        CommandArg listed = base.suggests(List.of("a", "b"));
        CommandArg called = base.suggests((Consumer<Object[]>) args -> { });
        assertNull(base.suggestSpec(), "the original slot must stay untouched");
        assertEquals(List.of("a", "b"), listed.suggestSpec());
        assertNotNull(called.suggestSpec());
        assertTrue(called.suggestSpec() instanceof Consumer<?>);
    }

    // ---- builder members --------------------------------------------------

    @Test
    void suggestFormatsNumbersAndDropsTheExactRemaining() {
        SuggestionsBuilder builder = new SuggestionsBuilder("g026 3", "g026 ".length());
        builder.suggest(3).suggest(3.5).suggest("three");
        assertEquals(List.of("3.5", "three"), texts(builder),
                "the integral form of the remaining text is dropped, 3.5 is not");

        SuggestionsBuilder exact = new SuggestionsBuilder("g026 3", "g026 ".length());
        exact.suggest(3);
        assertTrue(texts(exact).isEmpty(), "a value equal to the rest is suppressed");
    }

    @Test
    void suggestWithTooltipKeepsTheTooltip() {
        SuggestionsBuilder builder = new SuggestionsBuilder("g026 ", "g026 ".length());
        builder.suggestWithTooltip("tagged", TextComponent.of("hint"))
                .suggestWithTooltip(7, TextComponent.of("count"));
        List<SuggestionsBuilder.Entry> entries = builder.entries();
        assertEquals(2, entries.size());
        assertEquals("tagged", entries.get(0).text());
        assertNotNull(entries.get(0).tooltip());
        assertEquals("7", entries.get(1).text());
        assertNotNull(entries.get(1).tooltip());
        assertThrows(IllegalArgumentException.class,
                () -> builder.suggestWithTooltip("x", null));
    }

    @Test
    void suggestIdentifierKeepsWellFormedEntriesOnly() {
        SuggestionsBuilder builder = new SuggestionsBuilder("g026 ", "g026 ".length());
        builder.suggestIdentifier(List.of("minecraft:stone", "diamond", "Bad ID"));
        assertEquals(List.of("minecraft:stone", "diamond"), texts(builder),
                "malformed identifiers are skipped, not raised");
    }

    @Test
    void suggestBlockPositionsFormatsCoordinates() {
        SuggestionsBuilder builder = new SuggestionsBuilder("g026 ", "g026 ".length());
        builder.suggestBlockPositions(List.of(new BlockPos(0, 64, 0),
                new BlockPos(100, 64, -5)));
        assertEquals(List.of("0 64 0", "100 64 -5"), texts(builder));
    }

    @Test
    void suggestPositionsValidatesShapeThenFilters() {
        SuggestionsBuilder mixed = new SuggestionsBuilder("g026 ", "g026 ".length());
        assertThrows(IllegalArgumentException.class,
                () -> mixed.suggestPositions(List.of("1 2", "1 2 3")),
                "a mixed component count is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> mixed.suggestPositions(List.of("1 2 3 4")),
                "four components are rejected");
        mixed.suggestPositions(List.of());
        assertTrue(texts(mixed).isEmpty(), "an empty batch queues nothing");

        SuggestionsBuilder ok = new SuggestionsBuilder("g026 0 ", "g026 ".length());
        ok.suggestPositions(List.of("0 64 0", "100 64 100"));
        assertEquals(List.of("0 64 0"), texts(ok),
                "candidates are filtered against the typed text");
    }

    // ---- flat parser owns the line ---------------------------------------

    @Test
    void parseAllRejectsTrailingInputBeyondTheSlots() {
        Map<String, CommandArg> slots = slots("times", CommandArg.of("int"));
        assertEquals(Map.of("times", 3),
                CommandArg.parseAll(slots, "3"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CommandArg.parseAll(slots, "3 extra"));
        assertTrue(error.getMessage().contains("unexpected input 'extra'"),
                error.getMessage());
    }

    @Test
    void parseAllGreedyClosesTheLine() {
        Map<String, CommandArg> slots = slots(
                "verb", CommandArg.of("literal", new Object[]{"set"}),
                "rest", CommandArg.of("greedy"));
        assertEquals(Map.of("verb", "set", "rest", "and   the rest"),
                CommandArg.parseAll(slots, "set and   the rest"));
    }

    // ---- facade -----------------------------------------------------------

    @Test
    void theFacadeDelegatesNamesAndSuggestions() throws Exception {
        CommandRegistry backing = new CommandRegistry() {
            @Override
            public Object register(Map<String, Object> spec) {
                return spec.get("name");
            }

            @Override
            public void unregister(String name) {
            }

            @Override
            public CompletableFuture<Boolean> execute(String commandLine) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public List<String> names() {
                return List.of("g026", "help");
            }

            @Override
            public CompletableFuture<List<String>> suggestions(String commandPart) {
                return CompletableFuture.completedFuture(List.of("set"));
            }
        };
        CommandApi api = new CommandApi(backing);

        assertEquals(List.of("g026", "help"), api.getNames());
        assertEquals(List.of("set"), api.suggestions("g026 se").get());
    }

    @Test
    void anEmptyRegistryListsNothing() throws Exception {
        CommandRegistry backing = new CommandRegistry() {
            @Override
            public Object register(Map<String, Object> spec) {
                return spec.get("name");
            }

            @Override
            public void unregister(String name) {
            }

            @Override
            public CompletableFuture<Boolean> execute(String commandLine) {
                return CompletableFuture.completedFuture(true);
            }
        };
        CommandApi api = new CommandApi(backing);

        assertTrue(api.getNames().isEmpty());
        assertTrue(api.suggestions("anything").get().isEmpty());
        assertFalse(api.suggestions("anything").get().contains("anything"));
    }
}
