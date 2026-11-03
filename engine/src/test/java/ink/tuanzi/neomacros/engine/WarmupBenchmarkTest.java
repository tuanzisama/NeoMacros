package ink.tuanzi.neomacros.engine;

import ink.tuanzi.neomacros.core.event.EventBus;
import ink.tuanzi.neomacros.core.event.EventCatalog;
import ink.tuanzi.neomacros.core.runtime.ScriptContext;
import ink.tuanzi.neomacros.core.runtime.ScriptRuntime;
import ink.tuanzi.neomacros.core.thread.ScriptThreadPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Warmup benchmark behind risk item R1/R2: measures the first engine session
 * (cold: context creation + language initialization + module import) against
 * subsequent sessions reusing the warmed language. Prints the numbers for the
 * execution report; asserts only that imports complete.
 */
class WarmupBenchmarkTest {

    @TempDir
    Path scriptsDir;

    @Test
    void coldVersusWarmSessionCosts() throws Exception {
        Path entry = scriptsDir.resolve("bench.mjs");
        Files.writeString(entry,
                "import { marker } from '@neomacros/core';\n"
                + "globalThis.__marker = marker;\n");

        ModuleResolver resolver = new ModuleResolver() {
            @Override
            public Optional<String> virtualSource(String specifier) {
                return "@neomacros/core".equals(specifier)
                        ? Optional.of("export const marker = 'ok';\n")
                        : Optional.empty();
            }

            @Override
            public Optional<Path> fileFor(String specifier) {
                return Optional.empty();
            }
        };

        try (ScriptThreadPool pool = new ScriptThreadPool(2);
             GraalJsEngine engine = new GraalJsEngine(resolver);
             ScriptRuntime runtime = new ScriptRuntime(engine, null,
                     new EventCatalog(), new EventBus(new EventCatalog(), Runnable::run), pool, 500)) {
            List<Long> sessionMillis = new ArrayList<>();
            List<ScriptContext> contexts = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                ScriptContext context = runtime.createContext("bench-" + i);
                contexts.add(context);
                long start = System.nanoTime();
                var session = (GraalJsEngine.GraalJsSession) engine.openSession(context, scriptsDir);
                session.installBindings(java.util.Map.of("core", java.util.Map.of("marker", "ok")));
                session.importModule("bench", entry);
                long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
                sessionMillis.add(elapsedMillis);
                assertTrue(session.global("__marker").asString().equals("ok"));
                session.close();
            }
            contexts.forEach(ScriptContext::close);
            System.out.println("[warmup-benchmark] session times (ms): " + sessionMillis);
            System.out.println("[warmup-benchmark] cold=" + sessionMillis.get(0)
                    + "ms warm-avg=" + sessionMillis.subList(1, sessionMillis.size()).stream()
                    .mapToLong(Long::longValue).average().orElse(0) + "ms");
        }
    }
}
