package ink.tuanzi.neomacros.engine;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The length-cap contract of the neutral node model: everyday console
 * emissions keep the 2000-char string / 4000-char stack caps, while the
 * full-length formatter used by DevTools eval delivers strings and stacks
 * intact (oversized payloads travel as SSE segment frames instead).
 */
class ConsoleFormatterTest {

    @Test
    void defaultFormatterCapsStringsAtTheContractLength() {
        try (Context context = Context.create("js")) {
            Value value = context.eval("js", "'x'.repeat(5000)");
            ConsoleFormatter formatter = new ConsoleFormatter(context, ConsoleFormatter.MAX_NODES);
            var node = formatter.node(value);
            assertEquals("str", node.get("t"));
            String text = (String) node.get("v");
            assertEquals(ConsoleFormatter.MAX_STRING + 1, text.length(),
                    "capped strings stop at the limit plus the ellipsis");
            assertTrue(text.endsWith("…"));
        }
    }

    @Test
    void fullLengthFormatterDeliversStringsIntact() {
        try (Context context = Context.create("js")) {
            Value value = context.eval("js", "'x'.repeat(5000)");
            ConsoleFormatter formatter =
                    new ConsoleFormatter(context, ConsoleFormatter.MAX_NODES, true);
            var node = formatter.node(value);
            assertEquals("str", node.get("t"));
            assertEquals(5000, ((String) node.get("v")).length());
        }
    }

    @Test
    void fullLengthFormatterDeliversErrorStacksIntact() {
        try (Context context = Context.create("js")) {
            Value value = context.eval("js",
                    "({name: 'Error', message: 'boom', stack: 'z'.repeat(9000)})");
            ConsoleFormatter formatter =
                    new ConsoleFormatter(context, ConsoleFormatter.MAX_NODES, true);
            var node = formatter.node(value);
            assertEquals("err", node.get("t"));
            assertEquals(9000, ((String) node.get("stack")).length());
        }
    }

    @Test
    void defaultFormatterCapsErrorStacks() {
        try (Context context = Context.create("js")) {
            Value value = context.eval("js",
                    "({name: 'Error', message: 'boom', stack: 'z'.repeat(9000)})");
            ConsoleFormatter formatter = new ConsoleFormatter(context, ConsoleFormatter.MAX_NODES);
            var node = formatter.node(value);
            assertEquals("err", node.get("t"));
            String stack = (String) node.get("stack");
            assertEquals(ConsoleFormatter.MAX_STACK + 1, stack.length());
            assertTrue(stack.endsWith("…"));
        }
    }
}
