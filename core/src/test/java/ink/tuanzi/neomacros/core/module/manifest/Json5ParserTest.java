package ink.tuanzi.neomacros.core.module.manifest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON5 reader coverage: comments, unquoted keys, trailing commas, string
 * escapes, number forms, and the rejection paths.
 */
class Json5ParserTest {

    @Test
    void parsesPlainJson() {
        Map<String, Object> out = Json5Parser.parseObject("""
                {"name": "demo", "count": 2, "ok": true, "none": null,
                 "list": [1, "two", false]}
                """);
        assertEquals("demo", out.get("name"));
        assertEquals(2.0, out.get("count"));
        assertEquals(true, out.get("ok"));
        assertEquals(null, out.get("none"));
        assertEquals(List.of(1.0, "two", false), out.get("list"));
    }

    @Test
    void supportsCommentsUnquotedKeysAndTrailingCommas() {
        Map<String, Object> out = Json5Parser.parseObject("""
                // leading comment
                {
                    /* block
                       comment */
                    name: 'demo',      // inline comment
                    'quoted-key': 1,
                    flags: [
                        true,
                        false,   // trailing comma in array
                    ],
                    nested: { a: .5, },  // trailing comma in object
                }
                """);
        assertEquals("demo", out.get("name"));
        assertEquals(1.0, out.get("quoted-key"));
        assertEquals(List.of(true, false), out.get("flags"));
        assertEquals(Map.of("a", 0.5), out.get("nested"));
    }

    @Test
    void parsesStringEscapes() {
        Object value = Json5Parser.parseValue("'a\\'b\\\\c\\n\\t\\x41\\u00e9'");
        assertEquals("a'b\\c\n\tAé", value);
    }

    @Test
    void parsesLineContinuation() {
        Object value = Json5Parser.parseValue("\"one\\\n        two\"");
        assertEquals("onetwo", value);
    }

    @Test
    void parsesNumberForms() {
        assertEquals(0.5, Json5Parser.parseValue(".5"));
        assertEquals(5.0, Json5Parser.parseValue("5."));
        assertEquals(-3.5, Json5Parser.parseValue("-3.5"));
        assertEquals(255.0, Json5Parser.parseValue("0xff"));
        assertEquals(1_000.0, Json5Parser.parseValue("1_000"));
        assertEquals(150.0, Json5Parser.parseValue("1.5e2"));
        assertEquals(150.0, Json5Parser.parseValue("1.5E+2"));
        assertEquals(Double.POSITIVE_INFINITY, Json5Parser.parseValue("Infinity"));
        assertEquals(Double.NEGATIVE_INFINITY, Json5Parser.parseValue("-Infinity"));
        assertTrue(Double.isNaN((Double) Json5Parser.parseValue("NaN")));
    }

    @Test
    void keywordsDoNotAbsorbIdentifierSuffixes() {
        assertThrows(Json5Parser.Json5SyntaxException.class, () -> Json5Parser.parseValue("truex"));
        assertThrows(Json5Parser.Json5SyntaxException.class, () -> Json5Parser.parseValue("nullish"));
    }

    @Test
    void rejectsMalformedDocuments() {
        assertThrows(Json5Parser.Json5SyntaxException.class, () -> Json5Parser.parseObject("'unterminated"));
        assertThrows(Json5Parser.Json5SyntaxException.class, () -> Json5Parser.parseObject("{\"a\" 1}"));
        assertThrows(Json5Parser.Json5SyntaxException.class,
                () -> Json5Parser.parseObject("[1, 2 'oops']"));
        assertThrows(Json5Parser.Json5SyntaxException.class,
                () -> Json5Parser.parseObject("/* unterminated"));
        assertThrows(Json5Parser.Json5SyntaxException.class, () -> Json5Parser.parseObject("42"));
        assertThrows(Json5Parser.Json5SyntaxException.class, () -> Json5Parser.parseObject("{} extra"));
        assertThrows(Json5Parser.Json5SyntaxException.class, () -> Json5Parser.parseValue("0x"));
        assertThrows(Json5Parser.Json5SyntaxException.class, () -> Json5Parser.parseValue("+"));
    }
}
