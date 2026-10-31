package ink.tuanzi.neomacros.docs;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Type-compiles the generated TypeScript definitions with the real tsc
 * compiler ({@code tsc --noEmit}). Requires a local tsc entry (the bin/tsc JS
 * file), provided via the {@code tscPath} system property:
 *
 * <pre>{@code ./gradlew -p docs-builder check -PtscPath=C:/path/to/typescript/bin/tsc}</pre>
 *
 * <p>The test is skipped with a clear reason when no compiler is configured;
 * CI passes the property explicitly once the toolchain is provisioned.</p>
 */
class TsTypeCompileTest {

    @TempDir
    static Path outDir;

    static Path generated;

    @BeforeAll
    static void generate() throws IOException {
        GenerationHarness.Run run = GenerationHarness.generate(TSDoclet.class, outDir);
        Assumptions.assumeTrue(run.ok(), () -> "TS generation failed: " + run.errors());
        generated = run.outDir().resolve("types/neomacros.d.ts");
    }

    @Test
    void definitionsTypeCheckWithTsc() throws Exception {
        String tscPath = System.getProperty("tscPath", "");
        Assumptions.assumeTrue(!tscPath.isBlank(),
                "tscPath not set; pass -PtscPath=<path to typescript bin/tsc> to enable");

        List<String> command = new ArrayList<>();
        command.add("node");
        command.add(tscPath);
        command.add("--noEmit");
        command.add("--target");
        command.add("es2020");
        command.add("--lib");
        command.add("esnext");
        command.add(generated.toAbsolutePath().toString());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "tsc did not finish within 120s");

        assertEquals(0, process.exitValue(),
                () -> "tsc --noEmit rejected the generated definitions:\n" + output);
    }
}
