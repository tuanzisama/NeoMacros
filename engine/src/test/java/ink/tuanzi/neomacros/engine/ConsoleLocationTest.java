package ink.tuanzi.neomacros.engine;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Call-site resolution out of guest stack captures: glue frames skipped,
 * Windows file paths and file URIs both relativized, non-file sources
 * left unresolved.
 */
class ConsoleLocationTest {

    private static final Path ROOT = Path.of("C:\\macros\\scripts").toAbsolutePath();

    @Test
    void glueFramesAreSkippedAndOsPathsRelativized() {
        String stack = "Error\n"
                + "    at send (neomacros-engine-glue:50:21)\n"
                + "    at Object.log (neomacros-engine-glue:58:13)\n"
                + "    at " + ROOT.resolve("welcome\\index.js").toString() + ":12:5";
        ConsoleLocation.Location location = ConsoleLocation.parse(stack, ROOT);
        assertEquals("welcome/index.js", location.file());
        assertEquals(12, location.line());
        assertEquals(5, location.column());
    }

    @Test
    void fileUriSourcesRelativize() {
        String stack = "Error\n"
                + "    at send (neomacros-engine-glue:50:21)\n"
                + "    at " + ROOT.resolve("lib/util.js").toUri() + ":3:1";
        ConsoleLocation.Location location = ConsoleLocation.parse(stack, ROOT);
        assertEquals("lib/util.js", location.file());
        assertEquals(3, location.line());
    }

    @Test
    void nonFileSourcesStayUnresolved() {
        String stack = "Error\n"
                + "    at send (neomacros-engine-glue:50:21)\n"
                + "    at @neomacros/core:7:2";
        ConsoleLocation.Location location = ConsoleLocation.parse(stack, ROOT);
        assertNull(location.file());
        assertEquals(7, location.line());
    }

    @Test
    void emptyStacksResolveToNothing() {
        assertEquals(ConsoleLocation.Location.NONE, ConsoleLocation.parse("", ROOT));
        assertEquals(ConsoleLocation.Location.NONE, ConsoleLocation.parse(null, ROOT));
    }

    @Test
    void outsideRootsStayUnresolved() {
        String stack = "Error\n"
                + "    at C:\\somewhere\\else\\index.js:1:1";
        ConsoleLocation.Location location = ConsoleLocation.parse(stack, ROOT);
        assertNull(location.file());
    }
}
