package ink.tuanzi.neomacros.engine;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Minimal runtime probe: GraalJS must execute on the stock Java 17 JVM. */
class GraalRuntimeProbeTest {

    @Test
    void jsEvaluatesOnStockJvm() {
        try (Context context = Context.create("js")) {
            assertEquals(2, context.eval("js", "1 + 1").asInt());
        }
    }
}
